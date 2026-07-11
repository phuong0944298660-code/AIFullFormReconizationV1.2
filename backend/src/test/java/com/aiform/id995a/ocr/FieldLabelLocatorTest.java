package com.aiform.id995a.ocr;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class FieldLabelLocatorTest {

  private final FieldLabelLocator locator = new FieldLabelLocator();

  @Test
  void locatesPrintedLabelWithoutMatchingTheHandwrittenValue() {
    List<FieldLabelDetection> detections = List.of(
        new FieldLabelDetection(
            "Length of residence in country/territory of domicile",
            98,
            List.of(25, 1025, 430, 1072)
        ),
        new FieldLabelDetection("22", 61, List.of(445, 1030, 490, 1070)),
        new FieldLabelDetection(
            "Has the applicant completed any of the following programmes",
            99,
            List.of(25, 1092, 720, 1128)
        )
    );

    List<Integer> bbox = locator.locate(
        "Length of residence in country/territory of domicile",
        detections
    );

    assertThat(bbox).containsExactly(25, 1025, 430, 1072);
  }

  @Test
  void returnsNoHighlightWhenNoPrintedLabelMatchesReliably() {
    List<FieldLabelDetection> detections = List.of(
        new FieldLabelDetection("22", 98, List.of(445, 1030, 490, 1070)),
        new FieldLabelDetection("undergraduate qualification", 98, List.of(25, 1092, 400, 1128))
    );

    assertThat(locator.locate(
        "Length of residence in country/territory of domicile",
        detections
    )).isEmpty();
  }

  @Test
  void matchesLabelsDespitePunctuationAndWhitespaceDifferences() {
    List<FieldLabelDetection> detections = List.of(
        new FieldLabelDetection(
            "E-mail address (if any)",
            97,
            List.of(25, 915, 225, 950)
        )
    );

    assertThat(locator.locate("E mail address if any", detections))
        .containsExactly(25, 915, 225, 950);
  }

  @Test
  void rejectsGenericSuffixOverlapWhenDistinctiveFieldWordIsMissing() {
    assertThat(locator.locate(
        "Status in Hong Kong",
        List.of(new FieldLabelDetection(
            "in Hong Kong (to be completed by the applicant)",
            99,
            List.of(28, 176, 503, 201)
        ))
    )).isEmpty();
  }

  @Test
  void rejectsAmbiguousDuplicatePrintedLabels() {
    assertThat(locator.locate(
        "(if any)",
        List.of(
            new FieldLabelDetection("(if any)", 99, List.of(20, 100, 80, 120)),
            new FieldLabelDetection("(if any)", 99, List.of(220, 100, 280, 120))
        )
    )).isEmpty();
  }

  @Test
  void resolvesDuplicateLabelsUsingTheValueBboxGeometry() {
    List<FieldLabelDetection> detections = List.of(
        new FieldLabelDetection("Name of employer(s)", 98, List.of(100, 300, 300, 340)),
        new FieldLabelDetection("Name of employer(s)", 98, List.of(100, 700, 300, 740))
    );

    FieldLabelLocation location = locator.locate(
        "Name of employer(s)",
        detections,
        List.of(320, 720, 620, 780)
    );

    assertThat(location.bbox()).containsExactly(100, 700, 300, 740);
    assertThat(location.reason()).isEmpty();
  }

  @Test
  void mergesAdjacentOcrLinesBeforeMatchingTheLabel() {
    List<FieldLabelDetection> detections = List.of(
        new FieldLabelDetection("Name of", 96, List.of(100, 300, 220, 330)),
        new FieldLabelDetection("employer(s)", 97, List.of(100, 334, 280, 366))
    );

    FieldLabelLocation location = locator.locate(
        "Name of employer(s)",
        detections,
        List.of(300, 320, 620, 380)
    );

    assertThat(location.bbox()).containsExactly(100, 300, 280, 366);
    assertThat(location.reason()).isEmpty();
  }

  @Test
  void reportsWhyAConfidentOcrCandidateDidNotMatch() {
    FieldLabelLocation location = locator.locate(
        "Name of employer(s)",
        List.of(new FieldLabelDetection("Address", 98, List.of(100, 300, 220, 330))),
        List.of(300, 320, 620, 380)
    );

    assertThat(location.bbox()).isEmpty();
    assertThat(location.reason()).isEqualTo("ocr_text_not_found");
  }

  @Test
  void toleratesParenthesizedPluralBeingRecognizedAsAPlainPlural() {
    FieldLabelLocation location = locator.locate(
        "Name of employer(s)",
        List.of(new FieldLabelDetection("Name of employers", 96, List.of(100, 300, 280, 340))),
        List.of(300, 320, 620, 380)
    );

    assertThat(location.bbox()).containsExactly(100, 300, 280, 340);
  }
}
