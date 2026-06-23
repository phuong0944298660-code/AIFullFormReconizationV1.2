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
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
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
  void jobProgressDoesNotMoveBackwardWhenParallelFilesReportEarlierStages() throws Exception {
    Class<?> stateType = Class.forName("com.aiform.id995a.fdh.FdhReviewJobService$JobState");
    Constructor<?> constructor = stateType.getDeclaredConstructor(String.class, String.class, int.class);
    constructor.setAccessible(true);
    Object state = constructor.newInstance("job-1", "iang_recent_in_hk", 4);

    Method markPageProgress = stateType.getDeclaredMethod(
        "markPageProgress",
        String.class,
        int.class,
        int.class,
        String.class
    );
    Method markActive = stateType.getDeclaredMethod("markActive", String.class, String.class);
    Method snapshot = stateType.getDeclaredMethod("snapshot");
    markPageProgress.setAccessible(true);
    markActive.setAccessible(true);
    snapshot.setAccessible(true);

    markPageProgress.invoke(state, "付款证明.png", 1, 1, "已完成第 1 页识别");
    int advancedProgress = ((FdhReviewJobStatusResponse) snapshot.invoke(state)).progress();

    markActive.invoke(state, "ID990A.pdf", "正在渲染并识别材料类型");
    int laterProgress = ((FdhReviewJobStatusResponse) snapshot.invoke(state)).progress();

    assertThat(laterProgress).isGreaterThanOrEqualTo(advancedProgress);
  }

  @Test
  void iangRecentGraduateJobAssemblesStudentMaterialsAndLimitsId990aByOfficialPageNumbers() throws Exception {
    BaiduOcrPageRenderer renderer = mock(BaiduOcrPageRenderer.class);
    TemplateDetectionService templateDetectionService = mock(TemplateDetectionService.class);
    OcrDemoService ocrDemoService = mock(OcrDemoService.class);
    FdhOfficialPageNumberDetector officialPageNumberDetector = mock(FdhOfficialPageNumberDetector.class);
    FdhReviewJobService service = new FdhReviewJobService(
        renderer,
        templateDetectionService,
        ocrDemoService,
        new FdhReviewAssembler(5100, 1236, Clock.fixed(Instant.parse("2026-06-22T08:30:00Z"), ZoneOffset.UTC)),
        officialPageNumberDetector,
        2
    );

    when(renderer.render(anyString(), anyString(), any())).thenAnswer(invocation -> {
      String filename = invocation.getArgument(0);
      return renderedPages(filename.contains("ID990A") ? 6 : 1);
    });
    when(templateDetectionService.detect(anyString(), anyString(), any(), anyList())).thenAnswer(invocation -> {
      String filename = invocation.getArgument(0);
      if (filename.contains("ID990A")) {
        return template("id990a_2025_01", "ID 990A (01/2025)", 6);
      }
      return DocumentTemplate.unknown(1, "test");
    });
    when(officialPageNumberDetector.detect(anyString(), any(DocumentTemplate.class), anyList(), any()))
        .thenAnswer(invocation -> {
          String filename = invocation.getArgument(0);
          return filename.contains("ID990A") ? List.of(1, 2, 3, 4, 5, 5) : List.of();
        });
    when(ocrDemoService.recognizeRenderedForFdhReview(
        anyString(),
        anyList(),
        any(ExtractionProgressListener.class),
        any(),
        any(DocumentTemplate.class)
    )).thenAnswer(invocation -> iangResponse(
        invocation.getArgument(0),
        invocation.getArgument(1),
        invocation.getArgument(4)
    ));

    FdhReviewJobStatusResponse started = service.start(
        "iang_recent_in_hk",
        List.of(
            file("ID990A_iang_recent_graduate.pdf"),
            file("毕业证明.pdf"),
            file("港澳通行证_HKID.pdf"),
            file("付款证明.png")
        ),
        null
    );
    FdhReviewJobStatusResponse completed = waitForCompletion(service, started.jobId());

    assertThat(completed.status()).isEqualTo("completed");
    assertThat(completed.result()).isNotNull();
    assertThat(completed.result().applicationTypeId()).isEqualTo("iang_recent_in_hk");
    assertThat(completed.result().decision()).isEqualTo("REVIEW");
    assertThat(completed.result().decisionText()).contains("付款");
    assertThat(material(completed.result(), "id990a").statusText()).contains("前 5 页");
    assertThat(material(completed.result(), "paymentStatus").status()).isEqualTo("review");
    assertThat(completed.result().fields()).extracting(FdhReviewResult.StandardField::key)
        .contains(
            "application.scheme",
            "applicant.name.full_en",
            "education.graduation_date",
            "payment.application_fee_status"
        );
    assertThat(completed.result().documentFieldGroups())
        .extracting(FdhReviewResult.DocumentFieldGroup::materialId)
        .contains("id990a", "educationProof", "identityDocs", "paymentStatus");
    assertThat(completed.result().documentFieldGroups().stream()
        .filter(group -> "id990a".equals(group.materialId()))
        .findFirst()
        .orElseThrow()
        .pages()).hasSize(5);
    verify(ocrDemoService).recognizeRenderedForFdhReview(
        org.mockito.ArgumentMatchers.eq("ID990A_iang_recent_graduate.pdf"),
        org.mockito.ArgumentMatchers.argThat(pages -> pages.size() == 5),
        any(ExtractionProgressListener.class),
        any(),
        any(DocumentTemplate.class)
    );
  }

  @Test
  void iangRecentGraduateJobFailsId990aWhenOfficialPageNumberIsMissing() throws Exception {
    BaiduOcrPageRenderer renderer = mock(BaiduOcrPageRenderer.class);
    TemplateDetectionService templateDetectionService = mock(TemplateDetectionService.class);
    OcrDemoService ocrDemoService = mock(OcrDemoService.class);
    FdhOfficialPageNumberDetector officialPageNumberDetector = mock(FdhOfficialPageNumberDetector.class);
    FdhReviewJobService service = new FdhReviewJobService(
        renderer,
        templateDetectionService,
        ocrDemoService,
        new FdhReviewAssembler(5100, 1236, Clock.fixed(Instant.parse("2026-06-22T08:30:00Z"), ZoneOffset.UTC)),
        officialPageNumberDetector,
        1
    );

    when(renderer.render(anyString(), anyString(), any())).thenReturn(renderedPages(4));
    when(templateDetectionService.detect(anyString(), anyString(), any(), anyList()))
        .thenReturn(template("id990a_2025_01", "ID 990A (01/2025)", 4));
    when(officialPageNumberDetector.detect(anyString(), any(DocumentTemplate.class), anyList(), any()))
        .thenReturn(List.of(1, 2, 4, 5));
    when(ocrDemoService.recognizeRenderedForFdhReview(
        anyString(),
        anyList(),
        any(ExtractionProgressListener.class),
        any(),
        any(DocumentTemplate.class)
    )).thenAnswer(invocation -> iangResponse(
        invocation.getArgument(0),
        invocation.getArgument(1),
        invocation.getArgument(4)
    ));

    FdhReviewJobStatusResponse started = service.start(
        "iang_recent_in_hk",
        List.of(file("ID990A_missing_page_3.pdf")),
        null
    );
    FdhReviewJobStatusResponse completed = waitForCompletion(service, started.jobId());

    FdhReviewResult.MaterialRow id990a = material(completed.result(), "id990a");
    assertThat(id990a.status()).isEqualTo("fail");
    assertThat(id990a.issue()).contains("第 3 页");
    assertThat(completed.result().decision()).isEqualTo("FAIL");
  }

  @Test
  void iangRecentGraduateDocumentFieldsUseConciseEducationAndPaymentFieldLists() throws Exception {
    BaiduOcrPageRenderer renderer = mock(BaiduOcrPageRenderer.class);
    TemplateDetectionService templateDetectionService = mock(TemplateDetectionService.class);
    OcrDemoService ocrDemoService = mock(OcrDemoService.class);
    FdhOfficialPageNumberDetector officialPageNumberDetector = mock(FdhOfficialPageNumberDetector.class);
    FdhReviewJobService service = new FdhReviewJobService(
        renderer,
        templateDetectionService,
        ocrDemoService,
        new FdhReviewAssembler(5100, 1236, Clock.fixed(Instant.parse("2026-06-22T08:30:00Z"), ZoneOffset.UTC)),
        officialPageNumberDetector,
        2
    );

    when(renderer.render(anyString(), anyString(), any())).thenReturn(renderedPages(1));
    when(templateDetectionService.detect(anyString(), anyString(), any(), anyList()))
        .thenReturn(DocumentTemplate.unknown(1, "test"));
    when(officialPageNumberDetector.detect(anyString(), any(DocumentTemplate.class), anyList(), any()))
        .thenReturn(List.of());
    when(ocrDemoService.recognizeRenderedForFdhReview(
        anyString(),
        anyList(),
        any(ExtractionProgressListener.class),
        any(),
        any(DocumentTemplate.class)
    )).thenAnswer(invocation -> iangResponse(
        invocation.getArgument(0),
        invocation.getArgument(1),
        invocation.getArgument(4)
    ));

    FdhReviewJobStatusResponse started = service.start(
        "iang_recent_in_hk",
        List.of(file("毕业证明.pdf"), file("付款证明.png")),
        null
    );
    FdhReviewJobStatusResponse completed = waitForCompletion(service, started.jobId());

    FdhReviewResult.DocumentFieldPage educationPage = documentFieldPage(completed.result(), "educationProof");
    assertThat(educationPage.fields()).extracting(FdhReviewResult.DocumentField::label)
        .containsExactly("Ref", "收件人", "姓名", "身份证号", "大学", "学科及学位", "日期");

    FdhReviewResult.DocumentFieldPage paymentPage = documentFieldPage(completed.result(), "paymentStatus");
    assertThat(paymentPage.fields()).extracting(FdhReviewResult.DocumentField::label)
        .containsExactly("申请人", "申请编号", "申请人数", "每份申请需缴纳的申请费", "申请费总金额");
  }

  @Test
  void iangRecentGraduateEducationFieldsDoNotUseCertificationParagraphAsUniversityOrProgramme() throws Exception {
    BaiduOcrPageRenderer renderer = mock(BaiduOcrPageRenderer.class);
    TemplateDetectionService templateDetectionService = mock(TemplateDetectionService.class);
    OcrDemoService ocrDemoService = mock(OcrDemoService.class);
    FdhOfficialPageNumberDetector officialPageNumberDetector = mock(FdhOfficialPageNumberDetector.class);
    FdhReviewJobService service = new FdhReviewJobService(
        renderer,
        templateDetectionService,
        ocrDemoService,
        new FdhReviewAssembler(5100, 1236, Clock.fixed(Instant.parse("2026-06-22T08:30:00Z"), ZoneOffset.UTC)),
        officialPageNumberDetector,
        2
    );

    when(renderer.render(anyString(), anyString(), any())).thenReturn(renderedPages(1));
    when(templateDetectionService.detect(anyString(), anyString(), any(), anyList()))
        .thenReturn(DocumentTemplate.unknown(1, "test"));
    when(officialPageNumberDetector.detect(anyString(), any(DocumentTemplate.class), anyList(), any()))
        .thenReturn(List.of());
    when(ocrDemoService.recognizeRenderedForFdhReview(
        anyString(),
        anyList(),
        any(ExtractionProgressListener.class),
        any(),
        any(DocumentTemplate.class)
    )).thenAnswer(invocation -> iangResponse(
        invocation.getArgument(0),
        invocation.getArgument(1),
        invocation.getArgument(4)
    ));

    FdhReviewJobStatusResponse started = service.start(
        "iang_recent_in_hk",
        List.of(file("毕业证明_仅正文字段.pdf")),
        null
    );
    FdhReviewJobStatusResponse completed = waitForCompletion(service, started.jobId());

    FdhReviewResult.DocumentFieldPage educationPage = documentFieldPage(completed.result(), "educationProof");
    assertThat(documentFieldValue(educationPage, "大学")).isBlank();
    assertThat(documentFieldValue(educationPage, "学科及学位"))
        .isEqualTo("Master of Science in Computer Science (Full-time)");
  }

  @Test
  void iangRecentGraduateEducationStandardFieldsAcceptCertificateSpecificKeys() throws Exception {
    BaiduOcrPageRenderer renderer = mock(BaiduOcrPageRenderer.class);
    TemplateDetectionService templateDetectionService = mock(TemplateDetectionService.class);
    OcrDemoService ocrDemoService = mock(OcrDemoService.class);
    FdhOfficialPageNumberDetector officialPageNumberDetector = mock(FdhOfficialPageNumberDetector.class);
    FdhReviewJobService service = new FdhReviewJobService(
        renderer,
        templateDetectionService,
        ocrDemoService,
        new FdhReviewAssembler(5100, 1236, Clock.fixed(Instant.parse("2026-06-22T08:30:00Z"), ZoneOffset.UTC)),
        officialPageNumberDetector,
        2
    );

    when(renderer.render(anyString(), anyString(), any())).thenReturn(renderedPages(1));
    when(templateDetectionService.detect(anyString(), anyString(), any(), anyList()))
        .thenReturn(DocumentTemplate.unknown(1, "test"));
    when(officialPageNumberDetector.detect(anyString(), any(DocumentTemplate.class), anyList(), any()))
        .thenReturn(List.of());
    when(ocrDemoService.recognizeRenderedForFdhReview(
        anyString(),
        anyList(),
        any(ExtractionProgressListener.class),
        any(),
        any(DocumentTemplate.class)
    )).thenAnswer(invocation -> iangResponse(
        invocation.getArgument(0),
        invocation.getArgument(1),
        invocation.getArgument(4)
    ));

    FdhReviewJobStatusResponse started = service.start(
        "iang_recent_in_hk",
        List.of(file("毕业证明_专用字段.pdf")),
        null
    );
    FdhReviewJobStatusResponse completed = waitForCompletion(service, started.jobId());

    assertThat(standardField(completed.result(), "education.institution").normalizedValue())
        .isEqualTo("The Chinese University of Hong Kong");
    assertThat(standardField(completed.result(), "education.programme").normalizedValue())
        .isEqualTo("Master of Science in Computer Science (Full-time)");
    assertThat(standardField(completed.result(), "education.graduation_date").normalizedValue())
        .isEqualTo("16 June 2025");
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
  void reportsOfficialPageNumberRecognitionProgressBeforeExtraction() throws Exception {
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
    CountDownLatch pageNumberRecognitionStarted = new CountDownLatch(1);
    CountDownLatch allowPageNumberRecognitionToFinish = new CountDownLatch(1);

    when(renderer.render(anyString(), anyString(), any())).thenReturn(renderedPages(5));
    when(templateDetectionService.detect(anyString(), anyString(), any(), anyList()))
        .thenReturn(template("id988a_2024_06", "ID 988A (06/2024)", 5));
    when(officialPageNumberDetector.detect(anyString(), any(DocumentTemplate.class), anyList(), any()))
        .thenAnswer(invocation -> {
          pageNumberRecognitionStarted.countDown();
          assertThat(allowPageNumberRecognitionToFinish.await(2, TimeUnit.SECONDS)).isTrue();
          return List.of(1, 2, 3, 4, 5);
        });
    when(ocrDemoService.recognizeRenderedForFdhReview(
        anyString(),
        anyList(),
        any(ExtractionProgressListener.class),
        any(),
        any(DocumentTemplate.class)
    )).thenAnswer(invocation -> response(invocation.getArgument(0), invocation.getArgument(4)));

    FdhReviewJobStatusResponse started = service.start("entry_visa", List.of(file("ID988A.pdf")), null);
    assertThat(pageNumberRecognitionStarted.await(2, TimeUnit.SECONDS)).isTrue();

    FdhReviewJobStatusResponse running = service.status(started.jobId());
    allowPageNumberRecognitionToFinish.countDown();

    assertThat(running.status()).isEqualTo("running");
    assertThat(running.message()).contains("官方页码");
    assertThat(waitForCompletion(service, started.jobId()).status()).isEqualTo("completed");
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

  private OcrDemoResponse iangResponse(
      String filename,
      List<RenderedOcrPage> pages,
      DocumentTemplate template
  ) throws Exception {
    int pageCount = pages == null ? 0 : pages.size();
    String json;
    if (filename.contains("ID990A")) {
      json = """
          {
            "page_1": {
              "application_category": "IANG - recent graduate",
              "application_date": "22 June 2026"
            },
            "page_2": {
              "name_in_english": "ZHAO HANGYU",
              "hk_identity_card_no": "F539325(2)",
              "travel_document_no": "CA3273201",
              "date_of_birth": "03/08/1981",
              "sex": "Female"
            },
            "page_4": {
              "institution": "The Chinese University of Hong Kong",
              "programme": "Master of Science in Computer Science",
              "graduation_date": "16 June 2026"
            },
            "page_5": {
              "signature_of_applicant": "signature detected",
              "declaration_date": "22 June 2026"
            }
          }
          """;
    } else if (filename.contains("专用字段")) {
      json = """
          {
            "page_1": {
              "ref": "GS/19/1",
              "recipient": "IMMIGRATION DEPARTMENT",
              "student_name": "Mr. ZHAO, Hangyu（赵航宇）",
              "university": "The Chinese University of Hong Kong",
              "programme_degree": "Master of Science in Computer Science (Full-time)",
              "date": "16 June 2025"
            }
          }
          """;
    } else if (filename.contains("仅正文字段")) {
      json = """
          {
            "page_1": {
              "student_name": "Mr. ZHAO, Hangyu（赵航宇）",
              "hk_identity_card_no": "masked",
              "certification_text": "This is to certify that the above-named has satisfactorily completed all the programme requirements for the Master of Science in Computer Science (Full-time) of this University.",
              "date": "16 June 2025"
            }
          }
          """;
    } else if (filename.contains("毕业")) {
      json = """
          {
            "page_1": {
              "source_file": "毕业证明.pdf",
              "total_pages": "1",
              "ref": "GS/19/1",
              "to": "IMMIGRATION DEPARTMENT",
              "student_name": "ZHAO HANGYU",
              "hk_identity_card_no": "F539325(2)",
              "institution": "The Chinese University of Hong Kong",
              "programme": "Master of Science in Computer Science",
              "certification_text": "This is to certify that the above-named has completed the programme requirements.",
              "verification_contact": "Ms. Florence Lai",
              "signatory_name": "Angel Wong",
              "signatory_title": "Assistant Registrar",
              "completion_date": "16 June 2026"
            }
          }
          """;
    } else if (filename.contains("港澳") || filename.toLowerCase().contains("hkid")) {
      json = """
          {
            "page_1": {
              "permit_no": "CA3273201",
              "name_in_english": "ZHAO HANGYU",
              "date_of_birth": "1981.08.03"
            },
            "page_2": {
              "hk_identity_card_no": "F539325(2)"
            }
          }
          """;
    } else {
      json = """
          {
            "page_1": {
              "applicant_name": "ZHAO HANGYU",
              "temporary_application_reference_number": "1340351-25",
              "total_no_of_applicants": "1",
              "no_of_applicants_required_to_pay_the_application_fee": "1",
              "names_of_applicant_required_to_pay_the_application_fee": "ZHAO HANGYU",
              "application_fee_to_be_paid_for_each_application": "HK$ 600.00",
              "total_amount_of_application_fee": "HK$ 600.00",
              "payment_status": "The online application process is NOT YET COMPLETE",
              "payment_reference": "IA-2026-0622-0001"
            }
          }
          """;
    }
    return new OcrDemoResponse(
        filename,
        "test",
        pageCount,
        pages.stream()
            .map(page -> new OcrPage(
                page.page(),
                "data:image/png;base64,AAA=",
                100,
                100,
                "",
                List.of(),
                List.of(),
                List.of(),
                List.of()
            ))
            .toList(),
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

  private FdhReviewResult.StandardField standardField(FdhReviewResult result, String key) {
    return result.fields().stream()
        .filter(field -> key.equals(field.key()))
        .findFirst()
        .orElseThrow();
  }

  private FdhReviewResult.DocumentFieldPage documentFieldPage(FdhReviewResult result, String materialId) {
    return result.documentFieldGroups().stream()
        .filter(group -> materialId.equals(group.materialId()))
        .findFirst()
        .orElseThrow()
        .pages()
        .get(0);
  }

  private String documentFieldValue(FdhReviewResult.DocumentFieldPage page, String label) {
    return page.fields().stream()
        .filter(field -> label.equals(field.label()))
        .map(FdhReviewResult.DocumentField::value)
        .findFirst()
        .orElseThrow();
  }
}
