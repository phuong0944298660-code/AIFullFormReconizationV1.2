package com.aiform.id995a.ocr;

public record ParallelRecognitionOutput(
    String label,
    String value,
    double confidence
) {

  public ParallelRecognitionOutput {
    label = label == null ? "" : label;
    value = value == null ? "" : value;
    confidence = Math.max(0, Math.min(100, confidence));
  }
}
