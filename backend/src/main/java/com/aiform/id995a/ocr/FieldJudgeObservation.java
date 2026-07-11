package com.aiform.id995a.ocr;

public record FieldJudgeObservation(
    String status,
    String observedValue,
    String matchType,
    String legibility,
    String cropCoverage,
    String reason,
    String reasonZhHant
) {

  public FieldJudgeObservation {
    status = clean(status, "unavailable");
    observedValue = clean(observedValue, "");
    matchType = clean(matchType, "");
    legibility = clean(legibility, "unknown");
    cropCoverage = clean(cropCoverage, "unknown");
    reason = clean(reason, "");
    reasonZhHant = clean(reasonZhHant, "");
  }

  public FieldJudgeObservation(
      String status,
      String observedValue,
      String matchType,
      String legibility,
      String cropCoverage,
      String reason
  ) {
    this(status, observedValue, matchType, legibility, cropCoverage, reason, "");
  }

  public static FieldJudgeObservation unavailable(String reason) {
    return new FieldJudgeObservation("unavailable", "", "", "unknown", "unknown", reason, "");
  }

  private static String clean(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }
}
