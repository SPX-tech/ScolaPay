package com.scolapay.core.schooling.internal;

import com.scolapay.core.establishment.Establishments.Establishment;
import com.scolapay.core.schooling.internal.SchoolingRepository.Grant;
import com.scolapay.core.schooling.internal.SchoolingRepository.Student;
import java.time.Clock;

/**
 * Seule règle décidant si un lien donne accès aux informations académiques d'un dossier.
 * Elle est calculée à chaque lecture : le passage à la majorité prend effet le jour même, sans
 * traitement différé. Contribuer aux frais n'ouvre jamais cet accès.
 */
final class AcademicAccess {

    private AcademicAccess() {}

    static boolean allowed(Grant grant, Student student, Establishment establishment, Clock clock) {
        if ("SELF".equals(grant.kind())) {
            return true;
        }
        return switch (Majority.statusOf(student.birthDate(), establishment, clock)) {
            case MINOR -> grant.academicAccess();
            case ADULT -> grant.academicConsentAt() != null;
            case UNKNOWN -> false;
        };
    }
}
