package com.aiform.id995a.controller;

import com.aiform.id995a.fdh.FdhReviewConclusionResponse;
import com.aiform.id995a.fdh.FdhReviewConclusionService;
import com.aiform.id995a.fdh.FdhReviewJobService;
import com.aiform.id995a.fdh.FdhReviewJobStatusResponse;
import com.aiform.id995a.fdh.FdhReviewResult;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/fdh")
public class FdhReviewController {

  private final FdhReviewJobService reviewJobService;
  private final FdhReviewConclusionService reviewConclusionService;

  public FdhReviewController(
      FdhReviewJobService reviewJobService,
      FdhReviewConclusionService reviewConclusionService
  ) {
    this.reviewJobService = reviewJobService;
    this.reviewConclusionService = reviewConclusionService;
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
    try {
      return reviewJobService.status(jobId);
    } catch (ResponseStatusException exception) {
      if (exception.getStatusCode() != HttpStatus.NOT_FOUND) {
        throw exception;
      }
      // Jobs live in memory. Convert a restart-expired id into a terminal snapshot for polling clients.
      String message = exception.getReason();
      return new FdhReviewJobStatusResponse(
          jobId,
          "failed",
          0,
          0,
          100,
          "",
          message,
          message,
          null
      );
    }
  }

  @DeleteMapping("/review/jobs/{jobId}")
  public FdhReviewJobStatusResponse cancelReviewJob(@PathVariable String jobId) {
    return reviewJobService.cancel(jobId);
  }

  @PostMapping(value = "/review/conclusion", consumes = MediaType.APPLICATION_JSON_VALUE)
  public FdhReviewConclusionResponse generateReviewConclusion(@RequestBody FdhReviewResult result) {
    return reviewConclusionService.generate(result);
  }
}
