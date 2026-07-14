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
    double recognitionConfidence,
    List<Integer> recognitionBbox,
    List<Integer> labelBbox,
    List<Integer> valueBbox,
    List<Integer> evidenceBbox,
    String locationStatus,
    String locationMethod,
    double locationScore,
    String locationReason,
    String judgeStatus,
    String judgeObservedValue,
    String judgeMatchType,
    Integer verificationScore,
    String verificationStatus,
    String verificationReason,
    String scoreSource,
    String suggestedValue,
    String issue
) {

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
    recognitionConfidence = Math.max(0, Math.min(100, recognitionConfidence));
    recognitionBbox = copy(recognitionBbox);
    labelBbox = copy(labelBbox);
    valueBbox = copy(valueBbox);
    evidenceBbox = copy(evidenceBbox);
    locationStatus = clean(locationStatus, "not_run");
    locationMethod = clean(locationMethod, "");
    locationScore = Math.max(0, Math.min(100, locationScore));
    locationReason = clean(locationReason, "");
    judgeStatus = clean(judgeStatus, "not_run");
    judgeObservedValue = clean(judgeObservedValue, "");
    judgeMatchType = clean(judgeMatchType, "");
    if (verificationScore != null) {
      verificationScore = Math.max(0, Math.min(100, verificationScore));
    }
    verificationStatus = clean(verificationStatus, "not_run");
    verificationReason = clean(verificationReason, "");
    scoreSource = clean(scoreSource, "recognition_confidence");
    suggestedValue = clean(suggestedValue, "");
    issue = clean(issue, "");
  }

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
        page, path, label, value, displayValue, confidence, bbox, snapshotDataUrl,
        ocrText, ocrConfidence, ocrStatus, characters,
        confidence, bbox, bbox, bbox, bbox,
        "legacy", "legacy", 0, "", "not_run", "", "", null, "not_run", "", "recognition_confidence",
        "", ""
    );
  }

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
      List<FieldCharacterEvidence> characters,
      String suggestedValue,
      String issue
  ) {
    this(page, path, label, value, displayValue, confidence, bbox, snapshotDataUrl,
        ocrText, ocrConfidence, ocrStatus, characters,
        confidence, bbox, bbox, bbox, bbox,
        "legacy", "legacy", 0, "", "not_run", "", "", null, "not_run", "", "recognition_confidence",
        suggestedValue, issue);
  }

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
      List<FieldCharacterEvidence> characters,
      double recognitionConfidence,
      List<Integer> recognitionBbox,
      List<Integer> labelBbox,
      List<Integer> valueBbox,
      List<Integer> evidenceBbox,
      String locationStatus,
      String locationMethod,
      double locationScore,
      String locationReason,
      String judgeStatus,
      String judgeObservedValue,
      String judgeMatchType,
      Integer verificationScore,
      String verificationStatus,
      String verificationReason,
      String scoreSource
  ) {
    this(page, path, label, value, displayValue, confidence, bbox, snapshotDataUrl,
        ocrText, ocrConfidence, ocrStatus, characters,
        recognitionConfidence, recognitionBbox, labelBbox, valueBbox, evidenceBbox,
        locationStatus, locationMethod, locationScore, locationReason, judgeStatus, judgeObservedValue,
        judgeMatchType, verificationScore, verificationStatus, verificationReason, scoreSource,
        "", "");
  }

  private static List<Integer> copy(List<Integer> value) {
    return value == null ? List.of() : List.copyOf(value);
  }

  private static String clean(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }
}
