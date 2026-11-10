package com.scolapay.outboxprobe;

import com.scolapay.core.ScolaPayCoreApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * L'application réelle, plus un listener de sonde, lancée dans une JVM séparée pour pouvoir la tuer
 * brutalement et en faire tourner deux en même temps. Hors de {@code com.scolapay.core} : cette sonde
 * n'existe dans aucun autre test.
 */
public final class ProbeApplication {

    private ProbeApplication() {}

    public static void main(String[] args) {
        new SpringApplicationBuilder(ScolaPayCoreApplication.class, ProbeConfiguration.class,
                com.scolapay.testsupport.TestInvitationDelivery.class).run(args);
    }

    @Configuration(proxyBeanMethods = false)
    static class ProbeConfiguration {

        @Bean
        ProbeListener probeListener(
                JdbcClient jdbc, PlatformTransactionManager transactions, @Value("${probe.instance}") String instance) {
            return new ProbeListener(jdbc, transactions, instance);
        }

        /** Ne lance jamais le listener : reproduit un arrêt entre la validation et le début du traitement. */
        @Bean(name = {"applicationTaskExecutor", "taskExecutor"})
        @ConditionalOnProperty("probe.drop-dispatch")
        TaskExecutor droppingExecutor() {
            return task -> {};
        }
    }
}
