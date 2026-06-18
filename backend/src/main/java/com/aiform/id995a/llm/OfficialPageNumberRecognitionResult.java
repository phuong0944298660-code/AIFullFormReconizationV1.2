package com.aiform.id995a.llm;

public record OfficialPageNumberRecognitionResult(
    int uploadedPage,
    String formId,
    String version,
    int officialPageNumber,
    double confidence,
    String evidence
) {}
