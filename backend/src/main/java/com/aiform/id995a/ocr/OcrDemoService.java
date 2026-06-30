package com.aiform.id995a.ocr;

import com.aiform.id995a.llm.ExtractionProgressListener;
import com.aiform.id995a.llm.FieldRegionLocationGateway;
import com.aiform.id995a.llm.LlmModelProfile;
import com.aiform.id995a.llm.LlmModelRegistry;
import com.aiform.id995a.llm.MissingFieldRegion;
import com.aiform.id995a.llm.NormalizedBbox;
import com.aiform.id995a.llm.StructuredExtractionGateway;
import com.aiform.id995a.llm.StructuredExtractionResult;
import com.aiform.id995a.review.EngineStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OcrDemoService {

  private static final Logger log = LoggerFactory.getLogger(OcrDemoService.class);

  private final BaiduOcrPageRenderer pageRenderer;
  private final StructuredExtractionGateway structuredExtractionGateway;
  private final StructuredFieldEvidenceService structuredFieldEvidenceService;
  private final AddressFieldCropRefinementService addressFieldCropRefinementService;
  private final GeneralFieldCropRefinementService generalFieldCropRefinementService;
  private final SelectionFieldCropRefinementService selectionFieldCropRefinementService;
  private final DeclarationFooterFieldRefinementService declarationFooterFieldRefinementService;
  private final SmudgedFieldValueFilterService smudgedFieldValueFilterService;
  private final TemplateDetectionService templateDetectionService;
  private final TemplateClassificationLogService templateClassificationLogService;
  private final LlmModelRegistry llmModelRegistry;
  private final FieldRegionLocationGateway fieldRegionLocationGateway;

  public OcrDemoService(
      BaiduOcrPageRenderer pageRenderer,
      StructuredExtractionGateway structuredExtractionGateway,
      StructuredFieldEvidenceService structuredFieldEvidenceService,
      AddressFieldCropRefinementService addressFieldCropRefinementService,
      GeneralFieldCropRefinementService generalFieldCropRefinementService,
      SelectionFieldCropRefinementService selectionFieldCropRefinementService,
      DeclarationFooterFieldRefinementService declarationFooterFieldRefinementService,
      SmudgedFieldValueFilterService smudgedFieldValueFilterService,
      TemplateDetectionService templateDetectionService,
      TemplateClassificationLogService templateClassificationLogService,
      LlmModelRegistry llmModelRegistry,
      FieldRegionLocationGateway fieldRegionLocationGateway
  ) {
    this.pageRenderer = pageRenderer;
    this.structuredExtractionGateway = structuredExtractionGateway;
    this.structuredFieldEvidenceService = structuredFieldEvidenceService;
    this.addressFieldCropRefinementService = addressFieldCropRefinementService;
    this.generalFieldCropRefinementService = generalFieldCropRefinementService;
    this.selectionFieldCropRefinementService = selectionFieldCropRefinementService;
    this.declarationFooterFieldRefinementService = declarationFooterFieldRefinementService;
    this.smudgedFieldValueFilterService = smudgedFieldValueFilterService;
    this.templateDetectionService = templateDetectionService;
    this.templateClassificationLogService = templateClassificationLogService;
    this.llmModelRegistry = llmModelRegistry;
    this.fieldRegionLocationGateway = fieldRegionLocationGateway;
  }

  public OcrDemoResponse recognize(String filename, String contentType, byte[] fileBytes) throws IOException {
    return recognize(filename, contentType, fileBytes, null);
  }

  public OcrDemoResponse recognize(String filename, String contentType, byte[] fileBytes, String modelId) throws IOException {
    List<RenderedOcrPage> pages = pageRenderer.render(filename, contentType, fileBytes);
    DocumentTemplate template = templateDetectionService.detect(filename, contentType, fileBytes, pages);
    return recognizeRendered(normalizeFilename(filename), pages, ExtractionProgressListener.NOOP, modelId, template);
  }

  public OcrDemoResponse recognizeRendered(
      String filename,
      List<RenderedOcrPage> pages,
      ExtractionProgressListener progressListener
  ) throws IOException {
    return recognizeRendered(filename, pages, progressListener, null);
  }

  public OcrDemoResponse recognizeRendered(
      String filename,
      List<RenderedOcrPage> pages,
      ExtractionProgressListener progressListener,
      String modelId
  ) throws IOException {
    DocumentTemplate template = templateDetectionService.detect(filename, "", null, pages);
    return recognizeRendered(filename, pages, progressListener, modelId, template);
  }

  public OcrDemoResponse recognizeRendered(
      String filename,
      List<RenderedOcrPage> pages,
      ExtractionProgressListener progressListener,
      String modelId,
      DocumentTemplate template
  ) throws IOException {
    return recognizeRenderedInternal(filename, pages, progressListener, modelId, template, true, true);
  }

  public OcrDemoResponse recognizeRenderedForFdhReview(
      String filename,
      List<RenderedOcrPage> pages,
      ExtractionProgressListener progressListener,
      String modelId,
      DocumentTemplate template
  ) throws IOException {
    return recognizeRenderedForFdhReview(filename, pages, progressListener, modelId, template, true);
  }

  public OcrDemoResponse recognizeRenderedForFdhReview(
      String filename,
      List<RenderedOcrPage> pages,
      ExtractionProgressListener progressListener,
      String modelId,
      DocumentTemplate template,
      boolean recoverMissingFieldSnapshots
  ) throws IOException {
    return recognizeRenderedInternal(filename, pages, progressListener, modelId, template, false, recoverMissingFieldSnapshots);
  }

  private OcrDemoResponse recognizeRenderedInternal(
      String filename,
      List<RenderedOcrPage> pages,
      ExtractionProgressListener progressListener,
      String modelId,
      DocumentTemplate template,
      boolean refineFieldCrops,
      boolean recoverMissingFieldSnapshots
  ) throws IOException {
    String normalizedFilename = normalizeFilename(filename);
    List<RenderedOcrPage> safePages = pages == null ? List.of() : pages;
    DocumentTemplate resolvedTemplate = template == null
        ? templateDetectionService.detect(normalizedFilename, "", null, safePages)
        : template;
    LlmModelProfile modelProfile = llmModelRegistry.resolve(modelId);
    ExtractionProgressListener listener = progressListener == null ? ExtractionProgressListener.NOOP : progressListener;
    StructuredExtractionResult extraction = refineFieldCrops
        ? structuredExtractionGateway.extract(normalizedFilename, safePages, listener, modelProfile)
        : structuredExtractionGateway.extractAllowingPartialPages(normalizedFilename, safePages, listener, modelProfile);
    JsonNode structuredData = extraction.data();
    List<String> statusMessages = new ArrayList<>();
    statusMessages.add("Rendered " + safePages.size() + " page snapshot(s) and extracted structured JSON with " + modelProfile.label() + ".");
    statusMessages.add("Rendered page snapshots were sent directly to the multimodal LLM to find fields and filled regions; no preset field list or manual template coordinate boxes were used.");
    statusMessages.add("Detected document template: " + resolvedTemplate.templateId() + " (" + resolvedTemplate.matchSource() + ").");

    if (refineFieldCrops) {
      listener.postProcessingStep("address_crop_review", "Reviewing address fields.", 72);
      AddressFieldCropRefinementResult refinedExtraction = addressFieldCropRefinementService.refine(
          normalizedFilename,
          structuredData,
          safePages,
          modelProfile
      );
      listener.postProcessingStep("field_crop_review", "Reviewing ordinary and symbol-sensitive fields.", 80);
      GeneralFieldCropRefinementResult refinedGeneralFields = generalFieldCropRefinementService.refine(
          normalizedFilename,
          refinedExtraction.data(),
          safePages,
          modelProfile
      );
      listener.postProcessingStep("selection_crop_review", "Reviewing checkbox and declaration fields.", 88);
      SelectionFieldCropRefinementResult refinedSelections = selectionFieldCropRefinementService.refine(
          normalizedFilename,
          refinedGeneralFields.data(),
          safePages,
          modelProfile,
          resolvedTemplate
      );
      listener.postProcessingStep("declaration_footer_review", "Restoring declaration footer fields.", 92);
      DeclarationFooterFieldRefinementResult refinedFooterFields = declarationFooterFieldRefinementService.refine(
          normalizedFilename,
          refinedSelections.data(),
          safePages,
          modelProfile
      );
      listener.postProcessingStep("smudge_filter", "Filtering smudges, erasures, and correction marks.", 96);
      SmudgedFieldValueFilterResult filteredExtraction = smudgedFieldValueFilterService.filter(refinedFooterFields.data());
      structuredData = filteredExtraction.data();
      statusMessages.add("Address fields with clear value regions are second-pass transcribed from their field crop; updated fields: " + refinedExtraction.updated() + " / " + refinedExtraction.attempted() + ".");
      statusMessages.add("Ordinary text and symbol-sensitive fields with clear value regions are crop-reviewed by field type; updated fields: " + refinedGeneralFields.updated() + " / " + refinedGeneralFields.attempted() + ".");
      statusMessages.add("Checkbox and declaration fields with clear value regions are second-pass reviewed from their field crop; updated fields: " + refinedSelections.updated() + " / " + refinedSelections.attempted() + ".");
      statusMessages.add("Declaration page checkbox/date/signature fields are restored from fixed page crops when the page model misses them; updated fields: " + refinedFooterFields.updated() + " / " + refinedFooterFields.attempted() + ".");
      statusMessages.add("Smudged, crossed-out, erased, or correction marks mixed into field values are filtered as not filled; filtered fields: " + filteredExtraction.filtered() + ".");
    } else {
      listener.postProcessingStep("selection_geometry_restore", "Restoring fixed template checkbox fields.", 94);
      SelectionFieldCropRefinementResult restoredSelections = selectionFieldCropRefinementService.restoreTemplateSelections(
          normalizedFilename,
          structuredData,
          safePages,
          modelProfile,
          resolvedTemplate
      );
      structuredData = restoredSelections.data();
      statusMessages.add("ID 988A application type checkboxes are restored by the application-type LLM region recognizer, with fixed geometry as fallback; updated fields: " + restoredSelections.updated() + ".");
      statusMessages.add("FDH review fast path skipped second-pass field crop transcription to keep multi-file material review responsive.");
    }

    listener.postProcessingStep(
        "field_evidence",
        recoverMissingFieldSnapshots ? "Building field snapshots and display results." : "Building field display results.",
        98
    );
    JsonNode finalStructuredData = withTemplateMetadata(structuredData, resolvedTemplate);
    templateClassificationLogService.record(normalizedFilename, resolvedTemplate);
    Map<Integer, List<StructuredFieldDetail>> fieldDetailsByPage =
        structuredFieldEvidenceService.buildFieldDetails(finalStructuredData, safePages);
    if (!refineFieldCrops && recoverMissingFieldSnapshots) {
      FieldBboxRefill refill = refillMissingFieldBboxes(
          normalizedFilename, finalStructuredData, fieldDetailsByPage, safePages, modelProfile, listener
      );
      if (refill.refilled() > 0) {
        finalStructuredData = refill.structuredData();
        Map<Integer, List<StructuredFieldDetail>> rebuilt =
            structuredFieldEvidenceService.buildFieldDetails(finalStructuredData, safePages);
        fieldDetailsByPage.clear();
        fieldDetailsByPage.putAll(rebuilt);
        statusMessages.add("Located " + refill.refilled() + " missing field region(s) with the multimodal LLM to recover field snapshots.");
      }
    } else if (!refineFieldCrops) {
      statusMessages.add("Field snapshot recovery was disabled for this review mode; recognized values are returned without crop images.");
    }
    List<OcrPage> responsePages = safePages.stream()
        .map(page -> new OcrPage(
            page.page(),
            page.sourceImageDataUrl(),
            page.imageWidth(),
            page.imageHeight(),
            "",
            List.of(),
            List.of(),
            List.of(),
            fieldDetailsByPage.getOrDefault(page.page(), List.of())
        ))
        .toList();
    EngineStatus status = new EngineStatus(
        modelProfile.label(),
        false,
        List.copyOf(statusMessages)
    );
    return new OcrDemoResponse(
        normalizedFilename,
        extraction.model(),
        responsePages.size(),
        responsePages,
        List.of(),
        status,
        finalStructuredData,
        extraction.rawText()
    );
  }

  private JsonNode withTemplateMetadata(JsonNode data, DocumentTemplate template) {
    ObjectNode root = data != null && data.isObject()
        ? data.deepCopy()
        : JsonNodeFactory.instance.objectNode();
    ObjectNode metadata = root.putObject("_template");
    metadata.put("template_id", template.templateId());
    metadata.put("footer_id", template.footerId());
    metadata.put("page_count", template.pageCount());
    metadata.put("confidence", template.confidence());
    metadata.put("match_source", template.matchSource());
    metadata.put("structure_hash", template.structureHash());
    return root;
  }

  /**
   * FDH 快速路径：对「有值但缺 value_bbox（无法裁剪截图）」的字段，调多模态 LLM 视觉定位补 bbox，
   * 写回 _field_evidence 后由调用方重新 buildFieldDetails 裁出真实 snapshot。不使用固定坐标。
   */
  private FieldBboxRefill refillMissingFieldBboxes(
      String filename,
      JsonNode structuredData,
      Map<Integer, List<StructuredFieldDetail>> detailsByPage,
      List<RenderedOcrPage> pages,
      LlmModelProfile profile,
      ExtractionProgressListener listener
  ) {
    List<MissingFieldRegion> missing = new ArrayList<>();
    for (Map.Entry<Integer, List<StructuredFieldDetail>> entry : detailsByPage.entrySet()) {
      for (StructuredFieldDetail detail : entry.getValue()) {
        if (detail.displayValue() == null || detail.displayValue().isBlank()) {
          continue;
        }
        if (detail.bbox() != null && !detail.bbox().isEmpty()) {
          continue;
        }
        missing.add(new MissingFieldRegion(detail.page(), detail.path(), detail.label(), detail.displayValue()));
      }
    }
    if (missing.isEmpty()) {
      return new FieldBboxRefill(structuredData, 0);
    }
    if (log.isInfoEnabled()) {
      StringBuilder names = new StringBuilder();
      for (MissingFieldRegion region : missing) {
        if (names.length() > 0) {
          names.append(", ");
        }
        names.append("page_").append(region.page()).append(".").append(region.path());
      }
      log.info("Field-bbox refill: {} missing-bbox field(s): [{}]", missing.size(), names);
    }
    listener.postProcessingStep("field_bbox_location", "Locating missing field regions with LLM.", 95);
    Map<String, NormalizedBbox> located;
    try {
      located = fieldRegionLocationGateway.locateMissingFieldBboxes(filename, pages, missing, profile);
    } catch (IOException exception) {
      return new FieldBboxRefill(structuredData, 0);
    }
    if (located == null || located.isEmpty()) {
      log.info("Field-bbox refill: LLM located 0 of {} missing field(s).", missing.size());
      return new FieldBboxRefill(structuredData, 0);
    }
    log.info("Field-bbox refill: LLM located {} of {} missing field(s).", located.size(), missing.size());
    ObjectNode mutable = structuredData != null && structuredData.isObject()
        ? structuredData.deepCopy()
        : JsonNodeFactory.instance.objectNode();
    ObjectNode evidenceRoot = ensureChild(mutable, "_field_evidence");
    int refilled = 0;
    for (MissingFieldRegion field : missing) {
      NormalizedBbox bbox = located.get(field.page() + "|" + field.path());
      if (bbox == null) {
        continue;
      }
      ObjectNode pageEvidence = ensureChild(evidenceRoot, "page_" + field.page());
      ObjectNode fieldEvidence = ensureChild(pageEvidence, field.path());
      if (!fieldEvidence.has("label") && field.label() != null && !field.label().isBlank()) {
        fieldEvidence.put("label", field.label());
      }
      ObjectNode valueBbox = fieldEvidence.putObject("value_bbox");
      valueBbox.put("x", bbox.x());
      valueBbox.put("y", bbox.y());
      valueBbox.put("width", bbox.width());
      valueBbox.put("height", bbox.height());
      refilled += 1;
    }
    log.info("Field-bbox refill: refilled {} field(s) with bbox.", refilled);
    return new FieldBboxRefill(mutable, refilled);
  }

  private ObjectNode ensureChild(ObjectNode parent, String key) {
    JsonNode existing = parent.get(key);
    if (existing instanceof ObjectNode objectNode) {
      return objectNode;
    }
    ObjectNode child = JsonNodeFactory.instance.objectNode();
    parent.set(key, child);
    return child;
  }

  private record FieldBboxRefill(JsonNode structuredData, int refilled) {}

  String normalizeFilename(String filename) {
    return filename == null || filename.isBlank() ? "uploaded-document" : filename;
  }
}
