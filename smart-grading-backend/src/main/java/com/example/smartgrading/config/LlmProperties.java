package com.example.smartgrading.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ai.llm")
public class LlmProperties {
    private String apiKey;
    private String model;
    private String endpoint;
}
