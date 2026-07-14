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

class FdhReviewAssemblerWorkExperienceTest {

  private static final String CONTRACT_NO = "FH-CON-IDN2026-0612";

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final FdhReviewAssembler assembler = new FdhReviewAssembler(
      5100,
      1236,
      Clock.fixed(Instant.parse("2026-05-28T10:15:00Z"), ZoneOffset.UTC)
  );

  @Test
  void workExperiencePeriodsKeepIndexedKeysAndDoNotMergeSharedLabels() throws Exception {
    FdhReviewResult result = assembler.assemble(
        "change_employer",
        List.of(id988aWithWorkExperiencePeriods())
    );

    List<String> extractedKeys = result.fields().stream()
        .map(FdhReviewResult.StandardField::key)
        .filter(key -> key.startsWith("extracted."))
        .toList();

    assertThat(extractedKeys).contains(
        "extracted.name_of_current_employer_if_applicable",
        "extracted.address_of_current_employer_if_applicable",
        "extracted.employer_1_name",
        "extracted.employer_1_address",
        "extracted.employer_1_period_from",
        "extracted.employer_1_period_to",
        "extracted.employer_2_name",
        "extracted.employer_2_address",
        "extracted.employer_2_period_from",
        "extracted.employer_2_period_to",
        "extracted.total_duration_years",
        "extracted.total_duration_months"
    );
    assertThat(extractedKeys).doesNotContain(
        "extracted.name_of_employer_s",
        "extracted.address",
        "extracted.from_mm_yy",
        "extracted.to_mm_yy",
        "extracted.total_duration_of_working_experience_as_a_domestic_helper"
    );

    FdhReviewResult.StandardField currentEmployerName =
        field(result, "extracted.name_of_current_employer_if_applicable");
    assertThat(currentEmployerName.status()).isEqualTo("pass");
    assertThat(currentEmployerName.normalizedValue()).isEqualTo("Mrs. Karen WALKER");
    assertThat(currentEmployerName.sources()).hasSize(1);
    assertThat(currentEmployerName.sources().get(0).fieldName())
        .isEqualTo("Name of current employer (if applicable)");
    assertThat(currentEmployerName.sources().get(0).snapshotDataUrl())
        .isEqualTo("data:image/jpeg;base64,CURRENT_EMPLOYER_NAME");

    FdhReviewResult.StandardField currentEmployerAddress =
        field(result, "extracted.address_of_current_employer_if_applicable");
    assertThat(currentEmployerAddress.status()).isEqualTo("pass");
    assertThat(currentEmployerAddress.normalizedValue())
        .isEqualTo("Flat 26B, The Repulse Bay, 109 Repulse Bay Road, HK");
    assertThat(currentEmployerAddress.sources()).hasSize(1);
    assertThat(currentEmployerAddress.sources().get(0).fieldName())
        .isEqualTo("Address of current employer (if applicable)");
    assertThat(currentEmployerAddress.sources().get(0).snapshotDataUrl())
        .isEqualTo("data:image/jpeg;base64,CURRENT_EMPLOYER_ADDRESS");

    FdhReviewResult.StandardField employer1Address = field(result, "extracted.employer_1_address");
    assertThat(employer1Address.status()).isEqualTo("pass");
    assertThat(employer1Address.normalizedValue()).isEqualTo("Flat 5A, 12/F, Park View, 88 Tai Tam Road, Hong Kong");
    assertThat(employer1Address.sources()).hasSize(1);
    assertThat(employer1Address.sources().get(0).fieldName()).isEqualTo("Address");
    assertThat(employer1Address.sources().get(0).snapshotDataUrl()).isEqualTo("data:image/jpeg;base64,EMPLOYER_1_ADDRESS");

    FdhReviewResult.StandardField employer2From = field(result, "extracted.employer_2_period_from");
    assertThat(employer2From.status()).isEqualTo("pass");
    assertThat(employer2From.normalizedValue()).isEqualTo("08/22");
    assertThat(employer2From.sources()).hasSize(1);
    assertThat(employer2From.sources().get(0).fieldName()).isEqualTo("From (mm/yy)");

    FdhReviewResult.StandardField totalYears = field(result, "extracted.total_duration_years");
    assertThat(totalYears.status()).isEqualTo("pass");
    assertThat(totalYears.normalizedValue()).isEqualTo("6");
    assertThat(totalYears.sources()).hasSize(1);

    FdhReviewResult.StandardField totalMonths = field(result, "extracted.total_duration_months");
    assertThat(totalMonths.status()).isEqualTo("pass");
    assertThat(totalMonths.normalizedValue()).isEqualTo("9");
    assertThat(totalMonths.sources()).hasSize(1);
  }

  private FdhReviewResult.StandardField field(FdhReviewResult result, String key) {
    return result.fields().stream()
        .filter(field -> field.key().equals(key))
        .findFirst()
        .orElseThrow();
  }

  private FdhReviewDocument id988aWithWorkExperiencePeriods() throws Exception {
    List<StructuredFieldDetail> fields = List.of(
        structuredField(2, "page_2.name_of_current_employer", "Name of current employer (if applicable)", "Mrs. Karen WALKER", "CURRENT_EMPLOYER_NAME", 95),
        structuredField(2, "page_2.address_of_current_employer", "Address of current employer (if applicable)", "Flat 26B, The Repulse Bay, 109 Repulse Bay Road, HK", "CURRENT_EMPLOYER_ADDRESS", 95),
        structuredField(2, "page_2.employer_1_name", "Name of employer (s)", "Mrs. Linda CHEN", "EMPLOYER_1_NAME", 95),
        structuredField(2, "page_2.employer_1_address", "Address", "Flat 5A, 12/F, Park View, 88 Tai Tam Road, Hong Kong", "EMPLOYER_1_ADDRESS", 92),
        structuredField(2, "page_2.employer_1_period_from", "From (mm/yy)", "06/19", "EMPLOYER_1_FROM", 95),
        structuredField(2, "page_2.employer_1_period_to", "To (mm/yy)", "05/22", "EMPLOYER_1_TO", 95),
        structuredField(2, "page_2.employer_2_name", "Name of employer (s)", "Mrs. Karen WALKER", "EMPLOYER_2_NAME", 95),
        structuredField(2, "page_2.employer_2_address", "Address", "Flat 26B, The Repulse Bay, 109 Repulse Bay Road, HK", "EMPLOYER_2_ADDRESS", 95),
        structuredField(2, "page_2.employer_2_period_from", "From (mm/yy)", "08/22", "EMPLOYER_2_FROM", 95),
        structuredField(2, "page_2.employer_2_period_to", "To (mm/yy)", "03/26", "EMPLOYER_2_TO", 95),
        structuredField(2, "page_2.total_duration_years", "Total duration of working experience as a domestic helper", "6", "TOTAL_YEARS", 95),
        structuredField(2, "page_2.total_duration_months", "Total duration of working experience as a domestic helper", "9", "TOTAL_MONTHS", 95)
    );
    OcrPage page = new OcrPage(
        2,
        "data:image/png;base64,PAGE2",
        100,
        100,
        "",
        List.of(),
        List.of(),
        List.of(),
        fields
    );
    OcrDemoResponse response = new OcrDemoResponse(
        "ID988A.pdf",
        "test",
        5,
        List.of(page),
        List.of(),
        new EngineStatus("test", List.of()),
        objectMapper.readTree("""
            {
              "page_2": {
                "name_of_current_employer": "Mrs. Karen WALKER",
                "address_of_current_employer": "Flat 26B, The Repulse Bay, 109 Repulse Bay Road, HK",
                "employer_1_name": "Mrs. Linda CHEN",
                "employer_1_address": "Flat 5A, 12/F, Park View, 88 Tai Tam Road, Hong Kong",
                "employer_1_period_from": "06/19",
                "employer_1_period_to": "05/22",
                "employer_2_name": "Mrs. Karen WALKER",
                "employer_2_address": "Flat 26B, The Repulse Bay, 109 Repulse Bay Road, HK",
                "employer_2_period_from": "08/22",
                "employer_2_period_to": "03/26",
                "total_duration_years": "6",
                "total_duration_months": "9",
                "employment_contract_no": "%s"
              }
            }
            """.formatted(CONTRACT_NO)),
        ""
    );
    return new FdhReviewDocument(
        "ID988A.pdf",
        "application/pdf",
        5,
        new DocumentTemplate("id988a_2024_06", "ID 988A (06/2024)", 5, 98, "test", "id988a_2024_06"),
        response,
        "id988a"
    );
  }

  private StructuredFieldDetail structuredField(
      int page,
      String path,
      String label,
      String value,
      String snapshotId,
      double confidence
  ) {
    return new StructuredFieldDetail(
        page,
        path,
        label,
        text(value),
        value,
        confidence,
        List.of(10, 20, 100, 40),
        "data:image/jpeg;base64," + snapshotId,
        "",
        0,
        "not_run",
        List.of()
    );
  }

  private JsonNode text(String value) {
    return objectMapper.getNodeFactory().textNode(value);
  }
}
