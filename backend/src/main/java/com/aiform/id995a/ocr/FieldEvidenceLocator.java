package com.aiform.id995a.ocr;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class FieldEvidenceLocator {

  private final FieldLabelLocator labelLocator = new FieldLabelLocator();

  public FieldEvidenceRegion locate(
      String label,
      String expectedValue,
      List<FieldLabelDetection> detections,
      int imageWidth,
      int imageHeight
  ) {
    List<Integer> labelBbox = labelLocator.locate(label, detections);
    if (labelBbox.isEmpty()) {
      return locateValueOnly(expectedValue, detections, imageWidth, imageHeight);
    }
    FieldLabelDetection labelDetection = detections.stream()
        .filter(item -> item != null && labelBbox.equals(item.bbox()))
        .findFirst()
        .orElse(new FieldLabelDetection(label, 0, labelBbox));
    List<Candidate> candidates = detections.stream()
        .filter(item -> item != null && item.bbox().size() == 4 && !labelBbox.equals(item.bbox()))
        .map(item -> new Candidate(item, valueScore(expectedValue, item.text()), geometryScore(labelBbox, item.bbox())))
        .filter(candidate -> candidate.geometryScore() >= 0.55)
        .sorted(Comparator
            .comparingDouble(Candidate::valueScore)
            .thenComparingDouble(Candidate::geometryScore)
            .thenComparingDouble(candidate -> candidate.detection().detectionConfidence())
            .reversed())
        .toList();
    if (candidates.isEmpty()) {
      return FieldEvidenceRegion.partial(labelBbox, labelDetection.confidence(), "value_not_found");
    }
    if (candidates.size() > 1 && equallyPlausible(candidates.get(0), candidates.get(1))) {
      return new FieldEvidenceRegion(
          labelBbox,
          List.of(),
          List.of(),
          "ambiguous",
          "label_with_multiple_values",
          labelDetection.confidence(),
          "multiple_value_candidates"
      );
    }
    FieldLabelDetection valueDetection = candidates.get(0).detection();
    List<Integer> evidenceBbox = unionWithPadding(
        labelBbox,
        valueDetection.bbox(),
        imageWidth,
        imageHeight
    );
    return new FieldEvidenceRegion(
        labelBbox,
        valueDetection.bbox(),
        evidenceBbox,
        "located",
        "label_and_value",
        Math.min(labelDetection.confidence(), valueDetection.detectionConfidence()),
        ""
    );
  }

  private boolean equallyPlausible(Candidate left, Candidate right) {
    return Math.abs(left.valueScore() - right.valueScore()) < 0.0001
        && Math.abs(left.geometryScore() - right.geometryScore()) < 0.0001
        && Math.abs(left.detection().detectionConfidence() - right.detection().detectionConfidence()) < 0.0001;
  }

  private FieldEvidenceRegion locateValueOnly(
      String expectedValue,
      List<FieldLabelDetection> detections,
      int imageWidth,
      int imageHeight
  ) {
    List<Candidate> candidates = detections.stream()
        .filter(item -> item != null && item.bbox().size() == 4)
        .map(item -> new Candidate(item, valueScore(expectedValue, item.text()), 0))
        .filter(candidate -> candidate.valueScore() >= 0.72)
        .sorted(Comparator
            .comparingDouble(Candidate::valueScore)
            .thenComparingDouble(candidate -> candidate.detection().detectionConfidence())
            .reversed())
        .toList();
    if (candidates.isEmpty()) {
      return FieldEvidenceRegion.notFound("label_not_found");
    }
    if (candidates.size() > 1 && Math.abs(candidates.get(0).valueScore() - candidates.get(1).valueScore()) < 0.0001) {
      return FieldEvidenceRegion.notFound("multiple_value_candidates");
    }
    FieldLabelDetection valueDetection = candidates.get(0).detection();
    List<Integer> valueBbox = valueDetection.bbox();
    return new FieldEvidenceRegion(
        List.of(),
        valueBbox,
        unionWithPadding(valueBbox, valueBbox, imageWidth, imageHeight),
        "value_only",
        "value_only",
        valueDetection.detectionConfidence(),
        "label_not_found"
    );
  }

  private double valueScore(String expectedValue, String candidateText) {
    String expected = normalize(expectedValue);
    String candidate = normalize(candidateText);
    if (expected.isBlank() || candidate.isBlank()) {
      return 0;
    }
    if (expected.equals(candidate)) {
      return 1;
    }
    if (expected.contains(candidate) || candidate.contains(expected)) {
      return (double) Math.min(expected.length(), candidate.length())
          / Math.max(expected.length(), candidate.length());
    }
    return 0;
  }

  private double geometryScore(List<Integer> label, List<Integer> candidate) {
    int labelHeight = Math.max(1, label.get(3) - label.get(1));
    int verticalOverlap = Math.min(label.get(3), candidate.get(3)) - Math.max(label.get(1), candidate.get(1));
    if (candidate.get(0) >= label.get(2) && verticalOverlap >= Math.round(labelHeight * 0.35f)) {
      return 0.75;
    }
    int labelWidth = Math.max(1, label.get(2) - label.get(0));
    int horizontalGap = label.get(0) - candidate.get(2);
    if (candidate.get(2) <= label.get(0)
        && horizontalGap <= labelWidth
        && verticalOverlap >= Math.round(labelHeight * 0.35f)) {
      return 0.70;
    }
    int horizontalOverlap = Math.min(label.get(2), candidate.get(2)) - Math.max(label.get(0), candidate.get(0));
    boolean horizontallyNear = horizontalOverlap > 0
        || (candidate.get(0) <= label.get(2) + labelWidth
            && candidate.get(2) >= label.get(0) - labelWidth);
    int verticalGap = candidate.get(1) - label.get(3);
    if (verticalGap >= 0 && verticalGap <= labelHeight * 3 && horizontallyNear) {
      return 0.65;
    }
    return 0;
  }

  private List<Integer> unionWithPadding(
      List<Integer> label,
      List<Integer> value,
      int imageWidth,
      int imageHeight
  ) {
    int paddingX = Math.max(8, Math.round(Math.max(1, imageWidth) * 0.015f));
    int paddingY = Math.max(6, Math.round(Math.max(1, imageHeight) * 0.006f));
    int left = Math.max(0, Math.min(label.get(0), value.get(0)) - paddingX);
    int top = Math.max(0, Math.min(label.get(1), value.get(1)) - paddingY);
    int right = Math.min(imageWidth, Math.max(label.get(2), value.get(2)) + paddingX);
    int bottom = Math.min(imageHeight, Math.max(label.get(3), value.get(3)) + paddingY);
    return right > left && bottom > top ? List.of(left, top, right, bottom) : List.of();
  }

  private String normalize(String value) {
    return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
        .toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9\\p{IsHan}]+", "")
        .trim();
  }

  private record Candidate(FieldLabelDetection detection, double valueScore, double geometryScore) {}
}
