package com.aiform.id995a.llm;

import com.aiform.id995a.ocr.DocumentTemplate;
import com.aiform.id995a.ocr.RenderedOcrPage;
import java.io.IOException;
import java.util.List;

public interface OfficialPageNumberRecognitionGateway {

  List<OfficialPageNumberRecognitionResult> recognizeOfficialPageNumbers(
      String filename,
      DocumentTemplate template,
      List<RenderedOcrPage> pages,
      LlmModelProfile modelProfile
  ) throws IOException;
}
