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

    private static final String COLUMNS = "id, code, name, kind, timezone, currency, age_of_majority, status";

    private final JdbcClient jdbc;

    public EstablishmentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(Establishment establishment, Instant now) {
        jdbc.sql("""
                INSERT INTO establishments
                  (id, code, name, kind, timezone, currency, age_of_majority, status, created_at, updated_at)
                VALUES
                  (:id, :code, :name, :kind, :timezone, :currency, :ageOfMajority, :status, :now, :now)
                """)
                .param("id", establishment.id())
                .param("code", establishment.code())
                .param("name", establishment.name())
                .param("kind", establishment.kind())
                .param("timezone", establishment.timezone())
                .param("currency", establishment.currency())
                .param("ageOfMajority", establishment.ageOfMajority())
                .param("status", establishment.status())
                .param("now", Columns.timestamp(now))
                .update();
    }

    public Optional<Establishment> find(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM establishments WHERE id = :id")
                .param("id", id)
                .query(EstablishmentRepository::establishment)
                .optional();
    }

    /** Sérialise les changements de personnel d'un établissement : voir la règle du dernier administrateur. */
    public Optional<Establishment> lock(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM establishments WHERE id = :id FOR UPDATE")
                .param("id", id)
                .query(EstablishmentRepository::establishment)
                .optional();
    }

    public void updateSettings(UUID id, String name, int ageOfMajority, Instant now) {
        jdbc.sql("UPDATE establishments SET name = :name, age_of_majority = :age, updated_at = :now WHERE id = :id")
                .param("id", id)
                .param("name", name)
                .param("age", ageOfMajority)
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
                rs.getInt("age_of_majority"),
                rs.getString("status"));
    }
}
