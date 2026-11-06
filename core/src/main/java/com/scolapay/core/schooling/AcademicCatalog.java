package com.scolapay.core.schooling;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Structure académique publiée pour la facturation. L'établissement est obligatoire dans chaque
 * appel : l'isolation fait partie du contrat. Le contrôle d'accès reste à la charge de l'appelant.
 */
public interface AcademicCatalog {

    record YearSummary(UUID id, String label, LocalDate startsOn, LocalDate endsOn, String status) {

        public boolean closed() {
            return "CLOSED".equals(status);
        }
    }

    record UnitSummary(UUID id, UUID parentId, String kind, String code, String name, String status) {

        public boolean active() {
            return "ACTIVE".equals(status);
        }
    }

    record EnrollmentSummary(UUID id, UUID studentId, UUID academicYearId, UUID academicUnitId, String status, Instant enrolledAt) {

        public boolean active() {
            return "ACTIVE".equals(status);
        }
    }

    Optional<YearSummary> findYear(UUID establishmentId, UUID academicYearId);

    List<UnitSummary> units(UUID establishmentId);

    Optional<EnrollmentSummary> findEnrollment(UUID establishmentId, UUID enrollmentId);

    /** Les inscriptions actives de l'année, limitées à ces unités si elles sont indiquées. */
    List<EnrollmentSummary> activeEnrollments(UUID establishmentId, UUID academicYearId, List<UUID> unitsOrAll, int limit);

    /** Toutes les inscriptions de l'année, y compris celles qui sont terminées. */
    List<EnrollmentSummary> enrollments(UUID establishmentId, UUID academicYearId, int limit);

    /** Nombre d'inscriptions actives de l'année, par unité académique. */
    Map<UUID, Long> activeEnrollmentsByUnit(UUID establishmentId, UUID academicYearId);
}
