package com.scolapay.core.payments.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "scolapay.payments")
public record CashProperties(boolean cashReceptionsEnabled, boolean cashAllocationsEnabled) {}
