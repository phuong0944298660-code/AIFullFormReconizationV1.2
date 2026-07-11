package com.aiform.id995a.ocr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class FieldEvidenceLocatorTest {

  @Test
  void locatesLabelAndValueOnSameRow() {
    List<FieldLabelDetection> detections = List.of(
        new FieldLabelDetection("HK Identity Card No.", 98, List.of(100, 200, 360, 245)),
        new FieldLabelDetection("A123456(7)", 96, List.of(380, 200, 620, 245))
    );

    FieldEvidenceRegion result = new FieldEvidenceLocator().locate(
        "HK Identity Card No.",
        "A123456(7)",
        detections,
        1200,
        1600
    );

    assertEquals("located", result.status());
    assertEquals("label_and_value", result.method());
    assertEquals(List.of(100, 200, 360, 245), result.labelBbox());
    assertEquals(List.of(380, 200, 620, 245), result.valueBbox());
    assertTrue(result.evidenceBbox().get(0) <= 100);
    assertTrue(result.evidenceBbox().get(2) >= 620);
  }

  @Test
  void retainsTheLabelBboxWhenOnlyLabelIsLocated() {
    FieldEvidenceRegion result = new FieldEvidenceLocator().locate(
        "Passport No.",
        "K1234567",
        List.of(new FieldLabelDetection("Passport No.", 97, List.of(100, 100, 260, 140))),
        1000,
        1400
    );

    assertEquals("partial", result.status());
    assertEquals("value_not_found", result.reason());
    assertTrue(result.evidenceBbox().isEmpty());
  }

  @Test
  void retainsTheLabelBboxWhenValueIsNotDetected() {
    FieldEvidenceRegion result = new FieldEvidenceLocator().locate(
        "Passport No.",
        "K1234567",
        List.of(new FieldLabelDetection("Passport No.", 97, List.of(100, 100, 260, 140))),
        1000,
        1400
    );

    assertEquals("partial", result.status());
    assertEquals(List.of(100, 100, 260, 140), result.labelBbox());
    assertTrue(result.evidenceBbox().isEmpty());
  }

  @Test
  void usesNearbyDetectionWhenHandwrittenTextIsUnreadable() {
    FieldEvidenceRegion result = new FieldEvidenceLocator().locate(
        "Surname",
        "CHAN",
        List.of(
            new FieldLabelDetection("Surname", 98, List.of(100, 300, 240, 340)),
            new FieldLabelDetection("", 0, List.of(280, 300, 500, 345))
        ),
        1000,
        1400
    );

    assertEquals("located", result.status());
    assertEquals(List.of(280, 300, 500, 345), result.valueBbox());
  }

  @Test
  void rejectsEquallyPlausibleValueRegions() {
    FieldEvidenceRegion result = new FieldEvidenceLocator().locate(
        "Name",
        "CHAN TAI MAN",
        List.of(
            new FieldLabelDetection("Name", 99, List.of(100, 500, 220, 540)),
            new FieldLabelDetection("", 0, List.of(250, 500, 420, 540)),
            new FieldLabelDetection("", 0, List.of(450, 500, 620, 540))
        ),
        1000,
        1400
    );

    assertEquals("ambiguous", result.status());
    assertEquals("multiple_value_candidates", result.reason());
    assertTrue(result.evidenceBbox().isEmpty());
  }

  @Test
  void doesNotPairAnExactValueFromAnUnrelatedDistantRegion() {
    FieldEvidenceRegion result = new FieldEvidenceLocator().locate(
        "HKID",
        "A123456(7)",
        List.of(
            new FieldLabelDetection("HKID", 98, List.of(100, 100, 200, 130)),
            new FieldLabelDetection("A123456(7)", 99, List.of(800, 1000, 980, 1040))
        ),
        1200,
        1600
    );

    assertEquals("partial", result.status());
    assertEquals("value_not_found", result.reason());
    assertTrue(result.evidenceBbox().isEmpty());
  }

  @Test
  void fallsBackToValueOnlyEvidenceWhenTheFieldLabelIsNotDetected() {
    FieldEvidenceRegion result = new FieldEvidenceLocator().locate(
        "Surname in English",
        "CHEN",
        List.of(new FieldLabelDetection("CHEN", 98, List.of(440, 220, 570, 260))),
        1200,
        1600
    );

    assertEquals("value_only", result.status());
    assertEquals("value_only", result.method());
    assertTrue(result.labelBbox().isEmpty());
    assertEquals(List.of(440, 220, 570, 260), result.valueBbox());
    assertFalse(result.evidenceBbox().isEmpty());
  }
}
