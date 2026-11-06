package com.scolapay.core.schooling.internal;

import com.scolapay.core.shared.Columns;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class AcademicUnitRepository {

    public record AcademicUnit(UUID id, UUID establishmentId, UUID parentId, String kind, String code, String name, String status) {}

    private static final String SELECT = """
            SELECT id, establishment_id, parent_id, kind, code, name, status
            FROM academic_units
            """;

    private final JdbcClient jdbc;

    public AcademicUnitRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(AcademicUnit unit, Instant now) {
        jdbc.sql("""
                INSERT INTO academic_units (id, establishment_id, parent_id, kind, code, name, status, created_at, updated_at)
                VALUES (:id, :establishmentId, :parentId, :kind, :code, :name, :status, :now, :now)
                """)
                .param("id", unit.id())
                .param("establishmentId", unit.establishmentId())
                .param("parentId", unit.parentId())
                .param("kind", unit.kind())
                .param("code", unit.code())
                .param("name", unit.name())
                .param("status", unit.status())
                .param("now", Columns.timestamp(now))
                .update();
    }

    public Optional<AcademicUnit> find(UUID establishmentId, UUID id) {
        return jdbc.sql(SELECT + "WHERE establishment_id = :establishmentId AND id = :id")
                .param("establishmentId", establishmentId)
                .param("id", id)
                .query(AcademicUnitRepository::unit)
                .optional();
    }

    /** Sérialise l'archivage d'une unité et la création ou la réactivation de ses enfants. */
    public Optional<AcademicUnit> lock(UUID establishmentId, UUID id) {
        return jdbc.sql(SELECT + "WHERE establishment_id = :establishmentId AND id = :id FOR UPDATE")
                .param("establishmentId", establishmentId)
                .param("id", id)
                .query(AcademicUnitRepository::unit)
                .optional();
    }

    public List<AcademicUnit> list(UUID establishmentId) {
        return jdbc.sql(SELECT + "WHERE establishment_id = :establishmentId ORDER BY code")
                .param("establishmentId", establishmentId)
                .query(AcademicUnitRepository::unit)
                .list();
    }

    public void update(UUID establishmentId, UUID id, String name, String status, Instant now) {
        jdbc.sql("""
                UPDATE academic_units SET name = :name, status = :status, updated_at = :now
                WHERE establishment_id = :establishmentId AND id = :id
                """)
                .param("establishmentId", establishmentId)
                .param("id", id)
                .param("name", name)
                .param("status", status)
                .param("now", Columns.timestamp(now))
                .update();
    }

    public boolean hasActiveChildren(UUID establishmentId, UUID id) {
        return jdbc.sql("""
                SELECT EXISTS (
                  SELECT 1 FROM academic_units
                  WHERE establishment_id = :establishmentId AND parent_id = :id AND status = 'ACTIVE')
                """)
                .param("establishmentId", establishmentId)
                .param("id", id)
                .query(Boolean.class)
                .single();
    }

    private static AcademicUnit unit(ResultSet rs, int rowNum) throws SQLException {
        return new AcademicUnit(
                Columns.uuid(rs, "id"),
                Columns.uuid(rs, "establishment_id"),
                Columns.uuid(rs, "parent_id"),
                rs.getString("kind"),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("status"));
    }
}
