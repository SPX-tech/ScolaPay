package com.scolapay.core.schooling.internal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Surface des parents, tuteurs et étudiants. Elle ne prend aucun identifiant d'établissement :
 * un compte n'y voit que les dossiers auxquels un lien actif le rattache.
 */
@RestController
@RequestMapping("/api/v2/me/students")
class FamilyController {

    record ConsentRequest(@NotNull Boolean academicAccess) {}

    private final SchoolingService schooling;

    FamilyController(SchoolingService schooling) {
        this.schooling = schooling;
    }

    @GetMapping
    List<SchoolingService.FamilyStudent> myStudents() {
        return schooling.myStudents();
    }

    @GetMapping("/{studentId}")
    SchoolingService.FamilyStudent myStudent(@PathVariable UUID studentId) {
        return schooling.myStudent(studentId);
    }

    @GetMapping("/{studentId}/third-parties")
    List<SchoolingService.ThirdPartyView> myThirdParties(@PathVariable UUID studentId) {
        return schooling.myThirdParties(studentId);
    }

    @PatchMapping("/{studentId}/third-parties/{grantId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void setAcademicConsent(
            @PathVariable UUID studentId, @PathVariable UUID grantId, @Valid @RequestBody ConsentRequest request) {
        schooling.setAcademicConsent(studentId, grantId, request.academicAccess());
    }
}
