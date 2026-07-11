package com.aiform.id995a.ocr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiform.id995a.llm.DashScopeProperties;
import com.aiform.id995a.llm.ExtractionProgressListener;
import com.aiform.id995a.llm.LlmModelProfile;
import com.aiform.id995a.llm.LlmModelRegistry;
import com.aiform.id995a.llm.LlmProperties;
import com.aiform.id995a.llm.ParallelLlmProperties;
import com.aiform.id995a.llm.StructuredExtractionGateway;
import com.aiform.id995a.llm.StructuredExtractionResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ParallelStructuredExtractionServiceTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void startsSecondaryExtractionWithHiddenQwen35bProfileAndMergesDisagreement() throws Exception {
    AtomicReference<LlmModelProfile> usedProfile = new AtomicReference<>();
    JsonNode secondaryData = objectMapper.readTree("""
        {
          "page_2": {
            "travel_document_no": "CA3273207"
          },
          "_confidence": {
            "page_2": {
              "travel_document_no": 86
            }
          }
        }
        """);
    StructuredExtractionGateway gateway = new StructuredExtractionGateway() {
      @Override
      public StructuredExtractionResult extract(
          String filename,
          List<RenderedOcrPage> pages,
          ExtractionProgressListener progressListener
      ) {
        throw new UnsupportedOperationException();
      }

      @Override
      public StructuredExtractionResult extractAllowingPartialPages(
          String filename,
          List<RenderedOcrPage> pages,
          ExtractionProgressListener progressListener,
          LlmModelProfile modelProfile
      ) throws IOException {
        usedProfile.set(modelProfile);
        return new StructuredExtractionResult(
            secondaryData,
            "{}",
            modelProfile.model()
        );
      }
    };
    SelectionFieldCropRefinementService selectionRefinementService = mock(SelectionFieldCropRefinementService.class);
    when(selectionRefinementService.restoreTemplateSelections(anyString(), any(), anyList(), any(), any()))
        .thenReturn(new SelectionFieldCropRefinementResult(secondaryData, 0, 0));
    ParallelStructuredExtractionService service = new ParallelStructuredExtractionService(
        gateway,
        new LlmModelRegistry(
            new LlmProperties(true, "https://apie.zhisuaninfo.com/v1", "primary-key", "Qwen3.6-35B-A3B", 4096, 60, 4),
            new DashScopeProperties("", "", "", false),
            new ParallelLlmProperties(true, "", "parallel-key", "", false)
        ),
        new ParallelFieldComparisonService(objectMapper),
        mock(AddressFieldCropRefinementService.class),
        mock(GeneralFieldCropRefinementService.class),
        selectionRefinementService,
        mock(DeclarationFooterFieldRefinementService.class),
        mock(SmudgedFieldValueFilterService.class)
    );
    CompletableFuture<StructuredExtractionResult> secondary = service.start(
        "ID990A.pdf",
        List.of(new RenderedOcrPage(2, "", 100, 100)),
        false,
        null
    );
    JsonNode primary = objectMapper.readTree("""
        {
          "page_2": {
            "travel_document_no": "CA3273201"
          },
          "_confidence": {
            "page_2": {
              "travel_document_no": 91
            }
          }
        }
        """);

    JsonNode merged = service.merge(primary, secondary);

    assertThat(usedProfile.get().id()).isEqualTo("parallel-qwen3.5-397b-a17b");
    assertThat(usedProfile.get().model()).isEqualTo("Qwen3.5-397B-A17B");
    assertThat(merged.at("/_parallel_recognition/page_2/travel_document_no/model_agreement").asText())
        .isEqualTo("disagree");
    verify(selectionRefinementService).restoreTemplateSelections(anyString(), any(), anyList(), any(), any());
  }

  @Test
  void appliesFullPostProcessingBeforeComparingWhenRefineModeIsEnabled() throws Exception {
    JsonNode extractedData = objectMapper.readTree("""
        {"page_1":{"address":"A"}}
        """);
    JsonNode addressData = objectMapper.readTree("""
        {"page_1":{"address":"B"}}
        """);
    JsonNode generalData = objectMapper.readTree("""
        {"page_1":{"address":"C"}}
        """);
    JsonNode selectionData = objectMapper.readTree("""
        {"page_1":{"address":"D"}}
        """);
    JsonNode footerData = objectMapper.readTree("""
        {"page_1":{"address":"E"}}
        """);
    JsonNode filteredData = objectMapper.readTree("""
        {"page_1":{"address":"F"}}
        """);
    StructuredExtractionGateway gateway = new StructuredExtractionGateway() {
      @Override
      public StructuredExtractionResult extract(
          String filename,
          List<RenderedOcrPage> pages,
          ExtractionProgressListener progressListener
      ) {
        throw new UnsupportedOperationException();
      }

      @Override
      public StructuredExtractionResult extract(
          String filename,
          List<RenderedOcrPage> pages,
          ExtractionProgressListener progressListener,
          LlmModelProfile modelProfile
      ) {
        return new StructuredExtractionResult(extractedData, "{}", modelProfile.model());
      }
    };
    AddressFieldCropRefinementService addressRefinementService = mock(AddressFieldCropRefinementService.class);
    GeneralFieldCropRefinementService generalRefinementService = mock(GeneralFieldCropRefinementService.class);
    SelectionFieldCropRefinementService selectionRefinementService = mock(SelectionFieldCropRefinementService.class);
    DeclarationFooterFieldRefinementService footerRefinementService = mock(DeclarationFooterFieldRefinementService.class);
    SmudgedFieldValueFilterService smudgedFieldValueFilterService = mock(SmudgedFieldValueFilterService.class);
    when(addressRefinementService.refine(anyString(), any(), anyList(), any()))
        .thenReturn(new AddressFieldCropRefinementResult(addressData, 1, 1));
    when(generalRefinementService.refine(anyString(), any(), anyList(), any()))
        .thenReturn(new GeneralFieldCropRefinementResult(generalData, 1, 1));
    when(selectionRefinementService.refine(anyString(), any(), anyList(), any(), any()))
        .thenReturn(new SelectionFieldCropRefinementResult(selectionData, 1, 1));
    when(footerRefinementService.refine(anyString(), any(), anyList(), any()))
        .thenReturn(new DeclarationFooterFieldRefinementResult(footerData, 1, 1));
    when(smudgedFieldValueFilterService.filter(any()))
        .thenReturn(new SmudgedFieldValueFilterResult(filteredData, 1));
    ParallelStructuredExtractionService service = new ParallelStructuredExtractionService(
        gateway,
        new LlmModelRegistry(
            new LlmProperties(true, "https://apie.zhisuaninfo.com/v1", "primary-key", "Qwen3.6-35B-A3B", 4096, 60, 4),
            new DashScopeProperties("", "", "", false),
            new ParallelLlmProperties(true, "", "parallel-key", "", false)
        ),
        new ParallelFieldComparisonService(objectMapper),
        addressRefinementService,
        generalRefinementService,
        selectionRefinementService,
        footerRefinementService,
        smudgedFieldValueFilterService
    );

    StructuredExtractionResult secondary = service.start(
        "ID988A.pdf",
        List.of(new RenderedOcrPage(1, "", 100, 100)),
        true,
        new DocumentTemplate("id988a_2024_06", "ID 988A (06/2024)", 5, 94, "footer", "abc123")
    ).join();

    assertThat(secondary.data().at("/page_1/address").asText()).isEqualTo("F");
    assertThat(secondary.data().at("/_template/template_id").asText()).isEqualTo("id988a_2024_06");
    assertThat(secondary.data().at("/_template/match_source").asText()).isEqualTo("footer");
    verify(addressRefinementService).refine(anyString(), any(), anyList(), any());
    verify(generalRefinementService).refine(anyString(), any(), anyList(), any());
    verify(selectionRefinementService).refine(anyString(), any(), anyList(), any(), any());
    verify(footerRefinementService).refine(anyString(), any(), anyList(), any());
    verify(smudgedFieldValueFilterService).filter(any());
  }
}
