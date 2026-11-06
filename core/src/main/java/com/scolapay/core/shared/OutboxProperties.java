package com.scolapay.core.shared;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("scolapay.outbox")
record OutboxProperties(int batchSize, Duration retryDelay, Duration maxRetryDelay, Duration overdueAfter) {}
