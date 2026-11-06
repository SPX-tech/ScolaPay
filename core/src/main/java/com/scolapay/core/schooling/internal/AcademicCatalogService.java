package com.scolapay.core.schooling.internal;

import com.scolapay.core.schooling.AcademicCatalog;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AcademicCatalogService implements AcademicCatalog {

    private final SchoolingRepository schooling;
    private final AcademicUnitRepository units;

    AcademicCatalogService(SchoolingRepository schooling, AcademicUnitRepository units) {
        this.schooling = schooling;
        this.units = units;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<YearSummary> findYear(UUID establishmentId, UUID academicYearId) {
        return schooling.findYear(establishmentId, academicYearId)
                .map(year -> new YearSummary(year.id(), year.label(), year.startsOn(), year.endsOn(), year.status()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UnitSummary> units(UUID establishmentId) {
        return units.list(establishmentId).stream()
                .map(unit -> new UnitSummary(unit.id(), unit.parentId(), unit.kind(), unit.code(), unit.name(), unit.status()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Long> activeEnrollmentsByUnit(UUID establishmentId, UUID academicYearId) {
        return schooling.activeEnrollmentsByUnit(establishmentId, academicYearId);
    }
}
