package com.aiform.id995a.ocr;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "field-judge")
public record FieldJudgeProperties(
    boolean enabled,
    String mode,
    String baseUrl,
    String apiKey,
    String model,
    int timeoutSeconds,
    int passThreshold,
    int concurrency
) {

  public FieldJudgeProperties {
    mode = blank(mode) ? "shadow" : mode.trim();
    baseUrl = baseUrl == null ? "" : baseUrl.trim();
    apiKey = apiKey == null ? "" : apiKey.trim();
    model = blank(model) ? "Qwen3.6-Flash" : model.trim();
    timeoutSeconds = Math.max(2, timeoutSeconds);
    passThreshold = Math.max(0, Math.min(100, passThreshold));
    concurrency = Math.max(1, concurrency);
  }

  public boolean enforce() {
    return "enforce".equalsIgnoreCase(mode);
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
