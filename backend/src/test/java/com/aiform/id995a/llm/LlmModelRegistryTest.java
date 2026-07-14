package com.aiform.id995a.llm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LlmModelRegistryTest {

  @Test
  void exposesOnlyTheConfiguredPrimaryModel() {
    LlmModelRegistry registry = new LlmModelRegistry(
        new LlmProperties(true, "https://apie.zhisuaninfo.com/v1", "primary-key", "Qwen3.6-35B-A3B", 4096, 60, 4)
    );

    assertThat(registry.options().models())
        .extracting(LlmModelOption::id)
        .containsExactly("local-qwen3.6-35b-a3b");
  }
}
