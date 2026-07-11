package com.aiform.id995a.ocr;

import java.util.List;

public record FieldLabelDetection(
    String text,
    double confidence,
    List<Integer> bbox,
    double detectionConfidence,
    String textStatus
) {
  public FieldLabelDetection {
    text = text == null ? "" : text;
    confidence = Math.max(0, Math.min(100, confidence));
    bbox = bbox == null ? List.of() : List.copyOf(bbox);
    detectionConfidence = Math.max(0, Math.min(100, detectionConfidence));
    textStatus = textStatus == null || textStatus.isBlank()
        ? (text.isBlank() ? "unreadable" : "readable")
        : textStatus;
  }

  public FieldLabelDetection(String text, double confidence, List<Integer> bbox) {
    this(text, confidence, bbox, confidence, text == null || text.isBlank() ? "unreadable" : "readable");
  }
}
