package com.scolapay.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * {@link OutboxReliabilityTest} impose ses propres durées pour aller vite : ce test protège celles de
 * production, sans lesquelles une publication interrompue par un arrêt brutal ne serait jamais reprise.
 */
class OutboxConfigurationTest extends IntegrationTest {

    @Test
    void lesPublicationsBloqueesSontSurveilleesDansTousLesEtats() {
        for (String state : new String[] {"published", "processing", "resubmitted"}) {
            assertThat(environment.getProperty("spring.modulith.events.staleness." + state))
                    .as(state)
                    .isNotBlank();
        }
    }

    @Test
    void lesPublicationsTraiteesSontArchivees() {
        assertThat(environment.getProperty("spring.modulith.events.completion-mode")).isEqualTo("archive");
    }

    @Test
    void leSchemaVientDesMigrationsEtNonDeLaBibliotheque() {
        assertThat(environment.getProperty("spring.modulith.events.jdbc.schema-initialization.enabled")).isEqualTo("false");
        assertThat(jdbc.sql("SELECT count(*) FROM flyway_schema_history WHERE script = 'V2__event_publication.sql' AND success")
                        .query(Long.class)
                        .single())
                .isEqualTo(1);
    }
}
