package com.aiform.id995a.llm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LlmModelRegistryParallelRecognitionTest {

  @Test
  void exposesHiddenParallelRecognitionProfileForQwen27b() {
    LlmModelRegistry registry = new LlmModelRegistry(
        new LlmProperties(true, "https://apie.zhisuaninfo.com/v1", "primary-key", "Qwen3.6-35B-A3B", 4096, 60, 4),
        new DashScopeProperties("", "", "", false),
        new ParallelLlmProperties(true, "", "parallel-key", "", false)
    );

    LlmModelProfile profile = registry.parallelRecognitionProfile().orElseThrow();

    assertThat(profile.id()).isEqualTo("parallel-qwen3.5-397b-a17b");
    assertThat(profile.model()).isEqualTo("Qwen3.5-397B-A17B");
    assertThat(profile.baseUrl()).isEqualTo("https://token.zhisuaninfo.com/v1");
    assertThat(profile.apiKey()).isEqualTo("parallel-key");
    assertThat(profile.enableThinking()).isFalse();
    assertThat(registry.options().models())
        .extracting(LlmModelOption::id)
        .doesNotContain("parallel-qwen3.5-397b-a17b");
  }
}
