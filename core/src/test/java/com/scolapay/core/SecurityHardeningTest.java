package com.scolapay.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class SecurityHardeningTest extends IntegrationTest {

    private String sign(String secret, String subject, Instant expiresAt, String issuer, String audience) {
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(
                new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(subject)
                .audience(List.of(audience))
                .issuedAt(expiresAt.minusSeconds(3600))
                .expiresAt(expiresAt)
                .claim("ver", 0)
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    private UUID accountIdOf(String token) {
        return get("/api/v2/me", token).id("id");
    }

    @Test
    void lesEchecsDeConnexionSontIndiscernables() {
        Tenant tenant = newTenant();
        Response wrongPassword = post("/api/v2/auth/login", null, Map.of("identifier", tenant.adminContact(), "password", "mauvais-mot-de-passe"));
        Response unknown = post("/api/v2/auth/login", null, Map.of("identifier", uniqueEmail(), "password", "mauvais-mot-de-passe"));
        Response malformed = post("/api/v2/auth/login", null, Map.of("identifier", "pas un identifiant", "password", "mauvais-mot-de-passe"));
        assertThat(wrongPassword.status()).isEqualTo(401);
        assertThat(unknown.raw()).isEqualTo(wrongPassword.raw());
        assertThat(malformed.raw()).isEqualTo(wrongPassword.raw());
    }

    @Test
    void unCompteSeVerrouilleApresCinqEchecs_memeLeBonMotDePasseEstAlorsRefuse() {
        Tenant tenant = newTenant();
        for (int i = 0; i < 5; i++) {
            assertThat(post("/api/v2/auth/login", null, Map.of("identifier", tenant.adminContact(), "password", "mauvais-mot-de-passe")).status())
                    .isEqualTo(401);
        }
        Response locked = post("/api/v2/auth/login", null, Map.of("identifier", tenant.adminContact(), "password", PASSWORD));
        assertThat(locked.status()).isEqualTo(401);
        assertThat(locked.code()).isEqualTo("INVALID_CREDENTIALS");

        // Le verrou expire : le compte n'est pas perdu.
        jdbc.sql("""
                UPDATE accounts SET locked_until = now() - interval '1 minute'
                WHERE id = (SELECT account_id FROM account_identifiers WHERE value = :contact)
                """).param("contact", tenant.adminContact()).update();
        assertThat(login(tenant.adminContact(), PASSWORD)).isNotBlank();
    }

    @Test
    void unEchecIsoleNeVerrouillePasApresUneConnexionReussie() {
        Tenant tenant = newTenant();
        for (int round = 0; round < 3; round++) {
            for (int i = 0; i < 4; i++) {
                post("/api/v2/auth/login", null, Map.of("identifier", tenant.adminContact(), "password", "mauvais-mot-de-passe"));
            }
            assertThat(login(tenant.adminContact(), PASSWORD)).isNotBlank();
        }
    }

    @Test
    void lesJetonsFalsifiesSontRefuses() {
        Tenant tenant = newTenant();
        UUID account = accountIdOf(tenant.adminToken());
        String subject = account.toString();
        Instant future = Instant.now().plusSeconds(600);

        String wrongKey = sign("une-autre-cle-secrete-0123456789-abcdefgh", subject, future, "scolapay-core", "scolapay-core");
        String expired = sign(JWT_SECRET, subject, Instant.now().minusSeconds(600), "scolapay-core", "scolapay-core");
        String wrongIssuer = sign(JWT_SECRET, subject, future, "autre-emetteur", "scolapay-core");
        String wrongAudience = sign(JWT_SECRET, subject, future, "scolapay-core", "autre-service");
        String unsigned = base64Url("{\"alg\":\"none\"}") + "." + base64Url(
                "{\"sub\":\"" + subject + "\",\"iss\":\"scolapay-core\",\"aud\":[\"scolapay-core\"],\"ver\":0}") + ".";

        assertThat(get(tenant.path(""), sign(JWT_SECRET, subject, future, "scolapay-core", "scolapay-core")).status())
                .as("le jeton témoin, bien signé, doit passer")
                .isEqualTo(200);
        for (String forged : List.of(wrongKey, expired, wrongIssuer, wrongAudience, unsigned, "pas-un-jeton")) {
            assertThat(get(tenant.path(""), forged).status()).as(forged).isEqualTo(401);
        }
    }

    private static String base64Url(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void unJetonNePorteNiRoleNiEtablissement() {
        Tenant tenant = newTenant();
        String payload = new String(Base64.getUrlDecoder().decode(tenant.adminToken().split("\\.")[1]), StandardCharsets.UTF_8);
        assertThat(payload).doesNotContain("role", "tenant", "establishment", tenant.id().toString());
    }

    @Test
    void changerSonMotDePasseInvalideLesJetonsExistants() {
        Tenant tenant = newTenant();
        String newPassword = "une-nouvelle-phrase-solide";
        Response changed = post("/api/v2/me/password", tenant.adminToken(), Map.of("currentPassword", PASSWORD, "newPassword", newPassword));
        assertThat(changed.status()).as(changed.raw()).isEqualTo(204);
        assertThat(get(tenant.path(""), tenant.adminToken()).status()).isEqualTo(401);
        assertThat(get(tenant.path(""), login(tenant.adminContact(), newPassword)).status()).isEqualTo(200);
    }

    @Test
    void unCompteDesactiveEstCoupeImmediatement() {
        Tenant tenant = newTenant();
        UUID account = accountIdOf(tenant.adminToken());
        jdbc.sql("UPDATE accounts SET status = 'DISABLED' WHERE id = :id").param("id", account).update();
        assertThat(get(tenant.path(""), tenant.adminToken()).status()).isEqualTo(401);
        assertThat(post("/api/v2/auth/login", null, Map.of("identifier", tenant.adminContact(), "password", PASSWORD)).status()).isEqualTo(401);
    }

    @Test
    void uneInvitationEstAUsageUniqueEtSonJetonNestPasStockeEnClair() {
        Tenant tenant = newTenant();
        Response invited = post(tenant.path("/invitations"), tenant.adminToken(), Map.of("contact", uniqueEmail(), "role", "CASHIER"));
        String token = invited.text("token");

        Integer inClear = jdbc.sql("SELECT count(*) FROM invitations WHERE token_hash = :token").param("token", token).query(Integer.class).single();
        assertThat(inClear).isZero();

        acceptAsNewAccount(token, "Caissier");
        Response again = post("/api/v2/invitations/accept", null, Map.of("token", token, "fullName", "Bis", "password", PASSWORD));
        assertThat(again.status()).isEqualTo(409);
        assertThat(again.code()).isEqualTo("INVITATION_UNUSABLE");
    }

    @Test
    void uneInvitationExpireeOuRevoqueeOuInconnueNeSertPlus() {
        Tenant tenant = newTenant();
        Response expired = post(tenant.path("/invitations"), tenant.adminToken(), Map.of("contact", uniqueEmail(), "role", "CASHIER"));
        jdbc.sql("UPDATE invitations SET expires_at = now() - interval '1 minute' WHERE id = :id").param("id", expired.id("id")).update();
        assertThat(post("/api/v2/invitations/accept", null, Map.of("token", expired.text("token"), "fullName", "Expirée", "password", PASSWORD)).status()).isEqualTo(409);

        Response revoked = post(tenant.path("/invitations"), tenant.adminToken(), Map.of("contact", uniqueEmail(), "role", "CASHIER"));
        assertThat(delete(tenant.path("/invitations/" + revoked.text("id")), tenant.adminToken()).status()).isEqualTo(204);
        assertThat(post("/api/v2/invitations/accept", null, Map.of("token", revoked.text("token"), "fullName", "Révoquée", "password", PASSWORD)).status()).isEqualTo(409);

        assertThat(post("/api/v2/invitations/accept", null, Map.of("token", "inconnu", "fullName", "Inconnue", "password", PASSWORD)).status()).isEqualTo(404);
    }

    @Test
    void uneInvitationNePeutPasEtreDetourneeParUnAutreCompte() {
        Tenant tenant = newTenant();
        String invitedContact = uniqueEmail();
        Response invited = post(tenant.path("/invitations"), tenant.adminToken(), Map.of("contact", invitedContact, "role", "ESTABLISHMENT_ADMIN"));

        // Le destinataire n'a pas de compte : un compte connecté ne peut pas s'approprier l'invitation.
        Tenant intrus = newTenant();
        Response stolen = post("/api/v2/invitations/accept", intrus.adminToken(), Map.of("token", invited.text("token")));
        assertThat(stolen.status()).as(stolen.raw()).isEqualTo(403);
        assertThat(stolen.code()).isEqualTo("INVITATION_NOT_FOR_ACCOUNT");
        assertThat(get(tenant.path("/members"), intrus.adminToken()).status()).isEqualTo(404);

        // Le destinataire a un compte : un autre compte connecté est aussi refusé.
        String existing = uniqueEmail();
        Response other = post(tenant.path("/invitations"), tenant.adminToken(), Map.of("contact", existing, "role", "CASHIER"));
        acceptAsNewAccount(other.text("token"), "Existant");
        Response second = post(tenant.path("/invitations"), tenant.adminToken(), Map.of("contact", existing, "role", "AUDITOR"));
        assertThat(post("/api/v2/invitations/accept", intrus.adminToken(), Map.of("token", second.text("token"))).status()).isEqualTo(403);
    }

    @Test
    void unMotDePasseFaibleEstRefuse_etAucunCompteNEstCree() {
        Tenant tenant = newTenant();
        Response invited = post(tenant.path("/invitations"), tenant.adminToken(), Map.of("contact", uniqueEmail(), "role", "CASHIER"));
        for (String weak : List.of("court", "x".repeat(73))) {
            Response refused = post("/api/v2/invitations/accept", null, Map.of("token", invited.text("token"), "fullName", "Faible", "password", weak));
            assertThat(refused.status()).as(refused.raw()).isEqualTo(422);
            assertThat(refused.code()).isEqualTo("WEAK_PASSWORD");
        }
        // L'invitation reste utilisable : l'échec n'a rien consommé.
        acceptAsNewAccount(invited.text("token"), "Solide");
    }

    @Test
    void lesEntreesInvalidesSontRefuseesSansFuiteInterne() {
        Tenant tenant = newTenant();

        Response unknownField = post(tenant.path("/students"), tenant.adminToken(), Map.of(
                "studentNumber", "ETU-1", "fullName", "Élève Test", "debtCents", "0"));
        assertThat(unknownField.status()).as(unknownField.raw()).isEqualTo(400);

        Response validation = post(tenant.path("/students"), tenant.adminToken(), Map.of("studentNumber", "", "fullName", "x"));
        assertThat(validation.status()).isEqualTo(400);
        assertThat(validation.code()).isEqualTo("VALIDATION_FAILED");
        assertThat(validation.body().path("errors").size()).isGreaterThanOrEqualTo(2);

        Response malformed = send("POST", tenant.path("/students"), tenant.adminToken(), "{pas du json");
        assertThat(malformed.status()).isEqualTo(400);
        assertThat(malformed.raw()).doesNotContain("jackson", "Exception", "com.scolapay", "at org.");

        assertThat(get(tenant.path("/students?limit=100000"), tenant.adminToken()).status()).isEqualTo(422);
        assertThat(get(tenant.path("/students/pas-un-uuid"), tenant.adminToken()).status()).isEqualTo(400);

        Response timezone = post("/api/v2/platform/establishments", operatorToken(), Map.of(
                "code", uniqueCode(), "name", "Lycée", "kind", "SCHOOL", "timezone", "Mars/Olympus",
                "currency", "XAF", "countryCode", "TD", "firstAdminContact", uniqueEmail()));
        assertThat(timezone.code()).isEqualTo("INVALID_TIMEZONE");
        Response currency = post("/api/v2/platform/establishments", operatorToken(), Map.of(
                "code", uniqueCode(), "name", "Lycée", "kind", "SCHOOL", "timezone", "Africa/Ndjamena",
                "currency", "ZZZ", "countryCode", "TD", "firstAdminContact", uniqueEmail()));
        assertThat(currency.code()).isEqualTo("INVALID_CURRENCY");
    }

    @Test
    void unDoublonDeMatriculeEstUnConflit_mais_seulementDansLeMemeEtablissement() {
        Tenant a = newTenant();
        Tenant b = newTenant();
        Map<String, Object> student = Map.of("studentNumber", "ETU-PARTAGE", "fullName", "Élève Test");
        assertThat(post(a.path("/students"), a.adminToken(), student).status()).isEqualTo(201);
        Response duplicate = post(a.path("/students"), a.adminToken(), student);
        assertThat(duplicate.status()).as(duplicate.raw()).isEqualTo(409);
        assertThat(duplicate.raw()).doesNotContain("student_records", "duplicate key", "SQL");
        assertThat(post(b.path("/students"), b.adminToken(), student).status()).isEqualTo(201);
    }

    @Test
    void lesCorpsTropVolumineuxSontRefuses() {
        String huge = "{\"identifier\":\"" + "a".repeat(300_000) + "\",\"password\":\"x\"}";
        assertThat(send("POST", "/api/v2/auth/login", null, huge).status()).isEqualTo(413);
        // Sans longueur annoncée, le corps est coupé à la lecture.
        Response chunked = sendChunked("/api/v2/auth/login", huge.getBytes(StandardCharsets.UTF_8));
        assertThat(chunked.status()).isBetween(400, 413);
    }

    @Test
    void seuleLaSanteEstExposeeSansDetail() {
        Response health = get("/actuator/health", null);
        assertThat(health.status()).isEqualTo(200);
        assertThat(health.raw()).doesNotContain("db", "postgres", "components");
        assertThat(get("/actuator/env", null).status()).isEqualTo(401);
        assertThat(get("/actuator/env", operatorToken()).status()).isEqualTo(404);
    }

    @Test
    void lAmorcageNeRemplacePasUnOperateurExistant() {
        Integer operators = jdbc.sql("SELECT count(*) FROM accounts WHERE platform_operator").query(Integer.class).single();
        assertThat(operators).isEqualTo(1);
    }
}
