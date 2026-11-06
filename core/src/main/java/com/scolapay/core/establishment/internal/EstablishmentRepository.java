package com.scolapay.core.establishment.internal;

import com.scolapay.core.establishment.Establishments.Establishment;
import com.scolapay.core.shared.Columns;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class EstablishmentRepository {

    private static final String SELECT = """
            SELECT e.id, e.code, e.name, e.kind, e.timezone, e.currency, e.country_code, r.age_of_majority, e.status
            FROM establishments e
            JOIN legal_majority_rules r ON r.country_code = e.country_code
            """;

    private final JdbcClient jdbc;

    public EstablishmentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(
            UUID id, String code, String name, String kind, String timezone, String currency, String countryCode, Instant now) {
        jdbc.sql("""
                INSERT INTO establishments
                  (id, code, name, kind, timezone, currency, country_code, created_at, updated_at)
                VALUES
                  (:id, :code, :name, :kind, :timezone, :currency, :countryCode, :now, :now)
                """)
                .param("id", id)
                .param("code", code)
                .param("name", name)
                .param("kind", kind)
                .param("timezone", timezone)
                .param("currency", currency)
                .param("countryCode", countryCode)
                .param("now", Columns.timestamp(now))
                .update();
    }

    public boolean countryIsSupported(String countryCode) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM legal_majority_rules WHERE country_code = :countryCode)")
                .param("countryCode", countryCode)
                .query(Boolean.class)
                .single();
    }

    public Optional<Establishment> find(UUID id) {
        return jdbc.sql(SELECT + "WHERE e.id = :id")
                .param("id", id)
                .query(EstablishmentRepository::establishment)
                .optional();
    }

    /** Sérialise les changements de personnel d'un établissement : voir la règle du dernier administrateur. */
    public Optional<Establishment> lock(UUID id) {
        return jdbc.sql(SELECT + "WHERE e.id = :id FOR UPDATE OF e")
                .param("id", id)
                .query(EstablishmentRepository::establishment)
                .optional();
    }

    public void updateSettings(UUID id, String name, Instant now) {
        jdbc.sql("UPDATE establishments SET name = :name, updated_at = :now WHERE id = :id")
                .param("id", id)
                .param("name", name)
                .param("now", Columns.timestamp(now))
                .update();
    }

    public int setStatus(UUID id, String status, Instant now) {
        return jdbc.sql("UPDATE establishments SET status = :status, updated_at = :now WHERE id = :id")
                .param("id", id)
                .param("status", status)
                .param("now", Columns.timestamp(now))
                .update();
    }

    private static Establishment establishment(ResultSet rs, int rowNum) throws SQLException {
        return new Establishment(
                Columns.uuid(rs, "id"),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("kind"),
                rs.getString("timezone"),
                rs.getString("currency"),
                rs.getString("country_code"),
                rs.getInt("age_of_majority"),
                rs.getString("status"));
    }
}
