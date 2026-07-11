package com.aiform.id995a.ocr;

import java.util.List;

public record FieldEvidenceRegion(
    List<Integer> labelBbox,
    List<Integer> valueBbox,
    List<Integer> evidenceBbox,
    String status,
    String method,
    double locationScore,
    String reason
) {

  public FieldEvidenceRegion {
    labelBbox = labelBbox == null ? List.of() : List.copyOf(labelBbox);
    valueBbox = valueBbox == null ? List.of() : List.copyOf(valueBbox);
    evidenceBbox = evidenceBbox == null ? List.of() : List.copyOf(evidenceBbox);
    status = status == null || status.isBlank() ? "not_found" : status;
    method = method == null ? "" : method;
    locationScore = Math.max(0, Math.min(100, locationScore));
    reason = reason == null ? "" : reason;
  }

  public static FieldEvidenceRegion notFound(String reason) {
    return new FieldEvidenceRegion(List.of(), List.of(), List.of(), "not_found", "", 0, reason);
  }

  public static FieldEvidenceRegion partial(List<Integer> labelBbox, double score, String reason) {
    return new FieldEvidenceRegion(labelBbox, List.of(), List.of(), "partial", "label_only", score, reason);
  }
}
