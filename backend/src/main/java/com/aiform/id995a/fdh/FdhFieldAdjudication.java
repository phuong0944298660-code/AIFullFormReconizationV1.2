package com.aiform.id995a.fdh;

public record FdhFieldAdjudication(
    String key,
    String suggestedValue,
    String status,
    boolean corrected,
    String source,
    String reason
) {

  public FdhFieldAdjudication {
    key = key == null ? "" : key;
    suggestedValue = suggestedValue == null ? "" : suggestedValue;
    status = status == null || status.isBlank() ? "review" : status;
    source = source == null ? "" : source;
    reason = reason == null ? "" : reason;
  }
}
