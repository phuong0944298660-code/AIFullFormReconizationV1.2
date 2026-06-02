package com.aiform.id995a.fdh;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiform.id995a.llm.LlmProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Year;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;

class FdhReviewConclusionServiceTest {

  @Test
  void deterministicFallbackListsMaterialAndFieldResults() {
    FdhReviewConclusionService service = new FdhReviewConclusionService(
        new LlmProperties(false, "https://apie.zhisuaninfo.com/v1", "", "Qwen3.6-35B-A3B", 2048, 20, 2),
        new ObjectMapper()
    );

    FdhReviewResult result = new FdhReviewResult(
        "entry_visa",
        List.of(),
        List.of(new FdhReviewResult.MaterialRow(
            "id988a",
            1,
            "从外国受聘来港家庭傭工签证 / 延长逗留期限申请表",
            "ID 988A",
            "ID 988A (06/2024)",
            5,
            true,
            false,
            true,
            true,
            false,
            "fail",
            "缺核心材料",
            "影响最终结论",
            List.of(),
            "ID 988A 未上传"
        )),
        List.of(new FdhReviewResult.StandardField(
            "contract.dh_contract_no",
            "合约字段",
            "标准雇佣合约编号",
            true,
            "FH-CON-IDN2026-0612 / FH-CON-IDN2016-0612",
            "fail",
            "跨文件字段值明显不一致。",
            true,
            List.of(
                new FdhReviewResult.FieldSource("ID 988A", "A.pdf", "承诺", "Employment contract no.", "FH-CON-IDN2026-0612", 98, "", ""),
                new FdhReviewResult.FieldSource("ID 988B", "B.pdf", "承诺", "Employment contract no.", "FH-CON-IDN2016-0612", 98, "", "")
            ),
            "ID 988A、ID 988B 与 ID 407 的标准雇佣合约编号必须完整填写并保持一致。"
        )),
        "FAIL",
        "核心材料缺失或字段存在阻断问题。",
        new FdhReviewResult.FieldStats(1, 0, 1, 0, 1),
        "2026-06-01"
    );

    FdhReviewConclusionResponse response = service.generate(result);

    assertThat(response.llmEnabled()).isFalse();
    assertThat(response.text()).contains("整体结论：FAIL");
    assertThat(response.text()).contains("材料识别结果");
    assertThat(response.text()).contains("ID 988A：缺核心材料");
    assertThat(response.text()).contains("字段识别结果");
    assertThat(response.text()).contains("标准雇佣合约编号");
    assertThat(response.text()).contains("需人工审核");
  }

  @Test
  void retriesTransientEmptyHeaderFailureBeforeFallingBack() throws Exception {
    ObjectMapper objectMapper = new ObjectMapper();
    String modelText = "整体结论：PASS - 材料与字段均通过。\n材料识别结果：\n- ID 988A：PASS\n字段识别结果：\n- 申请类别：PASS";
    FlakyHttpClient httpClient = new FlakyHttpClient(jsonResponse(objectMapper, modelText));
    FdhReviewConclusionService service = new FdhReviewConclusionService(
        new LlmProperties(true, "https://apie.zhisuaninfo.com/v1", "test-key", "Qwen3.6-35B-A3B", 2048, 20, 2),
        httpClient,
        objectMapper
    );

    FdhReviewConclusionResponse response = service.generate(sampleResult());

    assertThat(httpClient.attempts()).isEqualTo(2);
    assertThat(response.status()).isEqualTo("ok");
    assertThat(response.text()).isEqualTo(modelText);
  }

  @Test
  void fallsBackWhenModelChangesRuleDecision() throws Exception {
    ObjectMapper objectMapper = new ObjectMapper();
    String contradictoryText = "整体结论：FAIL - 模型错误改写了规则结论。\n材料识别结果：\n- ID 988A：FAIL\n字段识别结果：\n- 申请类别：FAIL";
    FdhReviewConclusionService service = new FdhReviewConclusionService(
        new LlmProperties(true, "https://apie.zhisuaninfo.com/v1", "test-key", "Qwen3.6-35B-A3B", 2048, 20, 2),
        new StableHttpClient(jsonResponse(objectMapper, contradictoryText)),
        objectMapper
    );

    FdhReviewConclusionResponse response = service.generate(sampleResult());

    assertThat(response.status()).isEqualTo("format_fallback");
    assertThat(response.text()).contains("整体结论：PASS");
    assertThat(response.text()).doesNotContain("模型错误改写");
  }

  @Test
  void parsesModelFieldAdjudicationSuggestionsFromJsonResponse() throws Exception {
    ObjectMapper objectMapper = new ObjectMapper();
    String modelText = objectMapper.writeValueAsString(Map.of(
        "text", "整体结论：REVIEW - 合约编号存在跨文件差异，建议采用 ID 407 值并人工复核。\n材料识别结果：\n- ID 988A：PASS\n字段识别结果：\n- 标准雇佣合约编号：REVIEW",
        "fieldAdjudications", List.of(Map.of(
            "key", "contract.dh_contract_no",
            "suggestedValue", "RFH-CON-IDN-2026-0612",
            "status", "review",
            "corrected", true,
            "reason", "LLM 结合香港入境处材料规则，优先采用 ID 407 合约首页值；跨文件仍需人工复核。"
        ))
    ));
    FdhReviewConclusionService service = new FdhReviewConclusionService(
        new LlmProperties(true, "https://apie.zhisuaninfo.com/v1", "test-key", "Qwen3.6-35B-A3B", 2048, 20, 2),
        new StableHttpClient(jsonResponse(objectMapper, modelText)),
        objectMapper
    );

    FdhReviewConclusionResponse response = service.generate(conflictingContractResult("REVIEW"));

    assertThat(response.status()).isEqualTo("ok");
    assertThat(response.text()).startsWith("整体结论：REVIEW");
    assertThat(response.fieldAdjudications()).singleElement()
        .satisfies(adjudication -> {
          assertThat(adjudication.key()).isEqualTo("contract.dh_contract_no");
          assertThat(adjudication.suggestedValue()).isEqualTo("FH-CON-IDN-2026-0612");
          assertThat(adjudication.status()).isEqualTo("review");
          assertThat(adjudication.corrected()).isTrue();
          assertThat(adjudication.reason()).contains("人工复核");
        });
  }

  @Test
  void modelContractSuggestionCannotOverrideClosestNonFutureYearRule() throws Exception {
    int currentYear = Year.now().getValue();
    int futureYear = currentYear + 10;
    ObjectMapper objectMapper = new ObjectMapper();
    String modelText = objectMapper.writeValueAsString(Map.of(
        "text", "整体结论：REVIEW - 合约编号需要人工复核。\n材料识别结果：\n- ID 988A：PASS\n字段识别结果：\n- 标准雇佣合约编号：REVIEW",
        "fieldAdjudications", List.of(Map.of(
            "key", "contract.dh_contract_no",
            "suggestedValue", "RFH-CON-IDN-" + futureYear + "-0612",
            "status", "review",
            "corrected", true,
            "reason", "LLM suggested the future-year value."
        ))
    ));
    FdhReviewConclusionService service = new FdhReviewConclusionService(
        new LlmProperties(true, "https://apie.zhisuaninfo.com/v1", "test-key", "Qwen3.6-35B-A3B", 2048, 20, 2),
        new StableHttpClient(jsonResponse(objectMapper, modelText)),
        objectMapper
    );

    FdhReviewConclusionResponse response = service.generate(contractNumberResult(
        "FH-CON-IDN-" + currentYear + "-0612 / RFH-CON-IDN-" + futureYear + "-0612",
        List.of(
            new FdhReviewResult.FieldSource("ID 988A", "A.pdf", "Undertaking", "Employment contract no.", "FH-CON-IDN-" + currentYear + "-0612", 98, "", ""),
            new FdhReviewResult.FieldSource("ID 407", "407.pdf", "Contract cover", "Contract No.", "RFH-CON-IDN-" + futureYear + "-0612", 98, "", "")
        )
    ));

    assertThat(response.status()).isEqualTo("ok");
    assertThat(response.fieldAdjudications()).singleElement()
        .satisfies(adjudication -> {
          assertThat(adjudication.suggestedValue()).isEqualTo("FH-CON-IDN-" + currentYear + "-0612");
          assertThat(adjudication.status()).isEqualTo("review");
          assertThat(adjudication.reason()).contains("当前年份");
        });
  }

  @Test
  void deterministicContractNumberAdjudicationNormalizesRequiredPrefix() {
    FdhReviewConclusionService service = new FdhReviewConclusionService(
        new LlmProperties(false, "https://apie.zhisuaninfo.com/v1", "", "Qwen3.6-35B-A3B", 2048, 20, 2),
        new ObjectMapper()
    );

    FdhReviewConclusionResponse response = service.generate(conflictingContractResult("REVIEW"));

    assertThat(response.fieldAdjudications()).singleElement()
        .satisfies(adjudication -> {
          assertThat(adjudication.key()).isEqualTo("contract.dh_contract_no");
          assertThat(adjudication.suggestedValue()).isEqualTo("FH-CON-IDN-2026-0612");
          assertThat(adjudication.status()).isEqualTo("review");
          assertThat(adjudication.corrected()).isTrue();
        });
  }

  @Test
  void deterministicContractNumberAdjudicationPrefersClosestNonFutureYear() {
    int currentYear = Year.now().getValue();
    int oldYear = currentYear - 10;
    FdhReviewConclusionService service = new FdhReviewConclusionService(
        new LlmProperties(false, "https://apie.zhisuaninfo.com/v1", "", "Qwen3.6-35B-A3B", 2048, 20, 2),
        new ObjectMapper()
    );

    FdhReviewConclusionResponse response = service.generate(contractNumberResult(
        "FH-COW-PH" + currentYear + "-0708 / FH-CW-PH" + currentYear + "-0708 / RFH-CON-IDN-" + oldYear + "-0612",
        List.of(
            new FdhReviewResult.FieldSource("ID 988A", "A.pdf", "Undertaking", "Employment contract no.", "FH-COW-PH" + currentYear + "-0708", 98, "", ""),
            new FdhReviewResult.FieldSource("ID 988B", "B.pdf", "Undertaking", "Employment contract no.", "FH-CW-PH" + currentYear + "-0708", 98, "", ""),
            new FdhReviewResult.FieldSource("ID 407", "407.pdf", "Contract cover", "Contract No.", "RFH-CON-IDN-" + oldYear + "-0612", 98, "", "")
        )
    ));

    assertThat(response.fieldAdjudications()).singleElement()
        .satisfies(adjudication -> {
          assertThat(adjudication.suggestedValue()).isEqualTo("FH-COW-PH" + currentYear + "-0708");
          assertThat(adjudication.status()).isEqualTo("review");
          assertThat(adjudication.reason()).contains("当前年份");
        });
  }

  @Test
  void deterministicContractNumberAdjudicationIgnoresFutureYears() {
    int currentYear = Year.now().getValue();
    int futureYear = currentYear + 10;
    FdhReviewConclusionService service = new FdhReviewConclusionService(
        new LlmProperties(false, "https://apie.zhisuaninfo.com/v1", "", "Qwen3.6-35B-A3B", 2048, 20, 2),
        new ObjectMapper()
    );

    FdhReviewConclusionResponse response = service.generate(contractNumberResult(
        "FH-CON-IDN-" + currentYear + "-0612 / RFH-CON-IDN-" + futureYear + "-0612",
        List.of(
            new FdhReviewResult.FieldSource("ID 988A", "A.pdf", "Undertaking", "Employment contract no.", "FH-CON-IDN-" + currentYear + "-0612", 98, "", ""),
            new FdhReviewResult.FieldSource("ID 407", "407.pdf", "Contract cover", "Contract No.", "RFH-CON-IDN-" + futureYear + "-0612", 98, "", "")
        )
    ));

    assertThat(response.fieldAdjudications()).singleElement()
        .satisfies(adjudication -> {
          assertThat(adjudication.suggestedValue()).isEqualTo("FH-CON-IDN-" + currentYear + "-0612");
          assertThat(adjudication.status()).isEqualTo("review");
        });
  }

  private FdhReviewResult sampleResult() {
    return new FdhReviewResult(
        "entry_visa",
        List.of(),
        List.of(new FdhReviewResult.MaterialRow(
            "id988a",
            1,
            "从外国受聘来港家庭傭工签证 / 延长逗留期限申请表",
            "ID 988A",
            "ID 988A (06/2024)",
            5,
            true,
            true,
            true,
            true,
            false,
            "pass",
            "核心材料齐全",
            "影响最终结论",
            List.of("ID988A.pdf"),
            ""
        )),
        List.of(new FdhReviewResult.StandardField(
            "case.application_type",
            "案件与文档",
            "申请类别",
            true,
            "入境签证",
            "pass",
            "",
            true,
            List.of(new FdhReviewResult.FieldSource("ID 988A", "ID988A.pdf", "Application Type", "Application Type", "入境签证", 98, "", "")),
            "必须与用户选择的四类情形一致。"
        )),
        "PASS",
        "允许通过。",
        new FdhReviewResult.FieldStats(1, 1, 0, 0, 1),
        "2026-06-01"
    );
  }

  private FdhReviewResult conflictingContractResult(String decision) {
    return new FdhReviewResult(
        "entry_visa",
        List.of(),
        List.of(new FdhReviewResult.MaterialRow(
            "id407",
            3,
            "新标准雇佣合约正本一份",
            "ID 407",
            "ID 407 (11/2016)",
            4,
            true,
            true,
            true,
            true,
            false,
            "pass",
            "核心材料齐全",
            "影响最终结论",
            List.of("ID407.pdf"),
            ""
        )),
        List.of(new FdhReviewResult.StandardField(
            "contract.dh_contract_no",
            "合约字段",
            "标准雇佣合约编号",
            true,
            "FH-CON-IDN2026-0612 / FH-CON-IDN2016-0612 / RFH-CON-IDN-2026-0612",
            "review",
            "跨文件字段值明显不一致。",
            true,
            List.of(
                new FdhReviewResult.FieldSource("ID 988A", "A.pdf", "承诺", "Employment contract no.", "FH-CON-IDN2026-0612", 98, "", ""),
                new FdhReviewResult.FieldSource("ID 988B", "B.pdf", "承诺", "Employment contract no.", "FH-CON-IDN2016-0612", 95, "", ""),
                new FdhReviewResult.FieldSource("ID 407", "407.pdf", "合约首页", "Contract No.", "RFH-CON-IDN-2026-0612", 98, "", "")
            ),
            "ID 988A、ID 988B 与 ID 407 的标准雇佣合约编号必须完整填写并保持一致。"
        )),
        decision,
        "字段需要人工复核。",
        new FdhReviewResult.FieldStats(1, 0, 0, 1, 1),
        "2026-06-01"
    );
  }

  private FdhReviewResult contractNumberResult(String normalizedValue, List<FdhReviewResult.FieldSource> sources) {
    return new FdhReviewResult(
        "entry_visa",
        List.of(),
        List.of(new FdhReviewResult.MaterialRow(
            "id407",
            3,
            "ID 407",
            "ID 407",
            "ID 407 (11/2016)",
            4,
            true,
            true,
            true,
            true,
            false,
            "pass",
            "PASS",
            "blocking",
            List.of("ID407.pdf"),
            ""
        )),
        List.of(new FdhReviewResult.StandardField(
            "contract.dh_contract_no",
            "contract",
            "standard employment contract number",
            true,
            normalizedValue,
            "review",
            "cross-file mismatch",
            true,
            sources,
            "ID 988A, ID 988B and ID 407 contract numbers must be complete and consistent."
        )),
        "REVIEW",
        "field requires review",
        new FdhReviewResult.FieldStats(1, 0, 0, 1, 1),
        "2026-06-01"
    );
  }

  private String jsonResponse(ObjectMapper objectMapper, String content) throws Exception {
    return objectMapper.writeValueAsString(Map.of(
        "choices", List.of(Map.of(
            "finish_reason", "stop",
            "message", Map.of("content", content)
        )),
        "usage", Map.of("total_tokens", 128)
    ));
  }

  private static final class FlakyHttpClient extends HttpClient {
    private final AtomicInteger attempts = new AtomicInteger();
    private final String responseBody;

    private FlakyHttpClient(String responseBody) {
      this.responseBody = responseBody;
    }

    int attempts() {
      return attempts.get();
    }

    @Override
    public Optional<CookieHandler> cookieHandler() {
      return Optional.empty();
    }

    @Override
    public Optional<Duration> connectTimeout() {
      return Optional.empty();
    }

    @Override
    public Redirect followRedirects() {
      return Redirect.NEVER;
    }

    @Override
    public Optional<ProxySelector> proxy() {
      return Optional.empty();
    }

    @Override
    public SSLContext sslContext() {
      try {
        return SSLContext.getDefault();
      } catch (Exception exception) {
        throw new IllegalStateException(exception);
      }
    }

    @Override
    public SSLParameters sslParameters() {
      return new SSLParameters();
    }

    @Override
    public Optional<Authenticator> authenticator() {
      return Optional.empty();
    }

    @Override
    public Version version() {
      return Version.HTTP_1_1;
    }

    @Override
    public Optional<Executor> executor() {
      return Optional.empty();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) throws IOException {
      if (attempts.incrementAndGet() == 1) {
        throw new IOException("HTTP/1.1 header parser received no bytes");
      }
      return (HttpResponse<T>) new StubHttpResponse(request, responseBody);
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
      try {
        return CompletableFuture.completedFuture(send(request, responseBodyHandler));
      } catch (IOException exception) {
        CompletableFuture<HttpResponse<T>> future = new CompletableFuture<>();
        future.completeExceptionally(exception);
        return future;
      }
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(
        HttpRequest request,
        HttpResponse.BodyHandler<T> responseBodyHandler,
        HttpResponse.PushPromiseHandler<T> pushPromiseHandler
    ) {
      return sendAsync(request, responseBodyHandler);
    }
  }

  private static final class StableHttpClient extends HttpClient {
    private final String responseBody;

    private StableHttpClient(String responseBody) {
      this.responseBody = responseBody;
    }

    @Override
    public Optional<CookieHandler> cookieHandler() {
      return Optional.empty();
    }

    @Override
    public Optional<Duration> connectTimeout() {
      return Optional.empty();
    }

    @Override
    public Redirect followRedirects() {
      return Redirect.NEVER;
    }

    @Override
    public Optional<ProxySelector> proxy() {
      return Optional.empty();
    }

    @Override
    public SSLContext sslContext() {
      try {
        return SSLContext.getDefault();
      } catch (Exception exception) {
        throw new IllegalStateException(exception);
      }
    }

    @Override
    public SSLParameters sslParameters() {
      return new SSLParameters();
    }

    @Override
    public Optional<Authenticator> authenticator() {
      return Optional.empty();
    }

    @Override
    public Version version() {
      return Version.HTTP_1_1;
    }

    @Override
    public Optional<Executor> executor() {
      return Optional.empty();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
      return (HttpResponse<T>) new StubHttpResponse(request, responseBody);
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
      return CompletableFuture.completedFuture(send(request, responseBodyHandler));
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(
        HttpRequest request,
        HttpResponse.BodyHandler<T> responseBodyHandler,
        HttpResponse.PushPromiseHandler<T> pushPromiseHandler
    ) {
      return sendAsync(request, responseBodyHandler);
    }
  }

  private record StubHttpResponse(HttpRequest request, String body) implements HttpResponse<String> {
    @Override
    public int statusCode() {
      return 200;
    }

    @Override
    public Optional<HttpResponse<String>> previousResponse() {
      return Optional.empty();
    }

    @Override
    public HttpHeaders headers() {
      return HttpHeaders.of(Map.of(), (left, right) -> true);
    }

    @Override
    public Optional<SSLSession> sslSession() {
      return Optional.empty();
    }

    @Override
    public URI uri() {
      return request.uri();
    }

    @Override
    public HttpClient.Version version() {
      return HttpClient.Version.HTTP_1_1;
    }
  }
}
