package com.scolapay.core.identity.internal;

import com.scolapay.core.identity.Identifier;
import com.scolapay.core.shared.Columns;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class AccountRepository {

    public record Account(
            UUID id,
            String fullName,
            String passwordHash,
            String status,
            boolean platformOperator,
            int tokenVersion,
            int failedLoginAttempts,
            Instant lockedUntil) {

        public boolean active() {
            return "ACTIVE".equals(status);
        }
    }

    private static final String COLUMNS = """
            a.id, a.full_name, a.password_hash, a.status, a.platform_operator,
            a.token_version, a.failed_login_attempts, a.locked_until
            """;

    private final JdbcClient jdbc;

    public AccountRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void create(UUID id, String fullName, String passwordHash, boolean operator, Instant now) {
        jdbc.sql("""
                INSERT INTO accounts (id, full_name, password_hash, platform_operator, created_at, updated_at)
                VALUES (:id, :fullName, :passwordHash, :operator, :now, :now)
                """)
                .param("id", id)
                .param("fullName", fullName)
                .param("passwordHash", passwordHash)
                .param("operator", operator)
                .param("now", Columns.timestamp(now))
                .update();
    }

    public void addIdentifier(UUID accountId, Identifier identifier, Instant now) {
        jdbc.sql("""
                INSERT INTO account_identifiers (kind, value, account_id, created_at)
                VALUES (:kind, :value, :accountId, :now)
                """)
                .param("kind", identifier.kind().name())
                .param("value", identifier.value())
                .param("accountId", accountId)
                .param("now", Columns.timestamp(now))
                .update();
    }

    public Optional<Account> find(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM accounts a WHERE a.id = :id")
                .param("id", id)
                .query(AccountRepository::account)
                .optional();
    }

    public Optional<Account> findByIdentifier(Identifier identifier) {
        return byIdentifier(identifier, "");
    }

    /** Sérialise les tentatives de connexion d'un compte pour que le compteur d'échecs reste exact. */
    public Optional<Account> lockByIdentifier(Identifier identifier) {
        return byIdentifier(identifier, " FOR UPDATE OF a");
    }

    private Optional<Account> byIdentifier(Identifier identifier, String suffix) {
        return jdbc.sql("SELECT " + COLUMNS + """
                FROM accounts a
                JOIN account_identifiers i ON i.account_id = a.id
                WHERE i.kind = :kind AND i.value = :value
                """ + suffix)
                .param("kind", identifier.kind().name())
                .param("value", identifier.value())
                .query(AccountRepository::account)
                .optional();
    }

    public void recordFailedLogin(UUID id, int attempts, Instant lockedUntil, Instant now) {
        jdbc.sql("""
                UPDATE accounts
                SET failed_login_attempts = :attempts, locked_until = :lockedUntil, updated_at = :now
                WHERE id = :id
                """)
                .param("id", id)
                .param("attempts", attempts)
                .param("lockedUntil", Columns.timestamp(lockedUntil))
                .param("now", Columns.timestamp(now))
                .update();
    }

    public void clearFailedLogins(UUID id, Instant now) {
        jdbc.sql("""
                UPDATE accounts
                SET failed_login_attempts = 0, locked_until = NULL, updated_at = :now
                WHERE id = :id AND (failed_login_attempts <> 0 OR locked_until IS NOT NULL)
                """)
                .param("id", id)
                .param("now", Columns.timestamp(now))
                .update();
    }

    /** Incrémenter la version invalide immédiatement tous les jetons déjà émis. */
    public void updatePassword(UUID id, String passwordHash, Instant now) {
        jdbc.sql("""
                UPDATE accounts
                SET password_hash = :passwordHash, token_version = token_version + 1, updated_at = :now
                WHERE id = :id
                """)
                .param("id", id)
                .param("passwordHash", passwordHash)
                .param("now", Columns.timestamp(now))
                .update();
    }

    public List<Identifier> identifiers(UUID accountId) {
        return jdbc.sql("SELECT kind, value FROM account_identifiers WHERE account_id = :id ORDER BY kind, value")
                .param("id", accountId)
                .query((rs, rowNum) -> new Identifier(Identifier.Kind.valueOf(rs.getString("kind")), rs.getString("value")))
                .list();
    }

    public List<IdentifiedAccount> findAll(Collection<UUID> ids) {
        return jdbc.sql("""
                SELECT a.id, a.full_name, i.value
                FROM accounts a
                LEFT JOIN account_identifiers i ON i.account_id = a.id
                WHERE a.id IN (:ids)
                ORDER BY i.kind, i.value
                """)
                .param("ids", ids)
                .query((rs, rowNum) -> new IdentifiedAccount(
                        Columns.uuid(rs, "id"), rs.getString("full_name"), rs.getString("value")))
                .list();
    }

    public record IdentifiedAccount(UUID id, String fullName, String contact) {}

    public boolean operatorExists() {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM accounts WHERE platform_operator)")
                .query(Boolean.class)
                .single();
    }

    private static Account account(ResultSet rs, int rowNum) throws SQLException {
        return new Account(
                Columns.uuid(rs, "id"),
                rs.getString("full_name"),
                rs.getString("password_hash"),
                rs.getString("status"),
                rs.getBoolean("platform_operator"),
                rs.getInt("token_version"),
                rs.getInt("failed_login_attempts"),
                Columns.instant(rs, "locked_until"));
    }
}
