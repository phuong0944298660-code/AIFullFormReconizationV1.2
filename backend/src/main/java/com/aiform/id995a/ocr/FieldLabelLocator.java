package com.aiform.id995a.ocr;

import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class FieldLabelLocator {

  private static final double MIN_CONFIDENCE = 70;
  private static final double MIN_SCORE = 0.72;

  public List<Integer> locate(String label, List<FieldLabelDetection> detections) {
    String normalizedLabel = normalize(label);
    if (normalizedLabel.length() < 3 || detections == null || detections.isEmpty()) {
      return List.of();
    }
    FieldLabelDetection best = null;
    double bestScore = 0;
    boolean ambiguous = false;
    for (FieldLabelDetection detection : detections) {
      if (detection == null || detection.confidence() < MIN_CONFIDENCE || detection.bbox().size() < 4) {
        continue;
      }
      double score = matchScore(normalizedLabel, normalize(detection.text()));
      if (score > bestScore + 0.0001) {
        best = detection;
        bestScore = score;
        ambiguous = false;
      } else if (best != null
          && score >= MIN_SCORE
          && Math.abs(score - bestScore) <= 0.0001
          && !detection.bbox().equals(best.bbox())) {
        ambiguous = true;
      }
    }
    return best != null && bestScore >= MIN_SCORE && !ambiguous ? best.bbox() : List.of();
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
        tokens.add(token);
      }
    }
    return tokens;
  }

  private String normalize(String value) {
    return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
        .toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9\\p{IsHan}]+", " ")
        .trim()
        .replaceAll("\\s+", " ");
  }
}
