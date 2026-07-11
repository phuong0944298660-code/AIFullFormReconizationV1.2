package com.aiform.id995a.fdh;

import com.aiform.id995a.llm.ExtractionProgressListener;
import com.aiform.id995a.ocr.BaiduOcrPageRenderer;
import com.aiform.id995a.ocr.DocumentTemplate;
import com.aiform.id995a.ocr.OcrDemoResponse;
import com.aiform.id995a.ocr.OcrDemoService;
import com.aiform.id995a.ocr.RenderedOcrPage;
import com.aiform.id995a.ocr.TemplateDetectionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FdhReviewJobService {

  private static final Logger log = LoggerFactory.getLogger(FdhReviewJobService.class);
  private static final int DEFAULT_FILE_CONCURRENCY = 2;

  private final BaiduOcrPageRenderer pageRenderer;
  private final TemplateDetectionService templateDetectionService;
  private final OcrDemoService ocrDemoService;
  private final FdhReviewAssembler reviewAssembler;
  private final StudentIangReviewAssembler studentIangReviewAssembler;
  private final FdhOfficialPageNumberDetector officialPageNumberDetector;
  private final int fileConcurrency;
  private final ConcurrentMap<String, JobState> jobs = new ConcurrentHashMap<>();
  private final ExecutorService executor = Executors.newCachedThreadPool(applicationThreadFactory("fdh-review-job"));

  @Autowired
  public FdhReviewJobService(
      BaiduOcrPageRenderer pageRenderer,
      TemplateDetectionService templateDetectionService,
      OcrDemoService ocrDemoService,
      FdhReviewAssembler reviewAssembler,
      StudentIangReviewAssembler studentIangReviewAssembler,
      FdhOfficialPageNumberDetector officialPageNumberDetector,
      @Value("${fdh.review.file-concurrency:2}") int fileConcurrency
  ) {
    this.pageRenderer = pageRenderer;
    this.templateDetectionService = templateDetectionService;
    this.ocrDemoService = ocrDemoService;
    this.reviewAssembler = reviewAssembler;
    this.studentIangReviewAssembler = studentIangReviewAssembler;
    this.officialPageNumberDetector = officialPageNumberDetector;
    this.fileConcurrency = Math.max(1, Math.min(3, fileConcurrency));
  }

  FdhReviewJobService(
      BaiduOcrPageRenderer pageRenderer,
      TemplateDetectionService templateDetectionService,
      OcrDemoService ocrDemoService,
      FdhReviewAssembler reviewAssembler
  ) {
    this(
        pageRenderer,
        templateDetectionService,
        ocrDemoService,
        reviewAssembler,
        new StudentIangReviewAssembler(),
        new FdhOfficialPageNumberDetector(),
        DEFAULT_FILE_CONCURRENCY
    );
  }

  FdhReviewJobService(
      BaiduOcrPageRenderer pageRenderer,
      TemplateDetectionService templateDetectionService,
      OcrDemoService ocrDemoService,
      FdhReviewAssembler reviewAssembler,
      FdhOfficialPageNumberDetector officialPageNumberDetector,
      int fileConcurrency
  ) {
    this(
        pageRenderer,
        templateDetectionService,
        ocrDemoService,
        reviewAssembler,
        new StudentIangReviewAssembler(),
        officialPageNumberDetector,
        fileConcurrency
    );
  }

  public FdhReviewJobStatusResponse start(
      String applicationTypeId,
      List<MultipartFile> files,
      String modelId
  ) throws IOException {
    if (files == null || files.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one file is required.");
    }
    List<ReviewUpload> uploads = new ArrayList<>();
    for (MultipartFile file : files) {
      if (file == null || file.isEmpty()) {
        continue;
      }
      uploads.add(new ReviewUpload(
          filename(file.getOriginalFilename()),
          file.getContentType(),
          file.getBytes()
      ));
    }
    if (uploads.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one non-empty file is required.");
    }

    String jobId = UUID.randomUUID().toString();
    JobState state = new JobState(jobId, applicationTypeId, uploads.size());
    jobs.put(jobId, state);
    Future<?> future = executor.submit(() -> runJobParallel(state, uploads, modelId));
    state.attachFuture(future);
    return state.snapshot();
  }

  public FdhReviewJobStatusResponse status(String jobId) {
    JobState state = jobs.get(jobId);
    if (state == null) {
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND,
          "Recognition job is no longer available. The backend may have restarted; upload the documents again."
      );
    }
    state.markFailedIfWorkerEnded();
    return state.snapshot();
  }

  public FdhReviewJobStatusResponse cancel(String jobId) {
    JobState state = jobs.get(jobId);
    if (state == null) {
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND,
          "Recognition job is no longer available. The backend may have restarted; upload the documents again."
      );
    }
    Future<?> future = state.markCanceled();
    if (future != null) {
      future.cancel(true);
    }
    return state.snapshot();
  }

  private void runJobParallel(JobState state, List<ReviewUpload> uploads, String modelId) {
    List<IndexedReviewDocument> documents = new ArrayList<>();
    long jobStartedAt = System.nanoTime();
    int concurrency = Math.min(fileConcurrency, uploads.size());
    ExecutorService fileExecutor = Executors.newFixedThreadPool(
        concurrency,
        applicationThreadFactory("fdh-review-file")
    );
    try {
      log.info(
          "FDH review job {} started: applicationType={}, files={}, fileConcurrency={}",
          state.jobId,
          state.applicationTypeId(),
          uploads.size(),
          concurrency
      );
      List<Future<IndexedReviewDocument>> futures = new ArrayList<>();
      for (int index = 0; index < uploads.size(); index += 1) {
        if (state.isCanceled()) {
          return;
        }
        int fileIndex = index;
        ReviewUpload upload = uploads.get(index);
        futures.add(fileExecutor.submit(() -> processUpload(state, upload, fileIndex, uploads.size(), modelId)));
      }
      for (Future<IndexedReviewDocument> future : futures) {
        if (state.isCanceled()) {
          return;
        }
        IndexedReviewDocument document = future.get();
        if (document != null) {
          documents.add(document);
        }
      }
      if (state.isCanceled()) {
        return;
      }
      state.markReviewing();
      List<FdhReviewDocument> orderedDocuments = documents.stream()
          .sorted(Comparator.comparingInt(IndexedReviewDocument::index))
          .map(IndexedReviewDocument::document)
          .toList();
      FdhReviewResult result = assembleResult(state.applicationTypeId(), orderedDocuments);
      state.markCompleted(result);
      log.info(
          "FDH review job {} completed with decision={} in {} ms",
          state.jobId,
          result.decision(),
          elapsedMillisSince(jobStartedAt)
      );
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      state.markCanceled();
    } catch (ExecutionException exception) {
      Throwable cause = exception.getCause() == null ? exception : exception.getCause();
      if (state.isCanceled()) {
        state.markCanceled();
      } else {
        log.warn("FDH review job {} failed: {}", state.jobId, cause.getMessage(), cause);
        state.markFailed(userFacingFailureMessage(cause));
      }
    } catch (RuntimeException exception) {
      if (state.isCanceled()) {
        state.markCanceled();
      } else {
        log.warn("FDH review job {} failed: {}", state.jobId, exception.getMessage(), exception);
        state.markFailed(userFacingFailureMessage(exception));
      }
    } finally {
      fileExecutor.shutdownNow();
    }
  }

  private IndexedReviewDocument processUpload(
      JobState state,
      ReviewUpload upload,
      int index,
      int totalFiles,
      String modelId
  ) throws IOException {
    if (state.isCanceled()) {
      return null;
    }
    long fileStartedAt = System.nanoTime();
    log.info(
        "FDH review job {} processing file {}/{}: {}",
        state.jobId,
        index + 1,
        totalFiles,
        upload.filename()
    );
    state.markActive(upload.filename(), "正在渲染并识别材料类型");
    List<RenderedOcrPage> pages = pageRenderer.render(upload.filename(), upload.contentType(), upload.bytes());
    DocumentTemplate template = templateDetectionService.detect(upload.filename(), upload.contentType(), upload.bytes(), pages);
    String materialId = classifyMaterial(state.applicationTypeId(), template, upload.filename());
    state.markActive(upload.filename(), "正在准备字段提取范围");
    log.info(
        "FDH review job {} rendered and classified {} as {} (template={}, pages={}, source={}) in {} ms",
        state.jobId,
        upload.filename(),
        materialId,
        template.templateId(),
        pages.size(),
        template.matchSource(),
        elapsedMillisSince(fileStartedAt)
    );
    if (state.isCanceled()) {
      return null;
    }
    long extractionStartedAt = System.nanoTime();
    List<RenderedOcrPage> extractionPages = extractionPages(
        state.applicationTypeId(),
        materialId,
        template,
        pages
    );
    state.markActive(upload.filename(), "正在按已识别模板执行字段提取");
    OcrDemoResponse response;
    if (StudentIangMaterialCatalog.supports(state.applicationTypeId())) {
      response = ocrDemoService.recognizeRenderedForFdhReview(
          upload.filename(),
          extractionPages,
          state.progressListener(upload.filename(), extractionPages.size()),
          modelId,
          template,
          false
      );
    } else {
      response = ocrDemoService.recognizeRenderedForFdhReview(
          upload.filename(),
          extractionPages,
          state.progressListener(upload.filename(), extractionPages.size()),
          modelId,
          template
      );
    }
    log.info(
        "FDH review job {} completed extraction for {} in {} ms",
        state.jobId,
        upload.filename(),
        elapsedMillisSince(extractionStartedAt)
    );
    List<Integer> officialPageNumbers = officialPageNumbersFromStructuredData(
        state,
        upload,
        template,
        pages,
        extractionPages,
        response,
        modelId
    );
    state.markProcessedFile(upload.filename());
    return new IndexedReviewDocument(
        index,
        new FdhReviewDocument(
            upload.filename(),
            upload.contentType(),
            pages.size(),
            template,
            response,
            materialId,
            officialPageNumbers
        )
    );
  }

  private FdhReviewResult assembleResult(String applicationTypeId, List<FdhReviewDocument> documents) {
    if (StudentIangMaterialCatalog.supports(applicationTypeId)) {
      return studentIangReviewAssembler.assemble(applicationTypeId, documents);
    }
    return reviewAssembler.assemble(applicationTypeId, documents);
  }

  private String classifyMaterial(String applicationTypeId, DocumentTemplate template, String filename) {
    if (StudentIangMaterialCatalog.supports(applicationTypeId)) {
      return StudentIangMaterialCatalog.classify(template, filename);
    }
    return FdhMaterialCatalog.classify(template, filename);
  }

  private List<RenderedOcrPage> extractionPages(
      String applicationTypeId,
      String materialId,
      DocumentTemplate template,
      List<RenderedOcrPage> pages
  ) {
    List<RenderedOcrPage> safePages = pages == null ? List.of() : pages;
    if (StudentIangMaterialCatalog.supports(applicationTypeId) && "id990a".equals(materialId)) {
      return safePages.stream().limit(5).toList();
    }
    if (template == null) {
      return safePages;
    }
    String templateId = template.templateId();
    int pageLimit = extractionPageLimit(templateId);
    return pageLimit <= 0 ? safePages : safePages.stream().limit(pageLimit).toList();
  }

  private int extractionPageLimit(String templateId) {
    return switch (templateId) {
      case "id988a_2024_06" -> 4;
      case "id988b_2024_06" -> 3;
      default -> 0;
    };
  }

  private List<Integer> officialPageNumbersFromStructuredData(
      JobState state,
      ReviewUpload upload,
      DocumentTemplate template,
      List<RenderedOcrPage> allPages,
      List<RenderedOcrPage> extractionPages,
      OcrDemoResponse response,
      String modelId
  ) throws IOException {
    List<Integer> recognized = structuredOfficialPageNumbers(template, allPages, extractionPages, response);
    if (!recognized.isEmpty()) {
      return recognized;
    }
    return detectOfficialPageNumbers(state, upload, template, allPages, modelId);
  }

  private List<Integer> structuredOfficialPageNumbers(
      DocumentTemplate template,
      List<RenderedOcrPage> allPages,
      List<RenderedOcrPage> extractionPages,
      OcrDemoResponse response
  ) {
    if (response == null || response.structuredData() == null || extractionPages == null || extractionPages.isEmpty()) {
      return List.of();
    }
    int maxOfficialPage = expectedOfficialPageCount(template);
    if (maxOfficialPage <= 0) {
      return List.of();
    }
    String expectedForm = expectedFormId(template);
    String expectedVersion = expectedVersion(template);
    List<Integer> pageNumbers = new ArrayList<>();
    for (RenderedOcrPage page : extractionPages) {
      JsonNode metadata = officialPageMetadata(response.structuredData(), page.page());
      int officialPage = firstExistingInt(
          metadata,
          0,
          "official_page_no",
          "officialPageNo",
          "page_no",
          "pageNo",
          "official_page",
          "officialPage",
          "page_number"
      );
      double confidence = firstExistingDouble(metadata, 0, "confidence", "score");
      String formId = firstExistingText(metadata, "form_id", "formId", "document_id", "documentId");
      String version = firstExistingText(metadata, "version", "revision", "form_version", "formVersion");
      if (officialPage < 1 || officialPage > maxOfficialPage) {
        return List.of();
      }
      if (confidence > 0 && confidence < 60) {
        return List.of();
      }
      if (!expectedForm.isBlank() && !matchesNormalized(formId, expectedForm)) {
        return List.of();
      }
      if (!expectedVersion.isBlank() && !version.isBlank() && !normalize(version).equals(normalize(expectedVersion))) {
        return List.of();
      }
      pageNumbers.add(officialPage);
    }
    return withSkippedNonFillablePage(template, allPages, pageNumbers);
  }

  private JsonNode officialPageMetadata(JsonNode structuredData, int page) {
    String pageKey = "page_" + page;
    for (String rootKey : List.of("_official_page", "_official_pages", "official_page", "official_pages")) {
      JsonNode root = structuredData.path(rootKey);
      if (root.isObject()) {
        JsonNode value = root.path(pageKey);
        if (!value.isMissingNode() && !value.isNull()) {
          return value;
        }
        value = root.path(String.valueOf(page));
        if (!value.isMissingNode() && !value.isNull()) {
          return value;
        }
      }
    }
    return MissingNode.getInstance();
  }

  private List<Integer> withSkippedNonFillablePage(
      DocumentTemplate template,
      List<RenderedOcrPage> allPages,
      List<Integer> pageNumbers
  ) {
    int skippedPage = skippedNonFillableOfficialPage(template);
    if (skippedPage <= 0 || allPages == null || allPages.size() < skippedPage || pageNumbers.contains(skippedPage)) {
      return List.copyOf(pageNumbers);
    }
    List<Integer> copy = new ArrayList<>(pageNumbers);
    copy.add(skippedPage);
    return List.copyOf(copy);
  }

  private int skippedNonFillableOfficialPage(DocumentTemplate template) {
    if (template == null) {
      return 0;
    }
    return switch (template.templateId()) {
      case "id988a_2024_06" -> 5;
      case "id988b_2024_06" -> 4;
      default -> 0;
    };
  }

  private int expectedOfficialPageCount(DocumentTemplate template) {
    if (template == null) {
      return 0;
    }
    String templateId = template.templateId() == null ? "" : template.templateId().toLowerCase(Locale.ROOT);
    if (templateId.startsWith("id990a_")) {
      return 5;
    }
    return switch (template.templateId()) {
      case "id988a_2024_06" -> 5;
      case "id988b_2024_06", "id407_2016_11" -> 4;
      default -> 0;
    };
  }

  private String expectedFormId(DocumentTemplate template) {
    if (template == null) {
      return "";
    }
    String templateId = template.templateId() == null ? "" : template.templateId().toLowerCase(Locale.ROOT);
    if (templateId.startsWith("id990a_")) {
      return "ID 990A";
    }
    return switch (template.templateId()) {
      case "id988a_2024_06" -> "ID 988A";
      case "id988b_2024_06" -> "ID 988B";
      case "id407_2016_11" -> "ID 407";
      default -> "";
    };
  }

  private String expectedVersion(DocumentTemplate template) {
    if (template == null) {
      return "";
    }
    return switch (template.templateId()) {
      case "id988a_2024_06", "id988b_2024_06" -> "06/2024";
      case "id407_2016_11" -> "11/2016";
      default -> "";
    };
  }

  private boolean matchesNormalized(String actual, String expected) {
    String normalizedActual = normalize(actual).replace("ID", "");
    String normalizedExpected = normalize(expected).replace("ID", "");
    return !normalizedActual.isBlank() && normalizedActual.equals(normalizedExpected);
  }

  private String normalize(String value) {
    return value == null
        ? ""
        : value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9/]", "");
  }

  private String firstExistingText(JsonNode node, String... keys) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return "";
    }
    for (String key : keys) {
      JsonNode value = node.path(key);
      if (!value.isMissingNode() && !value.isNull()) {
        return value.asText("");
      }
    }
    return "";
  }

  private int firstExistingInt(JsonNode node, int fallback, String... keys) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return fallback;
    }
    for (String key : keys) {
      JsonNode value = node.path(key);
      if (value.isInt() || value.isLong()) {
        return value.asInt(fallback);
      }
      if (value.isTextual()) {
        String text = value.asText("").trim();
        if (text.matches("[0-9]+")) {
          return Integer.parseInt(text);
        }
      }
    }
    return fallback;
  }

  private double firstExistingDouble(JsonNode node, double fallback, String... keys) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return fallback;
    }
    for (String key : keys) {
      JsonNode value = node.path(key);
      if (value.isNumber()) {
        double number = value.asDouble(fallback);
        return number <= 1 ? number * 100 : number;
      }
      if (value.isTextual()) {
        try {
          double number = Double.parseDouble(value.asText("").trim());
          return number <= 1 ? number * 100 : number;
        } catch (NumberFormatException ignored) {
          // Try the next candidate key.
        }
      }
    }
    return fallback;
  }

  private void runJob(JobState state, List<ReviewUpload> uploads, String modelId) {
    List<FdhReviewDocument> documents = new ArrayList<>();
    long jobStartedAt = System.nanoTime();
    try {
      log.info(
          "FDH review job {} started: applicationType={}, files={}",
          state.jobId,
          state.applicationTypeId(),
          uploads.size()
      );
      for (int index = 0; index < uploads.size(); index += 1) {
        if (state.isCanceled()) {
          return;
        }
        ReviewUpload upload = uploads.get(index);
        long fileStartedAt = System.nanoTime();
        log.info(
            "FDH review job {} processing file {}/{}: {}",
            state.jobId,
            index + 1,
            uploads.size(),
            upload.filename()
        );
        state.markActive(upload.filename(), "正在渲染并识别材料类型");
        List<RenderedOcrPage> pages = pageRenderer.render(upload.filename(), upload.contentType(), upload.bytes());
        DocumentTemplate template = templateDetectionService.detect(upload.filename(), upload.contentType(), upload.bytes(), pages);
        String materialId = FdhMaterialCatalog.classify(template, upload.filename());
        log.info(
            "FDH review job {} rendered and classified {} as {} (template={}, pages={}) in {} ms",
            state.jobId,
            upload.filename(),
            materialId,
            template.templateId(),
            pages.size(),
            elapsedMillisSince(fileStartedAt)
        );
        if (state.isCanceled()) {
          return;
        }
        long llmStartedAt = System.nanoTime();
        state.markActive(upload.filename(), "正在调用本地 LLM 做结构化提取");
        OcrDemoResponse response = ocrDemoService.recognizeRenderedForFdhReview(
            upload.filename(),
            pages,
            state.progressListener(upload.filename(), pages.size()),
            modelId,
            template
        );
        log.info(
            "FDH review job {} completed LLM extraction for {} in {} ms",
            state.jobId,
            upload.filename(),
            elapsedMillisSince(llmStartedAt)
        );
        documents.add(new FdhReviewDocument(
            upload.filename(),
            upload.contentType(),
            response.pageCount(),
            template,
            response,
            materialId
        ));
        state.markProcessed(index + 1, upload.filename());
      }
      if (state.isCanceled()) {
        return;
      }
      state.markReviewing();
      FdhReviewResult result = reviewAssembler.assemble(state.applicationTypeId(), documents);
      state.markCompleted(result);
      log.info(
          "FDH review job {} completed with decision={} in {} ms",
          state.jobId,
          result.decision(),
          elapsedMillisSince(jobStartedAt)
      );
    } catch (IOException | RuntimeException exception) {
      if (state.isCanceled()) {
        state.markCanceled();
      } else {
        log.warn("FDH review job {} failed: {}", state.jobId, exception.getMessage(), exception);
        state.markFailed(userFacingFailureMessage(exception));
      }
    }
  }

  private List<Integer> detectOfficialPageNumbers(
      JobState state,
      ReviewUpload upload,
      DocumentTemplate template,
      List<RenderedOcrPage> pages,
      String modelId
  ) throws IOException {
    try {
      return officialPageNumberDetector.detect(upload.filename(), template, pages, modelId);
    } catch (IOException exception) {
      log.warn(
          "FDH review job {} skipped official page-number recognition for {}: {}",
          state.jobId,
          upload.filename(),
          exception.getMessage()
      );
      return List.of();
    }
  }

  private static long elapsedMillisSince(long startedAtNanos) {
    return Math.max(0, (System.nanoTime() - startedAtNanos) / 1_000_000);
  }

  private static ThreadFactory applicationThreadFactory(String namePrefix) {
    ClassLoader applicationClassLoader = FdhReviewJobService.class.getClassLoader();
    AtomicInteger sequence = new AtomicInteger();
    return task -> {
      Thread thread = new Thread(task, namePrefix + "-" + sequence.incrementAndGet());
      thread.setContextClassLoader(applicationClassLoader);
      return thread;
    };
  }

  private String filename(String value) {
    return value == null || value.isBlank() ? "uploaded-document" : value;
  }

  private String userFacingFailureMessage(Throwable failure) {
    String message = failure == null ? "" : failure.getMessage();
    String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT);
    if (normalized.contains("header parser received no bytes")
        || normalized.contains("connection reset")
        || normalized.contains("connection closed")
        || normalized.contains("closed before")
        || normalized.contains("unexpected end of")
        || normalized.contains("eof")) {
      return "模型服务连接中断，请重新发起识别；如连续出现，请稍后再试或降低并发。";
    }
    if (normalized.contains("system_cpu_overloaded")) {
      return "模型服务当前负载过高，请稍后重试。";
    }
    if (normalized.contains("upstream_request_failed") || normalized.contains("upstream request failed")) {
      return "模型服务上游请求失败，请稍后重试。";
    }
    if (normalized.contains("timed out") || normalized.contains("timeout")) {
      return "模型服务响应超时，请稍后重试。";
    }
    return message == null || message.isBlank() ? "材料审批任务失败。" : message;
  }

  private record ReviewUpload(String filename, String contentType, byte[] bytes) {
    private ReviewUpload {
      contentType = contentType == null ? "" : contentType;
      bytes = bytes == null ? new byte[0] : bytes;
    }
  }

  private record IndexedReviewDocument(int index, FdhReviewDocument document) {}

  private static final class JobState {

    private final String jobId;
    private final String applicationTypeId;
    private final int totalFiles;
    private String status = "queued";
    private int processedFiles;
    private int progress;
    private String activeFilename = "";
    private String message = "材料审批任务已创建，等待开始处理。";
    private String error = "";
    private FdhReviewResult result;
    private Future<?> future;

    private JobState(String jobId, String applicationTypeId, int totalFiles) {
      this.jobId = jobId;
      this.applicationTypeId = applicationTypeId == null || applicationTypeId.isBlank()
          ? "entry_visa"
          : applicationTypeId;
      this.totalFiles = totalFiles;
    }

    private synchronized String applicationTypeId() {
      return applicationTypeId;
    }

    private synchronized void attachFuture(Future<?> future) {
      this.future = future;
      if (isCanceled()) {
        future.cancel(true);
      }
    }

    private synchronized void markFailedIfWorkerEnded() {
      if (!isTerminal() && future != null && future.isDone()) {
        try {
          future.get();
          markFailed("任务工作线程意外结束，请重新发起识别。");
        } catch (InterruptedException exception) {
          Thread.currentThread().interrupt();
          markFailed("任务工作线程被中断，请重新发起识别。");
        } catch (ExecutionException exception) {
          Throwable cause = exception.getCause() == null ? exception : exception.getCause();
          log.error("FDH review job {} terminated outside the task error handler", jobId, cause);
          markFailed("任务工作线程执行异常：" + cause.getClass().getSimpleName()
              + (cause.getMessage() == null || cause.getMessage().isBlank() ? "" : " - " + cause.getMessage()));
        }
      }
    }

    private synchronized boolean isCanceled() {
      return "canceled".equals(status);
    }

    private synchronized void markActive(String filename, String stage) {
      if (isTerminal()) {
        return;
      }
      status = "running";
      activeFilename = filename == null ? "" : filename;
      advanceProgressTo(progressFor(processedFiles, totalFiles, 10));
      message = stage + "：" + activeFilename;
    }

    private synchronized void markProcessed(int processed, String filename) {
      if (isTerminal()) {
        return;
      }
      status = "running";
      processedFiles = Math.max(processedFiles, processed);
      activeFilename = filename == null ? "" : filename;
      advanceProgressTo(progressFor(processedFiles, totalFiles, 0));
      message = "已完成 " + processedFiles + " / " + totalFiles + " 份材料识别。";
    }

    private synchronized void markProcessedFile(String filename) {
      if (isTerminal()) {
        return;
      }
      status = "running";
      processedFiles = Math.min(totalFiles, processedFiles + 1);
      activeFilename = filename == null ? "" : filename;
      advanceProgressTo(progressFor(processedFiles, totalFiles, 0));
      message = "已完成 " + processedFiles + " / " + totalFiles + " 份材料识别。";
    }

    private synchronized void markReviewing() {
      if (isTerminal()) {
        return;
      }
      status = "post_processing";
      advanceProgressTo(96);
      activeFilename = "";
      message = "正在汇总材料清单、标准化字段和跨文件规则结论。";
    }

    private synchronized void markCompleted(FdhReviewResult completedResult) {
      if (isTerminal()) {
        return;
      }
      result = completedResult;
      status = "completed";
      processedFiles = totalFiles;
      progress = 100;
      activeFilename = "";
      message = "材料审批识别完成。";
      error = "";
    }

    private synchronized void markFailed(String failureMessage) {
      if (isCanceled()) {
        return;
      }
      status = "failed";
      progress = 100;
      error = failureMessage == null || failureMessage.isBlank() ? "材料审批任务失败。" : failureMessage;
      message = error;
    }

    private synchronized Future<?> markCanceled() {
      if ("completed".equals(status) || "failed".equals(status)) {
        return future;
      }
      status = "canceled";
      error = "";
      result = null;
      activeFilename = "";
      progress = 100;
      message = "材料审批任务已取消。";
      return future;
    }

    private synchronized ExtractionProgressListener progressListener(String filename, int pageCount) {
      return new ExtractionProgressListener() {
        @Override
        public void pageStarted(int page) {
          JobState.this.markPageProgress(filename, page, pageCount, "正在识别第 " + page + " 页");
        }

        @Override
        public void pageAttemptStarted(int page, int attempt, String reason) {
          JobState.this.markPageProgress(filename, page, pageCount, "正在识别第 " + page + " 页第 " + attempt + " 次请求");
        }

        @Override
        public void pageAttemptCompleted(int page, int attempt, String reason, long elapsedMillis) {
          JobState.this.markPageProgress(filename, page, pageCount, "已完成第 " + page + " 页第 " + attempt + " 次识别");
        }

        @Override
        public void pageAttemptFailed(int page, int attempt, String reason, long elapsedMillis, String failureMessage) {
          JobState.this.markPageProgress(filename, page, pageCount, "第 " + page + " 页第 " + attempt + " 次识别未返回");
        }

        @Override
        public void pageCompleted(int page) {
          JobState.this.markPageProgress(filename, page, pageCount, "已完成第 " + page + " 页识别");
        }

        @Override
        public void pageFailed(int page, String failureMessage) {
          JobState.this.markPageProgress(filename, page, pageCount, "第 " + page + " 页识别需人工复核");
        }

        @Override
        public void postProcessingStep(String stage, String stepMessage, int stepProgress) {
          JobState.this.markPostProcessingProgress(
              filename,
              stepMessage == null || stepMessage.isBlank() ? stage : stepMessage,
              stepProgress
          );
        }
      };
    }

    private synchronized void markPageProgress(String filename, int page, int pageCount, String stage) {
      if (isTerminal()) {
        return;
      }
      status = "running";
      activeFilename = filename == null ? "" : filename;
      advanceProgressTo(progressForPage(processedFiles, totalFiles, page, pageCount));
      message = stage + "：" + activeFilename;
    }

    private synchronized void markPostProcessingProgress(String filename, String stage, int stepProgress) {
      if (isTerminal()) {
        return;
      }
      status = "running";
      activeFilename = filename == null ? "" : filename;
      advanceProgressTo(progressForPostProcessing(processedFiles, totalFiles, stepProgress));
      message = stage + "：" + activeFilename;
    }

    private synchronized FdhReviewJobStatusResponse snapshot() {
      return new FdhReviewJobStatusResponse(
          jobId,
          status,
          totalFiles,
          processedFiles,
          effectiveProgress(),
          activeFilename,
          message,
          error,
          result
      );
    }

    private int effectiveProgress() {
      if ("completed".equals(status) || "canceled".equals(status)) {
        return 100;
      }
      if ("post_processing".equals(status)) {
        return Math.max(progress, 96);
      }
      return progress;
    }

    private boolean isTerminal() {
      return "completed".equals(status) || "failed".equals(status) || "canceled".equals(status);
    }

    private void advanceProgressTo(int candidate) {
      progress = Math.max(progress, Math.max(0, Math.min(100, candidate)));
    }

    private int progressFor(int processed, int total, int activeOffset) {
      if (total <= 0) {
        return 0;
      }
      int perFile = (int) Math.floor(processed * 90.0 / total);
      if (processed < total && activeOffset > 0) {
        perFile = Math.min(90, perFile + Math.max(1, activeOffset));
      }
      return Math.max(0, Math.min(95, perFile));
    }

    private int progressForPage(int processed, int total, int page, int pageCount) {
      if (total <= 0) {
        return 0;
      }
      int safePageCount = Math.max(1, pageCount);
      int safePage = Math.max(1, Math.min(safePageCount, page));
      double completedFileShare = processed * 90.0 / total;
      double activeFileShare = 90.0 / total;
      double pageRatio = safePage / (double) safePageCount;
      int pageProgress = (int) Math.floor(completedFileShare + activeFileShare * pageRatio);
      return Math.max(progress, Math.max(12, Math.min(95, pageProgress)));
    }

    private int progressForPostProcessing(int processed, int total, int stepProgress) {
      if (total <= 0) {
        return 0;
      }
      double completedFileShare = processed * 90.0 / total;
      double activeFileShare = 90.0 / total;
      double normalizedStep = Math.max(0, Math.min(100, stepProgress)) / 100.0;
      int step = (int) Math.floor(completedFileShare + activeFileShare * normalizedStep);
      return Math.max(progress, Math.max(10, Math.min(95, step)));
    }
  }
}
