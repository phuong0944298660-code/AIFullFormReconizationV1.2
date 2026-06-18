package com.aiform.id995a.llm;

import com.aiform.id995a.ocr.DocumentTemplate;
import com.aiform.id995a.ocr.RenderedOcrPage;
import java.io.IOException;
import java.util.List;

public interface ApplicationTypeSelectionRecognitionGateway {

  List<ApplicationTypeSelectionRecognitionResult> recognizeApplicationTypeSelections(
      String filename,
      RenderedOcrPage page,
      DocumentTemplate template,
      LlmModelProfile modelProfile
  ) throws IOException;
}
