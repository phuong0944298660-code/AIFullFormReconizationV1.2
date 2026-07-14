package com.aiform.id995a.llm;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LlmModelRegistry {

  public static final String DEFAULT_MODEL_ID = "local-qwen3.6-35b-a3b";
  public static final String DEFAULT_MODEL_LABEL = "主模型";

  private static final String DEFAULT_BASE_URL = "https://apie.zhisuaninfo.com/v1";

  private final LlmProperties llmProperties;

  public LlmModelRegistry(LlmProperties llmProperties) {
    this.llmProperties = llmProperties;
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

  private LlmModelProfile requireAvailable(LlmModelProfile profile) {
    if (!profile.available()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, profile.label() + " has no API key configured.");
    }
    return profile;
  }

  private List<LlmModelProfile> profiles() {
    return List.of(new LlmModelProfile(
        DEFAULT_MODEL_ID,
        DEFAULT_MODEL_LABEL,
        blank(llmProperties.model()) ? "Qwen3.6-35B-A3B" : llmProperties.model(),
        "OpenAI-compatible primary gateway",
        blank(llmProperties.baseUrl()) ? DEFAULT_BASE_URL : llmProperties.baseUrl(),
        llmProperties.apiKey(),
        false,
        true,
        blank(llmProperties.apiKey()) ? "缺少 LLM_API_KEY" : ""
    ));
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
