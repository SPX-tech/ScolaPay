package com.scolapay.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** Un lot reste borné : au-delà, on le découpe par unité académique. Plafond abaissé pour ne pas créer 5 000 élèves. */
@TestPropertySource(properties = "scolapay.billing.max-issuance-size=2")
class IssuanceLimitTest extends IntegrationTest {

    @Test
    void unLotTropGrandEstRefuseEtSeDecoupeParUnite() {
        Tenant tenant = newTenant();
        UUID year = openYear(tenant);
        UUID sixieme = newLevel(tenant);
        UUID terminale = newLevel(tenant);
        for (UUID level : List.of(sixieme, sixieme, terminale)) {
            Response enrolled = post(tenant.path("/students/" + newStudent(tenant) + "/enrollments"), tenant.adminToken(), Map.of(
                    "academicYearId", year.toString(), "academicUnitId", level.toString()));
            assertThat(enrolled.status()).as(enrolled.raw()).isEqualTo(201);
        }
        Response period = post(tenant.path("/billing/periods"), tenant.adminToken(), Map.of(
                "academicYearId", year.toString(), "code", "ANNEE", "name", "Année", "startsOn", "2026-09-01", "endsOn", "2027-07-15"));
        Response charge = post(tenant.path("/billing/fee-definitions"), tenant.adminToken(), Map.of(
                "academicYearId", year.toString(), "billingPeriodId", period.text("id"), "name", "Scolarité"));
        Response draft = post(tenant.path("/billing/fee-catalogs"), tenant.adminToken(), Map.of("academicYearId", year.toString()));
        String catalog = tenant.path("/billing/fee-catalogs/" + draft.text("id"));
        assertThat(post(catalog + "/items", tenant.adminToken(), Map.of(
                "feeDefinitionId", charge.text("id"), "feeCode", "SCOL", "label", "Scolarité", "category", "TUITION", "application", "AUTOMATIC",
                "amountMinor", "1000", "installments", List.of(Map.of("dueOn", "2026-10-01", "amountMinor", "1000")))).status()).isEqualTo(201);
        assertThat(post(catalog + "/publish", tenant.adminToken(), Map.of()).status()).isEqualTo(200);

        Response tooLarge = get(tenant.path("/billing/issuance-preview?academicYearId=" + year), tenant.adminToken());
        assertThat(tooLarge.status()).as(tooLarge.raw()).isEqualTo(422);
        assertThat(tooLarge.code()).isEqualTo("ISSUANCE_TOO_LARGE");

        Response narrowed = get(tenant.path("/billing/issuance-preview?academicYearId=" + year + "&academicUnitId=" + sixieme), tenant.adminToken());
        assertThat(narrowed.status()).as(narrowed.raw()).isEqualTo(200);
        assertThat(narrowed.body().path("activeEnrollments").asLong()).isEqualTo(2);
    }
}
