package com.aiform.id995a.fdh;

import com.aiform.id995a.ocr.DocumentTemplate;
import com.aiform.id995a.ocr.OcrDemoResponse;

record FdhReviewDocument(
    String filename,
    String contentType,
    int pageCount,
    DocumentTemplate template,
    OcrDemoResponse ocrResult,
    String materialId
) {

  FdhReviewDocument {
    filename = filename == null || filename.isBlank() ? "uploaded-document" : filename;
    contentType = contentType == null ? "" : contentType;
    pageCount = Math.max(0, pageCount);
    materialId = materialId == null || materialId.isBlank() ? "unknown" : materialId;
  }
}
