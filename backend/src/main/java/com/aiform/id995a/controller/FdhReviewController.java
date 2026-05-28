package com.aiform.id995a.controller;

import com.aiform.id995a.fdh.FdhReviewJobService;
import com.aiform.id995a.fdh.FdhReviewJobStatusResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/fdh")
public class FdhReviewController {

  private final FdhReviewJobService reviewJobService;

  public FdhReviewController(FdhReviewJobService reviewJobService) {
    this.reviewJobService = reviewJobService;
  }

  @PostMapping(value = "/review/jobs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public FdhReviewJobStatusResponse startReviewJob(
      @RequestPart("files") List<MultipartFile> files,
      @RequestParam(value = "applicationTypeId", defaultValue = "entry_visa") String applicationTypeId,
      @RequestParam(value = "modelId", required = false) String modelId
  ) throws IOException {
    return reviewJobService.start(applicationTypeId, files, modelId);
  }

  @GetMapping("/review/jobs/{jobId}")
  public FdhReviewJobStatusResponse reviewJobStatus(@PathVariable String jobId) {
    return reviewJobService.status(jobId);
  }

  @DeleteMapping("/review/jobs/{jobId}")
  public FdhReviewJobStatusResponse cancelReviewJob(@PathVariable String jobId) {
    return reviewJobService.cancel(jobId);
  }
}
