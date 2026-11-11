package com.scolapay.core.payments.internal;

import com.scolapay.core.audit.AuditTrail;
import com.scolapay.core.establishment.EstablishmentAccess;
import com.scolapay.core.establishment.Establishments;
import com.scolapay.core.establishment.Permission;
import com.scolapay.core.payments.internal.CashReceptionRepository.Reception;
import com.scolapay.core.shared.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class CashReceptionService {

    record Recorded(Reception reception, boolean created) {}

    private final CashReceptionRepository repository;
    private final EstablishmentAccess access;
    private final Establishments establishments;
    private final AuditTrail audit;
    private final CashProperties properties;
    private final Clock clock;

    CashReceptionService(CashReceptionRepository repository, EstablishmentAccess access,
            Establishments establishments, AuditTrail audit, CashProperties properties, Clock clock) {
        this.repository = repository;
        this.access = access;
        this.establishments = establishments;
        this.audit = audit;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(timeout = 10)
    public Recorded record(UUID establishmentId, UUID key, long received, long change, boolean confirmed) {
        UUID actorId = access.require(establishmentId, Permission.CASH_RECEPTIONS_RECORD);
        if (!properties.cashReceptionsEnabled()) {
            throw ApiException.notFound("CASH_RECEPTIONS_DISABLED", "La déclaration d'espèces est désactivée.");
        }
        if (!confirmed) {
            throw ApiException.invalid("CASH_RECEIPT_NOT_CONFIRMED", "Confirmez la réception effective des espèces.");
        }
        if (received < 1 || received > 999_999_999_999_999L || change < 0 || change >= received) {
            throw ApiException.invalid("INVALID_CASH_AMOUNTS", "Les montants reçus et rendus sont incompatibles.");
        }
        var establishment = establishments.find(establishmentId)
                .orElseThrow(() -> ApiException.notFound("ESTABLISHMENT_NOT_FOUND", "Établissement introuvable."));
        UUID id = UUID.randomUUID();
        // PostgreSQL conserve les microsecondes : la réponse initiale doit être identique à sa relecture.
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Reception reception = new Reception(id, "CR-" + id, key, establishment.currency(), received, change,
                now, actorId, now.atZone(ZoneId.of(establishment.timezone())).toLocalDate(), establishment.timezone());
        try {
            if (!repository.insert(establishmentId, reception)) {
                Reception existing = repository.findByKey(establishmentId, key);
                if (!existing.recordedByAccountId().equals(actorId)
                        || existing.amountReceivedMinor() != received || existing.changeGivenMinor() != change) {
                    throw ApiException.conflict("CASH_RECEPTION_KEY_CONFLICT", "Cette clé désigne une autre déclaration.");
                }
                return new Recorded(existing, false);
            }
            audit.record(establishmentId, actorId, "payments.cash.received", "cash_reception", id,
                    Map.of("reference", reception.reference()));
            return new Recorded(reception, true);
        } catch (TransientDataAccessException busy) {
            throw ApiException.conflict("CASH_RECEPTION_BUSY", "Traitement en cours. Rejouez la même déclaration.");
        } catch (DataIntegrityViolationException conflict) {
            // Aucun détail PostgreSQL, susceptible de contenir des données financières, n'est journalisé.
            throw ApiException.conflict("CASH_RECEPTION_CONFLICT", "La déclaration n'a pas pu être enregistrée.");
        }
    }

    @Transactional(readOnly = true)
    public Reception find(UUID establishmentId, UUID id) {
        access.require(establishmentId, Permission.CASH_RECEPTIONS_READ);
        return repository.find(establishmentId, id)
                .orElseThrow(() -> ApiException.notFound("CASH_RECEPTION_NOT_FOUND", "Déclaration introuvable."));
    }
}
