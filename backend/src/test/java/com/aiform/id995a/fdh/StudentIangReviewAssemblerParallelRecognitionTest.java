package com.aiform.id995a.fdh;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiform.id995a.ocr.DocumentTemplate;
import com.aiform.id995a.ocr.OcrDemoResponse;
import com.aiform.id995a.ocr.OcrPage;
import com.aiform.id995a.ocr.ParallelRecognitionOutput;
import com.aiform.id995a.ocr.StructuredFieldDetail;
import com.aiform.id995a.review.EngineStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class StudentIangReviewAssemblerParallelRecognitionTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final StudentIangReviewAssembler assembler = new StudentIangReviewAssembler(
      Clock.fixed(Instant.parse("2026-06-22T10:15:00Z"), ZoneOffset.UTC)
  );

  @Test
  void documentFieldKeepsParallelRecognitionConflictAsReviewWithoutModelNames() throws Exception {
    StructuredFieldDetail travelDocumentNo = new StructuredFieldDetail(
        2,
        "travel_document_no",
        "旅行证件号码",
        objectMapper.valueToTree("CA3273201"),
        "CA3273201",
        91,
        List.of(10, 20, 130, 50),
        "",
        "",
        0,
        "not_run",
        List.of(),
        "CA3273201",
        "并行识别结果不一致，建议采用“CA3273201”，该字段需人工复核确认。",
        "disagree",
        "parallel_llm_disagreement",
        List.of(
            new ParallelRecognitionOutput("识别结果 A", "CA3273201", 91),
            new ParallelRecognitionOutput("识别结果 B", "CA3273207", 86)
        )
    );
    FdhReviewDocument document = id990aDocument(travelDocumentNo);

    FdhReviewResult result = assembler.assemble(StudentIangMaterialCatalog.APPLICATION_TYPE_ID, List.of(document));

    FdhReviewResult.DocumentField field = result.documentFieldGroups().stream()
        .filter(group -> group.materialId().equals("id990a"))
        .flatMap(group -> group.pages().stream())
        .filter(page -> page.pageNo() == 2)
        .flatMap(page -> page.fields().stream())
        .filter(row -> row.label().equals("旅行证件号码"))
        .findFirst()
        .orElseThrow();
    assertThat(field.status()).isEqualTo("review");
    assertThat(field.suggestedValue()).isEqualTo("CA3273201");
    assertThat(field.modelAgreement()).isEqualTo("disagree");
    assertThat(field.conflictType()).isEqualTo("parallel_llm_disagreement");
    assertThat(field.issue()).contains("并行识别结果不一致");
    assertThat(field.modelOutputs()).hasSize(2);
    assertThat(field.modelOutputs().get(0).label()).isEqualTo("识别结果 A");
    assertThat(field.modelOutputs().get(1).value()).isEqualTo("CA3273207");
    assertThat(field.toString()).doesNotContain("Qwen");
  }

  private FdhReviewDocument id990aDocument(StructuredFieldDetail detail) throws Exception {
    OcrDemoResponse response = new OcrDemoResponse(
        "ID990A.pdf",
        "primary",
        5,
        List.of(
            new OcrPage(1, "", 100, 100, "", List.of(), List.of(), List.of(), List.of()),
            new OcrPage(2, "", 100, 100, "", List.of(), List.of(), List.of(), List.of(detail)),
            new OcrPage(3, "", 100, 100, "", List.of(), List.of(), List.of(), List.of()),
            new OcrPage(4, "", 100, 100, "", List.of(), List.of(), List.of(), List.of()),
            new OcrPage(5, "", 100, 100, "", List.of(), List.of(), List.of(), List.of())
        ),
        List.of(),
        new EngineStatus("primary", false, List.of()),
        objectMapper.readTree("""
            {
              "page_2": {
                "travel_document_no": "CA3273201"
              }
            }
            """),
        ""
    );
    return new FdhReviewDocument(
        "ID990A.pdf",
        "application/pdf",
        5,
        new DocumentTemplate("id990a_iang", "ID 990A", 5, 95, "test", "id990a"),
        response,
        "id990a",
        List.of(1, 2, 3, 4, 5)
    );
  }
}
