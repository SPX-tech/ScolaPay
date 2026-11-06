package com.scolapay.core.schooling.internal;

import com.scolapay.core.establishment.Establishments.Establishment;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/** L'âge de la majorité est une règle de l'établissement, évaluée dans son fuseau. */
final class Majority {

    private Majority() {}

    static LocalDate today(Establishment establishment, Clock clock) {
        return clock.instant().atZone(ZoneId.of(establishment.timezone())).toLocalDate();
    }

    /** Une date de naissance inconnue n'établit pas la majorité. */
    static boolean isReached(LocalDate birthDate, Establishment establishment, Clock clock) {
        return birthDate != null
                && !birthDate.plusYears(establishment.ageOfMajority()).isAfter(today(establishment, clock));
    }
}
