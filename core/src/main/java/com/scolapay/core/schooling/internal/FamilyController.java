package com.scolapay.core.schooling.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Surface des parents, tuteurs et étudiants. Elle ne prend aucun identifiant d'établissement :
 * un compte n'y voit que les dossiers auxquels un lien actif le rattache.
 */
@RestController
@RequestMapping("/api/v2/me/students")
class FamilyController {

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
}
