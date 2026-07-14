package com.aiform.id995a.ocr;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

public class FieldVerificationScorer {

  private static final Set<String> MATCH_TYPES = Set.of(
      "exact",
      "normalized_equal",
      "semantic_equal",
      "mismatch",
      "unreadable",
      "crop_incomplete"
  );

  private final int passThreshold;

  public FieldVerificationScorer(int passThreshold) {
    this.passThreshold = Math.max(0, Math.min(100, passThreshold));
  }

  public FieldVerification score(String expectedValue, FieldJudgeObservation observation) {
    if (observation == null || !"available".equals(observation.status())) {
      return FieldVerification.reviewWithoutScore(
          "judge_unavailable",
          observation == null ? "judge result is missing" : observation.reason()
      );
    }
    if (!MATCH_TYPES.contains(observation.matchType())) {
      return FieldVerification.reviewWithoutScore("judge_invalid", "unknown match type");
    }
    if (contradicts(expectedValue, observation)) {
      return FieldVerification.reviewWithoutScore("judge_contradiction", observation.reason());
    }

    int resultScore = switch (observation.matchType()) {
      case "exact" -> 100;
      case "normalized_equal" -> 90;
      case "semantic_equal" -> 80;
      case "crop_incomplete" -> 40;
      case "unreadable" -> 30;
      case "mismatch" -> 0;
      default -> 0;
    };
    if ("unreadable".equals(observation.legibility())) {
      resultScore = Math.min(resultScore, 30);
    }
    if (!"complete".equals(observation.cropCoverage())) {
      resultScore = Math.min(resultScore, 40);
    }
    return new FieldVerification(
        resultScore,
        resultScore > passThreshold ? "pass" : "review",
        observation.matchType(),
        observation.reason()
    );
  }

  private boolean contradicts(String expectedValue, FieldJudgeObservation observation) {
    return switch (observation.matchType()) {
      case "exact" -> !clean(expectedValue).equals(clean(observation.observedValue()));
      case "normalized_equal" -> !normalize(expectedValue).equals(normalize(observation.observedValue()));
      default -> false;
    };
  }

  private String normalize(String value) {
    return Normalizer.normalize(clean(value), Normalizer.Form.NFKC)
        .toLowerCase(Locale.ROOT)
        .replaceAll("\\s+", "");
  }

  private String clean(String value) {
    return value == null ? "" : value.trim();
  }
}
