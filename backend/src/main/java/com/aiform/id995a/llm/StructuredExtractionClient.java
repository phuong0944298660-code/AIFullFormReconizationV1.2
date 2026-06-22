package com.aiform.id995a.llm;

import com.aiform.id995a.ocr.RenderedOcrPage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class StructuredExtractionClient implements
    StructuredExtractionGateway,
    FieldCropTranscriptionGateway,
    OfficialPageNumberRecognitionGateway,
    ApplicationTypeSelectionRecognitionGateway,
    FieldRegionLocationGateway {

  private static final String DEFAULT_BASE_URL = "https://apie.zhisuaninfo.com/v1";
  private static final int OFFICIAL_PAGE_NUMBER_IMAGE_MAX_LONG_SIDE = 1600;

  private final LlmProperties properties;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;
  private final ObjectMapper lenientObjectMapper;

  @Autowired
  public StructuredExtractionClient(LlmProperties properties, ObjectMapper objectMapper) {
    this(properties, HttpClient.newHttpClient(), objectMapper);
  }

  StructuredExtractionClient(LlmProperties properties, HttpClient httpClient, ObjectMapper objectMapper) {
    this.properties = properties;
    this.httpClient = httpClient;
    this.objectMapper = objectMapper;
    this.lenientObjectMapper = JsonMapper.builder()
        .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
        .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
        .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
        .build();
  }

  @Override
  public StructuredExtractionResult extract(
      String filename,
      List<RenderedOcrPage> pages,
      ExtractionProgressListener progressListener
  ) throws IOException {
    return extract(filename, pages, progressListener, defaultProfile());
  }

  @Override
  public StructuredExtractionResult extract(
      String filename,
      List<RenderedOcrPage> pages,
      ExtractionProgressListener progressListener,
      LlmModelProfile modelProfile
  ) throws IOException {
    return extractInternal(filename, pages, progressListener, modelProfile, false);
  }

  @Override
  public StructuredExtractionResult extractAllowingPartialPages(
      String filename,
      List<RenderedOcrPage> pages,
      ExtractionProgressListener progressListener,
      LlmModelProfile modelProfile
  ) throws IOException {
    return extractInternal(filename, pages, progressListener, modelProfile, true);
  }

  private StructuredExtractionResult extractInternal(
      String filename,
      List<RenderedOcrPage> pages,
      ExtractionProgressListener progressListener,
      LlmModelProfile modelProfile,
      boolean allowPartialPages
  ) throws IOException {
    LlmModelProfile profile = modelProfile == null ? defaultProfile() : modelProfile;
    if (blank(profile.apiKey())) {
      throw new IOException("Missing LLM API key. Set LLM_API_KEY.");
    }
    ExtractionProgressListener listener = progressListener == null ? ExtractionProgressListener.NOOP : progressListener;
    try {
      List<PageExtraction> pageExtractions = extractPages(filename, pages, listener, profile, allowPartialPages);
      ObjectNode combined = objectMapper.createObjectNode();
      combined.put("source_file", filename == null || filename.isBlank() ? "uploaded-document" : filename);
      combined.put("total_pages", pages.size());
      ObjectNode combinedConfidence = combined.putObject("_confidence");
      ObjectNode combinedEvidence = combined.putObject("_field_evidence");
      ObjectNode combinedPageErrors = combined.putObject("_page_errors");
      StringBuilder rawText = new StringBuilder();

      boolean hasAnyPageFields = false;
      for (PageExtraction pageExtraction : pageExtractions) {
        String pageKey = "page_" + pageExtraction.page();
        JsonNode pageData = pageExtraction.response().data().path(pageKey);
        if (!pageData.isObject() && !pageData.isArray()) {
          pageData = objectMapper.createObjectNode();
        }
        if (!pageData.isEmpty()) {
          hasAnyPageFields = true;
        }
        combined.set(pageKey, pageData);
        mergeMetadataPage(combinedConfidence, pageExtraction.response().data().path("_confidence"), pageKey);
        mergeMetadataPage(combinedEvidence, pageExtraction.response().data().path("_field_evidence"), pageKey);
        mergeMetadataPage(combinedPageErrors, pageExtraction.response().data().path("_page_errors"), pageKey);
        rawText.append("/* ").append(pageKey).append(" */\n").append(pageExtraction.response().rawText()).append('\n');
      }
      if (!hasAnyPageFields && !pages.isEmpty() && !allowPartialPages) {
        throw new IOException("LLM returned empty structured JSON after retry.");
      }
      return new StructuredExtractionResult(combined, rawText.toString(), profile.model());
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IOException("LLM request interrupted.", exception);
    }
  }

  private List<PageExtraction> extractPages(
      String filename,
      List<RenderedOcrPage> pages,
      ExtractionProgressListener progressListener,
      LlmModelProfile profile,
      boolean allowPartialPages
  ) throws IOException, InterruptedException {
    int concurrency = pageConcurrency(pages.size());
    ExecutorService executor = Executors.newFixedThreadPool(concurrency);
    try {
      List<PageFuture> futures = new ArrayList<>();
      for (RenderedOcrPage page : pages) {
        futures.add(new PageFuture(
            page.page(),
            executor.submit(() -> extractPage(filename, pages.size(), page, progressListener, profile))
        ));
      }
      List<PageExtraction> results = new ArrayList<>();
      for (PageFuture pageFuture : futures) {
        try {
          results.add(pageFuture.future().get());
        } catch (ExecutionException exception) {
          Throwable cause = exception.getCause();
          if (allowPartialPages) {
            results.add(failedPageExtraction(pageFuture.page(), cause));
            continue;
          }
          if (cause instanceof IOException ioException) {
            throw ioException;
          }
          if (cause instanceof RuntimeException runtimeException) {
            throw runtimeException;
          }
          throw new IOException("LLM page extraction failed.", cause);
        }
      }
      return results.stream()
          .sorted(Comparator.comparingInt(PageExtraction::page))
          .toList();
    } finally {
      executor.shutdownNow();
    }
  }

  private PageExtraction failedPageExtraction(int page, Throwable cause) {
    String pageKey = "page_" + page;
    String message = conciseFailureMessage(cause);
    ObjectNode root = objectMapper.createObjectNode();
    root.set(pageKey, objectMapper.createObjectNode());
    ObjectNode pageErrors = root.putObject("_page_errors");
    pageErrors.put(pageKey, message);
    return new PageExtraction(page, new ExtractionResponse(root, "Page " + page + " extraction failed: " + message));
  }

  private String conciseFailureMessage(Throwable cause) {
    if (cause == null) {
      return "LLM page extraction failed.";
    }
    String message = cause.getMessage();
    if (message == null || message.isBlank()) {
      message = cause.getClass().getSimpleName();
    }
    return truncate(message, 220);
  }

  private PageExtraction extractPage(
      String filename,
      int totalPages,
      RenderedOcrPage page,
      ExtractionProgressListener progressListener,
      LlmModelProfile profile
  ) throws IOException {
    progressListener.pageStarted(page.page());
    try {
      ExtractionResponse extraction = sendPageAttempt(filename, totalPages, page, progressListener, profile, 1, "initial", false);
      if (isNoApplicantInputPage(extraction.data(), page)) {
        progressListener.pageCompleted(page.page());
        return new PageExtraction(page.page(), extraction);
      }
      if (isEmptyExtraction(extraction.data(), List.of(page))) {
        extraction = sendPageAttempt(filename, totalPages, page, progressListener, profile, 2, "empty_retry", true);
      }
      if (isEmptyExtraction(extraction.data(), List.of(page))) {
        extraction = new ExtractionResponse(objectMapper.createObjectNode(), extraction.rawText());
      }
      progressListener.pageCompleted(page.page());
      return new PageExtraction(page.page(), extraction);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      progressListener.pageFailed(page.page(), "LLM request interrupted.");
      throw new IOException("LLM request interrupted.", exception);
    } catch (IOException | RuntimeException exception) {
      progressListener.pageFailed(page.page(), exception.getMessage());
      throw exception;
    }
  }

  private ExtractionResponse sendPageAttempt(
      String filename,
      int totalPages,
      RenderedOcrPage page,
      ExtractionProgressListener progressListener,
      LlmModelProfile profile,
      int attempt,
      String reason,
      boolean retryAfterEmptyResponse
  ) throws IOException, InterruptedException {
    long startedAt = System.nanoTime();
    progressListener.pageAttemptStarted(page.page(), attempt, reason);
    try {
      ExtractionResponse response = sendExtractionRequest(
          buildRequestPayload(filename, List.of(page), retryAfterEmptyResponse, totalPages, profile),
          profile
      );
      progressListener.pageAttemptCompleted(page.page(), attempt, reason, elapsedMillisSince(startedAt));
      return response;
    } catch (InterruptedException exception) {
      progressListener.pageAttemptFailed(page.page(), attempt, reason, elapsedMillisSince(startedAt), "LLM request interrupted.");
      throw exception;
    } catch (IOException | RuntimeException exception) {
      progressListener.pageAttemptFailed(page.page(), attempt, reason, elapsedMillisSince(startedAt), exception.getMessage());
      throw exception;
    }
  }

  int pageConcurrency(int pageCount) {
    int configuredMaximum = Math.max(1, properties.pageConcurrency());
    int natural = switch (pageCount) {
      case 0, 1 -> 1;
      case 2 -> 2;
      case 3 -> 3;
      default -> 4;
    };
    return Math.max(1, Math.min(configuredMaximum, natural));
  }

  JsonNode buildRequestPayload(String filename, List<RenderedOcrPage> pages) {
    return buildRequestPayload(filename, pages, false, pages.size(), defaultProfile());
  }

  JsonNode buildRequestPayload(String filename, List<RenderedOcrPage> pages, LlmModelProfile profile) {
    return buildRequestPayload(filename, pages, false, pages.size(), profile == null ? defaultProfile() : profile);
  }

  @Override
  public List<FieldCropTranscriptionResult> transcribeFieldCrops(
      String filename,
      List<FieldCropTranscriptionRequest> crops,
      LlmModelProfile modelProfile
  ) throws IOException {
    if (crops == null || crops.isEmpty()) {
      return List.of();
    }
    LlmModelProfile profile = modelProfile == null ? defaultProfile() : modelProfile;
    if (blank(profile.apiKey())) {
      throw new IOException("Missing LLM API key. Set LLM_API_KEY.");
    }
    try {
      ExtractionResponse response = sendExtractionRequest(
          buildFieldCropTranscriptionPayload(filename, crops, profile),
          profile
      );
      return parseFieldCropTranscriptionResults(response.data(), crops);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IOException("LLM crop transcription request interrupted.", exception);
    }
  }

  @Override
  public Map<String, NormalizedBbox> locateMissingFieldBboxes(
      String filename,
      List<RenderedOcrPage> pages,
      List<MissingFieldRegion> missingFields,
      LlmModelProfile modelProfile
  ) throws IOException {
    Map<String, NormalizedBbox> located = new LinkedHashMap<>();
    if (missingFields == null || missingFields.isEmpty()) {
      return located;
    }
    LlmModelProfile profile = modelProfile == null ? defaultProfile() : modelProfile;
    if (blank(profile.apiKey())) {
      throw new IOException("Missing LLM API key. Set LLM_API_KEY.");
    }
    Map<Integer, RenderedOcrPage> pageByNo = new LinkedHashMap<>();
    for (RenderedOcrPage page : pages == null ? List.<RenderedOcrPage>of() : pages) {
      pageByNo.put(page.page(), page);
    }
    Map<Integer, List<MissingFieldRegion>> byPage = new LinkedHashMap<>();
    for (MissingFieldRegion field : missingFields) {
      byPage.computeIfAbsent(field.page(), ignored -> new ArrayList<>()).add(field);
    }
    for (Map.Entry<Integer, List<MissingFieldRegion>> entry : byPage.entrySet()) {
      RenderedOcrPage page = pageByNo.get(entry.getKey());
      if (page == null) {
        continue;
      }
      try {
        ExtractionResponse response = sendExtractionRequest(
            buildFieldRegionLocationPayload(filename, page, entry.getValue(), profile),
            profile
        );
        located.putAll(parseFieldRegionLocations(response.data(), entry.getKey(), entry.getValue()));
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new IOException("LLM field region location request interrupted.", exception);
      }
    }
    return located;
  }

  private JsonNode buildFieldRegionLocationPayload(
      String filename,
      RenderedOcrPage page,
      List<MissingFieldRegion> fields,
      LlmModelProfile profile
  ) {
    ObjectNode root = objectMapper.createObjectNode();
    root.put("model", blank(profile.model()) ? "Qwen3.6-35B-A3B" : profile.model());
    root.put("temperature", 0);
    root.put("max_tokens", Math.max(1024, Math.min(properties.maxTokens(), 4096)));
    root.put("stream", false);
    root.put("enable_thinking", profile.enableThinking());
    ObjectNode chatTemplateOptions = root.putObject("chat_template_kwargs");
    chatTemplateOptions.put("enable_thinking", profile.enableThinking());
    ObjectNode responseFormat = root.putObject("response_format");
    responseFormat.put("type", "json_object");

    ArrayNode messages = root.putArray("messages");
    ObjectNode systemMessage = messages.addObject();
    systemMessage.put("role", "system");
    systemMessage.put("content", "You are a precise field-location engine. Locate recognized values on the page image and return their bounding boxes. Return valid JSON only.");

    ObjectNode userMessage = messages.addObject();
    userMessage.put("role", "user");
    ArrayNode content = userMessage.putArray("content");
    ObjectNode text = content.addObject();
    text.put("type", "text");
    text.put("text", buildFieldRegionLocationPrompt(filename, page, fields));

    ObjectNode image = content.addObject();
    image.put("type", "image_url");
    ObjectNode imageUrl = image.putObject("image_url");
    imageUrl.put("url", page.sourceImageDataUrl());
    imageUrl.put("detail", "high");

    return root;
  }

  private String buildFieldRegionLocationPrompt(String filename, RenderedOcrPage page, List<MissingFieldRegion> fields) {
    StringBuilder builder = new StringBuilder();
    builder.append("This is page_").append(page.page()).append(" of a form. For each field listed below, the value has already been recognized. Locate WHERE that value is written on this page image by visual reasoning, and return the normalized bounding box of the filled value area.\n");
    builder.append("Do NOT use fixed coordinates, template layouts, ROI, or OCR output. Find the value text by visually scanning the page.\n");
    builder.append("Coordinates are normalized to [0,1] relative to the page image: {x, y, width, height}, where (x, y) is the top-left corner and (width, height) is the size of the filled value area.\n");
    builder.append("Return JSON only in this schema: {\"fields\":[{\"path\":\"<exact_path>\",\"value_bbox\":{\"x\":0.32,\"y\":0.39,\"width\":0.55,\"height\":0.03}}]}.\n");
    builder.append("Only include a field if you can visually locate its value on the page. If a value cannot be found, omit that field. Make a strong effort to locate common page fields such as present address, domicile address, employer name/address, employment period from/to, date, and signature fields.\n");
    builder.append("source_file: ").append(filename == null || filename.isBlank() ? "uploaded-document" : filename).append('\n');
    builder.append("Fields to locate (page_").append(page.page()).append("):\n");
    for (int index = 0; index < fields.size(); index += 1) {
      MissingFieldRegion field = fields.get(index);
      builder.append(index + 1)
          .append(". path=").append(field.path())
          .append(", label=").append(field.label())
          .append(", value=").append(field.value())
          .append('\n');
    }
    return builder.toString();
  }

  private Map<String, NormalizedBbox> parseFieldRegionLocations(JsonNode data, int page, List<MissingFieldRegion> fields) {
    Map<String, NormalizedBbox> located = new LinkedHashMap<>();
    if (data == null) {
      return located;
    }
    JsonNode fieldsNode = data.path("fields");
    if (!fieldsNode.isArray()) {
      return located;
    }
    for (JsonNode item : fieldsNode) {
      String path = item.path("path").asText("");
      if (path.isBlank()) {
        continue;
      }
      boolean valid = false;
      for (MissingFieldRegion field : fields) {
        if (field.path().equals(path)) {
          valid = true;
          break;
        }
      }
      if (!valid) {
        continue;
      }
      JsonNode bbox = item.path("value_bbox");
      if (bbox.isMissingNode() || bbox.isNull()) {
        continue;
      }
      double x = bbox.path("x").asDouble(-1);
      double y = bbox.path("y").asDouble(-1);
      double width = bbox.path("width").asDouble(-1);
      double height = bbox.path("height").asDouble(-1);
      if (x < 0 || y < 0 || width <= 0 || height <= 0) {
        continue;
      }
      located.put(page + "|" + path, new NormalizedBbox(x, y, width, height));
    }
    return located;
  }

  @Override
  public List<OfficialPageNumberRecognitionResult> recognizeOfficialPageNumbers(
      String filename,
      com.aiform.id995a.ocr.DocumentTemplate template,
      List<RenderedOcrPage> pages,
      LlmModelProfile modelProfile
  ) throws IOException {
    if (pages == null || pages.isEmpty()) {
      return List.of();
    }
    LlmModelProfile profile = modelProfile == null ? defaultProfile() : modelProfile;
    if (blank(profile.apiKey())) {
      throw new IOException("Missing LLM API key. Set LLM_API_KEY.");
    }
    try {
      ExtractionResponse response = sendExtractionRequest(
          buildOfficialPageNumberPayload(filename, template, pages, profile),
          profile
      );
      return parseOfficialPageNumberResults(response.data());
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IOException("LLM page number recognition request interrupted.", exception);
    }
  }

  @Override
  public List<ApplicationTypeSelectionRecognitionResult> recognizeApplicationTypeSelections(
      String filename,
      RenderedOcrPage page,
      com.aiform.id995a.ocr.DocumentTemplate template,
      LlmModelProfile modelProfile
  ) throws IOException {
    if (page == null) {
      return List.of();
    }
    LlmModelProfile profile = modelProfile == null ? defaultProfile() : modelProfile;
    if (blank(profile.apiKey())) {
      throw new IOException("Missing LLM API key. Set LLM_API_KEY.");
    }
    try {
      ExtractionResponse response = sendExtractionRequest(
          buildApplicationTypeSelectionPayload(filename, page, template, profile),
          profile
      );
      return parseApplicationTypeSelectionResults(response.data());
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IOException("LLM application type recognition request interrupted.", exception);
    }
  }

  JsonNode buildApplicationTypeSelectionPayload(
      String filename,
      RenderedOcrPage page,
      com.aiform.id995a.ocr.DocumentTemplate template,
      LlmModelProfile profile
  ) {
    ObjectNode root = objectMapper.createObjectNode();
    root.put("model", blank(profile.model()) ? "Qwen3.6-35B-A3B" : profile.model());
    root.put("temperature", 0);
    root.put("max_tokens", 1000);
    root.put("stream", false);
    root.put("enable_thinking", profile.enableThinking());
    ObjectNode chatTemplateOptions = root.putObject("chat_template_kwargs");
    chatTemplateOptions.put("enable_thinking", profile.enableThinking());
    ObjectNode responseFormat = root.putObject("response_format");
    responseFormat.put("type", "json_object");

    ArrayNode messages = root.putArray("messages");
    ObjectNode systemMessage = messages.addObject();
    systemMessage.put("role", "system");
    systemMessage.put("content", "You are an exact checkbox recognition engine for Hong Kong Immigration FDH forms. Return valid JSON only.");

    ObjectNode userMessage = messages.addObject();
    userMessage.put("role", "user");
    ArrayNode content = userMessage.putArray("content");
    ObjectNode text = content.addObject();
    text.put("type", "text");
    text.put("text", buildApplicationTypeSelectionPrompt(filename, page, template));

    ObjectNode image = content.addObject();
    image.put("type", "image_url");
    ObjectNode imageUrl = image.putObject("image_url");
    imageUrl.put("url", page.sourceImageDataUrl());
    imageUrl.put("detail", "high");
    return root;
  }

  private String buildApplicationTypeSelectionPrompt(
      String filename,
      RenderedOcrPage page,
      com.aiform.id995a.ocr.DocumentTemplate template
  ) {
    StringBuilder builder = new StringBuilder();
    builder.append("Inspect only section 1, Application Type, on the attached ID 988A page image.\n");
    builder.append("Locate the whole Application Type table yourself. Do not rely on upload order, OCR text, fixed coordinates, or previous extracted JSON.\n");
    builder.append("For each of the four printed checkboxes in section 1, decide whether the checkbox is intentionally selected. Count a clear tick, check, cross, or deliberate mark inside/across the box as selected. Treat smudges, erasures, accidental ink, or unrelated strokes as not selected.\n");
    builder.append("Return every option, including unselected options. Do not infer the selected option from the homepage or from other fields.\n");
    builder.append("Return JSON only in this schema: {\"options\":[{\"key\":\"entry_to_hong_kong_to_take_up_employment_as_a_domestic_helper_from_abroad\",\"value\":\"entry visa\",\"selected\":true,\"confidence\":0-100,\"evidence\":\"short visual evidence\"}]}.\n");
    builder.append("Allowed options, in printed order:\n");
    builder.append("1. key=entry_to_hong_kong_to_take_up_employment_as_a_domestic_helper_from_abroad, value=entry visa, label=Entry to Hong Kong to take up employment as a domestic helper from abroad / 入境签证\n");
    builder.append("2. key=contract_renewal_entry_visa, value=entry visa, label=Contract renewal with the same employer or change of employer / 入境签证\n");
    builder.append("3. key=contract_renewal_entry_visa_and_extension, value=entry visa AND Extension of Stay, label=Contract renewal with the same employer or change of employer / 入境签证及延期逗留\n");
    builder.append("4. key=complete_the_remaining_extended_period_of_the_current_contract, value=Extension of Stay, label=Complete the remaining/extended period of the current contract / 延期逗留\n");
    builder.append("source_file: ").append(filename == null || filename.isBlank() ? "uploaded-document" : filename).append('\n');
    builder.append("expected_template_id: ").append(template == null ? "" : template.templateId()).append('\n');
    builder.append("uploaded_page: ").append(page.page())
        .append(", image_size=").append(page.imageWidth()).append('x').append(page.imageHeight()).append('\n');
    return builder.toString();
  }

  private List<ApplicationTypeSelectionRecognitionResult> parseApplicationTypeSelectionResults(JsonNode data) {
    JsonNode results = data == null ? null : data.path("options");
    if (results == null || !results.isArray()) {
      results = data == null ? null : data.path("selections");
    }
    if (results == null || !results.isArray()) {
      results = data == null ? null : data.path("results");
    }
    if (results == null || !results.isArray()) {
      return List.of();
    }
    List<ApplicationTypeSelectionRecognitionResult> values = new ArrayList<>();
    for (JsonNode item : results) {
      values.add(new ApplicationTypeSelectionRecognitionResult(
          firstExistingText(item, "key", "option_key", "optionKey", "field_key", "fieldKey"),
          firstExistingText(item, "value", "selected_value", "selectedValue"),
          booleanValue(firstExisting(item, "selected", "checked", "is_selected", "isSelected")),
          normalizeConfidence(item.path("confidence").asDouble(0)),
          firstExistingText(item, "evidence", "reason", "visual_evidence")
      ));
    }
    return List.copyOf(values);
  }

  JsonNode buildOfficialPageNumberPayload(
      String filename,
      com.aiform.id995a.ocr.DocumentTemplate template,
      List<RenderedOcrPage> pages,
      LlmModelProfile profile
  ) {
    ObjectNode root = objectMapper.createObjectNode();
    root.put("model", blank(profile.model()) ? "Qwen3.6-35B-A3B" : profile.model());
    root.put("temperature", 0);
    root.put("max_tokens", 1200);
    root.put("stream", false);
    root.put("enable_thinking", profile.enableThinking());
    ObjectNode chatTemplateOptions = root.putObject("chat_template_kwargs");
    chatTemplateOptions.put("enable_thinking", profile.enableThinking());
    ObjectNode responseFormat = root.putObject("response_format");
    responseFormat.put("type", "json_object");

    ArrayNode messages = root.putArray("messages");
    ObjectNode systemMessage = messages.addObject();
    systemMessage.put("role", "system");
    systemMessage.put("content", "You are an exact official form footer page-number recognition engine. Return valid JSON only.");

    ObjectNode userMessage = messages.addObject();
    userMessage.put("role", "user");
    ArrayNode content = userMessage.putArray("content");
    ObjectNode text = content.addObject();
    text.put("type", "text");
    text.put("text", buildOfficialPageNumberPrompt(filename, template, pages));

    for (RenderedOcrPage page : pages) {
      ObjectNode image = content.addObject();
      image.put("type", "image_url");
      ObjectNode imageUrl = image.putObject("image_url");
      imageUrl.put("url", officialPageNumberImageDataUrl(page));
      imageUrl.put("detail", "low");
    }
    return root;
  }

  private String officialPageNumberImageDataUrl(RenderedOcrPage page) {
    if (page == null || page.pngBytes() == null || page.pngBytes().length == 0) {
      return page == null ? "" : page.sourceImageDataUrl();
    }
    try {
      BufferedImage original = ImageIO.read(new ByteArrayInputStream(page.pngBytes()));
      if (original == null) {
        return page.sourceImageDataUrl();
      }
      BufferedImage normalized = downscaleForOfficialPageNumberRecognition(original);
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      if (!ImageIO.write(normalized, "jpg", output)) {
        return page.sourceImageDataUrl();
      }
      return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
    } catch (IOException | RuntimeException exception) {
      return page.sourceImageDataUrl();
    }
  }

  private BufferedImage downscaleForOfficialPageNumberRecognition(BufferedImage original) {
    int width = original.getWidth();
    int height = original.getHeight();
    int longSide = Math.max(width, height);
    if (longSide <= OFFICIAL_PAGE_NUMBER_IMAGE_MAX_LONG_SIDE) {
      return toRgbImage(original, width, height);
    }
    double scale = OFFICIAL_PAGE_NUMBER_IMAGE_MAX_LONG_SIDE / (double) longSide;
    int targetWidth = Math.max(1, (int) Math.round(width * scale));
    int targetHeight = Math.max(1, (int) Math.round(height * scale));
    return toRgbImage(original, targetWidth, targetHeight);
  }

  private BufferedImage toRgbImage(BufferedImage source, int width, int height) {
    BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = target.createGraphics();
    try {
      graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      graphics.drawImage(source, 0, 0, width, height, null);
    } finally {
      graphics.dispose();
    }
    return target;
  }

  private String buildOfficialPageNumberPrompt(
      String filename,
      com.aiform.id995a.ocr.DocumentTemplate template,
      List<RenderedOcrPage> pages
  ) {
    String templateId = template == null ? "" : template.templateId();
    String footerId = template == null ? "" : template.footerId();
    StringBuilder builder = new StringBuilder();
    builder.append("Identify the official printed footer page number for each attached page image.\n");
    builder.append("Use visual reasoning over the whole page image. Locate the official form footer yourself; do not rely on upload order, provided page index, template coordinates, OCR text, or a fixed crop.\n");
    builder.append("For Hong Kong Immigration FDH forms, the footer normally contains a form id such as ID 988A, ID 988B, or ID 407, a version such as 06/2024 or 11/2016, and a printed page number near the footer area. The page number may appear at the bottom center, near the form footer, or near footer marks.\n");
    builder.append("Return only the printed official footer page number, not the uploaded page index, not a handwritten date, not a section number, not a barcode number, and not a page count inferred from sequence.\n");
    builder.append("If the page number is not visible or ambiguous, set page_no to null and confidence below 60.\n");
    builder.append("Return JSON only in this schema: {\"pages\":[{\"uploaded_page\":1,\"form_id\":\"ID 988B\",\"version\":\"06/2024\",\"page_no\":3,\"confidence\":0-100,\"evidence\":\"short visual evidence\"}]}.\n");
    builder.append("The images are provided in this exact order; uploaded_page must be copied from the listed uploaded_page value.\n");
    builder.append("source_file: ").append(filename == null || filename.isBlank() ? "uploaded-document" : filename).append('\n');
    builder.append("expected_template_id: ").append(templateId).append('\n');
    builder.append("expected_footer_id: ").append(footerId).append('\n');
    builder.append("pages:\n");
    for (RenderedOcrPage page : pages) {
      builder.append("- uploaded_page=").append(page.page())
          .append(", image_size=").append(page.imageWidth()).append('x').append(page.imageHeight())
          .append('\n');
    }
    return builder.toString();
  }

  private List<OfficialPageNumberRecognitionResult> parseOfficialPageNumberResults(JsonNode data) {
    JsonNode results = data == null ? null : data.path("pages");
    if (results == null || !results.isArray()) {
      results = data == null ? null : data.path("results");
    }
    if (results == null || !results.isArray()) {
      return List.of();
    }
    List<OfficialPageNumberRecognitionResult> values = new ArrayList<>();
    for (JsonNode item : results) {
      int uploadedPage = firstExistingInt(item, 0, "uploaded_page", "uploadedPage", "page", "input_page");
      int pageNo = firstExistingInt(item, 0, "page_no", "pageNo", "official_page", "officialPage", "official_page_number", "page_number");
      values.add(new OfficialPageNumberRecognitionResult(
          uploadedPage,
          firstExistingText(item, "form_id", "formId", "document_id", "documentId"),
          firstExistingText(item, "version", "revision", "form_version", "formVersion"),
          pageNo,
          normalizeConfidence(item.path("confidence").asDouble(0)),
          firstExistingText(item, "evidence", "reason", "visual_evidence")
      ));
    }
    return List.copyOf(values);
  }

  private JsonNode buildRequestPayload(
      String filename,
      List<RenderedOcrPage> pages,
      boolean retryAfterEmptyResponse,
      int totalPages,
      LlmModelProfile profile
  ) {
    ObjectNode root = objectMapper.createObjectNode();
    root.put("model", blank(profile.model()) ? "Qwen3.6-35B-A3B" : profile.model());
    root.put("temperature", 0);
    root.put("max_tokens", Math.max(1024, properties.maxTokens()));
    root.put("stream", false);
    root.put("enable_thinking", profile.enableThinking());
    ObjectNode chatTemplateOptions = root.putObject("chat_template_kwargs");
    chatTemplateOptions.put("enable_thinking", profile.enableThinking());
    ObjectNode responseFormat = root.putObject("response_format");
    responseFormat.put("type", "json_object");

    ArrayNode messages = root.putArray("messages");
    ObjectNode systemMessage = messages.addObject();
    systemMessage.put("role", "system");
    systemMessage.put("content", "You are a form document understanding engine. Return valid JSON only.");

    ObjectNode userMessage = messages.addObject();
    userMessage.put("role", "user");
    ArrayNode content = userMessage.putArray("content");
    ObjectNode text = content.addObject();
    text.put("type", "text");
    text.put("text", buildPrompt(filename, pages, retryAfterEmptyResponse, totalPages));

    for (RenderedOcrPage page : pages) {
      ObjectNode image = content.addObject();
      image.put("type", "image_url");
      ObjectNode imageUrl = image.putObject("image_url");
      imageUrl.put("url", page.sourceImageDataUrl());
      imageUrl.put("detail", "auto");
    }

    return root;
  }

  private JsonNode buildFieldCropTranscriptionPayload(
      String filename,
      List<FieldCropTranscriptionRequest> crops,
      LlmModelProfile profile
  ) {
    ObjectNode root = objectMapper.createObjectNode();
    root.put("model", blank(profile.model()) ? "Qwen3.6-35B-A3B" : profile.model());
    root.put("temperature", 0);
    root.put("max_tokens", Math.max(1024, Math.min(properties.maxTokens(), 4096)));
    root.put("stream", false);
    root.put("enable_thinking", profile.enableThinking());
    ObjectNode chatTemplateOptions = root.putObject("chat_template_kwargs");
    chatTemplateOptions.put("enable_thinking", profile.enableThinking());
    ObjectNode responseFormat = root.putObject("response_format");
    responseFormat.put("type", "json_object");

    ArrayNode messages = root.putArray("messages");
    ObjectNode systemMessage = messages.addObject();
    systemMessage.put("role", "system");
    systemMessage.put("content", "You are an exact field-crop transcription engine. Return valid JSON only.");

    ObjectNode userMessage = messages.addObject();
    userMessage.put("role", "user");
    ArrayNode content = userMessage.putArray("content");
    ObjectNode text = content.addObject();
    text.put("type", "text");
    text.put("text", buildFieldCropTranscriptionPrompt(filename, crops));

    for (FieldCropTranscriptionRequest crop : crops) {
      ObjectNode image = content.addObject();
      image.put("type", "image_url");
      ObjectNode imageUrl = image.putObject("image_url");
      imageUrl.put("url", crop.cropImageDataUrl());
      imageUrl.put("detail", "high");
    }

    return root;
  }

  private String buildFieldCropTranscriptionPrompt(String filename, List<FieldCropTranscriptionRequest> crops) {
    StringBuilder builder = new StringBuilder();
    builder.append("Transcribe only the visible applicant-filled value in each attached field crop.\n");
    builder.append("Do not correct, complete, normalize, or infer from common knowledge, addresses, phone/email patterns, or nearby context.\n");
    builder.append("Exclude smudged, crossed-out, erased, or correction marks from text. Treat those marks as not filled; do not use them to infer missing characters. Report visible rejected marks in excluded_marks.\n");
    builder.append("If a crossed-out, smudged, erased, or correction mark appears anywhere in or near a field value, before, between, over, or after normal characters, do not transcribe it as a letter, digit, punctuation, checkbox selection, or any part of the filled value. Preserve the adjacent normal filled characters exactly and report only the rejected mark in excluded_marks.\n");
    builder.append("For email, phone, fax, date, ID, passport, reference number, employment contract number, amount, and name crops, copy only visible characters exactly. Preserve visible punctuation and symbols such as @ . _ - / ( ) without inserting missing symbols by format rules.\n");
    builder.append("For email addresses, transcribe the local part and domain literally. Do not normalize unusual domain text: if the visible handwriting reads mial, hotmial, or yahooo, keep that exact sequence and do not change it to mail, hotmail, gmail, or yahoo.\n");
    builder.append("For long email crops, inspect characters immediately before and after @, hyphens, and dots; do not drop narrow visible letters such as l or i before a hyphen or dot.\n");
    builder.append("For employment contract numbers and other serial/reference numbers, distinguish uppercase F from T by visible strokes. A glyph with a vertical left stem plus top and middle horizontal strokes is F, not T. Inspect the full IDN year segment carefully and do not drop year digits such as 2026. Do not assume a prefix from document type or nearby printed text.\n");
    builder.append("Checkbox/option rows: return the selected option text only when there is a clear intentional selection mark such as a tick, check, cross, or filled box. If the checkbox area contains only a scribble, smudge, crossed-out mark, correction mark, erased ink, or ambiguous accidental ink, return text as an empty string, status as blank, and put the rejected mark in excluded_marks.\n");
    builder.append("For HK identity card no. crops, inspect the entire row area in the crop and output ONLY the result (no Yes/No prefix): if Yes is selected and an ID number is visible, return ONLY the exact visible ID number, for example \"Y432189(6)\" (do not include \"Yes\"); if Yes is selected but no ID number is visible, return \"未填写\"; if No is selected, return \"没有\".\n");
    builder.append("Preserve address number prefixes such as No, NO, no, N0 exactly as visible before digits. A visible NO88 must remain NO88; do not convert it to 168, 188, 88號, or any plausible street number.\n");
    builder.append("For address crops, read every visible applicant-filled address line inside the same field box from top to bottom; do not stop after the first line. Preserve lower lines with estate/building/floor/room text exactly when visible.\n");
    builder.append("For No/NO/no/N0 followed by digits in an address, copy every visible digit after No, including narrow trailing digits such as 3.\n");
    builder.append("If characters are ambiguous, keep the visible ambiguous characters and lower confidence instead of replacing them with a likely value.\n");
    builder.append("Return JSON only in this schema: {\"results\":[{\"page\":1,\"path\":\"field_path\",\"text\":\"exact visible value after excluding rejected marks\",\"address_number_fragment\":\"NO88 or blank\",\"excluded_marks\":[{\"text\":\"rejected mark\",\"reason\":\"smudged|crossed_out|erased|correction\"}],\"confidence\":0-100,\"status\":\"ok|unclear|blank\"}]}.\n");
    builder.append("source_file: ").append(filename == null || filename.isBlank() ? "uploaded-document" : filename).append('\n');
    builder.append("Crops are provided in this exact order:\n");
    for (int index = 0; index < crops.size(); index += 1) {
      FieldCropTranscriptionRequest crop = crops.get(index);
      builder.append(index + 1)
          .append(". page=").append(crop.page())
          .append(", path=").append(crop.path())
          .append(", label=").append(crop.label())
          .append(", current_first_pass_value=").append(crop.currentValue())
          .append('\n');
    }
    return builder.toString();
  }

  private List<FieldCropTranscriptionResult> parseFieldCropTranscriptionResults(
      JsonNode data,
      List<FieldCropTranscriptionRequest> requests
  ) {
    JsonNode results = data == null ? null : data.path("results");
    if (results == null || !results.isArray()) {
      return List.of();
    }
    List<FieldCropTranscriptionResult> values = new ArrayList<>();
    for (int index = 0; index < Math.min(results.size(), requests.size()); index += 1) {
      JsonNode item = results.get(index);
      FieldCropTranscriptionRequest request = requests.get(index);
      values.add(new FieldCropTranscriptionResult(
          item.path("page").asInt(request.page()),
          item.path("path").asText(request.path()),
          firstExistingText(item, "text", "transcription", "value"),
          firstExistingText(item, "address_number_fragment", "number_fragment", "fragment"),
          normalizeConfidence(item.path("confidence").asDouble(0)),
          item.path("status").asText("ok"),
          item.path("excluded_marks")
      ));
    }
    return List.copyOf(values);
  }

  private ExtractionResponse sendExtractionRequest(JsonNode payload, LlmModelProfile profile) throws IOException, InterruptedException {
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(trimTrailingSlash(profile.baseUrl()) + "/chat/completions"))
        .version(HttpClient.Version.HTTP_1_1)
        .timeout(Duration.ofSeconds(Math.max(10, properties.timeoutSeconds())))
        .header("Authorization", "Bearer " + profile.apiKey())
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload), StandardCharsets.UTF_8))
        .build();

    HttpResponse<String> response = sendWithTransientTransportRetry(request);
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new IOException("LLM HTTP " + response.statusCode() + ": " + truncate(response.body(), 600));
    }

    String rawText = extractMessageContent(response.body());
    JsonNode data = readModelJson(extractJson(rawText));
    return new ExtractionResponse(data, rawText);
  }

  private HttpResponse<String> sendWithTransientTransportRetry(HttpRequest request)
      throws IOException, InterruptedException {
    int maxAttempts = 3;
    for (int attempt = 1; attempt <= maxAttempts; attempt += 1) {
      try {
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (!isTransientHttpFailure(response) || attempt >= maxAttempts) {
          return response;
        }
      } catch (IOException exception) {
        if (attempt >= maxAttempts || !isTransientTransportFailure(exception)) {
          throw exception;
        }
      }
      Thread.sleep(250L * attempt);
    }
    throw new IOException("LLM request failed.");
  }

  private boolean isTransientHttpFailure(HttpResponse<String> response) {
    int statusCode = response.statusCode();
    if (statusCode != 502 && statusCode != 503 && statusCode != 504) {
      return false;
    }
    String body = response.body() == null ? "" : response.body().toLowerCase(Locale.ROOT);
    return body.contains("upstream_request_failed")
        || body.contains("upstream request failed")
        || body.contains("system_cpu_overloaded")
        || body.contains("temporarily unavailable")
        || body.contains("bad gateway")
        || body.contains("service unavailable")
        || body.isBlank();
  }

  private boolean isTransientTransportFailure(IOException exception) {
    Throwable current = exception;
    while (current != null) {
      String message = current.getMessage();
      if (message != null) {
        String normalized = message.toLowerCase(Locale.ROOT);
        if (normalized.contains("header parser received no bytes")
            || normalized.contains("connection reset")
            || normalized.contains("connection closed")
            || normalized.contains("closed before")
            || normalized.contains("unexpected end of")
            || normalized.contains("eof")) {
          return true;
        }
      }
      current = current.getCause();
    }
    return false;
  }

  private JsonNode readModelJson(String json) throws IOException {
    try {
      return objectMapper.readTree(json);
    } catch (JsonProcessingException strictException) {
      try {
        return lenientObjectMapper.readTree(json);
      } catch (JsonProcessingException lenientException) {
        throw new IOException(lenientException.getOriginalMessage(), lenientException);
      }
    }
  }

  private String buildPrompt(
      String filename,
      List<RenderedOcrPage> pages,
      boolean retryAfterEmptyResponse,
      int totalPages
  ) {
    StringBuilder builder = new StringBuilder();
    builder.append("Analyze these full-page form images directly and output one JSON object.\n");
    if (pages.size() == 1) {
      builder.append("The attached image is page_").append(pages.get(0).page()).append(" of ").append(totalPages)
          .append(". Return only this page's page_").append(pages.get(0).page()).append(" fields, plus source_file, total_pages, _confidence, and _field_evidence.\n");
    }
    builder.append("Do not use a predefined field list, manual annotations, template coordinate boxes, ROI crops, or OCR output.\n");
    builder.append("Find printed field labels, filling areas, handwriting, typed values, checked boxes, signatures, and photo/upload areas by visual reasoning.\n");
    builder.append("Return a compact result. Prioritize applicant-filled text, selected checkboxes, signatures, photos, and major visible blank fields. Do not enumerate every empty grid cell, every unchecked option, template instruction, explanatory paragraph, barcode, or page footer.\n");
    builder.append("Use nearby printed labels as JSON keys, normalized to lower_snake_case English where possible. Preserve Chinese or English field values exactly when visible.\n");
    builder.append("Rules:\n");
    builder.append("- Include source_file and total_pages at the top level.\n");
    builder.append("- Group page content under page_1, page_2, etc.\n");
    builder.append("- Keep page field values as plain applicant-filled values or null. Also include a top-level _confidence object mirroring page/field paths with integer confidence scores from 0 to 100.\n");
    builder.append("- Also include a top-level _field_evidence object mirroring page/field paths. MANDATORY: for EVERY non-null field value under page_N, you MUST provide a matching _field_evidence.page_N.<exact_field_path> entry with label and value_bbox as normalized {x,y,width,height} coordinates of that filled area on the page image. A non-null field without a value_bbox cannot produce a field screenshot and is treated as an incomplete extraction; do not omit it.\n");
    builder.append("- Before returning, self-check: for every non-null field value you output under each page_N, verify a matching _field_evidence.page_N.<exact_field_path>.value_bbox exists; if any is missing, add it before finalizing.\n");
    builder.append("- _field_evidence.page_N must be keyed by exact page_N field paths. Never put label/value_bbox directly under _field_evidence.page_N as one whole-page evidence object.\n");
    builder.append("- Example: {\"page_2\":{\"present_address\":\"Flat 7\"},\"_field_evidence\":{\"page_2\":{\"present_address\":{\"label\":\"Present address\",\"value_bbox\":{\"x\":0.20,\"y\":0.10,\"width\":0.55,\"height\":0.09}}}}}.\n");
    builder.append("- For filled handwritten, typed, or signature text, include char_confidences only for ambiguous, low-confidence, smudged, crossed-out, erased, or correction characters: [{char,index,confidence,status,reason,bbox}], where bbox is normalized inside the field value_bbox. Do not list every character when the value is clear; field value_bbox is enough.\n");
    builder.append("- Each leaf field value must be the applicant-filled value; if a major visible field is blank, use null.\n");
    builder.append("- For handwritten or typed applicant-filled values, copy only visible characters exactly as written. Do not correct, complete, normalize, or infer handwritten values from common knowledge, official addresses, names, phone/email patterns, or surrounding context. 禁止纠正、补全、规范化、按常识推断手写值. If a character or digit is unclear, keep the ambiguous visible text as-is with low confidence and char_confidences; do not replace it with a plausible value.\n");
    builder.append("- For email addresses, copy the visible local part and domain literally. Do not normalize or correct unusual domain text: visible mial, hotmial, yahooo, or similar nonstandard sequences must remain exactly as written, not mail, hotmail, gmail, yahoo, or another common provider.\n");
    builder.append("- For employment contract numbers and other serial/reference numbers, transcribe visible uppercase letters and digits exactly. Carefully distinguish F from T by strokes: vertical left stem plus top and middle horizontal strokes means F, not T. Do not assume the prefix from the form type or nearby printed contract text.\n");
    builder.append("- For Hong Kong FDH forms ID 988A and ID 988B, always extract the handwritten employment contract number / D.H. Contract No. from Undertaking paragraphs when visible, even when it is embedded inside Chinese or English paragraph text. Use the key employment_contract_no for this filled value.\n");
    builder.append("- If smudged, crossed-out, erased, or correction marks are mixed into a filled value, exclude those marks from the field value and treat those marks as not filled. Do not use them to infer missing characters. Put them only in _field_evidence.page_N.<field>.excluded_marks as [{text,reason,bbox,index,length}] when visible.\n");
    builder.append("- If a crossed-out, smudged, erased, or correction mark appears anywhere in or near a field value, before, between, over, or after normal characters, do not transcribe it as a letter, digit, punctuation, checkbox selection, or any part of the filled value. Preserve the adjacent normal filled characters exactly and record only the rejected mark in excluded_marks.\n");
    builder.append("- If a filling area contains only smudges, crossed-out text, erased text, or correction marks, return null for that field and record the rejected marks in excluded_marks. If both an old crossed-out value and a newer clear value are visible, return only the newer clear intended value.\n");
    builder.append("- For checkbox/option rows, count a selection only when the box has a clear intentional tick/check/cross/fill. A scribble, smudge, crossed-out mark, correction mark, erased ink, or ambiguous accidental ink near or inside a checkbox must be treated as not selected and not filled; return null and record it in excluded_marks.\n");
    builder.append("- Do not omit compact selected Yes/No option groups near filled fields, such as Separate servant room / 獨立工人房 / 独立工人房, and average monthly household income no less than HK$15,000. Return the selected option text exactly as visible, for example 有, Yes, 沒有, or No.\n");
    builder.append("- For table sections such as Particulars of household members, if any row contains applicant-filled handwriting or typed text, return every visible cell in that row, including name, year of birth, relationship with the employer, and HK identity card no. Do not collapse the table to only one column.\n");
    builder.append("- For employment history / previous employment table sections (such as Particulars of current and previous employment), if any row contains applicant-filled entries, return EACH employment period as separate fields, top to bottom: employer_1_name, employer_1_address, employer_1_period_from, employer_1_period_to, then employer_2_name, employer_2_address, employer_2_period_from, employer_2_period_to, and so on (one set of four fields per period, N = 1, 2, ...). Do NOT collapse multiple periods into one field or omit any period that has visible content.\n");
    builder.append("- If the page has no applicant-filled handwriting, typed values, selected checkboxes, signatures, photos, or other applicant input, return {\"page_N\":{\"no_applicant_input\":true}} for that page. In this case _field_evidence is not required for that page.\n");
    builder.append("- Ignore template instructions, empty borders, empty lines, barcodes, page numbers, and smudges/corrections that are not intended field values.\n");
    builder.append("- For checkbox option groups on the same row or in the same question, such as 有/没有, Yes/No, Male/Female, Married/Single, do not create one boolean field per option. Create one field named by the row/question label and set its value to the selected option text, for example {\"pillow\":\"没有\"}, {\"water_supply\":\"有\"}, {\"sex\":\"Female\"}. Use null only when no option in that group is selected.\n");
    builder.append("- For HK identity card no. Yes/No rows, inspect the whole row visually and output ONLY the result under hk_identity_card_no (no Yes/No prefix): if Yes is selected and a handwritten HK identity card number is visible on the same row, return ONLY the exact visible ID number, for example \"Y432189(6)\" (do not include \"Yes\"); if Yes is selected but no ID number is written, return \"未填写\"; if No is selected, return \"没有\".\n");
    builder.append("- Use true/false only for a standalone checkbox whose field label itself is the option statement, and name that field with checked/is_selected when the value is a checkbox state.\n");
    builder.append("- For handwritten quantity/count fill-ins embedded in printed labels, such as \"3名成人\", \"1名小孩\", \"0家庭成员需要经常照料\", set the field value to the applicant-written number (3, 1, 0). Do not output 1/0 as a presence flag unless the field itself is a standalone checkbox state.\n");
    builder.append("- For signatures, transcribe the visible handwritten signature text as the field value when readable. Do not return present for signatures. If a signature mark exists but the text cannot be read, use \"illegible_signature\"; otherwise use null.\n");
    builder.append("- For photos or pasted image areas, return \"present\" when an actual photo exists; otherwise use null.\n");
    builder.append("- Do not invent fields that are not visible on the page.\n");
    if (retryAfterEmptyResponse) {
      builder.append("The previous response was unusable because it was empty. Re-read the page and return compact visible applicant-fillable field labels and applicant-filled values. For every non-null handwritten, typed, checked, or signature value, add a matching _field_evidence.page_N.<exact_field_path>.value_bbox for that filled area when it is clear.\n");
    }
    builder.append("source_file: ").append(filename == null || filename.isBlank() ? "uploaded-document" : filename).append('\n');
    builder.append("total_pages: ").append(totalPages).append('\n');
    return builder.toString();
  }

  private void mergeMetadataPage(ObjectNode target, JsonNode sourceMetadata, String pageKey) {
    JsonNode pageMetadata = sourceMetadata.path(pageKey);
    if (!pageMetadata.isMissingNode() && !pageMetadata.isNull()) {
      target.set(pageKey, pageMetadata);
    } else if (sourceMetadata.isNumber() || sourceMetadata.isTextual() || sourceMetadata.isBoolean()) {
      target.set(pageKey, sourceMetadata);
    } else {
      target.set(pageKey, objectMapper.createObjectNode());
    }
  }

  private boolean isEmptyExtraction(JsonNode data, List<RenderedOcrPage> pages) {
    if (data == null || data.isMissingNode() || data.isNull() || data.isEmpty()) {
      return true;
    }
    for (RenderedOcrPage page : pages) {
      JsonNode pageData = data.path("page_" + page.page());
      if (pageData.isObject() && pageData.size() > 0) {
        return false;
      }
      if (pageData.isArray() && !pageData.isEmpty()) {
        return false;
      }
    }
    return true;
  }

  private boolean isNoApplicantInputPage(JsonNode data, RenderedOcrPage page) {
    if (data == null || data.isMissingNode() || data.isNull()) {
      return false;
    }
    String pageKey = "page_" + page.page();
    return data.path(pageKey).path("no_applicant_input").asBoolean(false)
        || data.path(pageKey).path("noApplicantInput").asBoolean(false)
        || data.path("no_applicant_input").asBoolean(false)
        || data.path("noApplicantInput").asBoolean(false);
  }

  private long elapsedMillisSince(long startedAtNanos) {
    return Math.max(0, Duration.ofNanos(System.nanoTime() - startedAtNanos).toMillis());
  }

  private String extractMessageContent(String responseBody) throws IOException {
    JsonNode root = objectMapper.readTree(responseBody);
    JsonNode message = root.at("/choices/0/message");
    JsonNode content = message.path("content");
    if (!content.isMissingNode() && !content.isNull() && !content.asText().isBlank()) {
      return content.asText();
    }

    for (String fallbackField : List.of("reasoning_content", "reasoning")) {
      String extracted = extractJsonIfPresent(message.path(fallbackField).asText(""));
      if (!blank(extracted)) {
        return extracted;
      }
    }
    throw new IOException("LLM response did not include JSON content: " + truncate(responseBody, 600));
  }

  private String extractJsonIfPresent(String text) {
    try {
      return extractJson(text);
    } catch (IOException exception) {
      return "";
    }
  }

  private String extractJson(String text) throws IOException {
    String trimmed = text == null ? "" : text.trim();
    int objectStart = trimmed.indexOf('{');
    int arrayStart = trimmed.indexOf('[');
    int start;
    char endChar;
    if (objectStart >= 0 && (arrayStart < 0 || objectStart < arrayStart)) {
      start = objectStart;
      endChar = '}';
    } else if (arrayStart >= 0) {
      start = arrayStart;
      endChar = ']';
    } else {
      throw new IOException("LLM response did not contain JSON: " + truncate(trimmed, 600));
    }

    int end = trimmed.lastIndexOf(endChar);
    if (end < start) {
      throw new IOException("LLM response contained incomplete JSON: " + truncate(trimmed, 600));
    }
    return trimmed.substring(start, end + 1);
  }

  private String trimTrailingSlash(String value) {
    if (blank(value)) {
      return DEFAULT_BASE_URL;
    }
    return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
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

  private JsonNode firstExisting(JsonNode node, String... keys) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return com.fasterxml.jackson.databind.node.MissingNode.getInstance();
    }
    for (String key : keys) {
      JsonNode value = node.path(key);
      if (!value.isMissingNode() && !value.isNull()) {
        return value;
      }
    }
    return com.fasterxml.jackson.databind.node.MissingNode.getInstance();
  }

  private boolean booleanValue(JsonNode node) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return false;
    }
    if (node.isBoolean()) {
      return node.asBoolean(false);
    }
    String text = node.asText("").trim().toLowerCase(Locale.ROOT);
    return text.equals("true")
        || text.equals("yes")
        || text.equals("selected")
        || text.equals("checked")
        || text.equals("tick")
        || text.equals("ticked");
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

  private double normalizeConfidence(double value) {
    double normalized = value <= 1 ? value * 100 : value;
    return Math.max(0, Math.min(100, Math.round(normalized)));
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private String truncate(String value, int maxLength) {
    if (value == null || value.length() <= maxLength) {
      return value;
    }
    return value.substring(0, maxLength) + "...";
  }

  private LlmModelProfile defaultProfile() {
    return new LlmModelProfile(
        LlmModelRegistry.DEFAULT_MODEL_ID,
        LlmModelRegistry.DEFAULT_MODEL_LABEL,
        blank(properties.model()) ? "Qwen3.6-35B-A3B" : properties.model(),
        "OpenAI-compatible local gateway",
        blank(properties.baseUrl()) ? DEFAULT_BASE_URL : properties.baseUrl(),
        properties.apiKey(),
        false,
        true,
        blank(properties.apiKey()) ? "缺少 LLM_API_KEY" : ""
    );
  }

  private record ExtractionResponse(JsonNode data, String rawText) {}

  private record PageFuture(int page, Future<PageExtraction> future) {}

  private record PageExtraction(int page, ExtractionResponse response) {}
}
