package com.scolapay.core.billing.internal;

import com.scolapay.core.shared.Columns;
import com.scolapay.core.shared.PageQuery;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.stereotype.Repository;

@Repository
public class BillingPartyRepository {

    public record Party(UUID id, String kind, String displayName, String identifier, UUID accountId, Instant createdAt, UUID createdBy, int revision) {}

    public record Correction(UUID id, UUID partyId, int previousRevision, int revision, String previousName,
            String previousIdentifier, String newName, String newIdentifier, String reason, Instant correctedAt, UUID correctedBy) {}

    public record Designation(
            UUID id,
            UUID obligationId,
            UUID partyId,
            String partyName,
            String partyKind,
            UUID partyAccountId,
            String role,
            String debtorStatus,
            String commitmentReference,
            Instant designatedAt,
            UUID designatedBy,
            Instant confirmedAt,
            UUID confirmedBy,
            Instant endedAt,
            UUID endedBy,
            String endReason) {

        boolean active() {
            return endedAt == null;
        }

        Designation withoutConfidentialText() {
            return new Designation(
                    id, obligationId, partyId, partyName, partyKind, partyAccountId, role, debtorStatus, commitmentReference,
                    designatedAt, designatedBy, confirmedAt, confirmedBy, endedAt, endedBy, null);
        }
    }

    public record ObligationRef(UUID establishmentId, UUID obligationId) {}

    public record HeldRole(String role, String debtorStatus) {}

    private static final String PARTY_COLUMNS = "id, kind, display_name, identifier, account_id, created_at, created_by, revision";

    private static final String DESIGNATION = """
            SELECT op.id, op.obligation_id, op.party_id, p.display_name, p.kind, p.account_id, op.role, op.debtor_status, op.commitment_reference,
                   op.designated_at, op.designated_by, op.confirmed_at, op.confirmed_by, op.ended_at, op.ended_by, op.end_reason
            FROM obligation_parties op
            JOIN billing_parties p ON p.id = op.party_id
            """;

    private final JdbcClient jdbc;

    public BillingPartyRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insertParty(UUID establishmentId, Party party) {
        jdbc.sql("""
                INSERT INTO billing_parties (id, establishment_id, kind, display_name, identifier, account_id, created_at, created_by)
                VALUES (:id, :establishmentId, :kind, :name, :identifier, :accountId, :createdAt, :createdBy)
                """)
                .param("id", party.id())
                .param("establishmentId", establishmentId)
                .param("kind", party.kind())
                .param("name", party.displayName())
                .param("identifier", party.identifier())
                .param("accountId", party.accountId())
                .param("createdAt", Columns.timestamp(party.createdAt()))
                .param("createdBy", party.createdBy())
                .update();
    }

    public Optional<Party> findParty(UUID establishmentId, UUID id) {
        return jdbc.sql("SELECT " + PARTY_COLUMNS + " FROM billing_parties WHERE establishment_id = :establishmentId AND id = :id")
                .param("establishmentId", establishmentId)
                .param("id", id)
                .query(BillingPartyRepository::party)
                .optional();
    }

    public Optional<Party> lockParty(UUID establishmentId, UUID id) {
        return jdbc.sql("SELECT " + PARTY_COLUMNS + " FROM billing_parties WHERE establishment_id=:establishmentId AND id=:id FOR UPDATE")
                .param("establishmentId", establishmentId).param("id", id).query(BillingPartyRepository::party).optional();
    }

    public void correctParty(UUID establishmentId, Correction correction) {
        jdbc.sql("""
                INSERT INTO billing_party_corrections
                    (id,establishment_id,party_id,previous_revision,revision,previous_name,previous_identifier,
                     new_name,new_identifier,reason,corrected_at,corrected_by)
                VALUES (:id,:establishment,:party,:previous,:revision,:previousName,:previousIdentifier,
                        :newName,:newIdentifier,:reason,:at,:actor)
                """).param("id", correction.id()).param("establishment", establishmentId).param("party", correction.partyId())
                .param("previous", correction.previousRevision()).param("revision", correction.revision())
                .param("previousName", correction.previousName()).param("previousIdentifier", correction.previousIdentifier())
                .param("newName", correction.newName()).param("newIdentifier", correction.newIdentifier())
                .param("reason", correction.reason()).param("at", Columns.timestamp(correction.correctedAt()))
                .param("actor", correction.correctedBy()).update();
        jdbc.sql("""
                UPDATE billing_parties SET display_name=:name,identifier=:identifier,revision=:revision
                WHERE establishment_id=:establishment AND id=:party
                """).param("name", correction.newName()).param("identifier", correction.newIdentifier())
                .param("revision", correction.revision()).param("establishment", establishmentId).param("party", correction.partyId()).update();
    }

    public List<Correction> correctionsOf(UUID establishmentId, UUID partyId, PageQuery page) {
        return jdbc.sql("""
                SELECT id,party_id,previous_revision,revision,previous_name,previous_identifier,new_name,new_identifier,
                       reason,corrected_at,corrected_by
                FROM billing_party_corrections WHERE establishment_id=:establishment AND party_id=:party
                ORDER BY revision DESC LIMIT :limit OFFSET :offset
                """).param("establishment", establishmentId).param("party", partyId)
                .param("limit", page.limit()).param("offset", page.offset())
                .query((rs,row) -> new Correction(Columns.uuid(rs,"id"),Columns.uuid(rs,"party_id"),
                        rs.getInt("previous_revision"),rs.getInt("revision"),rs.getString("previous_name"),
                        rs.getString("previous_identifier"),rs.getString("new_name"),rs.getString("new_identifier"),
                        rs.getString("reason"),Columns.instant(rs,"corrected_at"),Columns.uuid(rs,"corrected_by"))).list();
    }

    public boolean accountHasParty(UUID establishmentId, UUID accountId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM billing_parties WHERE establishment_id = :establishmentId AND account_id = :accountId)")
                .param("establishmentId", establishmentId)
                .param("accountId", accountId)
                .query(Boolean.class)
                .single();
    }

    public List<Party> listParties(UUID establishmentId, PageQuery page) {
        return jdbc.sql("SELECT " + PARTY_COLUMNS + """
                 FROM billing_parties
                WHERE establishment_id = :establishmentId
                ORDER BY display_name, id
                LIMIT :limit OFFSET :offset
                """)
                .param("establishmentId", establishmentId)
                .param("limit", page.limit())
                .param("offset", page.offset())
                .query(BillingPartyRepository::party)
                .list();
    }

    public void insertDesignation(UUID establishmentId, UUID id, UUID obligationId, UUID partyId, String role, Instant now, UUID actorId) {
        jdbc.sql("""
                INSERT INTO obligation_parties (id, establishment_id, obligation_id, party_id, role, debtor_status, designated_at, designated_by)
                VALUES (:id, :establishmentId, :obligationId, :partyId, :role, :debtorStatus, :now, :actorId)
                """)
                .param("id", id)
                .param("establishmentId", establishmentId)
                .param("obligationId", obligationId)
                .param("partyId", partyId)
                .param("role", role)
                .param("debtorStatus", "CONTRACTUAL_DEBTOR".equals(role) ? "UNCONFIRMED" : null)
                .param("now", Columns.timestamp(now))
                .param("actorId", actorId)
                .update();
    }

    public Optional<Designation> findDesignation(UUID establishmentId, UUID id) {
        return jdbc.sql(DESIGNATION + "WHERE op.establishment_id = :establishmentId AND op.id = :id")
                .param("establishmentId", establishmentId)
                .param("id", id)
                .query(BillingPartyRepository::designation)
                .optional();
    }

    public List<Designation> designationsOf(UUID establishmentId, UUID obligationId) {
        return jdbc.sql(DESIGNATION + """
                WHERE op.establishment_id = :establishmentId AND op.obligation_id = :obligationId
                ORDER BY op.designated_at, op.id
                """)
                .param("establishmentId", establishmentId)
                .param("obligationId", obligationId)
                .query(BillingPartyRepository::designation)
                .list();
    }

    public void markConfirmed(UUID establishmentId, UUID id, String reference, Instant now, UUID actorId) {
        jdbc.sql("""
                UPDATE obligation_parties
                SET debtor_status = 'CONFIRMED', commitment_reference = :reference, confirmed_at = :now, confirmed_by = :actorId
                WHERE establishment_id = :establishmentId AND id = :id
                """)
                .param("establishmentId", establishmentId)
                .param("id", id)
                .param("reference", reference)
                .param("now", Columns.timestamp(now))
                .param("actorId", actorId)
                .update();
    }

    public void markEnded(UUID establishmentId, UUID id, String reason, Instant now, UUID actorId) {
        jdbc.sql("""
                UPDATE obligation_parties SET ended_at = :now, ended_by = :actorId, end_reason = :reason
                WHERE establishment_id = :establishmentId AND id = :id
                """)
                .param("establishmentId", establishmentId)
                .param("id", id)
                .param("reason", reason)
                .param("now", Columns.timestamp(now))
                .param("actorId", actorId)
                .update();
    }

    public List<UUID> obligationIdsOfStudent(UUID establishmentId, UUID studentId, UUID academicYearId) {
        return jdbc.sql("""
                SELECT id FROM obligations
                WHERE establishment_id = :establishmentId AND student_record_id = :studentId AND academic_year_id = :yearId
                ORDER BY issued_at, id
                """)
                .param("establishmentId", establishmentId)
                .param("studentId", studentId)
                .param("yearId", academicYearId)
                .query(UUID.class)
                .list();
    }

    private static final String DUE = "(SELECT COALESCE(sum(e.amount_minor), 0) FROM obligation_entries e WHERE e.obligation_id = o.id)";

    private static final String BY_DEBTOR_STATE = """
             FROM obligations o
            WHERE o.establishment_id = :establishmentId AND o.academic_year_id = :yearId AND
            """ + DUE + """
             > 0
              AND (
                (:state = 'NONE' AND NOT EXISTS (
                  SELECT 1 FROM obligation_parties op
                  WHERE op.obligation_id = o.id AND op.role = 'CONTRACTUAL_DEBTOR' AND op.ended_at IS NULL))
                OR (:state = 'UNCONFIRMED' AND EXISTS (
                  SELECT 1 FROM obligation_parties op
                  WHERE op.obligation_id = o.id AND op.role = 'CONTRACTUAL_DEBTOR' AND op.ended_at IS NULL AND op.debtor_status = 'UNCONFIRMED')))
            """;

    public long countByDebtorState(UUID establishmentId, UUID academicYearId, String state) {
        return jdbc.sql("SELECT count(*)" + BY_DEBTOR_STATE)
                .param("establishmentId", establishmentId)
                .param("yearId", academicYearId)
                .param("state", state)
                .query(Long.class)
                .single();
    }

    public List<UUID> listByDebtorState(UUID establishmentId, UUID academicYearId, String state, PageQuery page) {
        return jdbc.sql("SELECT o.id" + BY_DEBTOR_STATE + " ORDER BY o.issued_at, o.id LIMIT :limit OFFSET :offset")
                .param("establishmentId", establishmentId)
                .param("yearId", academicYearId)
                .param("state", state)
                .param("limit", page.limit())
                .param("offset", page.offset())
                .query(UUID.class)
                .list();
    }

    // Surface des familles : un compte ne voit que les obligations où il tient un rôle actif.

    /** Un contributeur n'a rien à voir d'une obligation sans reste dû : son solde révélerait une remise ou une annulation. */
    private static final String REFS_OF_ACCOUNT = """
            SELECT DISTINCT o.establishment_id, o.id, o.issued_at
            FROM obligation_parties op
            JOIN billing_parties p ON p.id = op.party_id
            JOIN obligations o ON o.id = op.obligation_id
            WHERE p.account_id = :accountId AND op.ended_at IS NULL
              AND (op.role <> 'VOLUNTARY_CONTRIBUTOR' OR
            """ + DUE + " > 0)\n";

    public List<UUID> establishmentsOfAccount(UUID accountId) {
        return jdbc.sql("""
                SELECT DISTINCT bp.establishment_id FROM obligation_parties op
                JOIN billing_parties bp ON bp.id=op.party_id
                WHERE bp.account_id = :accountId AND op.ended_at IS NULL
                ORDER BY bp.establishment_id
                """).param("accountId", accountId).query(UUID.class).list();
    }

    public List<ObligationRef> refsOfAccount(UUID accountId, List<UUID> activeEstablishments, List<UUID> linkedStudents, PageQuery page) {
        return jdbc.sql(REFS_OF_ACCOUNT + """
                 AND o.establishment_id = ANY(:activeEstablishments)
                 AND (op.role <> 'VOLUNTARY_CONTRIBUTOR' OR o.student_record_id = ANY(:linkedStudents))
                 ORDER BY o.issued_at, o.id LIMIT :limit OFFSET :offset
                """)
                .param("accountId", accountId)
                .param("activeEstablishments", new SqlArrayValue("uuid", activeEstablishments.toArray()))
                .param("linkedStudents", new SqlArrayValue("uuid", linkedStudents.toArray()))
                .param("limit", page.limit())
                .param("offset", page.offset())
                .query((rs, rowNum) -> new ObligationRef(Columns.uuid(rs, "establishment_id"), Columns.uuid(rs, "id")))
                .list();
    }

    public Optional<ObligationRef> refOfAccount(UUID accountId, UUID obligationId) {
        return jdbc.sql(REFS_OF_ACCOUNT + " AND o.id = :obligationId")
                .param("accountId", accountId)
                .param("obligationId", obligationId)
                .query((rs, rowNum) -> new ObligationRef(Columns.uuid(rs, "establishment_id"), Columns.uuid(rs, "id")))
                .optional();
    }

    public List<HeldRole> rolesOfAccount(UUID accountId, UUID establishmentId, UUID obligationId) {
        return jdbc.sql("""
                SELECT op.role, op.debtor_status
                FROM obligation_parties op
                JOIN billing_parties p ON p.id = op.party_id
                WHERE p.account_id = :accountId AND op.establishment_id = :establishmentId AND op.obligation_id = :obligationId AND op.ended_at IS NULL
                """)
                .param("accountId", accountId)
                .param("establishmentId", establishmentId)
                .param("obligationId", obligationId)
                .query((rs, rowNum) -> new HeldRole(rs.getString("role"), rs.getString("debtor_status")))
                .list();
    }

    private static Party party(ResultSet rs, int rowNum) throws SQLException {
        return new Party(
                Columns.uuid(rs, "id"),
                rs.getString("kind"),
                rs.getString("display_name"),
                rs.getString("identifier"),
                Columns.uuid(rs, "account_id"),
                Columns.instant(rs, "created_at"),
                Columns.uuid(rs, "created_by"),
                rs.getInt("revision"));
    }

    private static Designation designation(ResultSet rs, int rowNum) throws SQLException {
        return new Designation(
                Columns.uuid(rs, "id"),
                Columns.uuid(rs, "obligation_id"),
                Columns.uuid(rs, "party_id"),
                rs.getString("display_name"),
                rs.getString("kind"),
                Columns.uuid(rs, "account_id"),
                rs.getString("role"),
                rs.getString("debtor_status"),
                rs.getString("commitment_reference"),
                Columns.instant(rs, "designated_at"),
                Columns.uuid(rs, "designated_by"),
                Columns.instant(rs, "confirmed_at"),
                Columns.uuid(rs, "confirmed_by"),
                Columns.instant(rs, "ended_at"),
                Columns.uuid(rs, "ended_by"),
                rs.getString("end_reason"));
    }
}
