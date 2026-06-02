package com.aiform.id995a.fdh;

import java.util.List;

public record FdhReviewConclusionResponse(
    boolean llmEnabled,
    String status,
    String model,
    String text,
    List<FdhFieldAdjudication> fieldAdjudications
) {

  public FdhReviewConclusionResponse {
    status = status == null || status.isBlank() ? "fallback" : status;
    model = model == null ? "" : model;
    text = text == null ? "" : text;
    fieldAdjudications = fieldAdjudications == null ? List.of() : List.copyOf(fieldAdjudications);
  }

  public FdhReviewConclusionResponse(boolean llmEnabled, String status, String model, String text) {
    this(llmEnabled, status, model, text, List.of());
  }
}
