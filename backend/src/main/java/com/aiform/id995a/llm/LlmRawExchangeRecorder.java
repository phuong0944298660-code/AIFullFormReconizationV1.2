package com.aiform.id995a.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

public final class LlmRawExchangeRecorder {
  private static final AtomicInteger SEQUENCE = new AtomicInteger();
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private LlmRawExchangeRecorder() {
  }

  public static boolean enabled() {
    return truthy(System.getenv("LLM_RAW_LOG_ENABLED")) || !blank(System.getenv("LLM_RAW_LOG_DIR"));
  }

  public static void record(
      String caller,
      URI uri,
      String requestBody,
      int statusCode,
      String responseBody
  ) {
    record("primary-llm", caller, caller, "", uri, requestBody, statusCode, responseBody);
  }

  public static void record(
      String modelType,
      String caller,
      String purpose,
      String model,
      URI uri,
      String requestBody,
      int statusCode,
      String responseBody
  ) {
    if (!enabled()) {
      return;
    }
    try {
      recordTo(
          outputRoot(), modelType, caller, purpose, model, uri, requestBody, statusCode, responseBody
      );
    } catch (IOException ignored) {
      // Raw model capture is diagnostic only; never fail recognition because logging failed.
    }
  }

  static String recordTo(
      Path root,
      String modelType,
      String caller,
      String purpose,
      String model,
      URI uri,
      String requestBody,
      int statusCode,
      String responseBody
  ) throws IOException {
    Files.createDirectories(root);
    int sequence = SEQUENCE.incrementAndGet();
    String safeModelType = sanitize(modelType);
    String safeCaller = sanitize(caller);
    String prefix = "%03d-%s-%s".formatted(sequence, safeModelType, safeCaller);
    TokenUsage usage = tokenUsage(responseBody);
    Files.writeString(root.resolve(prefix + "-request.json"), value(requestBody), StandardCharsets.UTF_8);
    Files.writeString(root.resolve(prefix + "-response.json"), value(responseBody), StandardCharsets.UTF_8);
    Files.writeString(
        root.resolve(prefix + "-meta.txt"),
        """
        timestamp=%s
        model_type=%s
        model=%s
        caller=%s
        purpose=%s
        uri=%s
        status=%d
        input_tokens=%s
        output_tokens=%s
        total_tokens=%s
        request_file=%s-request.json
        response_file=%s-response.json
        """.formatted(
            OffsetDateTime.now(), value(modelType), value(model), value(caller), value(purpose), uri, statusCode,
            usage.inputTokens(), usage.outputTokens(), usage.totalTokens(), prefix, prefix
        ),
        StandardCharsets.UTF_8
    );
    return prefix;
  }

  private static TokenUsage tokenUsage(String responseBody) {
    if (blank(responseBody)) {
      return TokenUsage.unavailable();
    }
    try {
      JsonNode usage = OBJECT_MAPPER.readTree(responseBody).path("usage");
      return new TokenUsage(
          tokenValue(usage, "prompt_tokens", "input_tokens"),
          tokenValue(usage, "completion_tokens", "output_tokens"),
          tokenValue(usage, "total_tokens")
      );
    } catch (IOException | RuntimeException ignored) {
      return TokenUsage.unavailable();
    }
  }

  private static String tokenValue(JsonNode usage, String... names) {
    if (usage == null || !usage.isObject()) {
      return "unavailable";
    }
    for (String name : names) {
      JsonNode value = usage.path(name);
      if (value.isIntegralNumber()) {
        return value.asText();
      }
    }
    return "unavailable";
  }

  private static Path outputRoot() {
    String configured = System.getenv("LLM_RAW_LOG_DIR");
    if (!blank(configured)) {
      return Path.of(configured);
    }
    String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(java.time.LocalDateTime.now());
    return Path.of("..", "outputs", "llm-raw-" + stamp);
  }

  private static String sanitize(String value) {
    String normalized = value == null ? "llm" : value.toLowerCase(Locale.ROOT);
    normalized = normalized.replaceAll("[^a-z0-9._-]+", "-");
    normalized = normalized.replaceAll("^-+|-+$", "");
    return normalized.isBlank() ? "llm" : normalized;
  }

  private static boolean truthy(String value) {
    if (blank(value)) {
      return false;
    }
    String normalized = value.trim().toLowerCase(Locale.ROOT);
    return normalized.equals("1") || normalized.equals("true") || normalized.equals("yes") || normalized.equals("on");
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private static String value(String value) {
    return value == null ? "" : value;
  }

  private record TokenUsage(String inputTokens, String outputTokens, String totalTokens) {
    private static TokenUsage unavailable() {
      return new TokenUsage("unavailable", "unavailable", "unavailable");
    }
  }
}
