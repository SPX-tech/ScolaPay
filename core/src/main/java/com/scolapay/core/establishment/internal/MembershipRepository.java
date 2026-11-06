package com.scolapay.core.establishment.internal;

import com.scolapay.core.establishment.Establishments.Establishment;
import com.scolapay.core.establishment.StaffRole;
import com.scolapay.core.shared.Columns;
import com.scolapay.core.shared.PageQuery;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class MembershipRepository {

    public record Membership(UUID id, UUID establishmentId, UUID accountId, String status, Set<StaffRole> roles) {}

    public record Affiliation(Establishment establishment, Set<StaffRole> roles) {}

    private final JdbcClient jdbc;

    public MembershipRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Vide si le compte n'a pas d'adhésion active à un établissement actif. */
    public Optional<Set<StaffRole>> activeRoles(UUID establishmentId, UUID accountId) {
        List<String> rows = jdbc.sql("""
                SELECT r.role
                FROM memberships m
                JOIN establishments e ON e.id = m.establishment_id
                LEFT JOIN membership_roles r ON r.membership_id = m.id
                WHERE m.establishment_id = :establishmentId
                  AND m.account_id = :accountId
                  AND m.status = 'ACTIVE'
                  AND e.status = 'ACTIVE'
                """)
                .param("establishmentId", establishmentId)
                .param("accountId", accountId)
                .query((rs, rowNum) -> rs.getString("role"))
                .list();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(toRoles(rows));
    }

    public List<Affiliation> affiliationsOf(UUID accountId) {
        record Row(Establishment establishment, String role) {}
        List<Row> rows = jdbc.sql("""
                SELECT e.id, e.code, e.name, e.kind, e.timezone, e.currency, e.age_of_majority, e.status, r.role
                FROM memberships m
                JOIN establishments e ON e.id = m.establishment_id
                LEFT JOIN membership_roles r ON r.membership_id = m.id
                WHERE m.account_id = :accountId AND m.status = 'ACTIVE' AND e.status = 'ACTIVE'
                ORDER BY e.name, e.id
                """)
                .param("accountId", accountId)
                .query((rs, rowNum) -> new Row(
                        new Establishment(
                                Columns.uuid(rs, "id"),
                                rs.getString("code"),
                                rs.getString("name"),
                                rs.getString("kind"),
                                rs.getString("timezone"),
                                rs.getString("currency"),
                                rs.getInt("age_of_majority"),
                                rs.getString("status")),
                        rs.getString("role")))
                .list();
        return rows.stream()
                .collect(Collectors.groupingBy(
                        Row::establishment,
                        java.util.LinkedHashMap::new,
                        Collectors.mapping(Row::role, Collectors.toList())))
                .entrySet().stream()
                .map(entry -> new Affiliation(entry.getKey(), toRoles(entry.getValue())))
                .toList();
    }

    public Optional<Membership> find(UUID establishmentId, UUID membershipId) {
        return jdbc.sql("""
                SELECT id, establishment_id, account_id, status
                FROM memberships
                WHERE establishment_id = :establishmentId AND id = :id
                """)
                .param("establishmentId", establishmentId)
                .param("id", membershipId)
                .query((rs, rowNum) -> new Membership(
                        Columns.uuid(rs, "id"),
                        Columns.uuid(rs, "establishment_id"),
                        Columns.uuid(rs, "account_id"),
                        rs.getString("status"),
                        Set.of()))
                .optional()
                .map(found -> new Membership(
                        found.id(), found.establishmentId(), found.accountId(), found.status(),
                        rolesOf(establishmentId, found.id())));
    }

    public List<Membership> list(UUID establishmentId, PageQuery page) {
        List<Membership> rows = jdbc.sql("""
                SELECT id, establishment_id, account_id, status
                FROM memberships
                WHERE establishment_id = :establishmentId
                ORDER BY created_at, id
                LIMIT :limit OFFSET :offset
                """)
                .param("establishmentId", establishmentId)
                .param("limit", page.limit())
                .param("offset", page.offset())
                .query((rs, rowNum) -> new Membership(
                        Columns.uuid(rs, "id"),
                        Columns.uuid(rs, "establishment_id"),
                        Columns.uuid(rs, "account_id"),
                        rs.getString("status"),
                        Set.of()))
                .list();
        return rows.stream()
                .map(row -> new Membership(
                        row.id(), row.establishmentId(), row.accountId(), row.status(),
                        rolesOf(establishmentId, row.id())))
                .toList();
    }

    /** Une adhésion par compte et par établissement : une nouvelle invitation la réactive et y ajoute le rôle. */
    public UUID upsertActive(UUID establishmentId, UUID accountId, Instant now) {
        return jdbc.sql("""
                INSERT INTO memberships (id, establishment_id, account_id, status, created_at, updated_at)
                VALUES (:id, :establishmentId, :accountId, 'ACTIVE', :now, :now)
                ON CONFLICT (establishment_id, account_id)
                DO UPDATE SET status = 'ACTIVE', updated_at = EXCLUDED.updated_at
                RETURNING id
                """)
                .param("id", UUID.randomUUID())
                .param("establishmentId", establishmentId)
                .param("accountId", accountId)
                .param("now", Columns.timestamp(now))
                .query(UUID.class)
                .single();
    }

    public void addRole(UUID establishmentId, UUID membershipId, StaffRole role, Instant now) {
        jdbc.sql("""
                INSERT INTO membership_roles (establishment_id, membership_id, role, granted_at)
                VALUES (:establishmentId, :membershipId, :role, :now)
                ON CONFLICT (membership_id, role) DO NOTHING
                """)
                .param("establishmentId", establishmentId)
                .param("membershipId", membershipId)
                .param("role", role.name())
                .param("now", Columns.timestamp(now))
                .update();
    }

    public void replaceRoles(UUID establishmentId, UUID membershipId, Set<StaffRole> roles, Instant now) {
        jdbc.sql("DELETE FROM membership_roles WHERE establishment_id = :establishmentId AND membership_id = :membershipId")
                .param("establishmentId", establishmentId)
                .param("membershipId", membershipId)
                .update();
        roles.forEach(role -> addRole(establishmentId, membershipId, role, now));
    }

    public void setStatus(UUID establishmentId, UUID membershipId, String status, Instant now) {
        jdbc.sql("""
                UPDATE memberships SET status = :status, updated_at = :now
                WHERE establishment_id = :establishmentId AND id = :id
                """)
                .param("establishmentId", establishmentId)
                .param("id", membershipId)
                .param("status", status)
                .param("now", Columns.timestamp(now))
                .update();
    }

    public int countOtherActiveAdmins(UUID establishmentId, UUID excludedMembershipId) {
        return jdbc.sql("""
                SELECT count(*)
                FROM memberships m
                JOIN membership_roles r ON r.membership_id = m.id
                WHERE m.establishment_id = :establishmentId
                  AND m.id <> :excluded
                  AND m.status = 'ACTIVE'
                  AND r.role = 'ESTABLISHMENT_ADMIN'
                """)
                .param("establishmentId", establishmentId)
                .param("excluded", excludedMembershipId)
                .query(Integer.class)
                .single();
    }

    private Set<StaffRole> rolesOf(UUID establishmentId, UUID membershipId) {
        return toRoles(jdbc.sql("""
                SELECT role FROM membership_roles
                WHERE establishment_id = :establishmentId AND membership_id = :membershipId
                """)
                .param("establishmentId", establishmentId)
                .param("membershipId", membershipId)
                .query(String.class)
                .list());
    }

    private static Set<StaffRole> toRoles(List<String> names) {
        return names.stream()
                .filter(Objects::nonNull)
                .map(StaffRole::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(StaffRole.class)));
    }
}
