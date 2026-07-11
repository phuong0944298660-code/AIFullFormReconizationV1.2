package com.aiform.id995a.ocr;

public record FieldVerification(
    Integer score,
    String status,
    String reasonCode,
    String reason
) {

  public FieldVerification {
    if (score != null) {
      score = Math.max(0, Math.min(100, score));
    }
    status = status == null || status.isBlank() ? "review" : status;
    reasonCode = reasonCode == null ? "" : reasonCode;
    reason = reason == null ? "" : reason;
  }

  public boolean hasScore() {
    return score != null;
  }

  public static FieldVerification reviewWithoutScore(String reasonCode, String reason) {
    return new FieldVerification(null, "review", reasonCode, reason);
  }
}
