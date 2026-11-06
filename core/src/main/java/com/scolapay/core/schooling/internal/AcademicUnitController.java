package com.scolapay.core.schooling.internal;

import com.scolapay.core.schooling.internal.AcademicUnitRepository.AcademicUnit;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/establishments/{establishmentId}/academic-units")
class AcademicUnitController {

    record CreateRequest(
            UUID parentId,
            @NotNull @Pattern(regexp = "^(PROGRAM|LEVEL)$") String kind,
            @NotNull @Pattern(regexp = "^[A-Z0-9][A-Z0-9-]{0,30}$") String code,
            @NotBlank @Size(max = 100) String name) {}

    record UpdateRequest(@Size(max = 100) String name, @Pattern(regexp = "^(ACTIVE|ARCHIVED)$") String status) {}

    private final AcademicUnitService units;

    AcademicUnitController(AcademicUnitService units) {
        this.units = units;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    AcademicUnit create(@PathVariable UUID establishmentId, @Valid @RequestBody CreateRequest request) {
        return units.create(establishmentId, request.parentId(), request.kind(), request.code(), request.name());
    }

    @GetMapping
    List<AcademicUnit> list(@PathVariable UUID establishmentId) {
        return units.list(establishmentId);
    }

    @PatchMapping("/{unitId}")
    AcademicUnit update(
            @PathVariable UUID establishmentId, @PathVariable UUID unitId, @Valid @RequestBody UpdateRequest request) {
        return units.update(establishmentId, unitId, request.name(), request.status());
    }
}
