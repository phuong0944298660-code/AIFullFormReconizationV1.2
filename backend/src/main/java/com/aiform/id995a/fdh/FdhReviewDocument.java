package com.aiform.id995a.fdh;

import com.aiform.id995a.ocr.DocumentTemplate;
import com.aiform.id995a.ocr.OcrDemoResponse;
import java.util.List;

record FdhReviewDocument(
    String filename,
    String contentType,
    int pageCount,
    DocumentTemplate template,
    OcrDemoResponse ocrResult,
    String materialId,
    List<Integer> officialPageNumbers
) {

  FdhReviewDocument {
    filename = filename == null || filename.isBlank() ? "uploaded-document" : filename;
    contentType = contentType == null ? "" : contentType;
    pageCount = Math.max(0, pageCount);
    materialId = materialId == null || materialId.isBlank() ? "unknown" : materialId;
    officialPageNumbers = officialPageNumbers == null ? List.of() : List.copyOf(officialPageNumbers);
  }

  FdhReviewDocument(
      String filename,
      String contentType,
      int pageCount,
      DocumentTemplate template,
      OcrDemoResponse ocrResult,
      String materialId
  ) {
    this(filename, contentType, pageCount, template, ocrResult, materialId, List.of());
  }
}
