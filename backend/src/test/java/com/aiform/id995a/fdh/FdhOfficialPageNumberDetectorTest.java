package com.aiform.id995a.fdh;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiform.id995a.llm.LlmModelProfile;
import com.aiform.id995a.llm.LlmModelRegistry;
import com.aiform.id995a.llm.OfficialPageNumberRecognitionGateway;
import com.aiform.id995a.llm.OfficialPageNumberRecognitionResult;
import com.aiform.id995a.llm.LlmProperties;
import com.aiform.id995a.llm.DashScopeProperties;
import com.aiform.id995a.ocr.DocumentTemplate;
import com.aiform.id995a.ocr.RenderedOcrPage;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class FdhOfficialPageNumberDetectorTest {

  @Test
  void usesLlmOfficialPageNumbersToDetectMissingMiddlePage() throws Exception {
    FdhOfficialPageNumberDetector detector = detectorReturning(List.of(
        result(1, "ID 988B", "06/2024", 1),
        result(2, "ID 988B", "06/2024", 3),
        result(3, "ID 988B", "06/2024", 4)
    ));

    List<Integer> pageNumbers = detector.detect(
        "何嘉萱-B(缺页).pdf",
        template("id988b_2024_06", "ID 988B (06/2024)", 3),
        renderedPages(3),
        null
    );

    assertThat(pageNumbers).containsExactly(1, 3, 4);
  }

  @Test
  void acceptsLlmOfficialPageNumbersForSupportedCoreForms() throws Exception {
    assertThat(detectorReturning(List.of(
        result(1, "ID 988A", "06/2024", 1),
        result(2, "ID 988A", "06/2024", 2),
        result(3, "ID 988A", "06/2024", 3),
        result(4, "ID 988A", "06/2024", 4),
        result(5, "ID 988A", "06/2024", 5)
    )).detect("ID988A.pdf", template("id988a_2024_06", "ID 988A (06/2024)", 5), renderedPages(5), null))
        .containsExactly(1, 2, 3, 4, 5);

    assertThat(detectorReturning(List.of(
        result(1, "ID 407", "11/2016", 1),
        result(2, "ID 407", "11/2016", 2),
        result(3, "ID 407", "11/2016", 3),
        result(4, "ID 407", "11/2016", 4)
    )).detect("ID407.pdf", template("id407_2016_11", "ID 407 (11/2016)", 4), renderedPages(4), null))
        .containsExactly(1, 2, 3, 4);
  }

  @Test
  void acceptsDuplicateOfficialPageFiveForId990a() throws Exception {
    assertThat(detectorReturning(List.of(
        result(1, "ID 990A", "", 1),
        result(2, "ID 990A", "", 2),
        result(3, "ID 990A", "", 3),
        result(4, "ID 990A", "", 4),
        result(5, "ID 990A", "", 5),
        result(6, "ID 990A", "", 5)
    )).detect("ID990A.pdf", template("id990a_2025_01", "ID 990A (01/2025)", 6), renderedPages(6), null))
        .containsExactly(1, 2, 3, 4, 5, 5);
  }

  @Test
  void rejectsLlmPageNumbersWithWrongFormOutOfRangeOrDuplicates() throws Exception {
    assertThat(detectorReturning(List.of(
        result(1, "ID 988A", "06/2024", 1),
        result(2, "ID 988A", "06/2024", 2)
    )).detect("ID988B.pdf", template("id988b_2024_06", "ID 988B (06/2024)", 2), renderedPages(2), null))
        .isEmpty();

    assertThat(detectorReturning(List.of(
        result(1, "ID 988B", "06/2024", 1),
        result(2, "ID 988B", "06/2024", 5)
    )).detect("ID988B.pdf", template("id988b_2024_06", "ID 988B (06/2024)", 2), renderedPages(2), null))
        .isEmpty();

    assertThat(detectorReturning(List.of(
        result(1, "ID 988B", "06/2024", 1),
        result(2, "ID 988B", "06/2024", 1)
    )).detect("ID988B.pdf", template("id988b_2024_06", "ID 988B (06/2024)", 2), renderedPages(2), null))
        .isEmpty();
  }

  @Test
  void returnsEmptyPageNumbersWhenRecognitionExceedsConfiguredTimeout() throws Exception {
    String property = "fdh.review.official-page-number-timeout-millis";
    String previous = System.getProperty(property);
    System.setProperty(property, "100");
    try {
      OfficialPageNumberRecognitionGateway gateway = (
          String filename,
          DocumentTemplate template,
          List<RenderedOcrPage> pages,
          LlmModelProfile profile
      ) -> {
        try {
          Thread.sleep(600);
        } catch (InterruptedException exception) {
          Thread.currentThread().interrupt();
          throw new IOException("interrupted", exception);
        }
        return List.of(
            result(1, "ID 988A", "06/2024", 1),
            result(2, "ID 988A", "06/2024", 2),
            result(3, "ID 988A", "06/2024", 3),
            result(4, "ID 988A", "06/2024", 4),
            result(5, "ID 988A", "06/2024", 5)
        );
      };
      FdhOfficialPageNumberDetector detector = new FdhOfficialPageNumberDetector(gateway, registry());

      long startedAt = System.nanoTime();
      List<Integer> pageNumbers = detector.detect(
          "ID988A.pdf",
          template("id988a_2024_06", "ID 988A (06/2024)", 5),
          renderedPages(5),
          null
      );
      long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;

      assertThat(pageNumbers).isEmpty();
      assertThat(elapsedMillis).isLessThan(400);
    } finally {
      if (previous == null) {
        System.clearProperty(property);
      } else {
        System.setProperty(property, previous);
      }
    }
  }

  private FdhOfficialPageNumberDetector detectorReturning(List<OfficialPageNumberRecognitionResult> results) {
    OfficialPageNumberRecognitionGateway gateway = (
        String filename,
        DocumentTemplate template,
        List<RenderedOcrPage> pages,
        LlmModelProfile profile
    ) -> results;
    return new FdhOfficialPageNumberDetector(gateway, registry());
  }

  private LlmModelRegistry registry() {
    return new LlmModelRegistry(
        new LlmProperties(true, "https://apie.zhisuaninfo.com/v1", "test-key", "Qwen3.6-35B-A3B", 4096, 60, 4),
        new DashScopeProperties("", "", "", false)
    );
  }

  private OfficialPageNumberRecognitionResult result(int uploadedPage, String formId, String version, int pageNo) {
    return new OfficialPageNumberRecognitionResult(uploadedPage, formId, version, pageNo, 95, "footer");
  }

  private List<RenderedOcrPage> renderedPages(int pages) {
    return java.util.stream.IntStream.rangeClosed(1, pages)
        .mapToObj(page -> new RenderedOcrPage(page, new byte[] {1}, "data:image/png;base64,AAA=", 100, 100))
        .toList();
  }

  private DocumentTemplate template(String templateId, String footerId, int pages) {
    return new DocumentTemplate(templateId, footerId, pages, 98, "test", templateId);
  }
}
