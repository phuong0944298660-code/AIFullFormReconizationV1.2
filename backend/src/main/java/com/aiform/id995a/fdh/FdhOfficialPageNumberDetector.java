package com.aiform.id995a.fdh;

import com.aiform.id995a.llm.LlmModelProfile;
import com.aiform.id995a.llm.LlmModelRegistry;
import com.aiform.id995a.llm.OfficialPageNumberRecognitionGateway;
import com.aiform.id995a.llm.OfficialPageNumberRecognitionResult;
import com.aiform.id995a.ocr.DocumentTemplate;
import com.aiform.id995a.ocr.RenderedOcrPage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class FdhOfficialPageNumberDetector {

  private static final Logger log = LoggerFactory.getLogger(FdhOfficialPageNumberDetector.class);
  private static final long DEFAULT_TIMEOUT_MILLIS = 30_000;

  private final OfficialPageNumberRecognitionGateway pageNumberGateway;
  private final LlmModelRegistry llmModelRegistry;
  private final long timeoutMillis;

  @Autowired
  FdhOfficialPageNumberDetector(
      OfficialPageNumberRecognitionGateway pageNumberGateway,
      LlmModelRegistry llmModelRegistry,
      @Value("${fdh.review.official-page-number-timeout-millis:30000}") long timeoutMillis
  ) {
    this.pageNumberGateway = pageNumberGateway;
    this.llmModelRegistry = llmModelRegistry;
    this.timeoutMillis = Math.max(1, timeoutMillis);
  }

  FdhOfficialPageNumberDetector(
      OfficialPageNumberRecognitionGateway pageNumberGateway,
      LlmModelRegistry llmModelRegistry
  ) {
    this(
        pageNumberGateway,
        llmModelRegistry,
        Long.getLong("fdh.review.official-page-number-timeout-millis", DEFAULT_TIMEOUT_MILLIS)
    );
  }

  FdhOfficialPageNumberDetector() {
    this(null, null);
  }

  List<Integer> detect(
      String filename,
      DocumentTemplate template,
      List<RenderedOcrPage> pages,
      String modelId
  ) throws IOException {
    List<RenderedOcrPage> safePages = pages == null ? List.of() : pages;
    int expectedPages = expectedPages(template);
    if (expectedPages <= 0 || safePages.isEmpty() || pageNumberGateway == null || llmModelRegistry == null) {
      return List.of();
    }
    LlmModelProfile profile = llmModelRegistry.resolve(modelId);
    List<OfficialPageNumberRecognitionResult> results = recognizeWithTimeout(filename, template, safePages, profile);
    return validatedPageNumbers(template, safePages, results, expectedPages);
  }

  List<Integer> detect(DocumentTemplate template, List<RenderedOcrPage> pages) throws IOException {
    return detect("", template, pages, null);
  }

  private List<OfficialPageNumberRecognitionResult> recognizeWithTimeout(
      String filename,
      DocumentTemplate template,
      List<RenderedOcrPage> pages,
      LlmModelProfile profile
  ) throws IOException {
    ExecutorService executor = Executors.newSingleThreadExecutor();
    Future<List<OfficialPageNumberRecognitionResult>> future = executor.submit(
        () -> pageNumberGateway.recognizeOfficialPageNumbers(filename, template, pages, profile)
    );
    try {
      return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
    } catch (TimeoutException exception) {
      future.cancel(true);
      log.warn(
          "Skipped official page-number recognition for {} after {} ms timeout.",
          filename,
          timeoutMillis
      );
      return List.of();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      future.cancel(true);
      throw new IOException("Official page-number recognition interrupted.", exception);
    } catch (ExecutionException exception) {
      Throwable cause = exception.getCause();
      if (cause instanceof IOException ioException) {
        throw ioException;
      }
      if (cause instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      throw new IOException("Official page-number recognition failed.", cause);
    } finally {
      executor.shutdownNow();
    }
  }

  private List<Integer> validatedPageNumbers(
      DocumentTemplate template,
      List<RenderedOcrPage> pages,
      List<OfficialPageNumberRecognitionResult> results,
      int expectedPages
  ) {
    if (results == null || results.isEmpty()) {
      return List.of();
    }
    Map<Integer, OfficialPageNumberRecognitionResult> byUploadedPage = results.stream()
        .filter(result -> result != null)
        .collect(Collectors.toMap(
            OfficialPageNumberRecognitionResult::uploadedPage,
            Function.identity(),
            (left, right) -> left
        ));
    String expectedForm = expectedFormId(template);
    String expectedVersion = expectedVersion(template);
    List<Integer> pageNumbers = new ArrayList<>();
    Set<Integer> seen = new HashSet<>();
    for (RenderedOcrPage page : pages) {
      OfficialPageNumberRecognitionResult result = byUploadedPage.get(page.page());
      if (!validResult(result, expectedForm, expectedVersion, expectedPages)) {
        return List.of();
      }
      if (!seen.add(result.officialPageNumber())) {
        return List.of();
      }
      pageNumbers.add(result.officialPageNumber());
    }
    return List.copyOf(pageNumbers);
  }

  private boolean validResult(
      OfficialPageNumberRecognitionResult result,
      String expectedForm,
      String expectedVersion,
      int expectedPages
  ) {
    if (result == null || result.officialPageNumber() < 1 || result.officialPageNumber() > expectedPages) {
      return false;
    }
    if (result.confidence() > 0 && result.confidence() < 60) {
      return false;
    }
    if (!expectedForm.isBlank() && !matchesFormId(result.formId(), expectedForm)) {
      return false;
    }
    return expectedVersion.isBlank()
        || result.version() == null
        || result.version().isBlank()
        || normalize(result.version()).equals(normalize(expectedVersion));
  }

  private boolean matchesFormId(String actual, String expected) {
    String normalizedActual = normalize(actual).replace("ID", "");
    String normalizedExpected = normalize(expected).replace("ID", "");
    return !normalizedActual.isBlank() && normalizedActual.equals(normalizedExpected);
  }

  private int expectedPages(DocumentTemplate template) {
    if (template == null) {
      return 0;
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

  private String normalize(String value) {
    return value == null
        ? ""
        : value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9/]", "");
  }
}
