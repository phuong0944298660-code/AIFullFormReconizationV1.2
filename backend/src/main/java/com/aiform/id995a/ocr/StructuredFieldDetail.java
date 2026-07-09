package com.aiform.id995a.ocr;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public record StructuredFieldDetail(
    int page,
    String path,
    String label,
    JsonNode value,
    String displayValue,
    double confidence,
    List<Integer> bbox,
    String snapshotDataUrl,
    String ocrText,
    double ocrConfidence,
    String ocrStatus,
    List<FieldCharacterEvidence> characters,
    String suggestedValue,
    String issue,
    String modelAgreement,
    String conflictType,
    List<ParallelRecognitionOutput> modelOutputs
) {

  public StructuredFieldDetail(
      int page,
      String path,
      String label,
      JsonNode value,
      String displayValue,
      double confidence,
      List<Integer> bbox,
      String snapshotDataUrl,
      String ocrText,
      double ocrConfidence,
      String ocrStatus,
      List<FieldCharacterEvidence> characters
  ) {
    this(
        page,
        path,
        label,
        value,
        displayValue,
        confidence,
        bbox,
        snapshotDataUrl,
        ocrText,
        ocrConfidence,
        ocrStatus,
        characters,
        "",
        "",
        "",
        "",
        List.of()
    );
  }

  public StructuredFieldDetail {
    path = path == null ? "" : path;
    label = label == null || label.isBlank() ? path : label;
    displayValue = displayValue == null ? "" : displayValue;
    confidence = Math.max(0, Math.min(100, confidence));
    bbox = bbox == null ? List.of() : List.copyOf(bbox);
    snapshotDataUrl = snapshotDataUrl == null ? "" : snapshotDataUrl;
    ocrText = ocrText == null ? "" : ocrText;
    ocrConfidence = Math.max(0, Math.min(100, ocrConfidence));
    ocrStatus = ocrStatus == null || ocrStatus.isBlank() ? "not_run" : ocrStatus;
    characters = characters == null ? List.of() : List.copyOf(characters);
    suggestedValue = suggestedValue == null ? "" : suggestedValue;
    issue = issue == null ? "" : issue;
    modelAgreement = modelAgreement == null ? "" : modelAgreement;
    conflictType = conflictType == null ? "" : conflictType;
    modelOutputs = modelOutputs == null ? List.of() : List.copyOf(modelOutputs);
  }
}
