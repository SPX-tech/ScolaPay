package com.scolapay.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.scolapay.core.TestApi.Reply;
import com.scolapay.core.TestApi.School;
import com.scolapay.testsupport.TestInvitationDelivery;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Démarre l'application comme en production, sur une base vierge et sans la confirmation juridique
 * accordée à la suite principale. Voir la décision 0017.
 */
class GoLiveTest {

    private static final String OPEN = "--scolapay.portal.enabled=true";

    private final List<ConfigurableApplicationContext> running = new ArrayList<>();
    private String url;
    private JdbcClient jdbc;

    @BeforeEach
    void newDatabase() {
        url = TestDatabases.createEmpty();
        jdbc = TestDatabases.client(url);
    }

    @AfterEach
    void stop() {
        running.forEach(ConfigurableApplicationContext::close);
    }

    @Test
    void laRegleDuTchadResteProvisoireDansLesMigrations() {
        start(false);

        assertThat(jdbc.sql("SELECT legally_confirmed FROM legal_majority_rules WHERE country_code = 'TD'")
                        .query(Boolean.class)
                        .single())
                .isFalse();
    }

    @Test
    void parDefautLePortailEstFermeAuxFamilles() {
        TestApi api = start(true);
        School school = api.newSchool();
        UUID minor = school.newStudent();
        UUID adult = school.newStudent(LocalDate.of(2000, 1, 1));

        assertThat(api.get("/api/v2/me/students", school.adminToken()).status()).isEqualTo(404);
        assertThat(api.get("/api/v2/me/billing/obligations", school.adminToken()).status()).as("Portail fermé, ces routes n'existent pas").isEqualTo(404);
        assertThat(api.get("/api/v2/me/students/" + minor, school.adminToken()).status()).isEqualTo(404);

        Reply thirdParty = inviteThirdParty(school, minor);
        assertThat(thirdParty.status()).as(thirdParty.raw()).isEqualTo(409);
        assertThat(thirdParty.code()).isEqualTo("PORTAL_CLOSED");

        Reply self = inviteAdult(school, adult);
        assertThat(self.status()).as(self.raw()).isEqualTo(409);
        assertThat(self.code()).isEqualTo("PORTAL_CLOSED");

        assertThat(school.enroll(minor).status()).as("Le personnel continue de travailler").isEqualTo(201);
        assertThat(api.get(school.path("/students/" + minor + "/access-grants"), school.adminToken()).status())
                .isEqualTo(200);
    }

    @Test
    void lesAutresEtablissementsNeDecouvrentPasLeDossierParLaFermeture() {
        TestApi api = start(true);
        School owner = api.newSchool();
        School other = api.newSchool();
        UUID student = owner.newStudent();

        Reply refused = api.post(
                owner.path("/students/" + student + "/third-party-invitations"),
                other.adminToken(),
                Map.of("contact", IntegrationTest.uniqueEmail(), "relationship", "MOTHER", "academicAccess", true));

        assertThat(refused.status()).as("L'isolation passe avant l'état du portail").isEqualTo(404);
    }

    /** Une invitation émise avant la fermeture ne doit ni aboutir, ni être consommée, ni créer de compte. */
    @Test
    void unPortailFermeRefuseUneInvitationDejaEmiseSansLaConsommer() {
        confirmLegalRules();
        TestApi open = start(true, OPEN);
        School school = open.newSchool();
        UUID minor = school.newStudent();
        UUID adult = school.newStudent(LocalDate.of(2000, 1, 1));
        String thirdPartyToken = inviteThirdParty(school, minor).text("token");
        String selfToken = inviteAdult(school, adult).text("token");
        closeAll();
        long accountsBefore = jdbc.sql("SELECT count(*) FROM accounts").query(Long.class).single();

        TestApi closed = start(false);
        for (String token : List.of(thirdPartyToken, selfToken)) {
            Reply refused = closed.accept(token);
            assertThat(refused.status()).as(refused.raw()).isEqualTo(409);
            assertThat(refused.code()).isEqualTo("PORTAL_CLOSED");
        }
        assertThat(jdbc.sql("SELECT count(*) FROM record_access_grants").query(Long.class).single()).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM accounts").query(Long.class).single()).isEqualTo(accountsBefore);
        closeAll();

        TestApi reopened = start(true, OPEN);
        for (String token : List.of(thirdPartyToken, selfToken)) {
            assertThat(reopened.accept(token).status()).as("L'invitation est restée utilisable").isEqualTo(200);
        }
    }

    @Test
    void refuseDeDemarrerSiUneRegleJuridiqueNEstPasConfirmee() {
        assertThatThrownBy(() -> start(true, OPEN))
                .hasStackTraceContaining("regles-juridiques-confirmees")
                .hasStackTraceContaining("TD");
    }

    @Test
    void refuseDeDemarrerSansCanalDeRemiseDesInvitations() {
        confirmLegalRules();

        assertThatThrownBy(() -> start(false, OPEN))
                .hasStackTraceContaining("canal-de-remise-des-invitations")
                .satisfies(error -> assertThat(stackTrace(error)).doesNotContain("regles-juridiques-confirmees"));
    }

    @Test
    void signaleToutesLesConditionsNonRemplies() {
        assertThatThrownBy(() -> start(false, OPEN))
                .hasStackTraceContaining("regles-juridiques-confirmees")
                .hasStackTraceContaining("canal-de-remise-des-invitations");
    }

    /** Sans cela, un établissement créé après le démarrage dans ce pays échapperait au contrôle. */
    @Test
    void uneRegleNonConfirmeeBloqueMemeSansEtablissementDansCePays() {
        confirmLegalRules();
        jdbc.sql("INSERT INTO legal_majority_rules (country_code, age_of_majority) VALUES ('CM', 21)").update();

        assertThatThrownBy(() -> start(true, OPEN)).hasStackTraceContaining("CM");
    }

    @Test
    void ouvreLePortailQuandToutesLesConditionsSontRemplies() {
        confirmLegalRules();

        TestApi api = start(true, OPEN);
        School school = api.newSchool();

        assertThat(api.get("/api/v2/me/students", school.adminToken()).status()).isEqualTo(200);
        assertThat(inviteThirdParty(school, school.newStudent()).status()).isEqualTo(201);
    }

    private static String stackTrace(Throwable error) {
        java.io.StringWriter writer = new java.io.StringWriter();
        error.printStackTrace(new java.io.PrintWriter(writer));
        return writer.toString();
    }

    private static Reply inviteThirdParty(School school, UUID student) {
        return school.api().post(
                school.path("/students/" + student + "/third-party-invitations"),
                school.adminToken(),
                Map.of("contact", IntegrationTest.uniqueEmail(), "relationship", "MOTHER", "academicAccess", true));
    }

    private static Reply inviteAdult(School school, UUID student) {
        return school.api().post(
                school.path("/students/" + student + "/self-access-invitations"),
                school.adminToken(),
                Map.of("contact", IntegrationTest.uniqueEmail()));
    }

    /** Les migrations d'abord, portail fermé, comme le ferait une migration de confirmation relue. */
    private void confirmLegalRules() {
        start(false);
        jdbc.sql("UPDATE legal_majority_rules SET legally_confirmed = true").update();
        closeAll();
    }

    private void closeAll() {
        running.forEach(ConfigurableApplicationContext::close);
        running.clear();
    }

    private TestApi start(boolean withDeliveryChannel, String... extraArguments) {
        List<String> arguments = new ArrayList<>(TestDatabases.applicationArguments(url));
        arguments.add("--server.port=0");
        arguments.addAll(List.of(extraArguments));
        Class<?>[] sources = withDeliveryChannel
                ? new Class<?>[] {ScolaPayCoreApplication.class, TestInvitationDelivery.class}
                : new Class<?>[] {ScolaPayCoreApplication.class};
        ConfigurableApplicationContext context =
                new SpringApplicationBuilder(sources).run(arguments.toArray(String[]::new));
        running.add(context);
        return new TestApi(context.getEnvironment().getRequiredProperty("local.server.port", Integer.class));
    }
}
