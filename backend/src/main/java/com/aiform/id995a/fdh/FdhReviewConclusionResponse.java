package com.aiform.id995a.fdh;

public record FdhReviewConclusionResponse(
    boolean llmEnabled,
    String status,
    String model,
    String text
) {

  public FdhReviewConclusionResponse {
    status = status == null || status.isBlank() ? "fallback" : status;
    model = model == null ? "" : model;
    text = text == null ? "" : text;
  }
}
