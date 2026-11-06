package com.scolapay.core.billing.internal;

import com.scolapay.core.shared.Columns;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class FeeCatalogRepository {

    public record Period(UUID id, UUID academicYearId, String code, String name, LocalDate startsOn, LocalDate endsOn, String status) {

        boolean active() {
            return "ACTIVE".equals(status);
        }
    }

    public record Version(
            UUID id,
            UUID academicYearId,
            int version,
            String status,
            String currency,
            Instant createdAt,
            Instant publishedAt,
            Instant supersededAt) {

        boolean draft() {
            return "DRAFT".equals(status);
        }
    }

    public record Installment(LocalDate dueOn, long amountMinor) {}

    public record Item(
            UUID id,
            String feeCode,
            String label,
            String category,
            String application,
            long amountMinor,
            UUID billingPeriodId,
            UUID academicUnitId,
            List<Installment> installments) {}

    private static final String PERIOD_COLUMNS = "id, academic_year_id, code, name, starts_on, ends_on, status";
    private static final String VERSION_COLUMNS =
            "id, academic_year_id, version, status, currency, created_at, published_at, superseded_at";

    private final JdbcClient jdbc;

    public FeeCatalogRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // Périodes

    public void insertPeriod(UUID establishmentId, Period period, Instant now) {
        jdbc.sql("""
                INSERT INTO billing_periods
                  (id, establishment_id, academic_year_id, code, name, starts_on, ends_on, status, created_at, updated_at)
                VALUES (:id, :establishmentId, :yearId, :code, :name, :startsOn, :endsOn, :status, :now, :now)
                """)
                .param("id", period.id())
                .param("establishmentId", establishmentId)
                .param("yearId", period.academicYearId())
                .param("code", period.code())
                .param("name", period.name())
                .param("startsOn", period.startsOn())
                .param("endsOn", period.endsOn())
                .param("status", period.status())
                .param("now", Columns.timestamp(now))
                .update();
    }

    public Optional<Period> findPeriod(UUID establishmentId, UUID id) {
        return jdbc.sql("SELECT " + PERIOD_COLUMNS + " FROM billing_periods WHERE establishment_id = :establishmentId AND id = :id")
                .param("establishmentId", establishmentId)
                .param("id", id)
                .query(FeeCatalogRepository::period)
                .optional();
    }

    public List<Period> listPeriods(UUID establishmentId, UUID academicYearId) {
        return jdbc.sql("SELECT " + PERIOD_COLUMNS + """
                 FROM billing_periods
                WHERE establishment_id = :establishmentId AND academic_year_id = :yearId
                ORDER BY starts_on, code
                """)
                .param("establishmentId", establishmentId)
                .param("yearId", academicYearId)
                .query(FeeCatalogRepository::period)
                .list();
    }

    public int updatePeriod(UUID establishmentId, UUID id, String name, String status, Instant now) {
        return jdbc.sql("""
                UPDATE billing_periods SET name = :name, status = :status, updated_at = :now
                WHERE establishment_id = :establishmentId AND id = :id
                """)
                .param("establishmentId", establishmentId)
                .param("id", id)
                .param("name", name)
                .param("status", status)
                .param("now", Columns.timestamp(now))
                .update();
    }

    // Versions

    public void insertVersion(UUID establishmentId, Version version, UUID createdBy) {
        jdbc.sql("""
                INSERT INTO fee_catalog_versions (id, establishment_id, academic_year_id, version, status, currency, created_at, created_by)
                VALUES (:id, :establishmentId, :yearId, :version, 'DRAFT', :currency, :createdAt, :createdBy)
                """)
                .param("id", version.id())
                .param("establishmentId", establishmentId)
                .param("yearId", version.academicYearId())
                .param("version", version.version())
                .param("currency", version.currency())
                .param("createdAt", Columns.timestamp(version.createdAt()))
                .param("createdBy", createdBy)
                .update();
    }

    public int nextVersionNumber(UUID establishmentId, UUID academicYearId) {
        return jdbc.sql("""
                SELECT COALESCE(max(version), 0) + 1 FROM fee_catalog_versions
                WHERE establishment_id = :establishmentId AND academic_year_id = :yearId
                """)
                .param("establishmentId", establishmentId)
                .param("yearId", academicYearId)
                .query(Integer.class)
                .single();
    }

    public Optional<Version> findVersion(UUID establishmentId, UUID id) {
        return jdbc.sql("SELECT " + VERSION_COLUMNS + " FROM fee_catalog_versions WHERE establishment_id = :establishmentId AND id = :id")
                .param("establishmentId", establishmentId)
                .param("id", id)
                .query(FeeCatalogRepository::version)
                .optional();
    }

    /** Sérialise les modifications d'un brouillon et sa publication. */
    public Optional<Version> lockVersion(UUID establishmentId, UUID id) {
        return jdbc.sql("SELECT " + VERSION_COLUMNS + " FROM fee_catalog_versions WHERE establishment_id = :establishmentId AND id = :id FOR UPDATE")
                .param("establishmentId", establishmentId)
                .param("id", id)
                .query(FeeCatalogRepository::version)
                .optional();
    }

    public List<Version> listVersions(UUID establishmentId, UUID academicYearId) {
        return jdbc.sql("SELECT " + VERSION_COLUMNS + """
                 FROM fee_catalog_versions
                WHERE establishment_id = :establishmentId AND academic_year_id = :yearId
                ORDER BY version DESC
                """)
                .param("establishmentId", establishmentId)
                .param("yearId", academicYearId)
                .query(FeeCatalogRepository::version)
                .list();
    }

    public Optional<Version> publishedVersion(UUID establishmentId, UUID academicYearId) {
        return jdbc.sql("SELECT " + VERSION_COLUMNS + """
                 FROM fee_catalog_versions
                WHERE establishment_id = :establishmentId AND academic_year_id = :yearId AND status = 'PUBLISHED'
                """)
                .param("establishmentId", establishmentId)
                .param("yearId", academicYearId)
                .query(FeeCatalogRepository::version)
                .optional();
    }

    /** La dernière version qui a été publiée avant celle-ci, en vigueur ou remplacée. */
    public Optional<Version> previousPublished(UUID establishmentId, UUID academicYearId, int beforeVersion) {
        return jdbc.sql("SELECT " + VERSION_COLUMNS + """
                 FROM fee_catalog_versions
                WHERE establishment_id = :establishmentId AND academic_year_id = :yearId
                  AND status IN ('PUBLISHED', 'SUPERSEDED') AND version < :version
                ORDER BY version DESC LIMIT 1
                """)
                .param("establishmentId", establishmentId)
                .param("yearId", academicYearId)
                .param("version", beforeVersion)
                .query(FeeCatalogRepository::version)
                .optional();
    }

    public void markSuperseded(UUID establishmentId, UUID id, Instant now) {
        jdbc.sql("""
                UPDATE fee_catalog_versions SET status = 'SUPERSEDED', superseded_at = :now
                WHERE establishment_id = :establishmentId AND id = :id
                """)
                .param("establishmentId", establishmentId)
                .param("id", id)
                .param("now", Columns.timestamp(now))
                .update();
    }

    public void markPublished(UUID establishmentId, UUID id, UUID publishedBy, Instant now) {
        jdbc.sql("""
                UPDATE fee_catalog_versions SET status = 'PUBLISHED', published_at = :now, published_by = :publishedBy
                WHERE establishment_id = :establishmentId AND id = :id
                """)
                .param("establishmentId", establishmentId)
                .param("id", id)
                .param("publishedBy", publishedBy)
                .param("now", Columns.timestamp(now))
                .update();
    }

    public void deleteDraft(UUID establishmentId, UUID id) {
        jdbc.sql("DELETE FROM fee_items WHERE establishment_id = :establishmentId AND catalog_version_id = :id")
                .param("establishmentId", establishmentId)
                .param("id", id)
                .update();
        jdbc.sql("DELETE FROM fee_catalog_versions WHERE establishment_id = :establishmentId AND id = :id")
                .param("establishmentId", establishmentId)
                .param("id", id)
                .update();
    }

    // Lignes

    public void insertItem(UUID establishmentId, UUID academicYearId, UUID versionId, Item item) {
        jdbc.sql("""
                INSERT INTO fee_items
                  (id, establishment_id, academic_year_id, catalog_version_id, fee_code, label, category, application,
                   amount_minor, billing_period_id, academic_unit_id)
                VALUES (:id, :establishmentId, :yearId, :versionId, :feeCode, :label, :category, :application,
                        :amountMinor, :periodId, :unitId)
                """)
                .param("id", item.id())
                .param("establishmentId", establishmentId)
                .param("yearId", academicYearId)
                .param("versionId", versionId)
                .param("feeCode", item.feeCode())
                .param("label", item.label())
                .param("category", item.category())
                .param("application", item.application())
                .param("amountMinor", item.amountMinor())
                .param("periodId", item.billingPeriodId())
                .param("unitId", item.academicUnitId())
                .update();
        insertInstallments(establishmentId, versionId, item);
    }

    public int updateItem(UUID establishmentId, UUID versionId, Item item) {
        int updated = jdbc.sql("""
                UPDATE fee_items SET fee_code = :feeCode, label = :label, category = :category, application = :application,
                  amount_minor = :amountMinor, billing_period_id = :periodId, academic_unit_id = :unitId
                WHERE establishment_id = :establishmentId AND catalog_version_id = :versionId AND id = :id
                """)
                .param("id", item.id())
                .param("establishmentId", establishmentId)
                .param("versionId", versionId)
                .param("feeCode", item.feeCode())
                .param("label", item.label())
                .param("category", item.category())
                .param("application", item.application())
                .param("amountMinor", item.amountMinor())
                .param("periodId", item.billingPeriodId())
                .param("unitId", item.academicUnitId())
                .update();
        if (updated > 0) {
            jdbc.sql("DELETE FROM fee_item_installments WHERE establishment_id = :establishmentId AND catalog_version_id = :versionId AND fee_item_id = :id")
                    .param("establishmentId", establishmentId)
                    .param("versionId", versionId)
                    .param("id", item.id())
                    .update();
            insertInstallments(establishmentId, versionId, item);
        }
        return updated;
    }

    public int deleteItem(UUID establishmentId, UUID versionId, UUID itemId) {
        return jdbc.sql("DELETE FROM fee_items WHERE establishment_id = :establishmentId AND catalog_version_id = :versionId AND id = :id")
                .param("establishmentId", establishmentId)
                .param("versionId", versionId)
                .param("id", itemId)
                .update();
    }

    private void insertInstallments(UUID establishmentId, UUID versionId, Item item) {
        int seq = 1;
        for (Installment installment : item.installments()) {
            jdbc.sql("""
                    INSERT INTO fee_item_installments (fee_item_id, seq, establishment_id, catalog_version_id, due_on, amount_minor)
                    VALUES (:itemId, :seq, :establishmentId, :versionId, :dueOn, :amountMinor)
                    """)
                    .param("itemId", item.id())
                    .param("seq", seq++)
                    .param("establishmentId", establishmentId)
                    .param("versionId", versionId)
                    .param("dueOn", installment.dueOn())
                    .param("amountMinor", installment.amountMinor())
                    .update();
        }
    }

    public List<Item> items(UUID establishmentId, UUID versionId) {
        Map<UUID, List<Installment>> installments = new LinkedHashMap<>();
        jdbc.sql("""
                SELECT fee_item_id, due_on, amount_minor FROM fee_item_installments
                WHERE establishment_id = :establishmentId AND catalog_version_id = :versionId
                ORDER BY fee_item_id, seq
                """)
                .param("establishmentId", establishmentId)
                .param("versionId", versionId)
                .query(rs -> {
                    installments.computeIfAbsent(Columns.uuid(rs, "fee_item_id"), id -> new ArrayList<>())
                            .add(new Installment(rs.getObject("due_on", LocalDate.class), rs.getLong("amount_minor")));
                });
        return jdbc.sql("""
                SELECT id, fee_code, label, category, application, amount_minor, billing_period_id, academic_unit_id
                FROM fee_items
                WHERE establishment_id = :establishmentId AND catalog_version_id = :versionId
                ORDER BY fee_code, academic_unit_id NULLS FIRST
                """)
                .param("establishmentId", establishmentId)
                .param("versionId", versionId)
                .query((rs, rowNum) -> new Item(
                        Columns.uuid(rs, "id"),
                        rs.getString("fee_code"),
                        rs.getString("label"),
                        rs.getString("category"),
                        rs.getString("application"),
                        rs.getLong("amount_minor"),
                        Columns.uuid(rs, "billing_period_id"),
                        Columns.uuid(rs, "academic_unit_id"),
                        List.copyOf(installments.getOrDefault(Columns.uuid(rs, "id"), List.of()))))
                .list();
    }

    /** Un code déjà publié, ou présent dans ce brouillon, reste sur sa période : voir la décision 0020. */
    public boolean feeCodeBoundToAnotherPeriod(
            UUID establishmentId, UUID academicYearId, UUID versionId, UUID excludedItemId, String feeCode, UUID periodId) {
        return jdbc.sql("""
                SELECT EXISTS (
                  SELECT 1 FROM fee_items i
                  JOIN fee_catalog_versions v ON v.id = i.catalog_version_id
                  WHERE i.establishment_id = :establishmentId AND i.academic_year_id = :yearId
                    AND i.fee_code = :feeCode AND i.billing_period_id <> :periodId
                    AND (v.status IN ('PUBLISHED', 'SUPERSEDED')
                         OR (i.catalog_version_id = :versionId AND i.id IS DISTINCT FROM CAST(:excluded AS uuid))))
                """)
                .param("establishmentId", establishmentId)
                .param("yearId", academicYearId)
                .param("feeCode", feeCode)
                .param("periodId", periodId)
                .param("versionId", versionId)
                .param("excluded", excludedItemId)
                .query(Boolean.class)
                .single();
    }

    private static Period period(ResultSet rs, int rowNum) throws SQLException {
        return new Period(
                Columns.uuid(rs, "id"),
                Columns.uuid(rs, "academic_year_id"),
                rs.getString("code"),
                rs.getString("name"),
                rs.getObject("starts_on", LocalDate.class),
                rs.getObject("ends_on", LocalDate.class),
                rs.getString("status"));
    }

    private static Version version(ResultSet rs, int rowNum) throws SQLException {
        return new Version(
                Columns.uuid(rs, "id"),
                Columns.uuid(rs, "academic_year_id"),
                rs.getInt("version"),
                rs.getString("status"),
                rs.getString("currency"),
                Columns.instant(rs, "created_at"),
                Columns.instant(rs, "published_at"),
                Columns.instant(rs, "superseded_at"));
    }
}
