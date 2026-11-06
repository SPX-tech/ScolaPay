package com.scolapay.core.schooling.internal;

import com.scolapay.core.audit.AuditTrail;
import com.scolapay.core.establishment.EstablishmentAccess;
import com.scolapay.core.establishment.Permission;
import com.scolapay.core.schooling.internal.AcademicUnitRepository.AcademicUnit;
import com.scolapay.core.shared.ApiException;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AcademicUnitService {

    private static final String ACTIVE = "ACTIVE";
    private static final String ARCHIVED = "ARCHIVED";
    private static final String PROGRAM = "PROGRAM";

    private final AcademicUnitRepository repository;
    private final EstablishmentAccess access;
    private final AuditTrail audit;
    private final Clock clock;

    public AcademicUnitService(AcademicUnitRepository repository, EstablishmentAccess access, AuditTrail audit, Clock clock) {
        this.repository = repository;
        this.access = access;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public AcademicUnit create(UUID establishmentId, UUID parentId, String kind, String code, String name) {
        UUID actorId = access.require(establishmentId, Permission.ACADEMIC_CATALOG_MANAGE);
        if (parentId != null) {
            AcademicUnit parent = lockExisting(establishmentId, parentId);
            if (!PROGRAM.equals(parent.kind())) {
                throw ApiException.invalid("PARENT_NOT_PROGRAM", "Seul un programme peut regrouper des unités académiques.");
            }
            requireActive(parent);
        }
        AcademicUnit unit = new AcademicUnit(UUID.randomUUID(), establishmentId, parentId, kind, code, requireName(name), ACTIVE);
        repository.insert(unit, clock.instant());
        audit.record(
                establishmentId, actorId, "schooling.academic_unit.created", "academic_unit", unit.id(),
                Map.of("kind", kind, "code", code));
        return unit;
    }

    @Transactional(readOnly = true)
    public List<AcademicUnit> list(UUID establishmentId) {
        access.require(establishmentId, Permission.ESTABLISHMENT_READ);
        return repository.list(establishmentId);
    }

    /** Une unité ne se supprime pas et ne change ni de code ni de parent : on la renomme ou on l'archive. */
    @Transactional
    public AcademicUnit update(UUID establishmentId, UUID unitId, String name, String status) {
        UUID actorId = access.require(establishmentId, Permission.ACADEMIC_CATALOG_MANAGE);
        if (name == null && status == null) {
            throw ApiException.invalid("NOTHING_TO_UPDATE", "Indiquez un nom ou un statut.");
        }
        AcademicUnit unit = lockExisting(establishmentId, unitId);
        String newName = name == null ? unit.name() : requireName(name);
        String newStatus = status == null ? unit.status() : status;
        if (ARCHIVED.equals(newStatus) && !ARCHIVED.equals(unit.status()) && repository.hasActiveChildren(establishmentId, unitId)) {
            throw ApiException.conflict(
                    "ACADEMIC_UNIT_HAS_ACTIVE_CHILDREN", "Archivez d'abord les unités rattachées à celle-ci.");
        }
        if (ACTIVE.equals(newStatus) && !ACTIVE.equals(unit.status()) && unit.parentId() != null) {
            requireActive(lockExisting(establishmentId, unit.parentId()));
        }
        repository.update(establishmentId, unitId, newName, newStatus, clock.instant());
        audit.record(
                establishmentId, actorId, "schooling.academic_unit.updated", "academic_unit", unitId,
                Map.of("status", newStatus, "renamed", !newName.equals(unit.name())));
        return new AcademicUnit(unitId, establishmentId, unit.parentId(), unit.kind(), unit.code(), newName, newStatus);
    }

    private AcademicUnit lockExisting(UUID establishmentId, UUID unitId) {
        return repository.lock(establishmentId, unitId)
                .orElseThrow(() -> ApiException.notFound("ACADEMIC_UNIT_NOT_FOUND", "Unité académique introuvable."));
    }

    private static void requireActive(AcademicUnit unit) {
        if (!ACTIVE.equals(unit.status())) {
            throw ApiException.conflict("ACADEMIC_UNIT_ARCHIVED", "Cette unité académique est archivée.");
        }
    }

    private static String requireName(String name) {
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            throw ApiException.invalid("INVALID_NAME", "Le nom ne peut pas être vide.");
        }
        return trimmed;
    }
}
