package com.aiform.id995a.fdh;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiform.id995a.ocr.DocumentTemplate;
import com.aiform.id995a.ocr.OcrDemoResponse;
import com.aiform.id995a.ocr.OcrPage;
import com.aiform.id995a.ocr.StructuredFieldDetail;
import com.aiform.id995a.review.EngineStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class FdhReviewAssemblerTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final FdhReviewAssembler assembler = new FdhReviewAssembler(
      5100,
      1236,
      Clock.fixed(Instant.parse("2026-05-28T10:15:00Z"), ZoneOffset.UTC)
  );

  @Test
  void passesWhenCoreMaterialsAndCrossFileFieldsAreComplete() throws Exception {
    FdhReviewResult result = assembler.assemble(
        "entry_visa",
        List.of(id988a(), id988b(), id407("SITI NURHALIZA", "HK$5,100", "HK$1,236"), passport("C8923745", 90))
    );

    assertThat(result.decision()).isEqualTo("PASS");
    assertThat(result.materials()).filteredOn(FdhReviewResult.MaterialRow::core)
        .allMatch(row -> "pass".equals(row.status()));
    assertThat(result.materials()).filteredOn(row -> row.no() > 3)
        .allMatch(row -> !row.blocking());
    assertThat(result.fields()).filteredOn(FdhReviewResult.StandardField::blocking)
        .noneMatch(field -> "fail".equals(field.status()));
  }

  @Test
  void failsWhenCoreMaterialIsMissing() throws Exception {
    FdhReviewResult result = assembler.assemble("entry_visa", List.of(id988a(), id988b()));

    FdhReviewResult.MaterialRow id407 = result.materials().stream()
        .filter(row -> row.id().equals("id407"))
        .findFirst()
        .orElseThrow();
    assertThat(result.decision()).isEqualTo("FAIL");
    assertThat(id407.status()).isEqualTo("fail");
    assertThat(id407.blocking()).isTrue();
  }

  @Test
  void materialRowsAfterThreeDoNotBlockFinalDecision() throws Exception {
    FdhReviewResult result = assembler.assemble(
        "entry_visa",
        List.of(id988a(), id988b(), id407("SITI NURHALIZA", "HK$5,100", "HK$1,236"))
    );

    FdhReviewResult.MaterialRow financialProof = result.materials().stream()
        .filter(row -> row.id().equals("financialProof"))
        .findFirst()
        .orElseThrow();
    assertThat(financialProof.status()).isEqualTo("warn");
    assertThat(financialProof.blocking()).isFalse();
    assertThat(result.decision()).isEqualTo("PASS");
  }

  @Test
  void obviousCrossDocumentMismatchFails() throws Exception {
    FdhReviewResult result = assembler.assemble(
        "entry_visa",
        List.of(id988a(), id988b(), id407("SITI NURHALIZA BINTI", "HK$5,100", "HK$1,236"), passport("C8923745", 90))
    );

    FdhReviewResult.StandardField helperName = field(result, "helper.name.full_en");
    assertThat(result.decision()).isEqualTo("FAIL");
    assertThat(helperName.status()).isEqualTo("fail");
    assertThat(helperName.issue()).contains("明显不一致");
  }

  @Test
  void lowConfidenceOrTinyDifferenceRequiresReview() throws Exception {
    FdhReviewResult result = assembler.assemble(
        "entry_visa",
        List.of(id988a(), id988b(), id407("SITI NURHALIZA", "HK$5,100", "HK$1,236"), passport("C892374S", 62))
    );

    FdhReviewResult.StandardField travelDoc = field(result, "helper.travel_doc.number");
    assertThat(result.decision()).isEqualTo("REVIEW");
    assertThat(travelDoc.status()).isEqualTo("review");
  }

  @Test
  void wageBelowConfiguredThresholdFails() throws Exception {
    FdhReviewResult result = assembler.assemble(
        "entry_visa",
        List.of(id988a(), id988b(), id407("SITI NURHALIZA", "HK$4,900", "HK$1,236"))
    );

    FdhReviewResult.StandardField wage = field(result, "contract.monthly_wage_hkd");
    assertThat(result.decision()).isEqualTo("FAIL");
    assertThat(wage.status()).isEqualTo("fail");
    assertThat(wage.issue()).contains("低于规则阈值");
  }

  @Test
  void failsEntryVisaWhenId988aEntryVisaCheckboxBelongsToContractRenewalRow() throws Exception {
    FdhReviewResult result = assembler.assemble(
        "entry_visa",
        List.of(
            id988aApplicationType(
                "contract_renewal_with_the_same_employer_or_change_of_employer",
                "entry visa"
            ),
            id988b(),
            id407("SITI NURHALIZA", "HK$5,100", "HK$1,236")
        )
    );

    FdhReviewResult.StandardField applicationType = field(result, "case.application_type");
    assertThat(result.decision()).isEqualTo("FAIL");
    assertThat(applicationType.status()).isEqualTo("fail");
    assertThat(applicationType.normalizedValue())
        .contains("Contract renewal with the same employer or change of employer")
        .contains("entry visa");
  }

  @Test
  void acceptsRenewalWhenId988aUsesContractRenewalRow() throws Exception {
    FdhReviewResult result = assembler.assemble(
        "renewal",
        List.of(
            id988aApplicationType(
                "contract_renewal_with_the_same_employer_or_change_of_employer",
                "entry visa"
            ),
            id988b(),
            id407("SITI NURHALIZA", "HK$5,100", "HK$1,236")
        )
    );

    FdhReviewResult.StandardField applicationType = field(result, "case.application_type");
    assertThat(applicationType.status()).isEqualTo("pass");
    assertThat(applicationType.normalizedValue())
        .contains("Contract renewal with the same employer or change of employer")
        .contains("entry visa");
  }

  private FdhReviewResult.StandardField field(FdhReviewResult result, String key) {
    return result.fields().stream()
        .filter(field -> field.key().equals(key))
        .findFirst()
        .orElseThrow();
  }

  private FdhReviewDocument id988a() throws Exception {
    return document(
        "ID988A.pdf",
        "id988a",
        5,
        "id988a_2024_06",
        "ID 988A (06/2024)",
        """
            {
              "page_1": {
                "application_type": "Entry visa - Domestic helper from abroad",
                "part_2_personal_particulars": {
                  "surname_en": "SITI",
                  "given_names_en": "NURHALIZA",
                  "travel_document_no": "C8923745",
                  "date_of_birth": "27/11/1992",
                  "nationality": "Indonesian",
                  "signature_of_applicant": "signature detected"
                }
              }
            }
            """
    );
  }

  private FdhReviewDocument id988aApplicationType(String applicationTypeKey, String value) throws Exception {
    return document(
        "ID988A.pdf",
        "id988a",
        5,
        "id988a_2024_06",
        "ID 988A (06/2024)",
        """
            {
              "page_1": {
                "application_type": {
                  "%s": "%s"
                },
                "part_2_personal_particulars": {
                  "surname_en": "SITI",
                  "given_names_en": "NURHALIZA",
                  "travel_document_no": "C8923745",
                  "date_of_birth": "27/11/1992",
                  "nationality": "Indonesian",
                  "signature_of_applicant": "signature detected"
                }
              }
            }
            """.formatted(applicationTypeKey, value)
    );
  }

  private FdhReviewDocument id988b() throws Exception {
    return document(
        "ID988B.pdf",
        "id988b",
        4,
        "id988b_2024_06",
        "ID 988B (06/2024)",
        """
            {
              "page_1": {
                "employer_particulars": {
                  "employer_name": "CHAN TAI MAN"
                }
              },
              "page_4": {
                "declaration": {
                  "signature_of_employer": "signature detected"
                }
              }
            }
            """
    );
  }

  private FdhReviewDocument id407(String helperName, String wages, String foodAllowance) throws Exception {
    return document(
        "ID407.pdf",
        "id407",
        4,
        "id407_2016_11",
        "ID 407 (11/2016)",
        """
            {
              "page_1": {
                "contract_no": "DH-2026-004218",
                "name_of_helper": "%s",
                "name_of_employer": "CHAN TAI MAN"
              },
              "page_2": {
                "monthly_wages": "%s",
                "food_allowance": "%s"
              },
              "page_4": {
                "signature_of_employer": "signature detected"
              }
            }
            """.formatted(helperName, wages, foodAllowance)
    );
  }

  private FdhReviewDocument passport(String passportNo, double confidence) throws Exception {
    StructuredFieldDetail passportNumber = new StructuredFieldDetail(
        1,
        "page_1.passport_no",
        "Passport No.",
        text(passportNo),
        passportNo,
        confidence,
        List.of(),
        "",
        passportNo,
        confidence,
        "available",
        List.of()
    );
    OcrPage page = new OcrPage(
        1,
        "data:image/png;base64,AAA=",
        100,
        100,
        "",
        List.of(),
        List.of(),
        List.of(),
        List.of(passportNumber)
    );
    OcrDemoResponse response = new OcrDemoResponse(
        "passport.jpg",
        "test",
        1,
        List.of(page),
        List.of(),
        new EngineStatus("test", false, List.of()),
        objectMapper.readTree("""
            {
              "page_1": {
                "passport_name": "SITI NURHALIZA",
                "date_of_birth": "27 NOV 1992",
                "nationality": "Indonesia"
              }
            }
            """),
        ""
    );
    return new FdhReviewDocument(
        "passport.jpg",
        "image/jpeg",
        1,
        new DocumentTemplate("unknown_1p_passport", "", 1, 70, "filename", "passport"),
        response,
        "helperTravelCopy"
    );
  }

  private FdhReviewDocument document(
      String filename,
      String materialId,
      int pages,
      String templateId,
      String footerId,
      String json
  ) throws Exception {
    return new FdhReviewDocument(
        filename,
        "application/pdf",
        pages,
        new DocumentTemplate(templateId, footerId, pages, 98, "test", templateId),
        response(filename, pages, json),
        materialId
    );
  }

  private OcrDemoResponse response(String filename, int pages, String json) throws Exception {
    return new OcrDemoResponse(
        filename,
        "test",
        pages,
        List.of(new OcrPage(
            1,
            "data:image/png;base64,AAA=",
            100,
            100,
            "",
            List.of(),
            List.of(),
            List.of(),
            List.of()
        )),
        List.of(),
        new EngineStatus("test", false, List.of()),
        objectMapper.readTree(json),
        ""
    );
  }

  private JsonNode text(String value) {
    return objectMapper.getNodeFactory().textNode(value);
  }
}
