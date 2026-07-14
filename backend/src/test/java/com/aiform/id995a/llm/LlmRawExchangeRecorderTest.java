package com.aiform.id995a.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LlmRawExchangeRecorderTest {

  @TempDir
  Path tempDir;

  @Test
  void recordsModelSpecificFilenamePurposeAndTokenUsage() throws Exception {
    String prefix = LlmRawExchangeRecorder.recordTo(
        tempDir,
        "primary-llm",
        "structured-extraction",
        "Extract structured fields and valueBbox from page 1",
        "Qwen3.6-35B-A3B",
        URI.create("https://example.test/v1/chat/completions"),
        "{\"request\":true}",
        200,
        "{\"usage\":{\"prompt_tokens\":123,\"completion_tokens\":45,\"total_tokens\":168}}"
    );

    assertTrue(prefix.matches("\\d{3}-primary-llm-structured-extraction"));
    assertTrue(Files.exists(tempDir.resolve(prefix + "-request.json")));
    assertTrue(Files.exists(tempDir.resolve(prefix + "-response.json")));
    String meta = Files.readString(tempDir.resolve(prefix + "-meta.txt"));
    assertTrue(meta.contains("model_type=primary-llm"));
    assertTrue(meta.contains("model=Qwen3.6-35B-A3B"));
    assertTrue(meta.contains("purpose=Extract structured fields and valueBbox from page 1"));
    assertTrue(meta.contains("input_tokens=123"));
    assertTrue(meta.contains("output_tokens=45"));
    assertTrue(meta.contains("total_tokens=168"));
  }

  @Test
  void recordsUnavailableTokensWhenResponseHasNoUsage() throws Exception {
    String prefix = LlmRawExchangeRecorder.recordTo(
        tempDir,
        "ppocr-tiny",
        "page-detect",
        "Locate field labels on one rendered page",
        "PP-OCRv6 Tiny",
        URI.create("http://127.0.0.1:18092/ocr/page-detect"),
        "{\"image_count\":1,\"image_bytes\":2048}",
        200,
        "{\"lines\":[]}"
    );

    String meta = Files.readString(tempDir.resolve(prefix + "-meta.txt"));
    assertTrue(prefix.matches("\\d{3}-ppocr-tiny-page-detect"));
    assertTrue(meta.contains("input_tokens=unavailable"));
    assertTrue(meta.contains("output_tokens=unavailable"));
    assertTrue(meta.contains("total_tokens=unavailable"));
    List<Path> files;
    try (var stream = Files.list(tempDir)) {
      files = stream.toList();
    }
    assertEquals(3, files.size());
  }
}
