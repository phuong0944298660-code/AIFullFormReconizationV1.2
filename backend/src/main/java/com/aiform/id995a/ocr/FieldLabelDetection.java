package com.aiform.id995a.ocr;

import java.util.List;

public record FieldLabelDetection(
    String text,
    double confidence,
    List<Integer> bbox
) {
  public FieldLabelDetection {
    text = text == null ? "" : text;
    confidence = Math.max(0, Math.min(100, confidence));
    bbox = bbox == null ? List.of() : List.copyOf(bbox);
  }
}
