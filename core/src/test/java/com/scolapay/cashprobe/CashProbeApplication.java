package com.scolapay.cashprobe;

import com.scolapay.core.ScolaPayCoreApplication;
import com.scolapay.testsupport.TestInvitationDelivery;
import jakarta.servlet.Filter;
import java.util.concurrent.CountDownLatch;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.util.ContentCachingResponseWrapper;

/** Sonde de reprise hors du scan de production ; aucun point de synchronisation dans payments. */
public final class CashProbeApplication {
    private CashProbeApplication() {}

    public static void main(String[] args) {
        new SpringApplicationBuilder(ScolaPayCoreApplication.class, TestInvitationDelivery.class, ProbeConfiguration.class).run(args);
    }

    @Configuration(proxyBeanMethods = false)
    static class ProbeConfiguration {
        @Bean
        FilterRegistrationBean<Filter> dropCommittedResponse(@Value("${probe.drop-cash-response:false}") boolean drop,
                @Value("${probe.drop-allocation-response:false}") boolean dropAllocation,
                @Value("${probe.drop-excess-request-response:false}") boolean dropExcess,
                @Value("${probe.drop-excess-decision-response:false}") boolean dropDecision,
                @Value("${probe.drop-identity-suffix:}") String identitySuffix) {
            FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
            registration.setFilter((request, response, chain) -> {
                var httpRequest = (jakarta.servlet.http.HttpServletRequest) request;
                boolean target=(drop && httpRequest.getRequestURI().endsWith("/payments/cash-receptions"))
                        || (dropAllocation && httpRequest.getRequestURI().endsWith("/allocations"))
                        || (dropExcess && httpRequest.getRequestURI().endsWith("/excess-requests"))
                        || (dropDecision && httpRequest.getRequestURI().endsWith("/approval"))
                        || (!identitySuffix.isEmpty() && httpRequest.getRequestURI().endsWith(identitySuffix));
                if (!target || !"POST".equals(httpRequest.getMethod())) {
                    chain.doFilter(request, response);
                    return;
                }
                ContentCachingResponseWrapper buffered = new ContentCachingResponseWrapper(
                        (jakarta.servlet.http.HttpServletResponse) response);
                chain.doFilter(request, buffered);
                if (buffered.getStatus() == 201) {
                    try { new CountDownLatch(1).await(); }
                    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                } else {
                    buffered.copyBodyToResponse();
                }
            });
            registration.addUrlPatterns("/api/v2/establishments/*");
            return registration;
        }
    }
}
