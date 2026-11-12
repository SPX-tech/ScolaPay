package com.scolapay.core.payments.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix="scolapay.payments.digital")
public record DigitalPaymentProperties(boolean intentsEnabled) {}
