package com.scolapay.notificationprobe;

import com.scolapay.core.ScolaPayCoreApplication;
import com.scolapay.core.notifications.NotificationTransport;
import com.scolapay.core.notifications.Notifications;
import com.scolapay.core.notifications.internal.TestResendTransportFactory;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;

/** Sonde hors du scan de production, dans une JVM que la CI peut tuer brutalement. */
public final class NotificationProbeApplication {
    private NotificationProbeApplication() {}

    public static void main(String[] args) {
        new SpringApplicationBuilder(ScolaPayCoreApplication.class, ProbeConfiguration.class).run(args);
    }

    @Configuration(proxyBeanMethods = false)
    static class ProbeConfiguration {
        @Bean
        NotificationTransport probeTransport(@Value("${probe.email-endpoint}") URI endpoint,
                @Value("${probe.block-after-submit:false}") boolean block) {
            NotificationTransport delegate = TestResendTransportFactory.create(endpoint);
            return new NotificationTransport() {
                @Override public String name() { return delegate.name(); }
                @Override public boolean supports(Notifications.Channel channel) { return delegate.supports(channel); }
                @Override public Duration idempotencyWindow() { return delegate.idempotencyWindow(); }
                @Override public Result send(Message message) {
                    Result result = delegate.send(message);
                    if (block && result.outcome() == Outcome.SUBMITTED) {
                        try { new CountDownLatch(1).await(); }
                        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                    }
                    return result;
                }
            };
        }

        @Bean(name = {"applicationTaskExecutor", "taskExecutor"})
        @ConditionalOnProperty("probe.drop-dispatch")
        TaskExecutor droppingExecutor() { return task -> {}; }
    }
}
