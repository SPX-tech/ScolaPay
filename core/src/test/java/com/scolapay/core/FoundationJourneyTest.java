package com.scolapay.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.scolapay.core.schooling.EnrollmentEnded;
import com.scolapay.core.schooling.StudentDirectory;
import com.scolapay.core.schooling.StudentEnrolled;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/** Le parcours métier de la fondation, de la création d'un établissement à l'accès d'un parent. */
class FoundationJourneyTest extends IntegrationTest {

    @Autowired
    StudentDirectory directory;

    @Test
    void parcoursComplet_duNouvelEtablissementAuDossierConsulteParLeParent() {
        // L'opérateur crée l'établissement et invite son administrateur.
        String adminContact = uniqueEmail();
        Response created = createEstablishment(adminContact);
        assertThat(created.status()).as(created.raw()).isEqualTo(201);
        UUID establishmentId = UUID.fromString(created.body().path("establishment").path("id").asString());
        String path = "/api/v2/establishments/" + establishmentId;

        // L'administrateur active son compte et le configure.
        acceptAsNewAccount(created.body().path("firstAdminInvitation").path("token").asString(), "Fatimé Oumar");
        String admin = login(adminContact, PASSWORD);
        Response affiliations = get("/api/v2/me/affiliations", admin);
        assertThat(affiliations.body().get(0).path("establishment").path("id").asString()).isEqualTo(establishmentId.toString());
        assertThat(affiliations.body().get(0).path("roles").get(0).asString()).isEqualTo("ESTABLISHMENT_ADMIN");

        // Année académique, puis dossier.
        Response year = post(path + "/academic-years", admin, Map.of(
                "label", "2026-2027", "startsOn", "2026-09-01", "endsOn", "2027-07-15"));
        assertThat(year.status()).as(year.raw()).isEqualTo(201);
        UUID yearId = year.id("id");
        assertThat(patch(path + "/academic-years/" + yearId, admin, Map.of("status", "OPEN")).status()).isEqualTo(200);

        Response student = post(path + "/students", admin, Map.of(
                "studentNumber", "ETU-0142", "fullName", "Achta Mahamat", "birthDate", "2012-03-14"));
        assertThat(student.status()).as(student.raw()).isEqualTo(201);
        UUID studentId = student.id("id");

        // Inscription, avec son historique et l'événement destiné à la facturation.
        Response enrollment = post(path + "/students/" + studentId + "/enrollments", admin, Map.of(
                "academicYearId", yearId.toString(), "level", "Terminale D"));
        assertThat(enrollment.status()).as(enrollment.raw()).isEqualTo(201);
        assertThat(enrollment.text("status")).isEqualTo("ACTIVE");
        assertThat(enrollment.text("academicYearLabel")).isEqualTo("2026-2027");
        UUID enrollmentId = enrollment.id("id");
        assertThat(events.published).anySatisfy(event -> {
            assertThat(event).isInstanceOf(StudentEnrolled.class);
            assertThat(((StudentEnrolled) event).enrollmentId()).isEqualTo(enrollmentId);
        });

        // Le parent reçoit une invitation et ouvre son compte avec son téléphone.
        String parentPhone = uniquePhone();
        acceptAsNewAccount(inviteGuardian(new Tenant(establishmentId, admin, adminContact), studentId, parentPhone, true), "Mahamat Abakar");
        String parent = login(parentPhone, PASSWORD);

        // Il voit le dossier de son enfant, sans être membre de l'établissement.
        Response children = get("/api/v2/me/students", parent);
        assertThat(children.status()).isEqualTo(200);
        assertThat(children.body().size()).isEqualTo(1);
        JsonNode child = children.body().get(0);
        assertThat(child.path("studentId").asString()).isEqualTo(studentId.toString());
        assertThat(child.path("access").asString()).isEqualTo("THIRD_PARTY");
        assertThat(child.path("academicAccess").asBoolean()).isTrue();
        assertThat(child.path("relationship").asString()).isEqualTo("MOTHER");
        assertThat(child.path("enrollments").get(0).path("level").asString()).isEqualTo("Terminale D");
        assertThat(get("/api/v2/me/students/" + studentId, parent).status()).isEqualTo(200);
        assertThat(get("/api/v2/me/affiliations", parent).body().size()).isZero();
        assertThat(get(path + "/students/" + studentId, parent).status()).isEqualTo(404);

        // La frontière publiée à la facturation désigne les contributeurs financiers.
        assertThat(directory.financialContributorAccounts(establishmentId, studentId)).hasSize(1);
        assertThat(directory.find(establishmentId, studentId)).isPresent();
        assertThat(directory.find(UUID.randomUUID(), studentId)).isEmpty();

        // Le retrait laisse une trace, et l'inscription reste consultable.
        Response ended = post(path + "/students/" + studentId + "/enrollments/" + enrollmentId + "/end", admin, Map.of(
                "status", "WITHDRAWN", "reason", "Déménagement"));
        assertThat(ended.status()).as(ended.raw()).isEqualTo(204);
        Response history = get(path + "/students/" + studentId + "/enrollments/" + enrollmentId + "/history", admin);
        assertThat(history.body().size()).isEqualTo(2);
        assertThat(history.body().get(1).path("toStatus").asString()).isEqualTo("WITHDRAWN");
        assertThat(history.body().get(1).path("reason").asString()).isEqualTo("Déménagement");
        assertThat(events.published).anySatisfy(event -> assertThat(event).isInstanceOf(EnrollmentEnded.class));

        // Une nouvelle inscription sur la même année redevient possible.
        assertThat(post(path + "/students/" + studentId + "/enrollments", admin, Map.of(
                "academicYearId", yearId.toString(), "level", "Terminale D")).status()).isEqualTo(201);

        // Chaque étape sensible est dans le journal d'audit de l'établissement.
        List<String> actions = new ArrayList<>();
        for (JsonNode event : get(path + "/audit-events?limit=200", admin).body()) {
            actions.add(event.path("action").asString());
        }
        assertThat(actions).contains(
                "establishment.created",
                "identity.invitation.issued",
                "identity.invitation.accepted",
                "schooling.year.created",
                "schooling.student.created",
                "schooling.enrollment.created",
                "schooling.enrollment.ended");
    }

    @Test
    void unParentVoitSesEnfantsDeDeuxEtablissementsAvecUnSeulCompte() {
        Tenant lycee = newTenant();
        Tenant universite = newTenant();
        UUID enfantLycee = newStudent(lycee);
        UUID enfantUniversite = newStudent(universite);
        String contact = uniqueEmail();

        acceptAsNewAccount(inviteGuardian(lycee, enfantLycee, contact, false), "Parent Unique");
        String parent = login(contact, PASSWORD);

        // Le compte existe déjà : le jeton seul ne suffit pas, il faut être connecté à ce compte.
        String secondInvitation = inviteGuardian(universite, enfantUniversite, contact, false);
        Response anonymous = post("/api/v2/invitations/accept", null, Map.of(
                "token", secondInvitation, "fullName", "Usurpateur", "password", PASSWORD));
        assertThat(anonymous.status()).as(anonymous.raw()).isEqualTo(401);
        assertThat(anonymous.code()).isEqualTo("LOGIN_REQUIRED");

        assertThat(post("/api/v2/invitations/accept", parent, Map.of("token", secondInvitation)).status()).isEqualTo(200);

        Response children = get("/api/v2/me/students", parent);
        assertThat(children.body().size()).isEqualTo(2);
        List<String> establishments = new ArrayList<>();
        for (JsonNode child : children.body()) {
            establishments.add(child.path("establishmentId").asString());
        }
        assertThat(establishments).containsExactlyInAnyOrder(lycee.id().toString(), universite.id().toString());
    }

    @Test
    void unMemeCompteADesRolesDistinctsDansChaqueEtablissement() {
        Tenant a = newTenant();
        Tenant b = newTenant();
        String contact = uniqueEmail();

        Response asAdmin = post(a.path("/invitations"), a.adminToken(), Map.of("contact", contact, "role", "ESTABLISHMENT_ADMIN"));
        acceptAsNewAccount(asAdmin.text("token"), "Double Casquette");
        String token = login(contact, PASSWORD);
        Response asCashier = post(b.path("/invitations"), b.adminToken(), Map.of("contact", contact, "role", "CASHIER"));
        assertThat(post("/api/v2/invitations/accept", token, Map.of("token", asCashier.text("token"))).status()).isEqualTo(200);

        Map<String, Object> student = Map.of("studentNumber", "ETU-" + UUID.randomUUID(), "fullName", "Élève Test");
        assertThat(post(a.path("/students"), token, student).status()).isEqualTo(201);
        Response refused = post(b.path("/students"), token, student);
        assertThat(refused.status()).as(refused.raw()).isEqualTo(403);
        assertThat(refused.code()).isEqualTo("PERMISSION_DENIED");
        assertThat(get(b.path("/students"), token).status()).isEqualTo(200);
        assertThat(get("/api/v2/me/affiliations", token).body().size()).isEqualTo(2);
    }

    @Test
    void lesPermissionsSuiventLeRoleDansUnEtablissement() {
        Tenant tenant = newTenant();
        UUID student = newStudent(tenant);
        String registrar = newStaff(tenant, "REGISTRAR");
        String cashier = newStaff(tenant, "CASHIER");
        String auditor = newStaff(tenant, "AUDITOR");

        // Le secrétariat gère les dossiers, mais pas le personnel ni le journal d'audit.
        assertThat(post(tenant.path("/students"), registrar, Map.of(
                "studentNumber", "ETU-" + UUID.randomUUID(), "fullName", "Élève Test")).status()).isEqualTo(201);
        assertThat(get(tenant.path("/members"), registrar).status()).isEqualTo(403);
        assertThat(get(tenant.path("/audit-events"), registrar).status()).isEqualTo(403);

        // La caisse consulte seulement.
        assertThat(get(tenant.path("/students/" + student), cashier).status()).isEqualTo(200);
        assertThat(post(tenant.path("/students"), cashier, Map.of(
                "studentNumber", "ETU-" + UUID.randomUUID(), "fullName", "Élève Test")).status()).isEqualTo(403);
        assertThat(post(tenant.path("/invitations"), cashier, Map.of(
                "contact", uniqueEmail(), "role", "ESTABLISHMENT_ADMIN")).status()).isEqualTo(403);

        // L'auditeur lit le journal sans rien pouvoir modifier.
        assertThat(get(tenant.path("/audit-events"), auditor).status()).isEqualTo(200);
        assertThat(patch(tenant.path(""), auditor, Map.of("name", "Nouveau nom")).status()).isEqualTo(403);
    }

    @Test
    void unEtudiantMajeurAccedeASonPropreDossierEtRienDAutre() {
        Tenant tenant = newTenant();
        UUID adulte = newStudent(tenant, LocalDate.now().minusYears(20));
        UUID camarade = newStudent(tenant, LocalDate.now().minusYears(20));

        String contact = uniqueEmail();
        Response invited = post(tenant.path("/students/" + adulte + "/self-access-invitations"), tenant.adminToken(), Map.of("contact", contact));
        assertThat(invited.status()).as(invited.raw()).isEqualTo(201);
        acceptAsNewAccount(invited.text("token"), "Étudiant Majeur");
        String etudiant = login(contact, PASSWORD);

        Response mine = get("/api/v2/me/students/" + adulte, etudiant);
        assertThat(mine.status()).as(mine.raw()).isEqualTo(200);
        assertThat(mine.text("access")).isEqualTo("SELF");
        assertThat(get("/api/v2/me/students/" + camarade, etudiant).status()).isEqualTo(404);
        assertThat(get(tenant.path("/students/" + adulte), etudiant).status()).isEqualTo(404);
    }

    @Test
    void unMineurOuUnAgeInconnuNOuvrePasDAccesPersonnel() {
        Tenant tenant = newTenant();
        String selfAccess = "/self-access-invitations";

        UUID mineur = newStudent(tenant, LocalDate.now().minusYears(15));
        Response refused = post(tenant.path("/students/" + mineur + selfAccess), tenant.adminToken(), Map.of("contact", uniqueEmail()));
        assertThat(refused.status()).as(refused.raw()).isEqualTo(409);
        assertThat(refused.code()).isEqualTo("STUDENT_NOT_ADULT");

        UUID inconnu = newStudent(tenant, null);
        assertThat(post(tenant.path("/students/" + inconnu + selfAccess), tenant.adminToken(), Map.of("contact", uniqueEmail())).status())
                .isEqualTo(409);

        // Une invitation déjà émise est revérifiée à l'acceptation, et rien n'est créé.
        UUID corrige = newStudent(tenant, LocalDate.now().minusYears(19));
        Response issued = post(tenant.path("/students/" + corrige + selfAccess), tenant.adminToken(), Map.of("contact", uniqueEmail()));
        assertThat(issued.status()).isEqualTo(201);
        jdbc.sql("UPDATE student_records SET birth_date = :birthDate WHERE id = :id")
                .param("id", corrige)
                .param("birthDate", LocalDate.now().minusYears(15))
                .update();
        Response accepted = post("/api/v2/invitations/accept", null, Map.of(
                "token", issued.text("token"), "fullName", "Trop Jeune", "password", PASSWORD));
        assertThat(accepted.status()).as(accepted.raw()).isEqualTo(409);
        assertThat(accepted.code()).isEqualTo("STUDENT_NOT_ADULT");
    }

    @Test
    void laRevocationCoupeLAccesImmediatementEtGardeLaTrace() {
        Tenant tenant = newTenant();
        UUID student = newStudent(tenant);
        String guardian = newGuardian(tenant, student);
        assertThat(get("/api/v2/me/students/" + student, guardian).status()).isEqualTo(200);

        Response grants = get(tenant.path("/students/" + student + "/access-grants"), tenant.adminToken());
        String grantId = grants.body().get(0).path("id").asString();
        assertThat(delete(tenant.path("/students/" + student + "/access-grants/" + grantId), tenant.adminToken()).status()).isEqualTo(204);

        assertThat(get("/api/v2/me/students/" + student, guardian).status()).isEqualTo(404);
        assertThat(get("/api/v2/me/students", guardian).body().size()).isZero();
        Response after = get(tenant.path("/students/" + student + "/access-grants"), tenant.adminToken());
        assertThat(after.body().get(0).path("revokedAt").isNull()).isFalse();
    }

    @Test
    void unEtablissementGardeToujoursUnAdministrateur() {
        Tenant tenant = newTenant();
        Response members = get(tenant.path("/members"), tenant.adminToken());
        String membershipId = members.body().get(0).path("membershipId").asString();

        Response demoted = patch(tenant.path("/members/" + membershipId), tenant.adminToken(), Map.of("roles", List.of("CASHIER")));
        assertThat(demoted.status()).as(demoted.raw()).isEqualTo(409);
        assertThat(demoted.code()).isEqualTo("LAST_ADMIN");
        Response revoked = patch(tenant.path("/members/" + membershipId), tenant.adminToken(), Map.of("status", "REVOKED"));
        assertThat(revoked.code()).isEqualTo("LAST_ADMIN");
    }

    @Test
    void uneInscriptionExigeUneAnneeOuverte() {
        Tenant tenant = newTenant();
        UUID student = newStudent(tenant);
        Response planned = post(tenant.path("/academic-years"), tenant.adminToken(), Map.of(
                "label", "Prévue", "startsOn", "2027-09-01", "endsOn", "2028-07-15"));
        Response enroll = post(tenant.path("/students/" + student + "/enrollments"), tenant.adminToken(), Map.of(
                "academicYearId", planned.text("id"), "level", "Sixième"));
        assertThat(enroll.status()).as(enroll.raw()).isEqualTo(409);
        assertThat(enroll.code()).isEqualTo("ACADEMIC_YEAR_NOT_OPEN");

        // Une transition de statut ne peut pas sauter d'étape.
        Response closed = patch(tenant.path("/academic-years/" + planned.text("id")), tenant.adminToken(), Map.of("status", "CLOSED"));
        assertThat(closed.status()).isEqualTo(409);
    }
}
