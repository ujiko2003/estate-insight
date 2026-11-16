package com.hy.estate.batch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "collect")
public record CollectProperties(int targetMonths) {}
