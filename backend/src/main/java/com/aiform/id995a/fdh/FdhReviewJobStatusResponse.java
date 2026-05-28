package com.aiform.id995a.fdh;

public record FdhReviewJobStatusResponse(
    String jobId,
    String status,
    int totalFiles,
    int processedFiles,
    int progress,
    String activeFilename,
    String message,
    String error,
    FdhReviewResult result
) {

  public FdhReviewJobStatusResponse {
    jobId = jobId == null ? "" : jobId;
    status = status == null || status.isBlank() ? "queued" : status;
    totalFiles = Math.max(0, totalFiles);
    processedFiles = Math.max(0, Math.min(processedFiles, totalFiles));
    progress = Math.max(0, Math.min(100, progress));
    activeFilename = activeFilename == null ? "" : activeFilename;
    message = message == null ? "" : message;
    error = error == null ? "" : error;
  }
}
