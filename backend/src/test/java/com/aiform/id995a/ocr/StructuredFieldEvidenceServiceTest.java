package com.aiform.id995a.ocr;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class StructuredFieldEvidenceServiceTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void keepsOcrLabelAndLlmValueBboxesIndependentWhenCallingJudge() throws Exception {
    FakeFieldRegionOcrGateway gateway = new FakeFieldRegionOcrGateway();
    gateway.pageDetections = List.of(
        new FieldLabelDetection("HKID", 98, List.of(60, 12, 82, 28))
    );
    AtomicReference<String> judgeSnapshot = new AtomicReference<>("");
    FieldJudgeGateway judge = (fieldKey, fieldLabel, expectedValue, valueType, snapshotDataUrl) -> {
      judgeSnapshot.set(snapshotDataUrl);
      return new FieldJudgeObservation(
          "available", "A123456(7)", "exact", "clear", "complete", ""
      );
    };
    StructuredFieldEvidenceService service = new StructuredFieldEvidenceService(
        gateway,
        judge,
        new FieldJudgeProperties(true, "enforce", "", "", "qwen3.6-flash", 2, 85, 1)
    );
    JsonNode structuredData = objectMapper.readTree("""
        {
          "page_1": {"hkid": "A123456(7)"},
          "_confidence": {"page_1": {"hkid": 99}},
          "_field_evidence": {"page_1": {"hkid": {
            "label": "HKID",
            "bbox": [1, 1, 20, 20],
            "value_bbox": [82, 12, 180, 28]
          }}}
        }
        """);

    StructuredFieldDetail detail = service.buildFieldDetails(structuredData, List.of(renderedPage()))
        .get(1)
        .get(0);

    assertThat(detail.recognitionConfidence()).isEqualTo(99);
    assertThat(detail.recognitionBbox()).containsExactly(82, 12, 180, 28);
    assertThat(detail.labelBbox()).containsExactly(60, 12, 82, 28);
    assertThat(detail.valueBbox()).containsExactly(82, 12, 180, 28);
    assertThat(detail.bbox()).isEqualTo(detail.labelBbox());
    assertThat(judgeSnapshot.get()).startsWith("data:image/jpeg;base64,");
    BufferedImage judgeImage = ImageIO.read(new ByteArrayInputStream(
        Base64.getDecoder().decode(judgeSnapshot.get().substring(judgeSnapshot.get().indexOf(',') + 1))
    ));
    assertThat(judgeImage.getWidth()).isEqualTo(112);
    assertThat(detail.judgeObservedValue()).isEqualTo("A123456(7)");
    assertThat(detail.verificationScore()).isEqualTo(100);
    assertThat(detail.verificationStatus()).isEqualTo("pass");
    assertThat(detail.scoreSource()).isEqualTo("field_judge");
  }

  @Test
  void storesBilingualJudgeReasonForTheFrontendLanguageSwitcher() throws Exception {
    FakeFieldRegionOcrGateway gateway = new FakeFieldRegionOcrGateway();
    FieldJudgeGateway judge = (fieldKey, fieldLabel, expectedValue, valueType, snapshotDataUrl) ->
        new FieldJudgeObservation(
            "available", "CHAN", "exact", "clear", "complete",
            "The value matches exactly.", "填寫值完全一致。"
        );
    StructuredFieldEvidenceService service = new StructuredFieldEvidenceService(
        gateway, judge, new FieldJudgeProperties(true, "enforce", "", "", "qwen3.6-flash", 2, 85, 1)
    );
    JsonNode structuredData = objectMapper.readTree("""
        {"page_1":{"name":"CHAN"},"_field_evidence":{"page_1":{"name":{"label":"Name","value_bbox":[82,12,180,28]}}}}
        """);

    StructuredFieldDetail detail = service.buildFieldDetails(structuredData, List.of(renderedPage())).get(1).get(0);

    assertThat(detail.verificationReason())
        .isEqualTo("{\"en\":\"The value matches exactly.\",\"zh-Hant\":\"填寫值完全一致。\"}");
  }

  @Test
  void doesNotFallBackToGenericBboxWhenLlmValueBboxIsMissing() throws Exception {
    FakeFieldRegionOcrGateway gateway = new FakeFieldRegionOcrGateway();
    gateway.pageDetections = List.of(
        new FieldLabelDetection("HKID", 98, List.of(60, 12, 82, 28))
    );
    AtomicInteger judgeCalls = new AtomicInteger();
    FieldJudgeGateway judge = (fieldKey, fieldLabel, expectedValue, valueType, snapshotDataUrl) -> {
      judgeCalls.incrementAndGet();
      return new FieldJudgeObservation(
          "available", "A123456(7)", "exact", "clear", "complete", ""
      );
    };
    StructuredFieldEvidenceService service = new StructuredFieldEvidenceService(
        gateway,
        judge,
        new FieldJudgeProperties(true, "enforce", "", "", "qwen3.6-flash", 2, 85, 1)
    );
    JsonNode structuredData = objectMapper.readTree("""
        {
          "page_1": {"hkid": "A123456(7)"},
          "_field_evidence": {"page_1": {"hkid": {
            "label": "HKID",
            "bbox": [82, 12, 180, 28]
          }}}
        }
        """);

    StructuredFieldDetail detail = service.buildFieldDetails(structuredData, List.of(renderedPage()))
        .get(1)
        .get(0);

    assertThat(judgeCalls).hasValue(0);
    assertThat(detail.valueBbox()).isEmpty();
    assertThat(detail.verificationScore()).isNull();
    assertThat(detail.verificationReason()).isEqualTo("value_bbox_missing");
  }

  @Test
  void exposesLabelBboxForHighlightWhileKeepingLlmValueBboxForCharacters() throws Exception {
    FakeFieldRegionOcrGateway gateway = new FakeFieldRegionOcrGateway();
    gateway.pageDetections = List.of(
        new FieldLabelDetection("Length of residence", 97, List.of(8, 12, 108, 28)),
        new FieldLabelDetection("22 year(s)", 99, List.of(120, 50, 180, 70))
    );
    StructuredFieldEvidenceService service = new StructuredFieldEvidenceService(gateway);
    JsonNode structuredData = objectMapper.readTree("""
        {
          "page_1": {"residence_years": "22 year(s)"},
          "_field_evidence": {
            "page_1": {
              "residence_years": {
                "label": "Length of residence",
                "value_bbox": [120, 50, 180, 70]
              }
            }
          }
        }
        """);

    StructuredFieldDetail detail = service.buildFieldDetails(structuredData, List.of(renderedPage()))
        .get(1)
        .get(0);

    assertThat(gateway.pageDetectionCallCount).isEqualTo(1);
    assertThat(detail.bbox()).containsExactly(8, 12, 108, 28);
    assertThat(detail.labelBbox()).containsExactly(8, 12, 108, 28);
    assertThat(detail.valueBbox()).containsExactly(120, 50, 180, 70);
    assertThat(detail.evidenceBbox()).isEmpty();
    assertThat(detail.snapshotDataUrl()).startsWith("data:image/jpeg;base64,");
    assertThat(detail.characters().get(0).bbox().get(0)).isEqualTo(120);
  }

  @Test
  void usesTheOcrLocatedFieldLabelForHighlightWhenItsValueIsNotLocated() throws Exception {
    FakeFieldRegionOcrGateway gateway = new FakeFieldRegionOcrGateway();
    gateway.pageDetections = List.of(
        new FieldLabelDetection("Surname in English", 98, List.of(8, 12, 140, 28))
    );
    StructuredFieldEvidenceService service = new StructuredFieldEvidenceService(gateway);
    JsonNode structuredData = objectMapper.readTree("""
        {
          "page_1": {"surname": "CHEN"},
          "_field_evidence": {"page_1": {"surname": {"label": "Surname in English"}}}
        }
        """);

    StructuredFieldDetail detail = service.buildFieldDetails(structuredData, List.of(renderedPage()))
        .get(1)
        .get(0);

    assertThat(detail.locationStatus()).isEqualTo("located");
    assertThat(detail.labelBbox()).containsExactly(8, 12, 140, 28);
    assertThat(detail.evidenceBbox()).isEmpty();
    assertThat(detail.bbox()).isEqualTo(detail.labelBbox());
  }

  @Test
  void buildsLlmFieldDetailsWithoutCallingOcrGateway() throws Exception {
    FakeFieldRegionOcrGateway gateway = new FakeFieldRegionOcrGateway();
    StructuredFieldEvidenceService service = new StructuredFieldEvidenceService();
    JsonNode structuredData = objectMapper.readTree("""
        {
          "page_1": {
            "personal": {
              "surname_en": "AGUIJAR",
              "travel_document_no": "PH88342115"
            }
          },
          "_confidence": {
            "page_1": 95
          },
          "_field_evidence": {
            "page_1": {
              "page_1.personal.surname_en": {
                "label": "Surname in English",
                "value_bbox": [20, 20, 60, 20],
                "char_confidences": [
                  {"char": "A", "index": 0, "confidence": 96, "bbox": [20, 20, 8, 20]},
                  {"char": "G", "index": 1, "confidence": 96, "bbox": [28, 20, 8, 20]},
                  {"char": "U", "index": 2, "confidence": 96, "bbox": [36, 20, 8, 20]},
                  {"char": "I", "index": 3, "confidence": 96, "bbox": [44, 20, 8, 20]},
                  {"char": "J", "index": 4, "confidence": 96, "bbox": [52, 20, 8, 20]},
                  {"char": "A", "index": 5, "confidence": 96, "bbox": [60, 20, 8, 20]},
                  {"char": "R", "index": 6, "confidence": 96, "bbox": [68, 20, 8, 20]}
                ]
              },
              "personal": {
                "travel_document_no": {
                  "label": "Travel document no.",
                  "value_bbox": {"x": 0.1, "y": 0.3, "width": 0.3, "height": 0.1}
                }
              }
            }
          }
        }
        """);

    Map<Integer, List<StructuredFieldDetail>> details = service.buildFieldDetails(
        structuredData,
        List.of(renderedPage())
    );

    assertThat(gateway.batchCallCount).isZero();
    assertThat(gateway.batchSizes).isEmpty();
    assertThat(details.get(1)).hasSize(2);
    assertThat(details.get(1).get(0).snapshotDataUrl()).startsWith("data:image/jpeg;base64,");
    assertThat(details.get(1).get(0).ocrText()).isBlank();
    assertThat(details.get(1).get(0).ocrStatus()).isEqualTo("not_run");
    assertThat(details.get(1).get(0).characters().get(0).bbox()).containsExactly(20, 20, 28, 40);
  }

  @Test
  void reportsNoSnapshotWhenLlmFieldEvidenceHasNoFieldBbox() throws Exception {
    FakeFieldRegionOcrGateway gateway = new FakeFieldRegionOcrGateway();
    StructuredFieldEvidenceService service = new StructuredFieldEvidenceService();
    JsonNode structuredData = objectMapper.readTree("""
        {
          "page_1": {
            "present_address": "Flat 7"
          },
          "_field_evidence": {
            "page_1": {
              "label": "Whole page",
              "value_bbox": [0, 0, 200, 200]
            }
          }
        }
        """);

    Map<Integer, List<StructuredFieldDetail>> details = service.buildFieldDetails(
        structuredData,
        List.of(renderedPage())
    );

    assertThat(gateway.batchCallCount).isEqualTo(0);
    assertThat(details.get(1)).hasSize(1);
    assertThat(details.get(1).get(0).ocrStatus()).isEqualTo("not_run");
    assertThat(details.get(1).get(0).snapshotDataUrl()).isBlank();
  }

  @Test
  void rendersYesNoOptionBooleansAsSelectedOptionMeaning() throws Exception {
    StructuredFieldEvidenceService service = new StructuredFieldEvidenceService();
    JsonNode structuredData = objectMapper.readTree("""
        {
          "page_3": {
            "supplied_facilities": {
              "pillow": false,
              "refrigerator": false,
              "table": false,
              "bed": true,
              "pillow_checked": false
            }
          }
        }
        """);

    Map<Integer, List<StructuredFieldDetail>> details = service.buildFieldDetails(
        structuredData,
        List.of(new RenderedOcrPage(3, "", 200, 200))
    );

    assertThat(details.get(3))
        .extracting(StructuredFieldDetail::displayValue)
        .contains("没有", "有", "未勾选");
    assertThat(details.get(3).stream()
        .filter(detail -> detail.path().equals("supplied_facilities.pillow"))
        .findFirst()
        .orElseThrow()
        .displayValue()).isEqualTo("没有");
    assertThat(details.get(3).stream()
        .filter(detail -> detail.path().equals("supplied_facilities.pillow_checked"))
        .findFirst()
        .orElseThrow()
        .displayValue()).isEqualTo("未勾选");
  }

  @Test
  void rendersHouseholdCountLabelsAsApplicantWrittenNumbersInsteadOfBinaryFlags() throws Exception {
    StructuredFieldEvidenceService service = new StructuredFieldEvidenceService();
    JsonNode structuredData = objectMapper.readTree("""
        {
          "page_3": {
            "家庭人数_3名成人": 1,
            "家庭人数_1名小孩": 0,
            "家庭人数_1名将出生的婴儿": 0,
            "家庭人数_0家庭成员需要经常照料": 0,
            "雇工数目": 0,
            "水电供应": "有"
          }
        }
        """);

    Map<Integer, List<StructuredFieldDetail>> details = service.buildFieldDetails(
        structuredData,
        List.of(new RenderedOcrPage(3, "", 200, 200))
    );

    assertThat(details.get(3))
        .filteredOn(detail -> detail.path().startsWith("家庭人数_") || detail.path().equals("雇工数目"))
        .extracting(StructuredFieldDetail::displayValue)
        .containsExactly("3", "1", "1", "0", "0");
    assertThat(details.get(3).stream()
        .filter(detail -> detail.path().equals("家庭人数_3名成人"))
        .findFirst()
        .orElseThrow()
        .characters())
        .extracting(FieldCharacterEvidence::text)
        .containsExactly("3");
  }

  @Test
  void ignoresNoApplicantInputMarkerWhenBuildingVisibleFieldDetails() throws Exception {
    StructuredFieldEvidenceService service = new StructuredFieldEvidenceService();
    JsonNode structuredData = objectMapper.readTree("""
        {
          "page_4": {
            "no_applicant_input": true
          }
        }
        """);

    Map<Integer, List<StructuredFieldDetail>> details = service.buildFieldDetails(
        structuredData,
        List.of(new RenderedOcrPage(4, "", 200, 200))
    );

    assertThat(details.get(4)).isEmpty();
  }

  @Test
  void omitsNullAndBlankFieldsWhenBuildingVisibleFieldDetails() throws Exception {
    StructuredFieldEvidenceService service = new StructuredFieldEvidenceService();
    JsonNode structuredData = objectMapper.readTree("""
        {
          "page_1": {
            "filled_name": "ALICE",
            "empty_address": null,
            "blank_note": "",
            "negative_answer": false
          }
        }
        """);

    Map<Integer, List<StructuredFieldDetail>> details = service.buildFieldDetails(
        structuredData,
        List.of(new RenderedOcrPage(1, "", 200, 200))
    );

    assertThat(details.get(1))
        .extracting(StructuredFieldDetail::path)
        .containsExactly("filled_name", "negative_answer");
  }

  @Test
  void preservesCharacterStatusFromFieldEvidence() throws Exception {
    StructuredFieldEvidenceService service = new StructuredFieldEvidenceService();
    JsonNode structuredData = objectMapper.readTree("""
        {
          "page_1": {
            "name": "AB"
          },
          "_field_evidence": {
            "page_1": {
              "name": {
                "label": "Name",
                "value_bbox": [20, 20, 80, 40],
                "char_confidences": [
                  {"char": "A", "index": 0, "confidence": 96, "status": "ok"},
                  {"char": "B", "index": 1, "confidence": 20, "status": "smudged"}
                ]
              }
            }
          }
        }
        """);

    Map<Integer, List<StructuredFieldDetail>> details = service.buildFieldDetails(
        structuredData,
        List.of(renderedPage())
    );

    assertThat(details.get(1).get(0).characters())
        .extracting(FieldCharacterEvidence::status)
        .containsExactly("ok", "smudged");
  }

  private RenderedOcrPage renderedPage() throws Exception {
    BufferedImage image = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = image.createGraphics();
    graphics.setColor(Color.WHITE);
    graphics.fillRect(0, 0, 200, 200);
    graphics.setColor(Color.BLACK);
    graphics.drawString("AGUIJAR", 20, 30);
    graphics.drawString("PH88342115", 20, 70);
    graphics.dispose();
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    ImageIO.write(image, "png", output);
    byte[] bytes = output.toByteArray();
    return new RenderedOcrPage(
        1,
        bytes,
        "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(bytes),
        200,
        200
    );
  }

  private static final class FakeFieldRegionOcrGateway implements FieldRegionOcrGateway {
    private int batchCallCount;
    private int pageDetectionCallCount;
    private final List<Integer> batchSizes = new ArrayList<>();
    private List<FieldLabelDetection> pageDetections = List.of();

    @Override
    public List<FieldLabelDetection> detectPage(byte[] pageImageBytes) {
      pageDetectionCallCount += 1;
      return pageDetections;
    }

    @Override
    public List<FieldRegionOcrResult> recognizeBatch(List<byte[]> cropImageBytes) {
      batchCallCount += 1;
      batchSizes.add(cropImageBytes.size());
      List<FieldRegionOcrResult> results = new ArrayList<>();
      for (int index = 0; index < cropImageBytes.size(); index += 1) {
        results.add(new FieldRegionOcrResult(index == 0 ? "AGUIAR" : "PH88342115", 91, "available"));
      }
      return results;
    }
  }
}
