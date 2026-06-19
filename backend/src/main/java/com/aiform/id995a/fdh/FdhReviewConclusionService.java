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
import java.time.Year;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class FdhReviewConclusionService {

  private static final Pattern CONTRACT_YEAR_PATTERN = Pattern.compile("(?:^|[^0-9])((?:19|20)\\d{2})(?=$|[^0-9])");

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
    List<FdhFieldAdjudication> fallbackAdjudications = deterministicFieldAdjudications(result);
    if (!properties.enabled() || blank(properties.apiKey())) {
      return new FdhReviewConclusionResponse(false, "disabled", properties.model(), fallback, fallbackAdjudications);
    }

    try {
      ModelConclusion modelConclusion = parseModelConclusion(callModelWithRetry(result));
      if (blank(modelConclusion.text())) {
        return new FdhReviewConclusionResponse(true, "empty_fallback", properties.model(), fallback, fallbackAdjudications);
      }
      String trimmed = modelConclusion.text().trim();
      if (!usableConclusion(trimmed, result.decision())) {
        return new FdhReviewConclusionResponse(true, "format_fallback", properties.model(), fallback, fallbackAdjudications);
      }
      return new FdhReviewConclusionResponse(
          true,
          "ok",
          properties.model(),
          trimmed,
          mergeAdjudications(fallbackAdjudications, modelConclusion.fieldAdjudications())
      );
    } catch (Exception exception) {
      return new FdhReviewConclusionResponse(
          true,
          "error_fallback: " + exception.getMessage(),
          properties.model(),
          fallback,
          fallbackAdjudications
      );
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
        "response_format", Map.of("type", "json_object"),
        "chat_template_kwargs", Map.of("enable_thinking", false),
        "messages", List.of(
            Map.of("role", "system", "content", """
                你是香港入境处外籍家庭佣工材料核验结果整理助手。
                只能基于输入 JSON 和香港入境处外籍家庭佣工申请材料规则输出结论，不得添加未提供事实。
                必须返回合法 JSON，不得返回 Markdown 代码块。
                对跨文件字段不一致的情况，可以给出一个建议采用值；但只要经历过纠偏或建议取值，字段状态必须保持 review，并说明需人工复核。
                材料缺失、缺页、模板错误属于材料层面，不得用字段建议值覆盖材料层阻断结论。
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
        请严格输出 JSON：
        {
          "text": "整体结论：<PASS/REVIEW/FAIL> - <一句话解释>\\n材料识别结果：\\n- <材料名称>：<状态>；<是否影响通过>；出处：<模板或文件>\\n字段识别结果：\\n- <字段名称>：<识别值或建议采用值>；<PASS/REVIEW/FAIL>；<原因和材料来源>",
          "fieldAdjudications": [
            {
              "key": "<字段 key>",
              "suggestedValue": "<建议采用值；无法建议则为空>",
              "status": "pass|review|fail",
              "corrected": true|false,
              "reason": "<结合材料规则和来源解释为什么建议该值>"
            }
          ]
        }

        字段建议规则：
        - 对跨文件字段不一致，结合材料名称、章节、字段名称和香港入境处材料规则建议一个采用值。
        - ID 407 是标准雇佣合约本体；ID 988A / ID 988B 中的合约编号是对该合约编号的引用。
        - 标准雇佣合约编号 contract.dh_contract_no 的开头必须为 FH-CON-；若识别到 RFH-CON- 等可判断为前缀涂抹或误写的结果，建议采用值应归一为 FH-CON- 开头；年份优先选择不超过当前年份且最接近当前年份的值，例如 2016/2026 取 2026，2026/2036 取 2026；但 status 仍为 review。
        - 若只是格式或 OCR 噪声差异，可建议采用更可信材料值；若年份、号码等实质差异仍须 corrected=true 且 status=review。
        - 不要把经历纠偏的字段改成 pass。

        下面是机器识别与规则审核 JSON，已移除图片 base64：
        """ + objectMapper.writeValueAsString(compactResult(result));
  }

  private ModelConclusion parseModelConclusion(String rawText) throws IOException {
    String trimmed = stripJsonFence(rawText == null ? "" : rawText.trim());
    if (trimmed.isBlank()) {
      return new ModelConclusion("", List.of());
    }
    try {
      JsonNode root = objectMapper.readTree(trimmed);
      if (root.isObject() && (root.has("text") || root.has("fieldAdjudications"))) {
        String text = root.path("text").asText("");
        return new ModelConclusion(text, parseFieldAdjudications(root.path("fieldAdjudications")));
      }
    } catch (IOException ignored) {
      // Some local models may still return the legacy plain-text conclusion.
    }
    return new ModelConclusion(trimmed, List.of());
  }

  private String stripJsonFence(String value) {
    if (value.startsWith("```")) {
      return value
          .replaceFirst("^```(?:json)?\\s*", "")
          .replaceFirst("\\s*```$", "")
          .trim();
    }
    return value;
  }

  private List<FdhFieldAdjudication> parseFieldAdjudications(JsonNode node) {
    if (node == null || !node.isArray()) {
      return List.of();
    }
    List<FdhFieldAdjudication> adjudications = new ArrayList<>();
    for (JsonNode item : node) {
      if (!item.isObject()) {
        continue;
      }
      String key = item.path("key").asText("");
      if (blank(key)) {
        continue;
      }
      boolean corrected = item.path("corrected").asBoolean(false);
      adjudications.add(new FdhFieldAdjudication(
          key,
          normalizeFieldValue(key, item.path("suggestedValue").asText("")),
          corrected ? "review" : item.path("status").asText("review"),
          corrected,
          "llm",
          item.path("reason").asText("")
      ));
    }
    return adjudications;
  }

  private List<FdhFieldAdjudication> mergeAdjudications(
      List<FdhFieldAdjudication> fallback,
      List<FdhFieldAdjudication> model
  ) {
    Map<String, FdhFieldAdjudication> merged = new LinkedHashMap<>();
    for (FdhFieldAdjudication adjudication : fallback == null ? List.<FdhFieldAdjudication>of() : fallback) {
      if (!blank(adjudication.key())) {
        merged.put(adjudication.key(), adjudication);
      }
    }
    for (FdhFieldAdjudication adjudication : model == null ? List.<FdhFieldAdjudication>of() : model) {
      if (!blank(adjudication.key()) && !blank(adjudication.suggestedValue())) {
        merged.put(adjudication.key(), protectContractAdjudication(merged.get(adjudication.key()), adjudication));
      }
    }
    return List.copyOf(merged.values());
  }

  private FdhFieldAdjudication protectContractAdjudication(
      FdhFieldAdjudication fallback,
      FdhFieldAdjudication model
  ) {
    if (!"contract.dh_contract_no".equals(clean(model.key()))
        || fallback == null
        || blank(fallback.suggestedValue())
        || clean(fallback.suggestedValue()).equals(clean(model.suggestedValue()))) {
      return model;
    }
    return new FdhFieldAdjudication(
        model.key(),
        fallback.suggestedValue(),
        "review",
        true,
        model.source(),
        fallback.reason()
    );
  }

  private Map<String, Object> compactResult(FdhReviewResult result) {
    return Map.of(
        "applicationTypeId", result.applicationTypeId(),
        "decision", result.decision(),
        "decisionText", result.decisionText(),
        "materials", result.materials().stream().map(this::compactMaterial).toList(),
        "fields", result.fields().stream().map(this::compactField).toList(),
        "ruleFallbackFieldAdjudications", deterministicFieldAdjudications(result).stream()
            .map(this::compactFieldAdjudication)
            .toList()
    );
  }

  private Map<String, Object> compactFieldAdjudication(FdhFieldAdjudication adjudication) {
    return Map.of(
        "key", adjudication.key(),
        "suggestedValue", adjudication.suggestedValue(),
        "status", adjudication.status(),
        "corrected", adjudication.corrected(),
        "source", adjudication.source(),
        "reason", adjudication.reason()
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

  private List<FdhFieldAdjudication> deterministicFieldAdjudications(FdhReviewResult result) {
    if (result == null) {
      return List.of();
    }
    return result.fields().stream()
        .map(this::deterministicFieldAdjudication)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .toList();
  }

  private Optional<FdhFieldAdjudication> deterministicFieldAdjudication(FdhReviewResult.StandardField field) {
    if (!Set.of("fail", "review").contains(field.status())) {
      return Optional.empty();
    }
    List<ValueGroup> groups = valueGroups(field);
    if (groups.size() <= 1) {
      if ("contract.dh_contract_no".equals(clean(field.key()))
          && groups.size() == 1
          && hasContractNumberPrefixRepair(field)) {
        ValueGroup suggested = groups.get(0);
        return Optional.of(new FdhFieldAdjudication(
            field.key(),
            suggested.value(),
            "review",
            true,
            "rules_fallback",
            suggestionReason(field.key(), suggested.value())
        ));
      }
      return Optional.empty();
    }
    ValueGroup suggested = chooseSuggestedValue(field, groups);
    return Optional.of(new FdhFieldAdjudication(
        field.key(),
        suggested.value(),
        "review",
        true,
        "rules_fallback",
        suggestionReason(field.key(), suggested.value())
    ));
  }

  private boolean hasContractNumberPrefixRepair(FdhReviewResult.StandardField field) {
    for (FdhReviewResult.FieldSource source : field.sources()) {
      String raw = clean(source.value());
      if (!blank(raw) && !raw.equals(normalizeFieldValue(field.key(), raw))) {
        return true;
      }
    }
    return false;
  }

  private ValueGroup chooseSuggestedValue(FdhReviewResult.StandardField field, List<ValueGroup> groups) {
    Comparator<ValueGroup> reliability = Comparator
        .comparingInt(ValueGroup::priority)
        .thenComparingDouble(ValueGroup::confidence)
        .thenComparingInt(ValueGroup::sourceCount);
    if ("contract.dh_contract_no".equals(clean(field.key()))) {
      int currentYear = Year.now().getValue();
      Optional<ValueGroup> closestNonFutureYear = groups.stream()
          .filter(group -> contractYear(group.value())
              .map(year -> year <= currentYear)
              .orElse(false))
          .max(Comparator
              .comparingInt((ValueGroup group) -> contractYear(group.value()).orElse(Integer.MIN_VALUE))
              .thenComparing(reliability));
      if (closestNonFutureYear.isPresent()) {
        return closestNonFutureYear.get();
      }
    }
    return groups.stream().max(reliability).orElse(groups.get(0));
  }

  private Optional<Integer> contractYear(String value) {
    Matcher matcher = CONTRACT_YEAR_PATTERN.matcher(clean(value));
    if (!matcher.find()) {
      return Optional.empty();
    }
    return Optional.of(Integer.parseInt(matcher.group(1)));
  }

  private List<ValueGroup> valueGroups(FdhReviewResult.StandardField field) {
    Map<String, ValueGroup> groups = new LinkedHashMap<>();
    for (FdhReviewResult.FieldSource source : field.sources()) {
      String value = normalizeFieldValue(field.key(), source.value());
      if (blank(value)) {
        continue;
      }
      ValueGroup existing = groups.get(value);
      if (existing == null) {
        groups.put(value, new ValueGroup(
            value,
            source.confidence(),
            sourcePriority(field.key(), source.documentName()),
            1
        ));
      } else {
        groups.put(value, new ValueGroup(
            existing.value(),
            Math.max(existing.confidence(), source.confidence()),
            Math.max(existing.priority(), sourcePriority(field.key(), source.documentName())),
            existing.sourceCount() + 1
        ));
      }
    }
    return List.copyOf(groups.values());
  }

  private String normalizeFieldValue(String fieldKey, String value) {
    String cleaned = clean(value);
    if ("contract.dh_contract_no".equals(clean(fieldKey))) {
      return normalizeDhContractNumber(cleaned);
    }
    return cleaned;
  }

  private String normalizeDhContractNumber(String value) {
    String compact = value.replaceAll("\\s+", "");
    String requiredPrefix = "FH-CON-";
    String upper = compact.toUpperCase(Locale.ROOT);
    int prefixIndex = upper.indexOf(requiredPrefix);
    if (prefixIndex == 0) {
      return requiredPrefix + compact.substring(requiredPrefix.length());
    }
    if (prefixIndex > 0 && prefixIndex <= 3) {
      return requiredPrefix + compact.substring(prefixIndex + requiredPrefix.length());
    }
    if (upper.matches("^[A-Z0-9]H-CON-[A-Z]{2,3}-?\\d{2,4}-\\d{3,}$")) {
      return "F" + compact.substring(1);
    }
    return value;
  }

  private String suggestionReason(String fieldKey, String suggestedValue) {
    if ("contract.dh_contract_no".equals(clean(fieldKey))) {
      return "建议采用值“" + suggestedValue + "”；标准雇佣合约编号前缀必须为 FH-CON-，年份优先选择不超过当前年份且最接近当前年份的值；该字段跨文件不一致，仍需人工复核。";
    }
    return "建议采用值“" + suggestedValue + "”；该字段跨文件不一致，仍需人工复核。";
  }

  private int sourcePriority(String fieldKey, String documentName) {
    String key = clean(fieldKey);
    String document = clean(documentName);
    if ("contract.dh_contract_no".equals(key)) {
      if (document.contains("ID 407")) {
        return 80;
      }
      if (document.contains("ID 988A")) {
        return 70;
      }
      if (document.contains("ID 988B")) {
        return 60;
      }
    }
    if (document.contains("ID 407")) {
      return 40;
    }
    if (document.contains("ID 988A")) {
      return 35;
    }
    if (document.contains("ID 988B")) {
      return 30;
    }
    return 10;
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
    List<String> forbiddenMarkers = List.of("```", "#", "|", "Markdown");
    return forbiddenMarkers.stream().noneMatch(text::contains);
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private String clean(String value) {
    return value == null ? "" : value.trim();
  }

  private String trimTrailingSlash(String value) {
    if (blank(value)) {
      return "https://apie.zhisuaninfo.com/v1";
    }
    return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
  }

  private record ModelConclusion(String text, List<FdhFieldAdjudication> fieldAdjudications) {
    private ModelConclusion {
      text = text == null ? "" : text;
      fieldAdjudications = fieldAdjudications == null ? List.of() : List.copyOf(fieldAdjudications);
    }
  }

  private record ValueGroup(String value, double confidence, int priority, int sourceCount) {}
}
