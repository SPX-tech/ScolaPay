package com.scolapay.core.schooling.internal;

import com.scolapay.core.audit.AuditTrail;
import com.scolapay.core.establishment.EstablishmentAccess;
import com.scolapay.core.establishment.Establishments;
import com.scolapay.core.establishment.Permission;
import com.scolapay.core.identity.AccountDirectory;
import com.scolapay.core.identity.CurrentActor;
import com.scolapay.core.identity.Identifier;
import com.scolapay.core.identity.Invitations;
import com.scolapay.core.schooling.EnrollmentEnded;
import com.scolapay.core.schooling.StudentDirectory;
import com.scolapay.core.schooling.StudentEnrolled;
import com.scolapay.core.schooling.internal.SchoolingRepository.AcademicYear;
import com.scolapay.core.schooling.internal.SchoolingRepository.Enrollment;
import com.scolapay.core.schooling.internal.SchoolingRepository.Grant;
import com.scolapay.core.schooling.internal.SchoolingRepository.HistoryEntry;
import com.scolapay.core.schooling.internal.SchoolingRepository.Student;
import com.scolapay.core.shared.ApiException;
import com.scolapay.core.shared.PageQuery;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SchoolingService implements StudentDirectory {

    public record GrantView(
            UUID id,
            UUID accountId,
            String fullName,
            String kind,
            String relationship,
            boolean financiallyResponsible,
            Instant grantedAt,
            Instant revokedAt) {}

    public record FamilyStudent(
            UUID studentId,
            UUID establishmentId,
            String establishmentName,
            String studentNumber,
            String fullName,
            String access,
            String relationship,
            boolean financiallyResponsible,
            List<Enrollment> enrollments) {}

    private static final Map<String, Set<String>> YEAR_TRANSITIONS = Map.of(
            "OPEN", Set.of("PLANNED"),
            "CLOSED", Set.of("OPEN"));
    private static final Set<String> ENDED_STATUSES = Set.of("COMPLETED", "WITHDRAWN");

    private final SchoolingRepository repository;
    private final EstablishmentAccess access;
    private final Establishments establishments;
    private final Invitations invitations;
    private final AccountDirectory directory;
    private final CurrentActor actor;
    private final AuditTrail audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public SchoolingService(
            SchoolingRepository repository,
            EstablishmentAccess access,
            Establishments establishments,
            Invitations invitations,
            AccountDirectory directory,
            CurrentActor actor,
            AuditTrail audit,
            ApplicationEventPublisher events,
            Clock clock) {
        this.repository = repository;
        this.access = access;
        this.establishments = establishments;
        this.invitations = invitations;
        this.directory = directory;
        this.actor = actor;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    // Années académiques

    @Transactional
    public AcademicYear createYear(UUID establishmentId, String label, LocalDate startsOn, LocalDate endsOn) {
        UUID actorId = access.require(establishmentId, Permission.ACADEMIC_YEARS_MANAGE);
        if (!startsOn.isBefore(endsOn)) {
            throw ApiException.invalid("INVALID_PERIOD", "La date de début doit précéder la date de fin.");
        }
        AcademicYear year = new AcademicYear(UUID.randomUUID(), establishmentId, label.trim(), startsOn, endsOn, "PLANNED");
        repository.insertYear(year, clock.instant());
        audit.record(establishmentId, actorId, "schooling.year.created", "academic_year", year.id(), Map.of("label", year.label()));
        return year;
    }

    @Transactional(readOnly = true)
    public List<AcademicYear> listYears(UUID establishmentId) {
        access.require(establishmentId, Permission.ESTABLISHMENT_READ);
        return repository.listYears(establishmentId);
    }

    @Transactional
    public AcademicYear changeYearStatus(UUID establishmentId, UUID yearId, String status) {
        UUID actorId = access.require(establishmentId, Permission.ACADEMIC_YEARS_MANAGE);
        AcademicYear year = repository.findYear(establishmentId, yearId)
                .orElseThrow(() -> ApiException.notFound("ACADEMIC_YEAR_NOT_FOUND", "Année académique introuvable."));
        Set<String> allowedFrom = YEAR_TRANSITIONS.get(status);
        if (allowedFrom == null || !allowedFrom.contains(year.status())
                || repository.transitionYear(establishmentId, yearId, year.status(), status, clock.instant()) == 0) {
            throw ApiException.conflict("INVALID_TRANSITION", "Cette transition de statut n'est pas autorisée.");
        }
        audit.record(
                establishmentId, actorId, "schooling.year.status.changed", "academic_year", yearId,
                Map.of("from", year.status(), "to", status));
        return repository.findYear(establishmentId, yearId).orElseThrow();
    }

    // Dossiers

    @Transactional
    public Student createStudent(UUID establishmentId, String studentNumber, String fullName, LocalDate birthDate) {
        UUID actorId = access.require(establishmentId, Permission.STUDENTS_MANAGE);
        if (birthDate != null && !birthDate.isBefore(today(establishmentId))) {
            throw ApiException.invalid("INVALID_BIRTH_DATE", "La date de naissance doit être dans le passé.");
        }
        Student student = new Student(
                UUID.randomUUID(), establishmentId, studentNumber.trim(), fullName.trim(), birthDate, "ACTIVE");
        repository.insertStudent(student, clock.instant());
        audit.record(establishmentId, actorId, "schooling.student.created", "student_record", student.id(), Map.of());
        return student;
    }

    @Transactional(readOnly = true)
    public List<Student> listStudents(UUID establishmentId, String search, PageQuery page) {
        access.require(establishmentId, Permission.STUDENTS_READ);
        String normalized = search == null || search.isBlank() ? null : search.trim();
        return repository.listStudents(establishmentId, normalized, page);
    }

    @Transactional(readOnly = true)
    public Student readStudent(UUID establishmentId, UUID studentId) {
        access.require(establishmentId, Permission.STUDENTS_READ);
        return requireStudent(establishmentId, studentId);
    }

    // Inscriptions

    @Transactional
    public Enrollment enroll(UUID establishmentId, UUID studentId, UUID academicYearId, String level) {
        UUID actorId = access.require(establishmentId, Permission.ENROLLMENTS_MANAGE);
        Student student = requireStudent(establishmentId, studentId);
        if (!"ACTIVE".equals(student.status())) {
            throw ApiException.conflict("STUDENT_NOT_ACTIVE", "Ce dossier n'est pas actif.");
        }
        AcademicYear year = repository.findYear(establishmentId, academicYearId)
                .orElseThrow(() -> ApiException.notFound("ACADEMIC_YEAR_NOT_FOUND", "Année académique introuvable."));
        if (!"OPEN".equals(year.status())) {
            throw ApiException.conflict("ACADEMIC_YEAR_NOT_OPEN", "Cette année académique n'est pas ouverte aux inscriptions.");
        }
        UUID enrollmentId = UUID.randomUUID();
        Instant now = clock.instant();
        String trimmedLevel = level.trim();
        // L'index unique partiel tranche les doubles inscriptions concurrentes : le perdant reçoit un 409.
        repository.insertEnrollment(enrollmentId, establishmentId, studentId, academicYearId, trimmedLevel, now);
        repository.appendHistory(establishmentId, enrollmentId, null, "ACTIVE", null, actorId, now);
        audit.record(
                establishmentId, actorId, "schooling.enrollment.created", "enrollment", enrollmentId,
                Map.of("studentId", studentId, "academicYearId", academicYearId));
        events.publishEvent(new StudentEnrolled(establishmentId, studentId, enrollmentId, academicYearId, trimmedLevel, now));
        return repository.listEnrollments(establishmentId, studentId).stream()
                .filter(enrollment -> enrollment.id().equals(enrollmentId))
                .findFirst()
                .orElseThrow();
    }

    @Transactional
    public void endEnrollment(UUID establishmentId, UUID studentId, UUID enrollmentId, String status, String reason) {
        UUID actorId = access.require(establishmentId, Permission.ENROLLMENTS_MANAGE);
        if (!ENDED_STATUSES.contains(status)) {
            throw ApiException.invalid("INVALID_STATUS", "Le statut final doit être COMPLETED ou WITHDRAWN.");
        }
        Instant now = clock.instant();
        if (repository.endEnrollment(establishmentId, studentId, enrollmentId, status, now) == 0) {
            throw ApiException.notFound("ENROLLMENT_NOT_FOUND", "Inscription active introuvable.");
        }
        repository.appendHistory(establishmentId, enrollmentId, "ACTIVE", status, reason, actorId, now);
        audit.record(
                establishmentId, actorId, "schooling.enrollment.ended", "enrollment", enrollmentId,
                Map.of("status", status));
        events.publishEvent(new EnrollmentEnded(establishmentId, studentId, enrollmentId, status, now));
    }

    @Transactional(readOnly = true)
    public List<Enrollment> listEnrollments(UUID establishmentId, UUID studentId) {
        access.require(establishmentId, Permission.STUDENTS_READ);
        requireStudent(establishmentId, studentId);
        return repository.listEnrollments(establishmentId, studentId);
    }

    @Transactional(readOnly = true)
    public List<HistoryEntry> enrollmentHistory(UUID establishmentId, UUID studentId, UUID enrollmentId) {
        access.require(establishmentId, Permission.STUDENTS_READ);
        requireStudent(establishmentId, studentId);
        return repository.history(establishmentId, studentId, enrollmentId);
    }

    // Accès des comptes aux dossiers

    @Transactional
    public Invitations.Issued inviteGuardian(
            UUID establishmentId, UUID studentId, String contact, String relationship, boolean financiallyResponsible) {
        UUID actorId = access.require(establishmentId, Permission.GUARDIANS_MANAGE);
        requireStudent(establishmentId, studentId);
        return invitations.issue(new Invitations.NewInvitation(
                Identifier.parse(contact),
                GuardianInvitationHandler.PURPOSE,
                establishmentId,
                studentId,
                Map.of(
                        GuardianInvitationHandler.RELATIONSHIP_ATTRIBUTE, relationship,
                        GuardianInvitationHandler.FINANCIAL_ATTRIBUTE, String.valueOf(financiallyResponsible)),
                actorId));
    }

    @Transactional
    public Invitations.Issued inviteAdultStudent(UUID establishmentId, UUID studentId, String contact) {
        UUID actorId = access.require(establishmentId, Permission.GUARDIANS_MANAGE);
        Student student = requireStudent(establishmentId, studentId);
        Establishments.Establishment establishment = establishments.find(establishmentId).orElseThrow();
        if (!Majority.isReached(student.birthDate(), establishment, clock)) {
            throw ApiException.conflict(
                    "STUDENT_NOT_ADULT", "L'étudiant doit avoir atteint l'âge de la majorité fixé par l'établissement.");
        }
        return invitations.issue(new Invitations.NewInvitation(
                Identifier.parse(contact), SelfAccessInvitationHandler.PURPOSE, establishmentId, studentId, Map.of(), actorId));
    }

    @Transactional(readOnly = true)
    public List<GrantView> listGrants(UUID establishmentId, UUID studentId) {
        access.require(establishmentId, Permission.STUDENTS_READ);
        requireStudent(establishmentId, studentId);
        List<Grant> grants = repository.grantsOfStudent(establishmentId, studentId);
        Map<UUID, AccountDirectory.AccountSummary> accounts =
                directory.findAll(grants.stream().map(Grant::accountId).distinct().toList());
        return grants.stream()
                .map(grant -> new GrantView(
                        grant.id(),
                        grant.accountId(),
                        accounts.get(grant.accountId()).fullName(),
                        grant.kind(),
                        grant.relationship(),
                        grant.financiallyResponsible(),
                        grant.grantedAt(),
                        grant.revokedAt()))
                .toList();
    }

    @Transactional
    public void revokeGrant(UUID establishmentId, UUID studentId, UUID grantId) {
        UUID actorId = access.require(establishmentId, Permission.GUARDIANS_MANAGE);
        if (repository.revokeGrant(establishmentId, studentId, grantId, actorId, clock.instant()) == 0) {
            throw ApiException.notFound("ACCESS_GRANT_NOT_FOUND", "Accès introuvable ou déjà révoqué.");
        }
        audit.record(establishmentId, actorId, "schooling.access.revoked", "record_access_grant", grantId, Map.of());
    }

    // Surface familles : tout part du compte connecté, sans paramètre d'établissement.

    @Transactional(readOnly = true)
    public List<FamilyStudent> myStudents() {
        UUID accountId = actor.requireAccountId();
        return repository.activeGrantsOfAccount(accountId).stream()
                .map(this::toFamilyStudent)
                .flatMap(Optional::stream)
                .toList();
    }

    @Transactional(readOnly = true)
    public FamilyStudent myStudent(UUID studentId) {
        UUID accountId = actor.requireAccountId();
        return repository.activeGrant(accountId, studentId)
                .flatMap(this::toFamilyStudent)
                .orElseThrow(() -> ApiException.notFound("STUDENT_NOT_FOUND", "Dossier introuvable."));
    }

    /** Un établissement suspendu disparaît aussi pour les familles. */
    private Optional<FamilyStudent> toFamilyStudent(Grant grant) {
        return establishments.find(grant.establishmentId())
                .filter(Establishments.Establishment::active)
                .flatMap(establishment -> repository.findStudent(establishment.id(), grant.studentId())
                        .map(student -> new FamilyStudent(
                                student.id(),
                                establishment.id(),
                                establishment.name(),
                                student.studentNumber(),
                                student.fullName(),
                                grant.kind(),
                                grant.relationship(),
                                grant.financiallyResponsible(),
                                repository.listEnrollments(establishment.id(), student.id()))));
    }

    // Frontière publiée

    @Override
    @Transactional(readOnly = true)
    public Optional<StudentSummary> find(UUID establishmentId, UUID studentId) {
        return repository.findStudent(establishmentId, studentId)
                .map(student -> new StudentSummary(
                        student.id(), student.establishmentId(), student.studentNumber(), student.fullName(), student.status()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> financiallyResponsibleAccounts(UUID establishmentId, UUID studentId) {
        return repository.financiallyResponsibleAccounts(establishmentId, studentId);
    }

    private Student requireStudent(UUID establishmentId, UUID studentId) {
        return repository.findStudent(establishmentId, studentId)
                .orElseThrow(() -> ApiException.notFound("STUDENT_NOT_FOUND", "Dossier introuvable."));
    }

    private LocalDate today(UUID establishmentId) {
        return Majority.today(establishments.find(establishmentId).orElseThrow(), clock);
    }
}
