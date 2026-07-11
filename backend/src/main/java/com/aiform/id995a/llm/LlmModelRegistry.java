package com.aiform.id995a.llm;

import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LlmModelRegistry {

  public static final String DEFAULT_MODEL_ID = "local-qwen3.6-35b-a3b";
  public static final String DASHSCOPE_MODEL_ID = "dashscope-qwen3.6-35b-a3b";
  public static final String DASHSCOPE_PLUS_MODEL_ID = "dashscope-qwen3.6-plus";
  public static final String PARALLEL_RECOGNITION_MODEL_ID = "parallel-qwen3.5-397b-a17b";
  public static final String DEFAULT_MODEL_LABEL = "本地模型";
  public static final String DASHSCOPE_MODEL_LABEL = "云原生模型";
  public static final String DASHSCOPE_PLUS_MODEL_LABEL = "qwen3.6-plus";

  private static final String DEFAULT_BASE_URL = "https://apie.zhisuaninfo.com/v1";
  private static final String PARALLEL_RECOGNITION_BASE_URL = "https://token.zhisuaninfo.com/v1";
  private static final String PARALLEL_RECOGNITION_MODEL = "Qwen3.5-397B-A17B";

  private final LlmProperties llmProperties;
  private final DashScopeProperties dashScopeProperties;
  private final ParallelLlmProperties parallelLlmProperties;

  public LlmModelRegistry(LlmProperties llmProperties, DashScopeProperties dashScopeProperties) {
    this(llmProperties, dashScopeProperties, new ParallelLlmProperties(false, "", "", "", false));
  }

  @Autowired
  public LlmModelRegistry(
      LlmProperties llmProperties,
      DashScopeProperties dashScopeProperties,
      ParallelLlmProperties parallelLlmProperties
  ) {
    this.llmProperties = llmProperties;
    this.dashScopeProperties = dashScopeProperties;
    this.parallelLlmProperties = parallelLlmProperties;
  }

  public LlmModelOptionsResponse options() {
    return new LlmModelOptionsResponse(DEFAULT_MODEL_ID, profiles().stream()
        .map(LlmModelProfile::toOption)
        .toList());
  }

  public LlmModelProfile resolve(String modelId) {
    String normalized = modelId == null || modelId.isBlank() ? DEFAULT_MODEL_ID : modelId.trim();
    return profiles().stream()
        .filter(profile -> profile.id().equals(normalized))
        .findFirst()
        .map(this::requireAvailable)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown LLM model: " + normalized));
  }

  public LlmModelProfile defaultProfile() {
    return resolve(DEFAULT_MODEL_ID);
  }

  public Optional<LlmModelProfile> parallelRecognitionProfile() {
    if (parallelLlmProperties == null || !parallelLlmProperties.enabled() || blank(parallelLlmProperties.apiKey())) {
      return Optional.empty();
    }
    return Optional.of(new LlmModelProfile(
        PARALLEL_RECOGNITION_MODEL_ID,
        "并行识别模型",
        blank(parallelLlmProperties.model()) ? PARALLEL_RECOGNITION_MODEL : parallelLlmProperties.model(),
        "OpenAI-compatible parallel recognition gateway",
        blank(parallelLlmProperties.baseUrl()) ? PARALLEL_RECOGNITION_BASE_URL : parallelLlmProperties.baseUrl(),
        parallelLlmProperties.apiKey(),
        parallelLlmProperties.enableThinking(),
        false,
        ""
    ));
  }

  private LlmModelProfile requireAvailable(LlmModelProfile profile) {
    if (!profile.available()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, profile.label() + " has no API key configured.");
    }
    return profile;
  }

  private List<LlmModelProfile> profiles() {
    return List.of(
        new LlmModelProfile(
            DEFAULT_MODEL_ID,
            DEFAULT_MODEL_LABEL,
            blank(llmProperties.model()) ? "Qwen3.6-35B-A3B" : llmProperties.model(),
            "OpenAI-compatible local gateway",
            blank(llmProperties.baseUrl()) ? DEFAULT_BASE_URL : llmProperties.baseUrl(),
            llmProperties.apiKey(),
            false,
            true,
            blank(llmProperties.apiKey()) ? "缺少 LLM_API_KEY" : ""
        ),
        new LlmModelProfile(
            DASHSCOPE_MODEL_ID,
            DASHSCOPE_MODEL_LABEL,
            blank(dashScopeProperties.model()) ? "qwen3.6-35b-a3b" : dashScopeProperties.model(),
            "DashScope OpenAI-compatible",
            blank(dashScopeProperties.baseUrl())
                ? "https://dashscope.aliyuncs.com/compatible-mode/v1"
                : dashScopeProperties.baseUrl(),
            dashScopeProperties.apiKey(),
            dashScopeProperties.enableThinking(),
            false,
            blank(dashScopeProperties.apiKey()) ? "缺少 DASHSCOPE_API_KEY" : ""
        ),
        new LlmModelProfile(
            DASHSCOPE_PLUS_MODEL_ID,
            DASHSCOPE_PLUS_MODEL_LABEL,
            "qwen3.6-plus",
            "DashScope OpenAI-compatible",
            blank(dashScopeProperties.baseUrl())
                ? "https://dashscope.aliyuncs.com/compatible-mode/v1"
                : dashScopeProperties.baseUrl(),
            dashScopeProperties.apiKey(),
            dashScopeProperties.enableThinking(),
            false,
            blank(dashScopeProperties.apiKey()) ? "缺少 DASHSCOPE_API_KEY" : ""
        )
    );
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
