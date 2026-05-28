package com.aiform.id995a.controller;

import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiform.id995a.fdh.FdhReviewJobService;
import com.aiform.id995a.fdh.FdhReviewJobStatusResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(FdhReviewController.class)
class FdhReviewControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private FdhReviewJobService reviewJobService;

  @Test
  void startsFdhReviewJobWithMultipleUploadedFilesAndApplicationType() throws Exception {
    when(reviewJobService.start(eq("entry_visa"), any(), eq(null)))
        .thenReturn(new FdhReviewJobStatusResponse(
            "job-1",
            "queued",
            2,
            0,
            0,
            "",
            "queued",
            "",
            null
        ));

    mockMvc.perform(multipart("/api/fdh/review/jobs")
            .file(file("ID988A.pdf"))
            .file(file("ID988B.pdf"))
            .param("applicationTypeId", "entry_visa"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.jobId", equalTo("job-1")))
        .andExpect(jsonPath("$.status", equalTo("queued")))
        .andExpect(jsonPath("$.totalFiles", equalTo(2)));
  }

  private MockMultipartFile file(String filename) {
    return new MockMultipartFile(
        "files",
        filename,
        "application/pdf",
        ("fake-" + filename).getBytes(StandardCharsets.UTF_8)
    );
  }
}
