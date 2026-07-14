package com.aiform.id995a.ocr;

import java.io.IOException;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TemplateClassificationService {

  private final DocumentPageRenderer pageRenderer;
  private final TemplateDetectionService templateDetectionService;
  private final TemplateClassificationLogService templateClassificationLogService;

  public TemplateClassificationService(
      DocumentPageRenderer pageRenderer,
      TemplateDetectionService templateDetectionService,
      TemplateClassificationLogService templateClassificationLogService
  ) {
    this.pageRenderer = pageRenderer;
    this.templateDetectionService = templateDetectionService;
    this.templateClassificationLogService = templateClassificationLogService;
  }

  public DocumentTemplate classify(String filename, String contentType, byte[] fileBytes) throws IOException {
    List<RenderedOcrPage> pages = pageRenderer.render(filename, contentType, fileBytes);
    DocumentTemplate template = templateDetectionService.detect(filename, contentType, fileBytes, pages);
    templateClassificationLogService.record(filename, template);
    return template;
  }
}
