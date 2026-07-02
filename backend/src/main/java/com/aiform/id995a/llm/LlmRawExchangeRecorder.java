package com.aiform.id995a.llm;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

public final class LlmRawExchangeRecorder {
  private static final AtomicInteger SEQUENCE = new AtomicInteger();

  private LlmRawExchangeRecorder() {
  }

  public static boolean enabled() {
    return truthy(System.getenv("LLM_RAW_LOG_ENABLED")) || !blank(System.getenv("LLM_RAW_LOG_DIR"));
  }

  public static void record(
      String caller,
      URI uri,
      String requestBody,
      int statusCode,
      String responseBody
  ) {
    if (!enabled()) {
      return;
    }
    try {
      Path root = outputRoot();
      Files.createDirectories(root);
      int sequence = SEQUENCE.incrementAndGet();
      String safeCaller = sanitize(caller);
      String prefix = "%03d-%s".formatted(sequence, safeCaller);
      Files.writeString(root.resolve(prefix + "-request.json"), value(requestBody), StandardCharsets.UTF_8);
      Files.writeString(root.resolve(prefix + "-response.json"), value(responseBody), StandardCharsets.UTF_8);
      Files.writeString(
          root.resolve(prefix + "-meta.txt"),
          """
          timestamp=%s
          caller=%s
          uri=%s
          status=%d
          request_file=%s-request.json
          response_file=%s-response.json
          """.formatted(OffsetDateTime.now(), value(caller), uri, statusCode, prefix, prefix),
          StandardCharsets.UTF_8
      );
    } catch (IOException ignored) {
      // Raw LLM capture is diagnostic only; never fail the recognition flow because logging failed.
    }
  }

  private static Path outputRoot() {
    String configured = System.getenv("LLM_RAW_LOG_DIR");
    if (!blank(configured)) {
      return Path.of(configured);
    }
    String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(java.time.LocalDateTime.now());
    return Path.of("..", "outputs", "llm-raw-" + stamp);
  }

  private static String sanitize(String value) {
    String normalized = value == null ? "llm" : value.toLowerCase(Locale.ROOT);
    normalized = normalized.replaceAll("[^a-z0-9._-]+", "-");
    normalized = normalized.replaceAll("^-+|-+$", "");
    return normalized.isBlank() ? "llm" : normalized;
  }

  private static boolean truthy(String value) {
    if (blank(value)) {
      return false;
    }
    String normalized = value.trim().toLowerCase(Locale.ROOT);
    return normalized.equals("1") || normalized.equals("true") || normalized.equals("yes") || normalized.equals("on");
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private static String value(String value) {
    return value == null ? "" : value;
  }
}
