package com.aiform.id995a.ocr;

import java.util.List;

public record FieldLabelLocation(List<Integer> bbox, double score, String reason) {

  public FieldLabelLocation {
    bbox = bbox == null ? List.of() : List.copyOf(bbox);
    score = Math.max(0, Math.min(100, score));
    reason = reason == null ? "" : reason;
  }

  public static FieldLabelLocation notFound(String reason) {
    return new FieldLabelLocation(List.of(), 0, reason);
  }
}
