package com.aiform.id995a.ocr;

import com.aiform.id995a.llm.ExtractionProgressListener;
import com.aiform.id995a.llm.LlmModelProfile;
import com.aiform.id995a.llm.LlmModelRegistry;
import com.aiform.id995a.llm.StructuredExtractionGateway;
import com.aiform.id995a.llm.StructuredExtractionResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ParallelStructuredExtractionService {

  private static final Logger log = LoggerFactory.getLogger(ParallelStructuredExtractionService.class);

  private final StructuredExtractionGateway structuredExtractionGateway;
  private final LlmModelRegistry llmModelRegistry;
  private final ParallelFieldComparisonService comparisonService;
  private final AddressFieldCropRefinementService addressFieldCropRefinementService;
  private final GeneralFieldCropRefinementService generalFieldCropRefinementService;
  private final SelectionFieldCropRefinementService selectionFieldCropRefinementService;
  private final DeclarationFooterFieldRefinementService declarationFooterFieldRefinementService;
  private final SmudgedFieldValueFilterService smudgedFieldValueFilterService;

  public ParallelStructuredExtractionService(
      StructuredExtractionGateway structuredExtractionGateway,
      LlmModelRegistry llmModelRegistry,
      ParallelFieldComparisonService comparisonService,
      AddressFieldCropRefinementService addressFieldCropRefinementService,
      GeneralFieldCropRefinementService generalFieldCropRefinementService,
      SelectionFieldCropRefinementService selectionFieldCropRefinementService,
      DeclarationFooterFieldRefinementService declarationFooterFieldRefinementService,
      SmudgedFieldValueFilterService smudgedFieldValueFilterService
  ) {
    this.structuredExtractionGateway = structuredExtractionGateway;
    this.llmModelRegistry = llmModelRegistry;
    this.comparisonService = comparisonService;
    this.addressFieldCropRefinementService = addressFieldCropRefinementService;
    this.generalFieldCropRefinementService = generalFieldCropRefinementService;
    this.selectionFieldCropRefinementService = selectionFieldCropRefinementService;
    this.declarationFooterFieldRefinementService = declarationFooterFieldRefinementService;
    this.smudgedFieldValueFilterService = smudgedFieldValueFilterService;
  }

  public CompletableFuture<StructuredExtractionResult> start(
      String filename,
      List<RenderedOcrPage> pages,
      boolean refineFieldCrops,
      DocumentTemplate template
  ) {
    Optional<LlmModelProfile> profile = llmModelRegistry.parallelRecognitionProfile();
    if (profile.isEmpty()) {
      return CompletableFuture.completedFuture(null);
    }
    LlmModelProfile parallelProfile = profile.get();
    List<RenderedOcrPage> safePages = pages == null ? List.of() : List.copyOf(pages);
    return CompletableFuture.supplyAsync(() -> {
      try {
        StructuredExtractionResult extraction = refineFieldCrops
            ? structuredExtractionGateway.extract(filename, safePages, ExtractionProgressListener.NOOP, parallelProfile)
            : structuredExtractionGateway.extractAllowingPartialPages(filename, safePages, ExtractionProgressListener.NOOP, parallelProfile);
        JsonNode refinedData = applyPostProcessing(
            filename,
            extraction.data(),
            safePages,
            parallelProfile,
            template,
            refineFieldCrops
        );
        return new StructuredExtractionResult(refinedData, extraction.rawText(), extraction.model());
      } catch (IOException exception) {
        throw new ParallelExtractionException(exception);
      }
    });
  }

  private JsonNode applyPostProcessing(
      String filename,
      JsonNode structuredData,
      List<RenderedOcrPage> pages,
      LlmModelProfile modelProfile,
      DocumentTemplate template,
      boolean refineFieldCrops
  ) throws IOException {
    if (structuredData == null) {
      return null;
    }
    if (refineFieldCrops) {
      AddressFieldCropRefinementResult refinedAddresses = addressFieldCropRefinementService.refine(
          filename,
          structuredData,
          pages,
          modelProfile
      );
      GeneralFieldCropRefinementResult refinedGeneralFields = generalFieldCropRefinementService.refine(
          filename,
          refinedAddresses.data(),
          pages,
          modelProfile
      );
      SelectionFieldCropRefinementResult refinedSelections = selectionFieldCropRefinementService.refine(
          filename,
          refinedGeneralFields.data(),
          pages,
          modelProfile,
          template
      );
      DeclarationFooterFieldRefinementResult refinedFooterFields = declarationFooterFieldRefinementService.refine(
          filename,
          refinedSelections.data(),
          pages,
          modelProfile
      );
      SmudgedFieldValueFilterResult filtered = smudgedFieldValueFilterService.filter(refinedFooterFields.data());
      return withTemplateMetadata(filtered.data(), template);
    }
    SelectionFieldCropRefinementResult restoredSelections = selectionFieldCropRefinementService.restoreTemplateSelections(
        filename,
        structuredData,
        pages,
        modelProfile,
        template
    );
    return withTemplateMetadata(restoredSelections.data(), template);
  }

  private JsonNode withTemplateMetadata(JsonNode data, DocumentTemplate template) {
    ObjectNode root = data != null && data.isObject()
        ? data.deepCopy()
        : JsonNodeFactory.instance.objectNode();
    if (template == null) {
      return root;
    }
    ObjectNode metadata = root.putObject("_template");
    metadata.put("template_id", template.templateId());
    metadata.put("footer_id", template.footerId());
    metadata.put("page_count", template.pageCount());
    metadata.put("confidence", template.confidence());
    metadata.put("match_source", template.matchSource());
    metadata.put("structure_hash", template.structureHash());
    return root;
  }

  public JsonNode merge(JsonNode primaryData, CompletableFuture<StructuredExtractionResult> secondaryFuture) {
    if (secondaryFuture == null) {
      return primaryData;
    }
    try {
      StructuredExtractionResult secondary = secondaryFuture.join();
      if (secondary == null || secondary.data() == null) {
        return primaryData;
      }
      return comparisonService.merge(primaryData, secondary.data());
    } catch (RuntimeException exception) {
      log.warn("Parallel LLM recognition skipped: {}", exception.getMessage());
      return primaryData;
    }
  }

  private static final class ParallelExtractionException extends RuntimeException {
    private ParallelExtractionException(Throwable cause) {
      super(cause);
    }
  }
}
