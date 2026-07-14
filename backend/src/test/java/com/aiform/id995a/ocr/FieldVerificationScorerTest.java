package com.aiform.id995a.ocr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import org.junit.jupiter.api.Test;

class FieldVerificationScorerTest {

  private final FieldVerificationScorer scorer = new FieldVerificationScorer(85);

  @Test
  void assignsDeterministicScoresToValidatedJudgeFacts() {
    List<Case> cases = List.of(
        new Case("exact", "clear", "complete", 100, "pass"),
        new Case("normalized_equal", "clear", "complete", 90, "pass"),
        new Case("semantic_equal", "clear", "complete", 80, "review"),
        new Case("crop_incomplete", "clear", "incomplete", 40, "review"),
        new Case("unreadable", "unreadable", "complete", 30, "review"),
        new Case("mismatch", "clear", "complete", 0, "review")
    );

    for (Case item : cases) {
      FieldVerification result = scorer.score(
          "A123456(7)",
          new FieldJudgeObservation(
              "available",
              item.matchType().equals("mismatch") ? "A123456(8)" : "A123456(7)",
              item.matchType(),
              item.legibility(),
              item.coverage(),
              "judge reason"
          )
      );
      assertEquals(item.score(), result.score(), item.matchType());
      assertEquals(item.status(), result.status(), item.matchType());
    }
  }

  @Test
  void normalizedFormattingCanPassAtThreshold() {
    FieldVerification result = scorer.score(
        "A 123456 (7)",
        new FieldJudgeObservation(
            "available",
            "A123456(7)",
            "normalized_equal",
            "clear",
            "complete",
            "spacing differs"
        )
    );

    assertEquals(90, result.score());
    assertEquals("pass", result.status());
  }

  @Test
  void requiresScoreStrictlyGreaterThanThresholdToPass() {
    FieldVerificationScorer boundaryScorer = new FieldVerificationScorer(80);

    FieldVerification result = boundaryScorer.score(
        "Hong Kong Polytechnic University",
        new FieldJudgeObservation(
            "available",
            "The Hong Kong Polytechnic University",
            "semantic_equal",
            "clear",
            "complete",
            "same institution"
        )
    );

    assertEquals(80, result.score());
    assertEquals("review", result.status());
  }

  @Test
  void rejectsContradictoryExactClaim() {
    FieldVerification result = scorer.score(
        "A123456(7)",
        new FieldJudgeObservation(
            "available",
            "A123456(8)",
            "exact",
            "clear",
            "complete",
            "claimed exact"
        )
    );

    assertFalse(result.hasScore());
    assertEquals("review", result.status());
    assertEquals("judge_contradiction", result.reasonCode());
  }

  @Test
  void doesNotNormalizeDifferentMonetaryValuesIntoEquality() {
    FieldVerification result = scorer.score(
        "HK$1.00",
        new FieldJudgeObservation(
            "available", "HK$100", "normalized_equal", "clear", "complete", ""
        )
    );

    assertFalse(result.hasScore());
    assertEquals("review", result.status());
    assertEquals("judge_contradiction", result.reasonCode());
  }

  @Test
  void unavailableJudgeFailsClosed() {
    FieldVerification result = scorer.score(
        "CHAN",
        FieldJudgeObservation.unavailable("timeout")
    );

    assertFalse(result.hasScore());
    assertEquals("review", result.status());
    assertEquals("judge_unavailable", result.reasonCode());
  }

  private record Case(String matchType, String legibility, String coverage, int score, String status) {}
}
