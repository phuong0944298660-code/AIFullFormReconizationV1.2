package com.aiform.id995a.ocr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class QwenFieldJudgeClient implements FieldJudgeGateway {

  private static final Set<String> MATCH_TYPES = Set.of(
      "exact", "normalized_equal", "semantic_equal", "mismatch", "unreadable", "crop_incomplete"
  );
  private static final Set<String> LEGIBILITY = Set.of("clear", "partially_clear", "unreadable");
  private static final Set<String> CROP_COVERAGE = Set.of("complete", "partial", "missing");

  private final FieldJudgeProperties properties;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;
  private final Semaphore concurrencyLimit;

  @Autowired
  public QwenFieldJudgeClient(FieldJudgeProperties properties, ObjectMapper objectMapper) {
    this(
        properties,
        objectMapper,
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(properties.timeoutSeconds())).build()
    );
  }

  QwenFieldJudgeClient(
      FieldJudgeProperties properties,
      ObjectMapper objectMapper,
      HttpClient httpClient
  ) {
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.httpClient = httpClient;
    this.concurrencyLimit = new Semaphore(properties.concurrency());
  }

  @Override
  public FieldJudgeObservation judge(
      String fieldKey,
      String fieldLabel,
      String expectedValue,
      String valueType,
      String snapshotDataUrl
  ) {
    if (!properties.enabled()) {
      return FieldJudgeObservation.unavailable("judge_disabled");
    }
    if (properties.baseUrl().isBlank() || properties.apiKey().isBlank()) {
      return FieldJudgeObservation.unavailable("judge_not_configured");
    }
    if (snapshotDataUrl == null || snapshotDataUrl.isBlank()) {
      return FieldJudgeObservation.unavailable("evidence_missing");
    }

    boolean permitAcquired = false;
    try {
      concurrencyLimit.acquire();
      permitAcquired = true;
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(trimTrailingSlash(properties.baseUrl()) + "/chat/completions"))
          .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
          .header("Authorization", "Bearer " + properties.apiKey())
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(
              objectMapper.writeValueAsString(buildPayload(
                  fieldKey, fieldLabel, expectedValue, valueType, snapshotDataUrl
              )),
              StandardCharsets.UTF_8
          ))
          .build();
      for (int attempt = 1; attempt <= 2; attempt += 1) {
        HttpResponse<String> response;
        try {
          response = httpClient.send(
              request,
              HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
          );
        } catch (IOException exception) {
          if (attempt == 2) {
            return FieldJudgeObservation.unavailable("network_error");
          }
          continue;
        }
        boolean retryable = response.statusCode() == 429 || response.statusCode() >= 500;
        if (retryable && attempt == 1) {
          continue;
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
          return FieldJudgeObservation.unavailable("http_" + response.statusCode());
        }
        try {
          return parseObservation(response.body());
        } catch (IOException exception) {
          return FieldJudgeObservation.unavailable("invalid_response");
        }
      }
      return FieldJudgeObservation.unavailable("network_error");
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return FieldJudgeObservation.unavailable("interrupted");
    } catch (IOException | RuntimeException exception) {
      return FieldJudgeObservation.unavailable("invalid_response");
    } finally {
      if (permitAcquired) {
        concurrencyLimit.release();
      }
    }
  }

  private ObjectNode buildPayload(
      String fieldKey,
      String fieldLabel,
      String expectedValue,
      String valueType,
      String snapshotDataUrl
  ) {
    ObjectNode payload = objectMapper.createObjectNode();
    payload.put("model", properties.model());
    payload.put("temperature", 0);
    payload.put("max_tokens", 256);
    payload.put("enable_thinking", false);
    payload.putObject("response_format").put("type", "json_object");

    ArrayNode messages = payload.putArray("messages");
    messages.addObject()
        .put("role", "system")
        .put("content", "You are an independent field evidence judge. Read only the supplied crop. "
            + "Return one JSON object with status, observedValue, matchType, legibility, cropCoverage, reason, reasonZhHant. "
            + "reason must be concise English. reasonZhHant must be the same concise explanation in Traditional Chinese. "
            + "Set status to available whenever you return this JSON object. "
            + "matchType must be exact, normalized_equal, semantic_equal, mismatch, unreadable, or crop_incomplete. "
            + "legibility must be clear, partially_clear, or unreadable. "
            + "cropCoverage must be complete, partial, or missing. Do not output a numeric score.");
    ArrayNode content = messages.addObject().put("role", "user").putArray("content");
    content.addObject().put("type", "text").put("text", buildUserPrompt(
        fieldKey, fieldLabel, expectedValue, valueType
    ));
    content.addObject().put("type", "image_url").putObject("image_url").put("url", snapshotDataUrl);
    return payload;
  }

  private String buildUserPrompt(
      String fieldKey,
      String fieldLabel,
      String expectedValue,
      String valueType
  ) {
    return "fieldKey: " + safe(fieldKey)
        + "\nfieldLabel: " + safe(fieldLabel)
        + "\nexpectedValue: " + safe(expectedValue)
        + "\nvalueType: " + safe(valueType)
        + "\nIndependently transcribe the filled value in the image, then compare it with expectedValue.";
  }

  private FieldJudgeObservation parseObservation(String responseBody) throws IOException {
    String content = objectMapper.readTree(responseBody).at("/choices/0/message/content").asText("");
    JsonNode observationNode = objectMapper.readTree(content);
    String matchType = observationNode.path("matchType").asText("");
    String legibility = observationNode.path("legibility").asText("");
    String cropCoverage = observationNode.path("cropCoverage").asText("");
    String observedValue = observationNode.path("observedValue").asText("");
    if (!MATCH_TYPES.contains(matchType)
        || !LEGIBILITY.contains(legibility)
        || !CROP_COVERAGE.contains(cropCoverage)
        || (observedValue.isBlank() && !Set.of("unreadable", "crop_incomplete").contains(matchType))) {
      return FieldJudgeObservation.unavailable("invalid_response");
    }
    return new FieldJudgeObservation(
        "available",
        observedValue,
        matchType,
        legibility,
        cropCoverage,
        observationNode.path("reason").asText(""),
        observationNode.path("reasonZhHant").asText("")
    );
  }

  private static String trimTrailingSlash(String value) {
    return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
  }

  private static String safe(String value) {
    return value == null ? "" : value;
  }
}
