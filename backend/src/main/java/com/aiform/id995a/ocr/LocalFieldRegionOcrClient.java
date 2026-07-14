package com.aiform.id995a.ocr;

import com.aiform.id995a.llm.LlmRawExchangeRecorder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.UUID;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LocalFieldRegionOcrClient implements FieldRegionOcrGateway {

  private final FieldOcrProperties properties;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;

  @Autowired
  public LocalFieldRegionOcrClient(FieldOcrProperties properties, ObjectMapper objectMapper) {
    this(properties, objectMapper, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
  }

  @Override
  public List<FieldLabelDetection> detectPage(byte[] pageImageBytes) throws IOException {
    if (!properties.enabled() || pageImageBytes == null || pageImageBytes.length == 0) {
      return List.of();
    }

    String boundary = "----page-ocr-" + UUID.randomUUID();
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(trimTrailingSlash(properties.baseUrl()) + "/ocr/page-detect"))
        .version(HttpClient.Version.HTTP_1_1)
        .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
        .POST(HttpRequest.BodyPublishers.ofByteArray(singleMultipartBody(boundary, pageImageBytes)))
        .build();

    try {
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      LlmRawExchangeRecorder.record(
          "ppocr-tiny",
          "page-detect",
          "Locate field label text and labelBbox on one rendered page",
          "PP-OCRv6 Tiny",
          request.uri(),
          objectMapper.createObjectNode()
              .put("image_count", 1)
              .put("image_bytes", pageImageBytes.length)
              .toString(),
          response.statusCode(),
          response.body()
      );
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        return List.of();
      }
      JsonNode lines = objectMapper.readTree(response.body()).path("lines");
      if (!lines.isArray()) {
        return List.of();
      }
      ArrayList<FieldLabelDetection> detections = new ArrayList<>();
      for (JsonNode line : lines) {
        List<Integer> bbox = parseBbox(line.path("bbox"));
        if (bbox.isEmpty()) {
          continue;
        }
        detections.add(new FieldLabelDetection(
            line.path("text").asText(""),
            normalizeConfidence(line.path("confidence").asDouble(0)),
            bbox,
            normalizeConfidence(line.path("detection_confidence").asDouble(
                line.path("confidence").asDouble(0)
            )),
            line.path("text_status").asText("")
        ));
      }
      return List.copyOf(detections);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return List.of();
    } catch (IOException exception) {
      return List.of();
    }
  }

  LocalFieldRegionOcrClient(
      FieldOcrProperties properties,
      ObjectMapper objectMapper,
      HttpClient httpClient
  ) {
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.httpClient = httpClient;
  }

  @Override
  public List<FieldRegionOcrResult> recognizeBatch(List<byte[]> cropImageBytes) throws IOException {
    if (!properties.enabled()) {
      return unavailableResults(cropImageBytes, "disabled");
    }
    if (cropImageBytes == null || cropImageBytes.isEmpty()) {
      return List.of();
    }

    String boundary = "----field-ocr-" + UUID.randomUUID();
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(trimTrailingSlash(properties.baseUrl()) + "/ocr/crop-recognize-batch"))
        .version(HttpClient.Version.HTTP_1_1)
        .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
        .POST(HttpRequest.BodyPublishers.ofByteArray(multipartBody(boundary, cropImageBytes)))
        .build();

    try {
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      long totalBytes = cropImageBytes.stream()
          .filter(java.util.Objects::nonNull)
          .mapToLong(bytes -> bytes.length)
          .sum();
      LlmRawExchangeRecorder.record(
          "ppocr-tiny",
          "crop-recognize-batch",
          "Recognize text in " + cropImageBytes.size() + " cropped field value image(s)",
          "PP-OCRv6 Tiny",
          request.uri(),
          objectMapper.createObjectNode()
              .put("image_count", cropImageBytes.size())
              .put("total_image_bytes", totalBytes)
              .toString(),
          response.statusCode(),
          response.body()
      );
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        return unavailableResults(cropImageBytes, "http_" + response.statusCode());
      }
      JsonNode root = objectMapper.readTree(response.body());
      JsonNode results = root.path("results");
      if (!results.isArray()) {
        return unavailableResults(cropImageBytes, "invalid_response");
      }
      return paddedResults(results, cropImageBytes.size());
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return unavailableResults(cropImageBytes, "interrupted");
    } catch (IOException exception) {
      return unavailableResults(cropImageBytes, "unavailable");
    }
  }

  private byte[] multipartBody(String boundary, List<byte[]> imageBytesList) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    for (int index = 0; index < imageBytesList.size(); index += 1) {
      byte[] imageBytes = imageBytesList.get(index);
      output.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
      output.write(("Content-Disposition: form-data; name=\"files\"; filename=\"crop-" + index + ".jpg\"\r\n").getBytes(StandardCharsets.UTF_8));
      output.write("Content-Type: image/jpeg\r\n\r\n".getBytes(StandardCharsets.UTF_8));
      if (imageBytes != null) {
        output.write(imageBytes);
      }
      output.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }
    output.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
    return output.toByteArray();
  }

  private byte[] singleMultipartBody(String boundary, byte[] imageBytes) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    output.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
    output.write("Content-Disposition: form-data; name=\"file\"; filename=\"page.jpg\"\r\n".getBytes(StandardCharsets.UTF_8));
    output.write("Content-Type: image/jpeg\r\n\r\n".getBytes(StandardCharsets.UTF_8));
    output.write(imageBytes);
    output.write("\r\n".getBytes(StandardCharsets.UTF_8));
    output.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
    return output.toByteArray();
  }

  private List<Integer> parseBbox(JsonNode bboxNode) {
    if (!bboxNode.isArray() || bboxNode.size() != 4) {
      return List.of();
    }
    int left = bboxNode.get(0).asInt();
    int top = bboxNode.get(1).asInt();
    int right = bboxNode.get(2).asInt();
    int bottom = bboxNode.get(3).asInt();
    if (left < 0 || top < 0 || right <= left || bottom <= top) {
      return List.of();
    }
    return List.of(left, top, right, bottom);
  }

  private List<FieldRegionOcrResult> paddedResults(JsonNode results, int expectedSize) {
    java.util.ArrayList<FieldRegionOcrResult> values = new java.util.ArrayList<>();
    for (int index = 0; index < expectedSize; index += 1) {
      JsonNode item = index < results.size() ? results.get(index) : null;
      if (item == null || item.isMissingNode() || item.isNull()) {
        values.add(FieldRegionOcrResult.unavailable("missing_result"));
        continue;
      }
      values.add(new FieldRegionOcrResult(
          item.path("text").asText(""),
          normalizeConfidence(item.path("confidence").asDouble(0)),
          item.path("status").asText("available")
      ));
    }
    return List.copyOf(values);
  }

  private List<FieldRegionOcrResult> unavailableResults(List<byte[]> cropImageBytes, String status) {
    int size = cropImageBytes == null ? 0 : cropImageBytes.size();
    java.util.ArrayList<FieldRegionOcrResult> results = new java.util.ArrayList<>();
    for (int index = 0; index < size; index += 1) {
      results.add(FieldRegionOcrResult.unavailable(status));
    }
    return List.copyOf(results);
  }

  private double normalizeConfidence(double value) {
    if (value <= 1) {
      return Math.round(value * 100);
    }
    return Math.round(value);
  }

  private String trimTrailingSlash(String value) {
    return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
  }
}
