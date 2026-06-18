package com.aiform.id995a.llm;

public record ApplicationTypeSelectionRecognitionResult(
    String optionKey,
    String value,
    boolean selected,
    double confidence,
    String evidence
) {}
