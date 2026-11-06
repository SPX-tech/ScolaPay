package com.scolapay.core.schooling.internal;

import com.scolapay.core.establishment.Establishments.Establishment;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/** L'âge légal vient du pays de l'établissement. Le jour s'évalue dans le fuseau de l'établissement. */
final class Majority {

    enum Status {
        MINOR,
        ADULT,
        UNKNOWN
    }

    private Majority() {}

    /** Une date de naissance inconnue n'établit ni la majorité ni la minorité. */
    static Status statusOf(LocalDate birthDate, Establishment establishment, Clock clock) {
        if (birthDate == null) {
            return Status.UNKNOWN;
        }
        boolean adult = !birthDate.plusYears(establishment.legalAgeOfMajority()).isAfter(today(establishment, clock));
        return adult ? Status.ADULT : Status.MINOR;
    }

    static LocalDate today(Establishment establishment, Clock clock) {
        return clock.instant().atZone(ZoneId.of(establishment.timezone())).toLocalDate();
    }

    static boolean isReached(LocalDate birthDate, Establishment establishment, Clock clock) {
        return statusOf(birthDate, establishment, clock) == Status.ADULT;
    }
}
