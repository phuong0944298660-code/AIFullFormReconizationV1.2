package com.aiform.id995a.fdh;

import com.aiform.id995a.llm.LlmProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class FdhReviewConclusionService {

  private final LlmProperties properties;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;

  @Autowired
  public FdhReviewConclusionService(LlmProperties properties, ObjectMapper objectMapper) {
    this(properties, HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .version(HttpClient.Version.HTTP_1_1)
        .build(), objectMapper);
  }

  FdhReviewConclusionService(LlmProperties properties, HttpClient httpClient, ObjectMapper objectMapper) {
    this.properties = properties;
    this.httpClient = httpClient;
    this.objectMapper = objectMapper;
  }

  public FdhReviewConclusionResponse generate(FdhReviewResult result) {
    String fallback = deterministicConclusion(result);
    if (!properties.enabled() || blank(properties.apiKey())) {
      return new FdhReviewConclusionResponse(false, "disabled", properties.model(), fallback);
    }

    try {
      String text = callModelWithRetry(result);
      if (blank(text)) {
        return new FdhReviewConclusionResponse(true, "empty_fallback", properties.model(), fallback);
      }
      String trimmed = text.trim();
      if (!usableConclusion(trimmed, result.decision())) {
        return new FdhReviewConclusionResponse(true, "format_fallback", properties.model(), fallback);
      }
      return new FdhReviewConclusionResponse(true, "ok", properties.model(), trimmed);
    } catch (Exception exception) {
      return new FdhReviewConclusionResponse(true, "error_fallback: " + exception.getMessage(), properties.model(), fallback);
    }
  }

  private String callModelWithRetry(FdhReviewResult result) throws IOException, InterruptedException {
    IOException firstIoException = null;
    for (int attempt = 0; attempt < 3; attempt += 1) {
      try {
        return callModel(result);
      } catch (IOException exception) {
        if (!retryableTransportError(exception) || attempt == 2) {
          throw exception;
        }
        firstIoException = exception;
        Thread.sleep(250L * (attempt + 1));
      }
    }
    throw firstIoException == null ? new IOException("LLM request failed") : firstIoException;
  }

  private boolean retryableTransportError(IOException exception) {
    Throwable current = exception;
    while (current != null) {
      String message = current.getMessage();
      if (message != null) {
        String normalized = message.toLowerCase(Locale.ROOT);
        if (normalized.contains("header parser received no bytes")
            || normalized.contains("http/1.1 header parser")
            || normalized.contains("connection reset")
            || normalized.contains("connection closed")
            || normalized.contains("closed before")
            || normalized.contains("unexpected end of")
            || normalized.contains("eof")
            || normalized.contains("goaway received")) {
          return true;
        }
      }
      current = current.getCause();
    }
    return false;
  }

  private String callModel(FdhReviewResult result) throws IOException, InterruptedException {
    Map<String, Object> payload = Map.of(
        "model", properties.model(),
        "temperature", 0,
        "max_tokens", properties.maxTokens(),
        "stream", false,
        "enable_thinking", false,
        "chat_template_kwargs", Map.of("enable_thinking", false),
        "messages", List.of(
            Map.of("role", "system", "content", """
                你是香港入境处外籍家庭佣工材料核验结果整理助手。
                只能基于输入 JSON 输出中文纯文本，不得添加未提供事实、建议、解决方案、Markdown 表格或标题层级。
                必须分点说明材料识别结果和字段识别结果；字段不一致、必填缺失、材料缺失或低置信时，要明确标注“需人工审核”或“不通过”。
                """),
            Map.of("role", "user", "content", buildPrompt(result))
        )
    );

    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(trimTrailingSlash(properties.baseUrl()) + "/chat/completions"))
        .version(HttpClient.Version.HTTP_1_1)
        .timeout(Duration.ofSeconds(Math.max(5, properties.timeoutSeconds())))
        .header("Authorization", "Bearer " + properties.apiKey())
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload), StandardCharsets.UTF_8))
        .build();

    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new IOException("LLM HTTP " + response.statusCode());
    }

    JsonNode content = objectMapper.readTree(response.body()).at("/choices/0/message/content");
    return content.isMissingNode() || content.isNull() ? "" : content.asText();
  }

  private String buildPrompt(FdhReviewResult result) throws IOException {
    return """
        请严格按以下格式输出：
        整体结论：<PASS/REVIEW/FAIL> - <一句话解释>
        材料识别结果：
        - <材料名称>：<状态>；<是否影响通过>；出处：<模板或文件>
        字段识别结果：
        - <字段名称>：<识别值>；<PASS/REVIEW/FAIL>；<如不一致或需人工审核，必须写清楚原因和材料来源>

        下面是机器识别与规则审核 JSON，已移除图片 base64：
        """ + objectMapper.writeValueAsString(compactResult(result));
  }

  private Map<String, Object> compactResult(FdhReviewResult result) {
    return Map.of(
        "applicationTypeId", result.applicationTypeId(),
        "decision", result.decision(),
        "decisionText", result.decisionText(),
        "materials", result.materials().stream().map(this::compactMaterial).toList(),
        "fields", result.fields().stream().map(this::compactField).toList()
    );
  }

  private Map<String, Object> compactMaterial(FdhReviewResult.MaterialRow material) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("no", material.no());
    row.put("shortName", material.shortName());
    row.put("templateId", material.templateId());
    row.put("applicable", material.applicable());
    row.put("uploaded", material.uploaded());
    row.put("core", material.core());
    row.put("blocking", material.blocking());
    row.put("status", material.status());
    row.put("statusText", material.statusText());
    row.put("scopeText", material.scopeText());
    row.put("uploadedFilenames", material.uploadedFilenames());
    row.put("issue", material.issue());
    return row;
  }

  private Map<String, Object> compactField(FdhReviewResult.StandardField field) {
    return Map.of(
        "key", field.key(),
        "category", field.category(),
        "label", field.label(),
        "required", field.required(),
        "normalizedValue", field.normalizedValue(),
        "status", field.status(),
        "issue", field.issue(),
        "blocking", field.blocking(),
        "rule", field.rule(),
        "sources", field.sources().stream().map(this::compactSource).toList()
    );
  }

  private Map<String, Object> compactSource(FdhReviewResult.FieldSource source) {
    return Map.of(
        "documentName", source.documentName(),
        "filename", source.filename(),
        "section", source.section(),
        "fieldName", source.fieldName(),
        "value", source.value(),
        "confidence", source.confidence()
    );
  }

  private String deterministicConclusion(FdhReviewResult result) {
    StringBuilder builder = new StringBuilder();
    builder.append("整体结论：")
        .append(result.decision())
        .append(" - ")
        .append(blank(result.decisionText()) ? decisionText(result.decision()) : result.decisionText())
        .append('\n');

    builder.append("材料识别结果：");
    for (FdhReviewResult.MaterialRow material : result.materials()) {
      if (!material.applicable()) {
        continue;
      }
      builder.append('\n')
          .append("- ")
          .append(material.shortName())
          .append("：")
          .append(material.statusText())
          .append("；")
          .append(material.blocking() ? "影响最终通过" : "不影响最终通过")
          .append("；出处：")
          .append(blank(material.templateId()) ? material.shortName() : material.templateId());
      if (!blank(material.issue())) {
        builder.append("；问题：").append(material.issue());
      }
    }

    builder.append('\n').append("字段识别结果：");
    for (FdhReviewResult.StandardField field : result.fields()) {
      builder.append('\n')
          .append("- ")
          .append(field.label())
          .append("：")
          .append(blank(field.normalizedValue()) ? "未识别" : field.normalizedValue())
          .append("；")
          .append(fieldStatusText(field));
      if (!blank(field.issue())) {
        builder.append("；").append(field.issue());
      }
      if (!field.sources().isEmpty()) {
        builder.append("；出处：").append(sourceSummary(field.sources()));
      }
    }

    return builder.toString();
  }

  private String fieldStatusText(FdhReviewResult.StandardField field) {
    if ("pass".equals(field.status())) {
      return "PASS";
    }
    if ("review".equals(field.status())) {
      return "需人工审核";
    }
    if (field.issue().contains("不一致")) {
      return "不通过，跨文件字段不一致，需人工审核";
    }
    return "不通过";
  }

  private String sourceSummary(List<FdhReviewResult.FieldSource> sources) {
    return sources.stream()
        .map(source -> source.documentName() + " / " + source.section() + " / " + source.fieldName())
        .distinct()
        .reduce((left, right) -> left + "；" + right)
        .orElse("未取得可用字段证据");
  }

  private String decisionText(String decision) {
    return switch (decision) {
      case "PASS" -> "允许通过";
      case "REVIEW" -> "需人工复核";
      case "FAIL" -> "不允许通过";
      default -> decision;
    };
  }

  private boolean usableConclusion(String text, String expectedDecision) {
    if (blank(text)) {
      return false;
    }
    if (!text.startsWith("整体结论：" + expectedDecision)) {
      return false;
    }
    List<String> forbiddenMarkers = List.of("```", "#", "|", "Markdown", "解决方案", "建议");
    return forbiddenMarkers.stream().noneMatch(text::contains);
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private String trimTrailingSlash(String value) {
    if (blank(value)) {
      return "https://apie.zhisuaninfo.com/v1";
    }
    return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
  }
}
