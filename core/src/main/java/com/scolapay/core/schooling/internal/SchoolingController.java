package com.scolapay.core.schooling.internal;

import com.scolapay.core.identity.Invitations;
import com.scolapay.core.schooling.internal.SchoolingRepository.AcademicYear;
import com.scolapay.core.schooling.internal.SchoolingRepository.Enrollment;
import com.scolapay.core.schooling.internal.SchoolingRepository.HistoryEntry;
import com.scolapay.core.schooling.internal.SchoolingRepository.Student;
import com.scolapay.core.shared.PageQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class SchoolingController {

    private static final String BASE = "/api/v2/establishments/{establishmentId}";
    private static final String STUDENT = BASE + "/students/{studentId}";

    record CreateYearRequest(
            @NotBlank @Size(min = 2, max = 50) String label,
            @NotNull LocalDate startsOn,
            @NotNull LocalDate endsOn) {}

    record ChangeYearStatusRequest(@NotBlank @Size(max = 20) String status) {}

    record CreateStudentRequest(
            @NotBlank @Size(max = 64) String studentNumber,
            @NotBlank @Size(min = 2, max = 200) String fullName,
            LocalDate birthDate) {}

    record EnrollRequest(@NotNull UUID academicYearId, @NotNull UUID academicUnitId, UUID confirmedFeeCatalogVersionId) {}

    record EndEnrollmentRequest(@NotBlank @Size(max = 20) String status, @Size(max = 500) String reason) {}

    record ThirdPartyInvitationRequest(
            @NotBlank @Size(max = 254) String contact,
            @NotBlank @Pattern(regexp = "^(MOTHER|FATHER|GUARDIAN|OTHER)$") String relationship,
            boolean academicAccess,
            boolean financialContributor) {}

    record SelfAccessInvitationRequest(@NotBlank @Size(max = 254) String contact) {}

    private final SchoolingService schooling;

    SchoolingController(SchoolingService schooling) {
        this.schooling = schooling;
    }

    @PostMapping(BASE + "/academic-years")
    @ResponseStatus(HttpStatus.CREATED)
    AcademicYear createYear(@PathVariable UUID establishmentId, @Valid @RequestBody CreateYearRequest request) {
        return schooling.createYear(establishmentId, request.label(), request.startsOn(), request.endsOn());
    }

    @GetMapping(BASE + "/academic-years")
    List<AcademicYear> listYears(@PathVariable UUID establishmentId) {
        return schooling.listYears(establishmentId);
    }

    @PatchMapping(BASE + "/academic-years/{yearId}")
    AcademicYear changeYearStatus(
            @PathVariable UUID establishmentId,
            @PathVariable UUID yearId,
            @Valid @RequestBody ChangeYearStatusRequest request) {
        return schooling.changeYearStatus(establishmentId, yearId, request.status());
    }

    @PostMapping(BASE + "/students")
    @ResponseStatus(HttpStatus.CREATED)
    Student createStudent(@PathVariable UUID establishmentId, @Valid @RequestBody CreateStudentRequest request) {
        return schooling.createStudent(establishmentId, request.studentNumber(), request.fullName(), request.birthDate());
    }

    @GetMapping(BASE + "/students")
    List<Student> listStudents(
            @PathVariable UUID establishmentId,
            @RequestParam(required = false) @Size(max = 100) String search,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset) {
        return schooling.listStudents(establishmentId, search, PageQuery.of(limit, offset));
    }

    @GetMapping(STUDENT)
    Student readStudent(@PathVariable UUID establishmentId, @PathVariable UUID studentId) {
        return schooling.readStudent(establishmentId, studentId);
    }

    @PostMapping(STUDENT + "/enrollments")
    @ResponseStatus(HttpStatus.CREATED)
    Enrollment enroll(
            @PathVariable UUID establishmentId,
            @PathVariable UUID studentId,
            @Valid @RequestBody EnrollRequest request) {
        return schooling.enroll(
                establishmentId, studentId, request.academicYearId(), request.academicUnitId(), request.confirmedFeeCatalogVersionId());
    }

    @GetMapping(STUDENT + "/enrollments")
    List<Enrollment> listEnrollments(@PathVariable UUID establishmentId, @PathVariable UUID studentId) {
        return schooling.listEnrollments(establishmentId, studentId);
    }

    @PostMapping(STUDENT + "/enrollments/{enrollmentId}/end")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void endEnrollment(
            @PathVariable UUID establishmentId,
            @PathVariable UUID studentId,
            @PathVariable UUID enrollmentId,
            @Valid @RequestBody EndEnrollmentRequest request) {
        schooling.endEnrollment(establishmentId, studentId, enrollmentId, request.status(), request.reason());
    }

    @GetMapping(STUDENT + "/enrollments/{enrollmentId}/history")
    List<HistoryEntry> enrollmentHistory(
            @PathVariable UUID establishmentId, @PathVariable UUID studentId, @PathVariable UUID enrollmentId) {
        return schooling.enrollmentHistory(establishmentId, studentId, enrollmentId);
    }

    @PostMapping(STUDENT + "/third-party-invitations")
    @ResponseStatus(HttpStatus.CREATED)
    Invitations.Issued inviteThirdParty(
            @PathVariable UUID establishmentId,
            @PathVariable UUID studentId,
            @Valid @RequestBody ThirdPartyInvitationRequest request) {
        return schooling.inviteThirdParty(
                establishmentId,
                studentId,
                request.contact(),
                request.relationship(),
                request.academicAccess(),
                request.financialContributor());
    }

    @PostMapping(STUDENT + "/self-access-invitations")
    @ResponseStatus(HttpStatus.CREATED)
    Invitations.Issued inviteAdultStudent(
            @PathVariable UUID establishmentId,
            @PathVariable UUID studentId,
            @Valid @RequestBody SelfAccessInvitationRequest request) {
        return schooling.inviteAdultStudent(establishmentId, studentId, request.contact());
    }

    @GetMapping(STUDENT + "/access-grants")
    List<SchoolingService.GrantView> listGrants(@PathVariable UUID establishmentId, @PathVariable UUID studentId) {
        return schooling.listGrants(establishmentId, studentId);
    }

    @DeleteMapping(STUDENT + "/access-grants/{grantId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revokeGrant(
            @PathVariable UUID establishmentId, @PathVariable UUID studentId, @PathVariable UUID grantId) {
        schooling.revokeGrant(establishmentId, studentId, grantId);
    }
}
