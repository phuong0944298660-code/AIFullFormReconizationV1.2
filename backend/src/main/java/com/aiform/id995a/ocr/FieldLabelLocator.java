package com.aiform.id995a.ocr;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class FieldLabelLocator {

  private static final double MIN_CONFIDENCE = 70;
  private static final double MIN_SCORE = 0.72;

  public List<Integer> locate(String label, List<FieldLabelDetection> detections) {
    return locate(label, detections, List.of()).bbox();
  }

  public FieldLabelLocation locate(
      String label,
      List<FieldLabelDetection> detections,
      List<Integer> valueBbox
  ) {
    String normalizedLabel = normalize(label);
    if (normalizedLabel.length() < 3) {
      return FieldLabelLocation.notFound("label_too_short");
    }
    if (detections == null || detections.isEmpty()) {
      return FieldLabelLocation.notFound("ocr_unavailable");
    }

    List<FieldLabelDetection> reliable = detections.stream()
        .filter(item -> item != null && item.bbox().size() >= 4 && item.confidence() >= MIN_CONFIDENCE)
        .toList();
    if (reliable.isEmpty()) {
      return FieldLabelLocation.notFound("ocr_below_confidence");
    }

    List<Candidate> candidates = candidates(reliable).stream()
        .map(item -> candidate(normalizedLabel, item, valueBbox))
        .filter(item -> item.textScore() >= MIN_SCORE)
        .sorted(Comparator.comparingDouble(Candidate::combinedScore).reversed())
        .toList();
    if (candidates.isEmpty()) {
      return FieldLabelLocation.notFound("ocr_text_not_found");
    }

    Candidate best = candidates.get(0);
    if (candidates.size() > 1
        && Math.abs(best.combinedScore() - candidates.get(1).combinedScore()) <= 0.0001
        && !best.detection().bbox().equals(candidates.get(1).detection().bbox())) {
      return FieldLabelLocation.notFound("ambiguous_candidates");
    }
    return new FieldLabelLocation(best.detection().bbox(), best.combinedScore() * 100, "");
  }

  private Candidate candidate(
      String normalizedLabel,
      FieldLabelDetection detection,
      List<Integer> valueBbox
  ) {
    double textScore = matchScore(normalizedLabel, normalize(detection.text()));
    double geometryScore = geometryScore(detection.bbox(), valueBbox);
    double combinedScore = valueBbox != null && valueBbox.size() >= 4
        ? textScore * 0.8 + geometryScore * 0.2
        : textScore;
    return new Candidate(detection, textScore, combinedScore);
  }

  private List<FieldLabelDetection> candidates(List<FieldLabelDetection> detections) {
    ArrayList<FieldLabelDetection> values = new ArrayList<>(detections);
    for (int left = 0; left < detections.size(); left += 1) {
      for (int right = left + 1; right < detections.size(); right += 1) {
        FieldLabelDetection first = detections.get(left);
        FieldLabelDetection second = detections.get(right);
        if (areAdjacent(first.bbox(), second.bbox())) {
          values.add(merge(first, second));
        }
      }
    }
    return values;
  }

  private boolean areAdjacent(List<Integer> first, List<Integer> second) {
    int firstHeight = first.get(3) - first.get(1);
    int secondHeight = second.get(3) - second.get(1);
    int maxHeight = Math.max(firstHeight, secondHeight);
    int verticalGap = Math.max(0, Math.max(first.get(1), second.get(1)) - Math.min(first.get(3), second.get(3)));
    int horizontalOverlap = overlap(first.get(0), first.get(2), second.get(0), second.get(2));
    int minWidth = Math.min(first.get(2) - first.get(0), second.get(2) - second.get(0));
    if (verticalGap <= Math.round(maxHeight * 1.5) && horizontalOverlap >= minWidth * 0.35) {
      return true;
    }

    int horizontalGap = Math.max(0, Math.max(first.get(0), second.get(0)) - Math.min(first.get(2), second.get(2)));
    int verticalOverlap = overlap(first.get(1), first.get(3), second.get(1), second.get(3));
    return horizontalGap <= Math.round(maxHeight * 2.5)
        && verticalOverlap >= Math.min(firstHeight, secondHeight) * 0.45;
  }

  private FieldLabelDetection merge(FieldLabelDetection first, FieldLabelDetection second) {
    boolean firstComesFirst = first.bbox().get(1) < second.bbox().get(1)
        || (first.bbox().get(1).equals(second.bbox().get(1)) && first.bbox().get(0) <= second.bbox().get(0));
    FieldLabelDetection leading = firstComesFirst ? first : second;
    FieldLabelDetection trailing = firstComesFirst ? second : first;
    List<Integer> bbox = List.of(
        Math.min(first.bbox().get(0), second.bbox().get(0)),
        Math.min(first.bbox().get(1), second.bbox().get(1)),
        Math.max(first.bbox().get(2), second.bbox().get(2)),
        Math.max(first.bbox().get(3), second.bbox().get(3))
    );
    return new FieldLabelDetection(
        leading.text() + " " + trailing.text(),
        Math.min(first.confidence(), second.confidence()),
        bbox
    );
  }

  private double geometryScore(List<Integer> labelBbox, List<Integer> valueBbox) {
    if (valueBbox == null || valueBbox.size() < 4) {
      return 0;
    }
    double labelCenterX = (labelBbox.get(0) + labelBbox.get(2)) / 2.0;
    double labelCenterY = (labelBbox.get(1) + labelBbox.get(3)) / 2.0;
    double valueCenterX = (valueBbox.get(0) + valueBbox.get(2)) / 2.0;
    double valueCenterY = (valueBbox.get(1) + valueBbox.get(3)) / 2.0;
    double width = Math.max(1, valueBbox.get(2) - valueBbox.get(0));
    double height = Math.max(1, valueBbox.get(3) - valueBbox.get(1));
    double dx = (labelCenterX - valueCenterX) / width;
    double dy = (labelCenterY - valueCenterY) / height;
    double distanceScore = 1.0 / (1.0 + Math.sqrt(dx * dx + dy * dy));
    boolean leftOfValue = labelBbox.get(2) <= valueBbox.get(2)
        && overlap(labelBbox.get(1), labelBbox.get(3), valueBbox.get(1), valueBbox.get(3)) > 0;
    boolean aboveValue = labelBbox.get(3) <= valueBbox.get(3)
        && overlap(labelBbox.get(0), labelBbox.get(2), valueBbox.get(0), valueBbox.get(2)) > 0;
    return Math.min(1, distanceScore + (leftOfValue || aboveValue ? 0.2 : 0));
  }

  private int overlap(int firstStart, int firstEnd, int secondStart, int secondEnd) {
    return Math.max(0, Math.min(firstEnd, secondEnd) - Math.max(firstStart, secondStart));
  }

  private double matchScore(String label, String candidate) {
    if (candidate.isBlank()) {
      return 0;
    }
    if (label.equals(candidate)) {
      return 1;
    }
    if (label.startsWith(candidate) || candidate.startsWith(label)) {
      return (double) Math.min(label.length(), candidate.length()) / Math.max(label.length(), candidate.length());
    }
    Set<String> labelTokens = tokens(label);
    Set<String> candidateTokens = tokens(candidate);
    if (labelTokens.isEmpty() || candidateTokens.isEmpty()) {
      return 0;
    }
    String distinctiveToken = labelTokens.iterator().next();
    if (!candidateTokens.contains(distinctiveToken)) {
      return 0;
    }
    long matched = labelTokens.stream().filter(candidateTokens::contains).count();
    return (double) matched / labelTokens.size();
  }

  private Set<String> tokens(String value) {
    Set<String> tokens = new LinkedHashSet<>();
    for (String token : value.split("[^a-z0-9\\p{IsHan}]+")) {
      if (token.length() >= 2) {
        tokens.add(canonicalToken(token));
      }
    }
    return tokens;
  }

  private String canonicalToken(String token) {
    if (token.length() > 4 && token.endsWith("s") && !token.endsWith("ss") && !token.endsWith("us")) {
      return token.substring(0, token.length() - 1);
    }
    return token;
  }

  private String normalize(String value) {
    return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
        .toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9\\p{IsHan}]+", " ")
        .trim()
        .replaceAll("\\s+", " ");
  }

  private record Candidate(
      FieldLabelDetection detection,
      double textScore,
      double combinedScore
  ) {}
}
