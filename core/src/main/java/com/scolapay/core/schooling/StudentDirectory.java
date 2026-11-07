package com.scolapay.core.schooling;

import java.util.Optional;
import java.util.UUID;

/**
 * Lecture publiée pour les modules métier suivants, en premier lieu la facturation.
 * L'établissement est obligatoire dans chaque appel : l'isolation fait partie du contrat.
 * Le contrôle d'accès reste à la charge du module appelant.
 */
public interface StudentDirectory {

    record StudentSummary(UUID id, UUID establishmentId, String studentNumber, String fullName, String status) {}

    Optional<StudentSummary> find(UUID establishmentId, UUID studentId);

    /** {@code academicAccess} applique la règle d'accès académique du jour : majorité et consentement compris. */
    record StudentLink(boolean academicAccess) {}

    /** Le lien actif entre un compte et un dossier. L'absence de lien ferme tout accès académique. */
    Optional<StudentLink> linkOf(UUID establishmentId, UUID studentId, UUID accountId);

    /** Le compte derrière un lien actif de cet établissement. */
    Optional<UUID> accountOfLink(UUID establishmentId, UUID grantId);
}
