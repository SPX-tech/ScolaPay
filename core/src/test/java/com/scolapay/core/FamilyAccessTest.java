package com.scolapay.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.scolapay.core.schooling.StudentDirectory;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/**
 * La majorité est une règle juridique, l'accès aux informations académiques une autorisation.
 * Un étudiant majeur contrôle ce dernier, et contribuer aux frais ne l'ouvre jamais.
 */
class FamilyAccessTest extends IntegrationTest {

    private static final LocalDate MINOR = LocalDate.now().minusYears(14);
    private static final LocalDate ADULT = LocalDate.now().minusYears(20);

    @Autowired
    StudentDirectory directory;

    private record Enrolled(UUID studentId, String studentNumber) {}

    private Enrolled enrolledStudent(Tenant tenant, LocalDate birthDate) {
        UUID studentId = newStudent(tenant, birthDate);
        Response enrolled = post(tenant.path("/students/" + studentId + "/enrollments"), tenant.adminToken(), Map.of(
                "academicYearId", openYear(tenant).toString(), "level", "Niveau-Confidentiel"));
        assertThat(enrolled.status()).as(enrolled.raw()).isEqualTo(201);
        String number = get(tenant.path("/students/" + studentId), tenant.adminToken()).text("studentNumber");
        return new Enrolled(studentId, number);
    }

    private String newThirdParty(Tenant tenant, UUID studentId, boolean academic, boolean financial) {
        String contact = uniqueEmail();
        Response invited = inviteThirdParty(tenant, studentId, contact, academic, financial);
        assertThat(invited.status()).as(invited.raw()).isEqualTo(201);
        acceptAsNewAccount(invited.text("token"), "Tiers Test");
        return login(contact, PASSWORD);
    }

    private String newSelf(Tenant tenant, UUID studentId) {
        String contact = uniqueEmail();
        Response invited = post(
                tenant.path("/students/" + studentId + "/self-access-invitations"), tenant.adminToken(), Map.of("contact", contact));
        assertThat(invited.status()).as(invited.raw()).isEqualTo(201);
        acceptAsNewAccount(invited.text("token"), "Étudiant Majeur");
        return login(contact, PASSWORD);
    }

    private void setBirthDate(UUID studentId, LocalDate birthDate) {
        jdbc.sql("UPDATE student_records SET birth_date = :birthDate WHERE id = :id")
                .param("id", studentId)
                .param("birthDate", birthDate)
                .update();
    }

    private void assertAcademicVisible(UUID studentId, String token, Enrolled student) {
        Response response = get("/api/v2/me/students/" + studentId, token);
        assertThat(response.status()).as(response.raw()).isEqualTo(200);
        assertThat(response.body().path("academicAccess").asBoolean()).isTrue();
        assertThat(response.raw()).contains("Niveau-Confidentiel", student.studentNumber());
    }

    /** Le lien reste visible et identifie l'élève, sans aucune information académique. */
    private void assertAcademicHidden(UUID studentId, String token, Enrolled student) {
        Response response = get("/api/v2/me/students/" + studentId, token);
        assertThat(response.status()).as(response.raw()).isEqualTo(200);
        assertThat(response.body().path("academicAccess").asBoolean()).isFalse();
        assertThat(response.body().path("studentNumber").isNull()).isTrue();
        assertThat(response.body().path("enrollments").size()).isZero();
        assertThat(response.raw()).doesNotContain("Niveau-Confidentiel", student.studentNumber());
        assertThat(response.text("fullName")).isEqualTo("Élève Test");
    }

    private String grantIdOf(String studentToken, UUID studentId) {
        Response parties = get("/api/v2/me/students/" + studentId + "/third-parties", studentToken);
        assertThat(parties.status()).as(parties.raw()).isEqualTo(200);
        return parties.body().get(0).path("grantId").asString();
    }

    private Response setConsent(String token, UUID studentId, String grantId, boolean academic) {
        return patch("/api/v2/me/students/" + studentId + "/third-parties/" + grantId, token, Map.of("academicAccess", academic));
    }

    @Test
    void unTuteurDUnMineurVoitLesInformationsAcademiques() {
        Tenant tenant = newTenant();
        Enrolled student = enrolledStudent(tenant, MINOR);
        assertAcademicVisible(student.studentId(), newThirdParty(tenant, student.studentId(), true, false), student);
    }

    @Test
    void lAccesAcademiqueDuTuteurCesseDesLaMajorite_maisSaContributionFinanciereDemeure() {
        Tenant tenant = newTenant();
        Enrolled student = enrolledStudent(tenant, MINOR);
        String tuteur = newThirdParty(tenant, student.studentId(), true, true);
        assertAcademicVisible(student.studentId(), tuteur, student);

        // Le passage à la majorité prend effet sans action ni traitement différé.
        setBirthDate(student.studentId(), ADULT);

        assertAcademicHidden(student.studentId(), tuteur, student);
        assertThat(get("/api/v2/me/students/" + student.studentId(), tuteur).body().path("financialContributor").asBoolean()).isTrue();
        assertThat(directory.financialContributorAccounts(tenant.id(), student.studentId())).hasSize(1);
    }

    @Test
    void unEtudiantMajeurDecideDeLAccesDUnTiers_etPeutRevenirSurSonChoix() {
        Tenant tenant = newTenant();
        Enrolled student = enrolledStudent(tenant, MINOR);
        String tuteur = newThirdParty(tenant, student.studentId(), true, false);
        setBirthDate(student.studentId(), ADULT);
        String etudiant = newSelf(tenant, student.studentId());
        assertAcademicHidden(student.studentId(), tuteur, student);

        String grantId = grantIdOf(etudiant, student.studentId());
        assertThat(setConsent(etudiant, student.studentId(), grantId, true).status()).isEqualTo(204);
        assertAcademicVisible(student.studentId(), tuteur, student);
        assertThat(get("/api/v2/me/students/" + student.studentId() + "/third-parties", etudiant).body().get(0).path("academicAccess").asBoolean()).isTrue();

        assertThat(setConsent(etudiant, student.studentId(), grantId, false).status()).isEqualTo(204);
        assertAcademicHidden(student.studentId(), tuteur, student);

        List<String> actions = new ArrayList<>();
        for (JsonNode event : get(tenant.path("/audit-events?limit=200"), tenant.adminToken()).body()) {
            actions.add(event.path("action").asString());
        }
        assertThat(actions).contains("schooling.access.consent.granted", "schooling.access.consent.withdrawn");
    }

    @Test
    void sansCompteDeLEtudiantMajeurAucunTiersNeRecupereLAcces() {
        Tenant tenant = newTenant();
        Enrolled student = enrolledStudent(tenant, MINOR);
        String tuteur = newThirdParty(tenant, student.studentId(), true, true);
        setBirthDate(student.studentId(), ADULT);
        assertAcademicHidden(student.studentId(), tuteur, student);

        // Le personnel voit lui aussi que l'accès effectif est fermé, sans moyen de le rouvrir.
        Response grants = get(tenant.path("/students/" + student.studentId() + "/access-grants"), tenant.adminToken());
        assertThat(grants.body().get(0).path("academicAccess").asBoolean()).isFalse();
        assertThat(grants.body().get(0).path("financialContributor").asBoolean()).isTrue();
    }

    @Test
    void unContributeurFinancierNAJamaisAccesAuDossier_memePourUnMineur() {
        Tenant tenant = newTenant();
        Enrolled student = enrolledStudent(tenant, MINOR);
        String contributeur = newThirdParty(tenant, student.studentId(), false, true);

        assertAcademicHidden(student.studentId(), contributeur, student);
        Response list = get("/api/v2/me/students", contributeur);
        assertThat(list.body().size()).isEqualTo(1);
        assertThat(list.raw()).doesNotContain("Niveau-Confidentiel", student.studentNumber());
        assertThat(directory.financialContributorAccounts(tenant.id(), student.studentId())).hasSize(1);
    }

    @Test
    void unTiersPeutContribuerPourUnEtudiantMajeurSansAccederASonDossier() {
        Tenant tenant = newTenant();
        Enrolled student = enrolledStudent(tenant, ADULT);
        String contributeur = newThirdParty(tenant, student.studentId(), false, true);
        assertAcademicHidden(student.studentId(), contributeur, student);
        assertThat(directory.financialContributorAccounts(tenant.id(), student.studentId())).hasSize(1);
    }

    @Test
    void lEtablissementNePeutPasAccorderLuiMemeLAccesAuDossierDUnMajeur() {
        Tenant tenant = newTenant();
        UUID adulte = newStudent(tenant, ADULT);
        Response refused = inviteThirdParty(tenant, adulte, uniqueEmail(), true, true);
        assertThat(refused.status()).as(refused.raw()).isEqualTo(409);
        assertThat(refused.code()).isEqualTo("ADULT_CONSENT_REQUIRED");
    }

    @Test
    void uneMinoriteNonEtablieNOuvreAucunAccesAcademique() {
        Tenant tenant = newTenant();
        UUID inconnu = newStudent(tenant, null);
        Response refused = inviteThirdParty(tenant, inconnu, uniqueEmail(), true, false);
        assertThat(refused.status()).as(refused.raw()).isEqualTo(409);
        assertThat(refused.code()).isEqualTo("MINORITY_NOT_ESTABLISHED");

        // Un accès déjà accordé se ferme si la date de naissance devient inconnue.
        Enrolled student = enrolledStudent(tenant, MINOR);
        String tuteur = newThirdParty(tenant, student.studentId(), true, false);
        setBirthDate(student.studentId(), null);
        assertAcademicHidden(student.studentId(), tuteur, student);
    }

    @Test
    void unLienSansAucunDroitEstRefuse() {
        Tenant tenant = newTenant();
        Response refused = inviteThirdParty(tenant, newStudent(tenant, MINOR), uniqueEmail(), false, false);
        assertThat(refused.status()).as(refused.raw()).isEqualTo(422);
        assertThat(refused.code()).isEqualTo("NO_CAPABILITY");
    }

    @Test
    void seulLEtudiantLuiMemeDecide() {
        Tenant tenant = newTenant();
        Enrolled student = enrolledStudent(tenant, MINOR);
        String tuteur = newThirdParty(tenant, student.studentId(), true, false);
        setBirthDate(student.studentId(), ADULT);
        String etudiant = newSelf(tenant, student.studentId());
        String grantId = grantIdOf(etudiant, student.studentId());
        String path = "/api/v2/me/students/" + student.studentId() + "/third-parties";

        // Le tiers ne peut pas s'accorder l'accès, ni l'administrateur de l'établissement, ni l'opérateur.
        for (String other : List.of(tuteur, tenant.adminToken(), operatorToken(), newTenant().adminToken())) {
            assertThat(get(path, other).status()).isEqualTo(404);
            assertThat(setConsent(other, student.studentId(), grantId, true).status()).isEqualTo(404);
        }
        assertAcademicHidden(student.studentId(), tuteur, student);
    }

    @Test
    void unEtudiantNePeutPasConsentirPourUnLienEtranger() {
        Tenant tenant = newTenant();
        UUID premier = newStudent(tenant, ADULT);
        String etudiant = newSelf(tenant, premier);

        Enrolled autre = enrolledStudent(tenant, MINOR);
        newThirdParty(tenant, autre.studentId(), true, false);
        String grantEtranger = get(tenant.path("/students/" + autre.studentId() + "/access-grants"), tenant.adminToken())
                .body().get(0).path("id").asString();

        assertThat(setConsent(etudiant, premier, grantEtranger, true).status()).isEqualTo(404);
    }

    @Test
    void lAgeLegalNestPasConfigurableParLEtablissement() {
        Tenant tenant = newTenant();
        Response settings = patch(tenant.path(""), tenant.adminToken(), Map.of("name", "Lycée", "ageOfMajority", 15));
        assertThat(settings.status()).as(settings.raw()).isEqualTo(400);

        Response creation = post("/api/v2/platform/establishments", operatorToken(), Map.of(
                "code", uniqueCode(), "name", "Lycée", "kind", "SCHOOL", "timezone", "Africa/Ndjamena",
                "currency", "XAF", "countryCode", "TD", "ageOfMajority", 15, "firstAdminContact", uniqueEmail()));
        assertThat(creation.status()).as(creation.raw()).isEqualTo(400);

        assertThat(get(tenant.path(""), tenant.adminToken()).body().path("legalAgeOfMajority").asInt()).isEqualTo(18);
    }

    @Test
    void unPaysSansRegleJuridiqueEstRefuse() {
        Response refused = post("/api/v2/platform/establishments", operatorToken(), Map.of(
                "code", uniqueCode(), "name", "Lycée", "kind", "SCHOOL", "timezone", "Africa/Ndjamena",
                "currency", "XAF", "countryCode", "ZZ", "firstAdminContact", uniqueEmail()));
        assertThat(refused.status()).as(refused.raw()).isEqualTo(422);
        assertThat(refused.code()).isEqualTo("UNSUPPORTED_COUNTRY");
    }

    @Test
    void laRegleDuTchadResteProvisoireTantQuElleNEstPasConfirmee() {
        Boolean confirmed = jdbc.sql("SELECT legally_confirmed FROM legal_majority_rules WHERE country_code = 'TD'")
                .query(Boolean.class)
                .single();
        assertThat(confirmed).isFalse();
    }
}
