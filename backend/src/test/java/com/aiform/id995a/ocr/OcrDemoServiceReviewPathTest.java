package com.aiform.id995a.ocr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aiform.id995a.llm.ExtractionProgressListener;
import com.aiform.id995a.llm.FieldRegionLocationGateway;
import com.aiform.id995a.llm.LlmModelProfile;
import com.aiform.id995a.llm.LlmModelRegistry;
import com.aiform.id995a.llm.LlmProperties;
import com.aiform.id995a.llm.StructuredExtractionGateway;
import com.aiform.id995a.llm.StructuredExtractionResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class OcrDemoServiceReviewPathTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void reviewRecognitionUsesFullPostProcessingPath() throws Exception {
    JsonNode extracted = objectMapper.readTree("""
        {"page_1":{"address":"A"}}
        """);
    JsonNode addressReviewed = objectMapper.readTree("""
        {"page_1":{"address":"B"}}
        """);
    JsonNode generalReviewed = objectMapper.readTree("""
        {"page_1":{"address":"C"}}
        """);
    JsonNode selectionReviewed = objectMapper.readTree("""
        {"page_1":{"address":"D"}}
        """);
    JsonNode footerReviewed = objectMapper.readTree("""
        {"page_1":{"address":"E"}}
        """);
    JsonNode filtered = objectMapper.readTree("""
        {"page_1":{"address":"F"}}
        """);

    StructuredExtractionGateway gateway = new StructuredExtractionGateway() {
      @Override
      public StructuredExtractionResult extract(
          String filename,
          List<RenderedOcrPage> pages,
          ExtractionProgressListener progressListener
      ) {
        return new StructuredExtractionResult(extracted, "{}", "Qwen3.6-35B-A3B");
      }

      @Override
      public StructuredExtractionResult extractAllowingPartialPages(
          String filename,
          List<RenderedOcrPage> pages,
          ExtractionProgressListener progressListener,
          LlmModelProfile modelProfile
      ) throws IOException {
        throw new IOException("review path must not use fast partial-page extraction");
      }
    };

    AddressFieldCropRefinementService addressService = mock(AddressFieldCropRefinementService.class);
    GeneralFieldCropRefinementService generalService = mock(GeneralFieldCropRefinementService.class);
    SelectionFieldCropRefinementService selectionService = mock(SelectionFieldCropRefinementService.class);
    DeclarationFooterFieldRefinementService footerService = mock(DeclarationFooterFieldRefinementService.class);
    SmudgedFieldValueFilterService smudgeService = mock(SmudgedFieldValueFilterService.class);
    when(addressService.refine(anyString(), any(), anyList(), any()))
        .thenReturn(new AddressFieldCropRefinementResult(addressReviewed, 1, 1));
    when(generalService.refine(anyString(), any(), anyList(), any()))
        .thenReturn(new GeneralFieldCropRefinementResult(generalReviewed, 1, 1));
    when(selectionService.refine(anyString(), any(), anyList(), any(), any()))
        .thenReturn(new SelectionFieldCropRefinementResult(selectionReviewed, 1, 1));
    when(footerService.refine(anyString(), any(), anyList(), any()))
        .thenReturn(new DeclarationFooterFieldRefinementResult(footerReviewed, 1, 1));
    when(smudgeService.filter(any()))
        .thenReturn(new SmudgedFieldValueFilterResult(filtered, 0));
    OcrDemoService service = new OcrDemoService(
        mock(DocumentPageRenderer.class),
        gateway,
        new StructuredFieldEvidenceService(),
        addressService,
        generalService,
        selectionService,
        footerService,
        smudgeService,
        mock(TemplateDetectionService.class),
        mock(TemplateClassificationLogService.class),
        new LlmModelRegistry(
            new LlmProperties(true, "https://apie.zhisuaninfo.com/v1", "test-key", "Qwen3.6-35B-A3B", 4096, 60, 4)
        ),
        mock(FieldRegionLocationGateway.class)
    );

    OcrDemoResponse response = service.recognizeRenderedForFdhReview(
        "ID 990A-test.pdf",
        List.of(new RenderedOcrPage(1, "data:image/png;base64,abc", 100, 100)),
        ExtractionProgressListener.NOOP,
        null,
        new DocumentTemplate("id990a_test", "ID 990A", 5, 90, "test", "hash"),
        false
    );

    assertThat(response.structuredData().at("/page_1/address").asText()).isEqualTo("F");
    assertThat(response.engineStatus().messages()).anySatisfy(message ->
        assertThat(message).contains("Address fields with clear value regions are second-pass transcribed")
    );
    assertThat(response.engineStatus().messages()).anySatisfy(message ->
        assertThat(message).contains("Ordinary text and symbol-sensitive fields with clear value regions are crop-reviewed")
    );
    assertThat(response.engineStatus().messages()).anySatisfy(message ->
        assertThat(message).contains("Checkbox and declaration fields with clear value regions are second-pass reviewed")
    );
  }
}
