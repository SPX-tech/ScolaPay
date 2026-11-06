package com.scolapay.core.schooling;

import java.util.List;
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

    /**
     * Les comptes autorisés à contribuer aux frais de l'élève. Ce droit est distinct de l'accès
     * aux informations académiques : un contributeur peut n'avoir aucun accès au dossier.
     */
    List<UUID> financialContributorAccounts(UUID establishmentId, UUID studentId);
}
