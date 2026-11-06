package com.scolapay.core.schooling.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.scolapay.core.establishment.Establishments.Establishment;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MajorityTest {

    private static Establishment establishment(int legalAge, String timezone) {
        return new Establishment(UUID.randomUUID(), "E-1", "Lycée", "SCHOOL", timezone, "XAF", "TD", legalAge, "ACTIVE");
    }

    private static Clock at(String instant) {
        return Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
    }

    @Test
    void laMajoriteEstAtteinteLeJourAnniversaire() {
        Establishment school = establishment(18, "UTC");
        Clock clock = at("2026-06-15T12:00:00Z");
        assertThat(Majority.isReached(LocalDate.of(2008, 6, 15), school, clock)).isTrue();
        assertThat(Majority.isReached(LocalDate.of(2008, 6, 16), school, clock)).isFalse();
    }

    @Test
    void laDateDeNaissanceInconnueNEtablitNiLaMajoriteNiLaMinorite() {
        assertThat(Majority.statusOf(null, establishment(18, "UTC"), at("2026-06-15T12:00:00Z"))).isEqualTo(Majority.Status.UNKNOWN);
        assertThat(Majority.isReached(null, establishment(18, "UTC"), at("2026-06-15T12:00:00Z"))).isFalse();
    }

    @Test
    void laVeilleDeLaMajoriteLEleveEstMineur() {
        Clock clock = at("2026-06-14T12:00:00Z");
        assertThat(Majority.statusOf(LocalDate.of(2008, 6, 15), establishment(18, "UTC"), clock)).isEqualTo(Majority.Status.MINOR);
    }

    @Test
    void leJourEstCeluiDuFuseauDeLEtablissement() {
        // 23 h 30 UTC le 14 : on est déjà le 15 à N'Djamena, encore le 14 à UTC.
        Clock clock = at("2026-06-14T23:30:00Z");
        LocalDate birthDate = LocalDate.of(2008, 6, 15);
        assertThat(Majority.isReached(birthDate, establishment(18, "Africa/Ndjamena"), clock)).isTrue();
        assertThat(Majority.isReached(birthDate, establishment(18, "UTC"), clock)).isFalse();
    }

    @Test
    void lAgeAppliqueEstCeluiDeLaRegleLegale() {
        Clock clock = at("2026-06-15T12:00:00Z");
        LocalDate birthDate = LocalDate.of(2006, 1, 1);
        assertThat(Majority.isReached(birthDate, establishment(18, "UTC"), clock)).isTrue();
        assertThat(Majority.isReached(birthDate, establishment(21, "UTC"), clock)).isFalse();
    }
}
