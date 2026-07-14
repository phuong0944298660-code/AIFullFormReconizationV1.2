package com.aiform.id995a.ocr;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class QwenFieldJudgeClientTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void defaultsToGatewayRegisteredModelId() {
    FieldJudgeProperties properties = new FieldJudgeProperties(
        true, "enforce", "https://token.zhisuaninfo.com/v1", "test-key", "", 20, 80, 1
    );

    assertThat(properties.model()).isEqualTo("Qwen3.6-Flash");
  }

  @Test
  void sendsNonThinkingVisionRequestAndParsesStructuredObservation() throws Exception {
    AtomicReference<String> requestBody = new AtomicReference<>();
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/chat/completions", exchange -> {
      requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      write(exchange, 200, """
          {"choices":[{"message":{"content":"{\\"status\\":\\"available\\",\\"observedValue\\":\\"A123456(7)\\",\\"matchType\\":\\"exact\\",\\"legibility\\":\\"clear\\",\\"cropCoverage\\":\\"complete\\",\\"reason\\":\\"The value matches exactly.\\",\\"reasonZhHant\\":\\"填寫值完全一致。\\"}"}}]}
          """);
    });
    server.start();
    try {
      QwenFieldJudgeClient client = new QwenFieldJudgeClient(
          properties(server, true),
          objectMapper
      );

      FieldJudgeObservation observation = client.judge(
          "applicant.hkid",
          "HKID",
          "A123456(7)",
          "identifier",
          "data:image/jpeg;base64,AA=="
      );

      assertThat(observation.status()).isEqualTo("available");
      assertThat(observation.observedValue()).isEqualTo("A123456(7)");
      assertThat(observation.matchType()).isEqualTo("exact");
      assertThat(observation.reasonZhHant()).isEqualTo("填寫值完全一致。");

      JsonNode request = objectMapper.readTree(requestBody.get());
      assertThat(request.path("model").asText()).isEqualTo("Qwen3.6-Flash");
      assertThat(request.path("enable_thinking").asBoolean()).isFalse();
      assertThat(request.path("temperature").asDouble()).isZero();
      assertThat(request.path("response_format").path("type").asText()).isEqualTo("json_object");
      assertThat(request.toString()).contains("data:image/jpeg;base64,AA==");
      assertThat(request.toString()).doesNotContain("recognitionConfidence");
    } finally {
      server.stop(0);
    }
  }

  @Test
  void failsClosedWhenJudgeReturnsInvalidJson() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/chat/completions", exchange -> write(
        exchange,
        200,
        "{\"choices\":[{\"message\":{\"content\":\"not-json\"}}]}"
    ));
    server.start();
    try {
      QwenFieldJudgeClient client = new QwenFieldJudgeClient(properties(server, true), objectMapper);

      FieldJudgeObservation observation = client.judge(
          "applicant.name", "Name", "CHAN TAI MAN", "text", "data:image/jpeg;base64,AA=="
      );

      assertThat(observation.status()).isEqualTo("unavailable");
      assertThat(observation.reason()).isEqualTo("invalid_response");
    } finally {
      server.stop(0);
    }
  }

  @Test
  void acceptsProviderStatusWhenTheStructuredJudgementIsOtherwiseValid() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/chat/completions", exchange -> write(
        exchange,
        200,
        "{\"choices\":[{\"message\":{\"content\":\"{\\\"status\\\":\\\"error\\\",\\\"observedValue\\\":\\\"\\\",\\\"matchType\\\":\\\"crop_incomplete\\\",\\\"legibility\\\":\\\"unreadable\\\",\\\"cropCoverage\\\":\\\"missing\\\",\\\"reason\\\":\\\"The crop is blank.\\\"}\"}}]}"
    ));
    server.start();
    try {
      FieldJudgeObservation observation = new QwenFieldJudgeClient(properties(server, true), objectMapper)
          .judge("applicant.name", "Name", "CHAN TAI MAN", "text", "data:image/jpeg;base64,AA==");

      assertThat(observation.status()).isEqualTo("available");
      assertThat(observation.matchType()).isEqualTo("crop_incomplete");
      assertThat(observation.reason()).isEqualTo("The crop is blank.");
    } finally {
      server.stop(0);
    }
  }

  @Test
  void doesNotCallRemoteModelWhenJudgeIsDisabled() {
    QwenFieldJudgeClient client = new QwenFieldJudgeClient(
        new FieldJudgeProperties(false, "shadow", "", "", "qwen3.6-flash", 2, 85, 1),
        objectMapper
    );

    FieldJudgeObservation observation = client.judge(
        "applicant.name", "Name", "CHAN TAI MAN", "text", "data:image/jpeg;base64,AA=="
    );

    assertThat(observation.status()).isEqualTo("unavailable");
    assertThat(observation.reason()).isEqualTo("judge_disabled");
  }

  @Test
  void retriesOnceAfterRateLimit() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/chat/completions", exchange -> {
      if (calls.incrementAndGet() == 1) {
        write(exchange, 429, "{\"error\":\"rate limited\"}");
      } else {
        write(exchange, 200, "{\"choices\":[{\"message\":{\"content\":\"{\\\"status\\\":\\\"available\\\",\\\"observedValue\\\":\\\"OK\\\",\\\"matchType\\\":\\\"exact\\\",\\\"legibility\\\":\\\"clear\\\",\\\"cropCoverage\\\":\\\"complete\\\",\\\"reason\\\":\\\"\\\"}\"}}]}");
      }
    });
    server.start();
    try {
      FieldJudgeObservation observation = new QwenFieldJudgeClient(properties(server, true), objectMapper)
          .judge("field", "Field", "OK", "text", "data:image/jpeg;base64,AA==");

      assertThat(observation.status()).isEqualTo("available");
      assertThat(calls.get()).isEqualTo(2);
    } finally {
      server.stop(0);
    }
  }

  @Test
  void rejectsExactResponseWithoutObservedValue() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/chat/completions", exchange -> write(
        exchange, 200,
        "{\"choices\":[{\"message\":{\"content\":\"{\\\"status\\\":\\\"available\\\",\\\"matchType\\\":\\\"exact\\\",\\\"legibility\\\":\\\"clear\\\",\\\"cropCoverage\\\":\\\"complete\\\",\\\"reason\\\":\\\"\\\"}\"}}]}"
    ));
    server.start();
    try {
      FieldJudgeObservation observation = new QwenFieldJudgeClient(properties(server, true), objectMapper)
          .judge("flag", "Flag", "true", "boolean", "data:image/jpeg;base64,AA==");

      assertThat(observation.status()).isEqualTo("unavailable");
      assertThat(observation.reason()).isEqualTo("invalid_response");
    } finally {
      server.stop(0);
    }
  }

  private static FieldJudgeProperties properties(HttpServer server, boolean enabled) {
    return new FieldJudgeProperties(
        enabled,
        "shadow",
        "http://127.0.0.1:" + server.getAddress().getPort(),
        "test-key",
        "Qwen3.6-Flash",
        2,
        85,
        1
    );
  }

  private static void write(HttpExchange exchange, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }
}
