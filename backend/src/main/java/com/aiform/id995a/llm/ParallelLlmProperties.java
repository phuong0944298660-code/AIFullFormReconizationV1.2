package com.aiform.id995a.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "parallel-llm")
public record ParallelLlmProperties(
    boolean enabled,
    String baseUrl,
    String apiKey,
    String model,
    boolean enableThinking
) {}
