package com.scolapay.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@TestPropertySource(properties = "scolapay.payments.cash-receptions-enabled=true")
class CashReceptionTest extends BillingScenarioTest {

    @Autowired PlatformTransactionManager transactions;

    @Test
    void uneReceptionConserveLaPreuveDesEspecesSansDeclarerDeReglement() {
        Tenant tenant = newTenant();
        String cashier = newStaff(tenant, "CASHIER");
        Response created = record(tenant, cashier, UUID.randomUUID(), "12000", "2000");
        assertThat(created.status()).as(created.raw()).isEqualTo(201);
        assertThat(created.text("currency")).isEqualTo("XAF");
        assertThat(created.text("amountReceivedMinor")).isEqualTo("12000");
        assertThat(created.text("changeGivenMinor")).isEqualTo("2000");
        assertThat(created.text("amountRetainedMinor")).isEqualTo("10000");
        assertThat(created.text("unallocatedMinor")).isEqualTo("10000");
        assertThat(created.text("settledMinor")).isEqualTo("0");
        assertThat(created.text("allocationState")).isEqualTo("UNALLOCATED");
        assertThat(created.text("proofNature")).isEqualTo("INTERNAL_CASH_DECLARATION");
        assertThat(created.text("reference")).isNotBlank();
        assertThat(created.text("recordedByAccountId")).isEqualTo(get("/api/v2/me", cashier).text("id"));
        Response read = get(tenant.path("/payments/cash-receptions/" + created.id("id")), cashier);
        assertThat(read.status()).as(read.raw()).isEqualTo(200);
        assertThat(read.body()).isEqualTo(created.body());
    }

    @Test
    void unRejeuRetourneLaMemePreuveEtUnAutreMontantEstRefuse() {
        Tenant tenant = newTenant();
        UUID key = UUID.randomUUID();
        Response created = record(tenant, tenant.adminToken(), key, "12000", "2000");
        assertThat(created.status()).as(created.raw()).isEqualTo(201);
        Response replay = record(tenant, tenant.adminToken(), key, "12000", "2000");
        assertThat(replay.status()).as(replay.raw()).isEqualTo(200);
        assertThat(replay.body()).isEqualTo(created.body());
        Response mismatch = record(tenant, tenant.adminToken(), key, "12001", "2000");
        assertThat(mismatch.status()).as(mismatch.raw()).isEqualTo(409);
        assertThat(mismatch.code()).isEqualTo("CASH_RECEPTION_KEY_CONFLICT");
        assertThat(receptions(tenant)).isEqualTo(1);
        assertThat(audits(tenant)).isEqualTo(1);
    }

    @Test
    void huitTentativesSimultaneesNeCreentQuUneDeclarationEtUnAudit() throws Exception {
        Tenant tenant = newTenant();
        UUID key = UUID.randomUUID();
        List<Response> responses = concurrently(8, () -> record(tenant, tenant.adminToken(), key, "25000", "0"));
        assertThat(responses).allSatisfy(response -> assertThat(response.status()).as(response.raw()).isIn(200, 201));
        assertThat(responses.stream().filter(response -> response.status() == 201)).hasSize(1);
        assertThat(responses.stream().map(response -> response.id("id")).distinct()).hasSize(1);
        assertThat(receptions(tenant)).isEqualTo(1);
        assertThat(audits(tenant)).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CASHIER", "ACCOUNTANT", "ESTABLISHMENT_ADMIN"})
    void seulsLesOperateursHabilitesDeclarentDesEspeces(String role) {
        Tenant tenant = newTenant();
        String staff = newStaff(tenant, role);
        assertThat(record(tenant, staff, UUID.randomUUID(), "25000", "0").status()).isEqualTo(201);
    }

    @ParameterizedTest
    @ValueSource(strings = {"DIRECTOR", "AUDITOR", "REGISTRAR"})
    void unRoleDeControleOuDeScolariteNePeutPasDeclarer(String role) {
        Tenant tenant = newTenant();
        assertThat(record(tenant, newStaff(tenant, role), UUID.randomUUID(), "25000", "0").status()).isEqualTo(403);
        assertThat(receptions(tenant)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"DIRECTOR", "AUDITOR"})
    void lesRolesDeControlePeuventRelireLaPreuveSansLaModifier(String role) {
        Tenant tenant = newTenant();
        Response created = record(tenant, tenant.adminToken(), UUID.randomUUID(), "12000", "2000");
        String reader = newStaff(tenant, role);
        Response read = get(tenant.path("/payments/cash-receptions/" + created.id("id")), reader);
        assertThat(read.status()).as(read.raw()).isEqualTo(200);
        assertThat(read.body()).isEqualTo(created.body());
        assertThat(patch(tenant.path("/payments/cash-receptions/" + created.id("id")), reader,
                Map.of("amountReceivedMinor", "99999")).status()).isEqualTo(405);
    }

    @Test
    void laCleEstPropreALEtablissementEtNeTransferePasUneDeclarationEntreAuteurs() {
        Tenant a = newTenant();
        Tenant b = newTenant();
        UUID key = UUID.randomUUID();
        Response first = record(a, a.adminToken(), key, "25000", "0");
        Response second = record(b, b.adminToken(), key, "25000", "0");
        assertThat(first.status()).isEqualTo(201);
        assertThat(second.status()).isEqualTo(201);
        assertThat(first.id("id")).isNotEqualTo(second.id("id"));
        Response cross = get(b.path("/payments/cash-receptions/" + first.id("id")), b.adminToken());
        assertThat(cross.status()).isEqualTo(404);
        assertThat(cross.raw()).doesNotContain(first.text("reference"));
        assertThat(record(a, newStaff(a, "CASHIER"), key, "25000", "0").status()).isEqualTo(409);
    }

    @Test
    void niUnOperateurGlobalNiUnCompteExterneNePeutDeclarerOuLire() {
        Tenant tenant = newTenant();
        Response created = record(tenant, tenant.adminToken(), UUID.randomUUID(), "25000", "0");
        assertThat(record(tenant, null, UUID.randomUUID(), "25000", "0").status()).isEqualTo(401);
        assertThat(record(tenant, operatorToken(), UUID.randomUUID(), "25000", "0").status()).isEqualTo(404);
        String foreign = newTenant().adminToken();
        assertThat(get(tenant.path("/payments/cash-receptions/" + created.id("id")), foreign).status()).isEqualTo(404);
    }

    @Test
    void unLienFamilialNeDonneAucunDroitSurLesDeclarationsDeCaisse() {
        Tenant tenant = newTenant();
        String guardian = newGuardian(tenant, newStudent(tenant));
        UUID key = UUID.randomUUID();
        Response created = record(tenant, tenant.adminToken(), key, "25000", "0");
        assertThat(record(tenant, guardian, UUID.randomUUID(), "25000", "0").status()).isEqualTo(404);
        assertThat(get(tenant.path("/payments/cash-receptions/" + created.id("id")), guardian).status()).isEqualTo(404);
        assertThat(get(tenant.path("/payments/cash-receptions/by-key/" + key), guardian).status()).isEqualTo(404);
    }

    @Test
    void lesBornesMonetairesGardentLeurPrecisionEtNeDeviennentPasDesFlottants() {
        Tenant tenant = newTenant();
        Response created = record(tenant, tenant.adminToken(), UUID.randomUUID(), "999999999999999", "999999999999998");
        assertThat(created.status()).as(created.raw()).isEqualTo(201);
        assertThat(created.text("amountReceivedMinor")).isEqualTo("999999999999999");
        assertThat(created.text("amountRetainedMinor")).isEqualTo("1");
        assertThat(created.text("unallocatedMinor")).isEqualTo("1");
        assertThat(created.text("settledMinor")).isEqualTo("0");
    }

    @Test
    void laRevocationRetireAussiLeDroitDeRejouerEtDeRelire() {
        Tenant tenant = newTenant();
        String cashier = newStaff(tenant, "CASHIER");
        UUID actor = get("/api/v2/me", cashier).id("id");
        UUID key = UUID.randomUUID();
        Response created = record(tenant, cashier, key, "25000", "0");
        jdbc.sql("UPDATE memberships SET status='REVOKED' WHERE establishment_id=:tenant AND account_id=:actor")
                .param("tenant", tenant.id()).param("actor", actor).update();
        assertThat(record(tenant, cashier, key, "25000", "0").status()).isEqualTo(404);
        assertThat(get(tenant.path("/payments/cash-receptions/" + created.id("id")), cashier).status()).isEqualTo(404);
        assertThat(receptions(tenant)).isEqualTo(1);
    }

    @Test
    void laValidationRefuseMontantsIncompatiblesConfirmationAbsenteEtChampsNonAutorises() {
        Tenant tenant = newTenant();
        for (String received : List.of("0", "-1", "1.5", "1000000000000000", "01")) {
            assertThat(record(tenant, tenant.adminToken(), UUID.randomUUID(), received, "0").status()).as(received).isEqualTo(400);
        }
        for (String change : List.of("-1", "1.5", "1000000000000000")) {
            assertThat(record(tenant, tenant.adminToken(), UUID.randomUUID(), "10", change).status()).as(change).isEqualTo(400);
        }
        for (String change : List.of("10", "11")) {
            assertThat(record(tenant, tenant.adminToken(), UUID.randomUUID(), "10", change).status()).isEqualTo(422);
        }
        Map<String, Object> body = new HashMap<>(Map.of("idempotencyKey", UUID.randomUUID(),
                "amountReceivedMinor", "25000", "changeGivenMinor", "0", "cashReceivedConfirmed", false));
        assertThat(post(tenant.path("/payments/cash-receptions"), tenant.adminToken(), body).status()).isEqualTo(422);
        body.remove("cashReceivedConfirmed");
        assertThat(post(tenant.path("/payments/cash-receptions"), tenant.adminToken(), body).status()).isEqualTo(400);
        body.put("cashReceivedConfirmed", true);
        body.put("recordedByAccountId", UUID.randomUUID());
        assertThat(post(tenant.path("/payments/cash-receptions"), tenant.adminToken(), body).status()).isEqualTo(400);
        assertThat(receptions(tenant)).isZero();
    }

    @Test
    void uneReceptionSansAuditNePeutPasEtreValideeMemeParSqlDirect() {
        Tenant tenant = newTenant();
        UUID actor = get("/api/v2/me", tenant.adminToken()).id("id");
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(tx ->
                insertRaw(tenant, id, actor, "XAF", 25000, 0, true, "CR-" + id)))
                .isInstanceOf(RuntimeException.class).hasStackTraceContaining("audit lie");
        assertThat(receptions(tenant)).isZero();
    }

    @Test
    void unAuditDuneAutrePreuveNeValidePasLaReception() {
        Tenant tenant = newTenant();
        UUID actor = get("/api/v2/me", tenant.adminToken()).id("id");
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            insertRaw(tenant, id, actor, "XAF", 25000, 0, true, "CR-" + id);
            jdbc.sql("""
                    INSERT INTO audit_events(id,occurred_at,establishment_id,actor_account_id,action,target_type,target_id,details)
                    VALUES (:audit,now(),:tenant,:actor,'payments.cash.received','cash_reception',:id,'{"reference":"autre"}')
                    """).param("audit", UUID.randomUUID()).param("tenant", tenant.id()).param("actor", actor).param("id", id).update();
        })).isInstanceOf(RuntimeException.class).hasStackTraceContaining("audit lie");
        assertThat(receptions(tenant)).isZero();
        assertThat(audits(tenant)).isZero();
    }

    @Test
    @Timeout(25)
    void uneCleVerrouilleeProduitUnRefusBorneSansPerdreLaPossibiliteDeRejeu() {
        Tenant tenant = newTenant();
        UUID actor = get("/api/v2/me", tenant.adminToken()).id("id");
        AtomicReference<UUID> blockedKey = new AtomicReference<>();
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            UUID id = UUID.randomUUID();
            insertRaw(tenant, id, actor, "XAF", 25000, 0, true, "CR-" + id);
            blockedKey.set(jdbc.sql("SELECT idempotency_key FROM cash_receptions WHERE establishment_id=:tenant AND id=:id")
                    .param("tenant", tenant.id()).param("id", id).query(UUID.class).single());
            tx.setRollbackOnly();
            Response busy = record(tenant, tenant.adminToken(), blockedKey.get(), "25000", "0");
            assertThat(busy.status()).as(busy.raw()).isEqualTo(409);
            assertThat(busy.code()).isEqualTo("CASH_RECEPTION_BUSY");
            assertThat(busy.raw()).doesNotContain("55P03", "SQLException", "INSERT", "25000");
        });
        assertThat(receptions(tenant)).isZero();
        assertThat(record(tenant, tenant.adminToken(), blockedKey.get(), "25000", "0").status()).isEqualTo(201);
        assertThat(receptions(tenant)).isEqualTo(1);
        assertThat(audits(tenant)).isEqualTo(1);
    }

    @Test
    void lesContraintesSqlRefusentNullFauxMontantsDeviseEtAuteurDUnAutreEtablissement() {
        Tenant tenant = newTenant();
        UUID actor = get("/api/v2/me", tenant.adminToken()).id("id");
        UUID foreignActor = get("/api/v2/me", newTenant().adminToken()).id("id");
        for (Boolean confirmed : new Boolean[] {null, false}) {
            UUID id = UUID.randomUUID();
            assertThatThrownBy(() -> insertRawWithAudit(tenant, id, actor, "XAF", 25000, 0, confirmed, "CR-" + id))
                    .isInstanceOf(DataAccessException.class);
        }
        for (long[] amounts : new long[][] {{0,0}, {-1,0}, {10,10}, {10,-1}, {1_000_000_000_000_000L,0}}) {
            UUID id = UUID.randomUUID();
            assertThatThrownBy(() -> insertRawWithAudit(tenant, id, actor, "XAF", amounts[0], amounts[1], true, "CR-" + id))
                    .isInstanceOf(DataAccessException.class);
        }
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> insertRawWithAudit(tenant, id, actor, "EUR", 25000, 0, true, "CR-" + id)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertRawWithAudit(tenant, id, foreignActor, "XAF", 25000, 0, true, "CR-" + id)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertRawWithAudit(tenant, id, actor, "XAF", 25000, 0, true, null)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertRawWithAudit(tenant, id, actor, "XAF", 25000, 0, true, "CR-autre"))
                .isInstanceOf(DataAccessException.class);
        assertThat(receptions(tenant)).isZero();
        UUID valid = UUID.randomUUID();
        insertRawWithAudit(tenant, valid, actor, "XAF", 25000, 0, true, "CR-" + valid);
        assertThat(receptions(tenant)).isEqualTo(1);
        assertThat(get(tenant.path("/payments/cash-receptions/" + valid), tenant.adminToken()).text("unallocatedMinor")).isEqualTo("25000");
    }

    @Test
    void laDeclarationEtSaPreuveNePeuventEtreModifieesSupprimeesOuTronquees() {
        Tenant tenant = newTenant();
        record(tenant, tenant.adminToken(), UUID.randomUUID(), "25000", "0");
        for (String sql : List.of("UPDATE cash_receptions SET amount_received_minor=1 WHERE establishment_id=:tenant",
                "DELETE FROM cash_receptions WHERE establishment_id=:tenant")) {
            assertThatThrownBy(() -> jdbc.sql(sql).param("tenant", tenant.id()).update()).isInstanceOf(DataAccessException.class);
        }
        assertThatThrownBy(() -> jdbc.sql("TRUNCATE cash_receptions CASCADE").update()).isInstanceOf(DataAccessException.class);
        assertThat(receptions(tenant)).isEqualTo(1);
    }

    @Test
    void unEchecDAuditAnnuleLaReceptionEtPermetUnRejeuApresCorrection() {
        Tenant tenant = newTenant();
        UUID key = UUID.randomUUID();
        String constraint = "test_cash_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.sql("ALTER TABLE audit_events ADD CONSTRAINT " + constraint
                + " CHECK (NOT (establishment_id='" + tenant.id() + "'::uuid AND action='payments.cash.received')) NOT VALID").update();
        try {
            Response failed = record(tenant, tenant.adminToken(), key, "25000", "0");
            assertThat(failed.status()).as(failed.raw()).isEqualTo(409);
            assertThat(failed.code()).isEqualTo("CASH_RECEPTION_CONFLICT");
            assertThat(failed.raw()).doesNotContain("INSERT", "cash_receptions", "SQLException", "25000");
            assertThat(receptions(tenant)).isZero();
            assertThat(audits(tenant)).isZero();
        } finally {
            jdbc.sql("ALTER TABLE audit_events DROP CONSTRAINT " + constraint).update();
        }
        assertThat(record(tenant, tenant.adminToken(), key, "25000", "0").status()).isEqualTo(201);
        assertThat(audits(tenant)).isEqualTo(1);
    }

    @Test
    void laDeclarationNeChangePasLeDuEtNePermetAucunSettlementNiRestitution() throws Exception {
        School school = school();
        Tenant tenant = school.tenant();
        UUID obligation = issuedObligation(school);
        Response created = record(tenant, tenant.adminToken(), UUID.randomUUID(), "100000", "0");
        assertThat(created.status()).isEqualTo(201);
        UUID actor = get("/api/v2/me", tenant.adminToken()).id("id");
        assertThatThrownBy(() -> jdbc.sql("""
                INSERT INTO obligation_entries(id, establishment_id, obligation_id, kind, amount_minor,
                    reason, idempotency_key, recorded_at, recorded_by)
                VALUES (:id,:tenant,:obligation,'SETTLEMENT',-1,'Tentative interdite',:key,now(),:actor)
                """).param("id", UUID.randomUUID()).param("tenant", tenant.id()).param("obligation", obligation)
                .param("key", UUID.randomUUID().toString()).param("actor", actor).update())
                .isInstanceOf(DataAccessException.class);
        assertThat(jdbc.sql("SELECT sum(amount_minor) FROM obligation_entries WHERE obligation_id=:id")
                .param("id", obligation).query(Long.class).single()).isEqualTo(100000);
        for (String suffix : List.of("/allocations", "/restitutions", "/corrections")) {
            assertThat(post(tenant.path("/payments/cash-receptions/" + created.id("id") + suffix), tenant.adminToken(), Map.of()).status())
                    .isEqualTo(404);
        }
    }

    long receptions(Tenant tenant) {
        return jdbc.sql("SELECT count(*) FROM cash_receptions WHERE establishment_id=:tenant")
                .param("tenant", tenant.id()).query(Long.class).single();
    }

    long audits(Tenant tenant) {
        return jdbc.sql("SELECT count(*) FROM audit_events WHERE establishment_id=:tenant AND action='payments.cash.received'")
                .param("tenant", tenant.id()).query(Long.class).single();
    }

    void insertRaw(Tenant tenant, UUID id, UUID actor, String currency, long received, long change, Boolean confirmed, String reference) {
        jdbc.sql("""
                INSERT INTO cash_receptions(id,establishment_id,reference,idempotency_key,currency,
                    amount_received_minor,change_given_minor,cash_received_confirmed,recorded_at,
                    recorded_by_account_id,business_date,timezone)
                VALUES (:id,:tenant,:reference,:key,:currency,:received,:change,:confirmed,
                    '2026-11-11T09:00:00Z',:actor,'2026-11-11','Africa/Ndjamena')
                """).param("id", id).param("tenant", tenant.id()).param("reference", reference).param("key", UUID.randomUUID())
                .param("currency", currency).param("received", received).param("change", change)
                .param("confirmed", confirmed).param("actor", actor).update();
    }

    void insertRawWithAudit(Tenant tenant, UUID id, UUID actor, String currency, long received, long change, Boolean confirmed, String reference) {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            insertRaw(tenant, id, actor, currency, received, change, confirmed, reference);
            jdbc.sql("""
                    INSERT INTO audit_events(id,occurred_at,establishment_id,actor_account_id,action,target_type,target_id,details)
                    VALUES (:audit,now(),:tenant,:actor,'payments.cash.received','cash_reception',:id,
                            jsonb_build_object('reference',CAST(:reference AS text)))
                    """).param("audit", UUID.randomUUID()).param("tenant", tenant.id()).param("actor", actor)
                    .param("id", id).param("reference", reference).update();
        });
    }

    Response record(Tenant tenant, String token, UUID key, String received, String change) {
        return post(tenant.path("/payments/cash-receptions"), token, Map.of(
                "idempotencyKey", key, "amountReceivedMinor", received, "changeGivenMinor", change,
                "cashReceivedConfirmed", true));
    }
}
