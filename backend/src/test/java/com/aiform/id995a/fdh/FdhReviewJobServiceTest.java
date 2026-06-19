package com.aiform.id995a.fdh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiform.id995a.llm.ExtractionProgressListener;
import com.aiform.id995a.ocr.BaiduOcrPageRenderer;
import com.aiform.id995a.ocr.DocumentTemplate;
import com.aiform.id995a.ocr.OcrDemoResponse;
import com.aiform.id995a.ocr.OcrDemoService;
import com.aiform.id995a.ocr.OcrPage;
import com.aiform.id995a.ocr.RenderedOcrPage;
import com.aiform.id995a.ocr.TemplateDetectionService;
import com.aiform.id995a.review.EngineStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class FdhReviewJobServiceTest {

  private static final String CONTRACT_NO = "FH-CON-IDN2026-0612";

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void asyncJobRunsMaterialClassificationLlmExtractionAndRules() throws Exception {
    BaiduOcrPageRenderer renderer = mock(BaiduOcrPageRenderer.class);
    TemplateDetectionService templateDetectionService = mock(TemplateDetectionService.class);
    OcrDemoService ocrDemoService = mock(OcrDemoService.class);
    FdhReviewAssembler assembler = new FdhReviewAssembler(
        5100,
        1236,
        Clock.fixed(Instant.parse("2026-05-28T10:15:00Z"), ZoneOffset.UTC)
    );
    FdhReviewJobService service = new FdhReviewJobService(
        renderer,
        templateDetectionService,
        ocrDemoService,
        assembler
    );

    when(renderer.render(anyString(), anyString(), any())).thenAnswer(invocation -> {
      String filename = invocation.getArgument(0);
      return renderedPages(filename.contains("988A") ? 5 : filename.contains("988B") ? 4 : 4);
    });
    when(templateDetectionService.detect(anyString(), anyString(), any(), anyList())).thenAnswer(invocation -> {
      String filename = invocation.getArgument(0);
      if (filename.contains("988A")) {
        return template("id988a_2024_06", "ID 988A (06/2024)", 5);
      }
      if (filename.contains("988B")) {
        return template("id988b_2024_06", "ID 988B (06/2024)", 4);
      }
      return template("id407_2016_11", "ID 407 (11/2016)", 4);
    });
    when(ocrDemoService.recognizeRenderedForFdhReview(
        anyString(),
        anyList(),
        any(ExtractionProgressListener.class),
        any(),
        any(DocumentTemplate.class)
    )).thenAnswer(invocation -> response(invocation.getArgument(0), invocation.getArgument(4)));

    FdhReviewJobStatusResponse started = service.start(
        "entry_visa",
        List.of(file("ID988A.pdf"), file("ID988B.pdf"), file("ID407.pdf")),
        null
    );
    FdhReviewJobStatusResponse completed = waitForCompletion(service, started.jobId());

    assertThat(completed.status()).isEqualTo("completed");
    assertThat(completed.progress()).isEqualTo(100);
    assertThat(completed.result()).isNotNull();
    assertThat(completed.result().decision()).isEqualTo("PASS");
    assertThat(completed.result().uploadedFiles()).hasSize(3);
    assertThat(completed.result().materials()).filteredOn(FdhReviewResult.MaterialRow::core)
        .allMatch(row -> "pass".equals(row.status()));
    verify(ocrDemoService, times(3)).recognizeRenderedForFdhReview(
        anyString(),
        anyList(),
        any(ExtractionProgressListener.class),
        any(),
        any(DocumentTemplate.class)
    );
  }

  @Test
  void reportsPageLevelProgressWhileCurrentFileIsStillRunning() throws Exception {
    BaiduOcrPageRenderer renderer = mock(BaiduOcrPageRenderer.class);
    TemplateDetectionService templateDetectionService = mock(TemplateDetectionService.class);
    OcrDemoService ocrDemoService = mock(OcrDemoService.class);
    FdhReviewJobService service = new FdhReviewJobService(
        renderer,
        templateDetectionService,
        ocrDemoService,
        new FdhReviewAssembler(5100, 1236, Clock.systemUTC())
    );
    CountDownLatch pageStarted = new CountDownLatch(1);
    CountDownLatch allowCompletion = new CountDownLatch(1);

    when(renderer.render(anyString(), anyString(), any())).thenReturn(renderedPages(5));
    when(templateDetectionService.detect(anyString(), anyString(), any(), anyList()))
        .thenReturn(template("id988a_2024_06", "ID 988A (06/2024)", 5));
    when(ocrDemoService.recognizeRenderedForFdhReview(
        anyString(),
        anyList(),
        any(ExtractionProgressListener.class),
        any(),
        any(DocumentTemplate.class)
    )).thenAnswer(invocation -> {
      ExtractionProgressListener listener = invocation.getArgument(2);
      listener.pageStarted(3);
      pageStarted.countDown();
      assertThat(allowCompletion.await(2, TimeUnit.SECONDS)).isTrue();
      return response(invocation.getArgument(0), invocation.getArgument(4));
    });

    FdhReviewJobStatusResponse started = service.start("entry_visa", List.of(file("ID988A.pdf")), null);
    assertThat(pageStarted.await(2, TimeUnit.SECONDS)).isTrue();

    FdhReviewJobStatusResponse running = service.status(started.jobId());
    allowCompletion.countDown();

    assertThat(running.status()).isEqualTo("running");
    assertThat(running.progress()).isGreaterThan(10);
    assertThat(running.message()).contains("第 3 页");
    assertThat(waitForCompletion(service, started.jobId()).status()).isEqualTo("completed");
  }

  @Test
  void limitsMultipleUploadedMaterialsToTwoParallelExtractions() throws Exception {
    BaiduOcrPageRenderer renderer = mock(BaiduOcrPageRenderer.class);
    TemplateDetectionService templateDetectionService = mock(TemplateDetectionService.class);
    OcrDemoService ocrDemoService = mock(OcrDemoService.class);
    FdhReviewJobService service = new FdhReviewJobService(
        renderer,
        templateDetectionService,
        ocrDemoService,
        new FdhReviewAssembler(5100, 1236, Clock.systemUTC())
    );
    CountDownLatch firstTwoExtractionsStarted = new CountDownLatch(2);
    CountDownLatch thirdExtractionStarted = new CountDownLatch(1);
    CountDownLatch releaseExtractions = new CountDownLatch(1);
    AtomicInteger extractionCalls = new AtomicInteger();
    AtomicInteger activeExtractions = new AtomicInteger();
    AtomicInteger maxActiveExtractions = new AtomicInteger();

    when(renderer.render(anyString(), anyString(), any())).thenAnswer(invocation -> renderedPages(
        invocation.getArgument(0, String.class).contains("988A") ? 5 : 4
    ));
    when(templateDetectionService.detect(anyString(), anyString(), any(), anyList())).thenAnswer(invocation -> {
      String filename = invocation.getArgument(0);
      if (filename.contains("988A")) {
        return template("id988a_2024_06", "ID 988A (06/2024)", 5);
      }
      if (filename.contains("988B")) {
        return template("id988b_2024_06", "ID 988B (06/2024)", 4);
      }
      return template("id407_2016_11", "ID 407 (11/2016)", 4);
    });
    when(ocrDemoService.recognizeRenderedForFdhReview(
        anyString(),
        anyList(),
        any(ExtractionProgressListener.class),
        any(),
        any(DocumentTemplate.class)
    )).thenAnswer(invocation -> {
      int call = extractionCalls.incrementAndGet();
      int active = activeExtractions.incrementAndGet();
      maxActiveExtractions.accumulateAndGet(active, Math::max);
      if (call <= 2) {
        firstTwoExtractionsStarted.countDown();
      }
      if (call == 3) {
        thirdExtractionStarted.countDown();
      }
      assertThat(releaseExtractions.await(2, TimeUnit.SECONDS)).isTrue();
      activeExtractions.decrementAndGet();
      return response(invocation.getArgument(0), invocation.getArgument(4));
    });

    FdhReviewJobStatusResponse started = service.start(
        "entry_visa",
        List.of(file("ID988A.pdf"), file("ID988B.pdf"), file("ID407.pdf")),
        null
    );
    boolean firstTwoStarted = firstTwoExtractionsStarted.await(500, TimeUnit.MILLISECONDS);
    boolean thirdStartedBeforeRelease = thirdExtractionStarted.await(250, TimeUnit.MILLISECONDS);
    releaseExtractions.countDown();
    FdhReviewJobStatusResponse completed = waitForCompletion(service, started.jobId());

    assertThat(firstTwoStarted).isTrue();
    assertThat(thirdStartedBeforeRelease).isFalse();
    assertThat(maxActiveExtractions.get()).isEqualTo(2);
    assertThat(thirdExtractionStarted.await(2, TimeUnit.SECONDS)).isTrue();
    assertThat(completed.status()).isEqualTo("completed");
  }

  @Test
  void skipsNonFillableOfficialPagesForExtractionButKeepsOriginalMaterialPageCount() throws Exception {
    BaiduOcrPageRenderer renderer = mock(BaiduOcrPageRenderer.class);
    TemplateDetectionService templateDetectionService = mock(TemplateDetectionService.class);
    OcrDemoService ocrDemoService = mock(OcrDemoService.class);
    FdhReviewJobService service = new FdhReviewJobService(
        renderer,
        templateDetectionService,
        ocrDemoService,
        new FdhReviewAssembler(5100, 1236, Clock.systemUTC())
    );
    Map<String, List<Integer>> extractedPagesByFilename = new ConcurrentHashMap<>();

    when(renderer.render(anyString(), anyString(), any())).thenAnswer(invocation -> {
      String filename = invocation.getArgument(0);
      return renderedPages(filename.contains("988A") ? 5 : 4);
    });
    when(templateDetectionService.detect(anyString(), anyString(), any(), anyList())).thenAnswer(invocation -> {
      String filename = invocation.getArgument(0);
      if (filename.contains("988A")) {
        return template("id988a_2024_06", "ID 988A (06/2024)", 5);
      }
      if (filename.contains("988B")) {
        return template("id988b_2024_06", "ID 988B (06/2024)", 4);
      }
      return template("id407_2016_11", "ID 407 (11/2016)", 4);
    });
    when(ocrDemoService.recognizeRenderedForFdhReview(
        anyString(),
        anyList(),
        any(ExtractionProgressListener.class),
        any(),
        any(DocumentTemplate.class)
    )).thenAnswer(invocation -> {
      String filename = invocation.getArgument(0);
      List<RenderedOcrPage> pages = invocation.getArgument(1);
      extractedPagesByFilename.put(filename, pages.stream().map(RenderedOcrPage::page).toList());
      return response(filename, invocation.getArgument(4), pages.size());
    });

    FdhReviewJobStatusResponse started = service.start(
        "entry_visa",
        List.of(file("ID988A.pdf"), file("ID988B.pdf"), file("ID407.pdf")),
        null
    );
    FdhReviewJobStatusResponse completed = waitForCompletion(service, started.jobId());

    assertThat(completed.status()).isEqualTo("completed");
    assertThat(extractedPagesByFilename.get("ID988A.pdf")).containsExactly(1, 2, 3, 4);
    assertThat(extractedPagesByFilename.get("ID988B.pdf")).containsExactly(1, 2, 3);
    assertThat(extractedPagesByFilename.get("ID407.pdf")).containsExactly(1, 2, 3, 4);
    assertThat(uploadedFile(completed.result(), "id988a").pages()).isEqualTo(5);
    assertThat(uploadedFile(completed.result(), "id988b").pages()).isEqualTo(4);
    assertThat(uploadedFile(completed.result(), "id407").pages()).isEqualTo(4);
  }

  @Test
  void usesDetectedOfficialPageNumbersForMissingMiddlePageAndNonFillablePageSkipping() throws Exception {
    BaiduOcrPageRenderer renderer = mock(BaiduOcrPageRenderer.class);
    TemplateDetectionService templateDetectionService = mock(TemplateDetectionService.class);
    OcrDemoService ocrDemoService = mock(OcrDemoService.class);
    FdhOfficialPageNumberDetector officialPageNumberDetector = mock(FdhOfficialPageNumberDetector.class);
    FdhReviewJobService service = new FdhReviewJobService(
        renderer,
        templateDetectionService,
        ocrDemoService,
        new FdhReviewAssembler(5100, 1236, Clock.systemUTC()),
        officialPageNumberDetector,
        1
    );
    Map<String, List<Integer>> extractedPagesByFilename = new ConcurrentHashMap<>();

    when(renderer.render(anyString(), anyString(), any())).thenReturn(renderedPages(3));
    when(templateDetectionService.detect(anyString(), anyString(), any(), anyList()))
        .thenReturn(template("id988b_2024_06", "ID 988B (06/2024)", 4));
    when(officialPageNumberDetector.detect(anyString(), any(DocumentTemplate.class), anyList(), any()))
        .thenReturn(List.of(1, 3, 4));
    when(ocrDemoService.recognizeRenderedForFdhReview(
        anyString(),
        anyList(),
        any(ExtractionProgressListener.class),
        any(),
        any(DocumentTemplate.class)
    )).thenAnswer(invocation -> {
      String filename = invocation.getArgument(0);
      List<RenderedOcrPage> pages = invocation.getArgument(1);
      extractedPagesByFilename.put(filename, pages.stream().map(RenderedOcrPage::page).toList());
      return response(filename, invocation.getArgument(4), pages.size());
    });

    FdhReviewJobStatusResponse started = service.start("entry_visa", List.of(file("ID988B.pdf")), null);
    FdhReviewJobStatusResponse completed = waitForCompletion(service, started.jobId());

    FdhReviewResult.MaterialRow id988b = material(completed.result(), "id988b");
    assertThat(completed.status()).isEqualTo("completed");
    assertThat(extractedPagesByFilename.get("ID988B.pdf")).containsExactly(1, 2);
    assertThat(uploadedFile(completed.result(), "id988b").pages()).isEqualTo(3);
    assertThat(id988b.status()).isEqualTo("fail");
    assertThat(id988b.issue()).contains("\u7f3a\u7b2c 2 \u9875").doesNotContain("\u7f3a\u7b2c 4 \u9875");
  }

  @Test
  void continuesWhenOfficialPageNumberRecognitionConnectionCloses() throws Exception {
    BaiduOcrPageRenderer renderer = mock(BaiduOcrPageRenderer.class);
    TemplateDetectionService templateDetectionService = mock(TemplateDetectionService.class);
    OcrDemoService ocrDemoService = mock(OcrDemoService.class);
    FdhOfficialPageNumberDetector officialPageNumberDetector = mock(FdhOfficialPageNumberDetector.class);
    FdhReviewJobService service = new FdhReviewJobService(
        renderer,
        templateDetectionService,
        ocrDemoService,
        new FdhReviewAssembler(5100, 1236, Clock.systemUTC()),
        officialPageNumberDetector,
        1
    );
    Map<String, List<Integer>> extractedPagesByFilename = new ConcurrentHashMap<>();

    when(renderer.render(anyString(), anyString(), any())).thenReturn(renderedPages(5));
    when(templateDetectionService.detect(anyString(), anyString(), any(), anyList()))
        .thenReturn(template("id988a_2024_06", "ID 988A (06/2024)", 5));
    when(officialPageNumberDetector.detect(anyString(), any(DocumentTemplate.class), anyList(), any()))
        .thenThrow(new IOException("HTTP/1.1 header parser received no bytes"));
    when(ocrDemoService.recognizeRenderedForFdhReview(
        anyString(),
        anyList(),
        any(ExtractionProgressListener.class),
        any(),
        any(DocumentTemplate.class)
    )).thenAnswer(invocation -> {
      String filename = invocation.getArgument(0);
      List<RenderedOcrPage> pages = invocation.getArgument(1);
      extractedPagesByFilename.put(filename, pages.stream().map(RenderedOcrPage::page).toList());
      return response(filename, invocation.getArgument(4), pages.size());
    });

    FdhReviewJobStatusResponse started = service.start("entry_visa", List.of(file("ID988A.pdf")), null);
    FdhReviewJobStatusResponse completed = waitForCompletion(service, started.jobId());

    assertThat(completed.status()).isEqualTo("completed");
    assertThat(extractedPagesByFilename.get("ID988A.pdf")).containsExactly(1, 2, 3, 4);
  }

  @Test
  void reportsUserFacingMessageWhenRequiredExtractionLlmConnectionCloses() throws Exception {
    BaiduOcrPageRenderer renderer = mock(BaiduOcrPageRenderer.class);
    TemplateDetectionService templateDetectionService = mock(TemplateDetectionService.class);
    OcrDemoService ocrDemoService = mock(OcrDemoService.class);
    FdhReviewJobService service = new FdhReviewJobService(
        renderer,
        templateDetectionService,
        ocrDemoService,
        new FdhReviewAssembler(5100, 1236, Clock.systemUTC())
    );

    when(renderer.render(anyString(), anyString(), any())).thenReturn(renderedPages(5));
    when(templateDetectionService.detect(anyString(), anyString(), any(), anyList()))
        .thenReturn(template("id988a_2024_06", "ID 988A (06/2024)", 5));
    when(ocrDemoService.recognizeRenderedForFdhReview(
        anyString(),
        anyList(),
        any(ExtractionProgressListener.class),
        any(),
        any(DocumentTemplate.class)
    )).thenThrow(new IOException("HTTP/1.1 header parser received no bytes"));

    FdhReviewJobStatusResponse started = service.start("entry_visa", List.of(file("ID988A.pdf")), null);
    FdhReviewJobStatusResponse failed = waitForCompletion(service, started.jobId());

    assertThat(failed.status()).isEqualTo("failed");
    assertThat(failed.error()).contains("模型服务连接中断");
    assertThat(failed.error()).doesNotContain("HTTP/1.1 header parser received no bytes");
    assertThat(failed.message()).isEqualTo(failed.error());
  }

  private FdhReviewJobStatusResponse waitForCompletion(FdhReviewJobService service, String jobId) throws Exception {
    FdhReviewJobStatusResponse status = service.status(jobId);
    for (int attempt = 0; attempt < 30; attempt += 1) {
      status = service.status(jobId);
      if ("completed".equals(status.status()) || "failed".equals(status.status())) {
        return status;
      }
      Thread.sleep(25);
    }
    return status;
  }

  private MockMultipartFile file(String filename) {
    return new MockMultipartFile(
        "files",
        filename,
        "application/pdf",
        ("fake-" + filename).getBytes(StandardCharsets.UTF_8)
    );
  }

  private List<RenderedOcrPage> renderedPages(int pages) {
    return java.util.stream.IntStream.rangeClosed(1, pages)
        .mapToObj(page -> new RenderedOcrPage(page, new byte[] {1}, "data:image/png;base64,AAA=", 100, 100))
        .toList();
  }

  private DocumentTemplate template(String templateId, String footerId, int pages) {
    return new DocumentTemplate(templateId, footerId, pages, 98, "test", templateId);
  }

  private OcrDemoResponse response(String filename, DocumentTemplate template) throws Exception {
    return response(filename, template, template.pageCount());
  }

  private OcrDemoResponse response(String filename, DocumentTemplate template, int pageCount) throws Exception {
    String json = switch (template.templateId()) {
      case "id988a_2024_06" -> """
          {
            "page_1": {
              "application_type": "Entry visa - Domestic helper from abroad",
              "part_2_personal_particulars": {
                "surname_en": "NURHALIZA",
                "given_names_en": "SITI",
                "travel_document_no": "C8923745",
                "date_of_birth": "27/11/1992",
                "nationality": "Indonesian",
                "signature_of_applicant": "signature detected"
              }
            },
            "page_4": {"employment_contract_no": "%s"}
          }
          """.formatted(CONTRACT_NO);
      case "id988b_2024_06" -> """
          {
            "page_1": {"employer_particulars": {"employer_name": "CHAN TAI MAN"}},
            "page_3": {"employment_contract_no": "%s"},
            "page_4": {"declaration": {"signature_of_employer": "signature detected"}}
          }
          """.formatted(CONTRACT_NO);
      default -> """
          {
            "page_1": {
              "contract_no": "%s",
              "name_of_helper": "SITI NURHALIZA",
              "name_of_employer": "CHAN TAI MAN"
            },
            "page_2": {
              "monthly_wages": "HK$5,100",
              "food_allowance": "HK$1,236"
            },
            "page_4": {"signature_of_employer": "signature detected"}
          }
          """.formatted(CONTRACT_NO);
    };
    return new OcrDemoResponse(
        filename,
        "test",
        pageCount,
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

  private FdhReviewResult.UploadedFile uploadedFile(FdhReviewResult result, String materialId) {
    return result.uploadedFiles().stream()
        .filter(file -> materialId.equals(file.materialId()))
        .findFirst()
        .orElseThrow();
  }

  private FdhReviewResult.MaterialRow material(FdhReviewResult result, String materialId) {
    return result.materials().stream()
        .filter(row -> materialId.equals(row.id()))
        .findFirst()
        .orElseThrow();
  }
}
