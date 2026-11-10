package com.scolapay.core.billing.internal;

import com.scolapay.core.audit.AuditTrail;
import com.scolapay.core.billing.internal.BillingPartyRepository.Correction;
import com.scolapay.core.billing.internal.BillingPartyRepository.Party;
import com.scolapay.core.establishment.EstablishmentAccess;
import com.scolapay.core.establishment.Permission;
import com.scolapay.core.shared.ApiException;
import com.scolapay.core.shared.PageQuery;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Corriger une information ne rattache jamais un autre compte à des droits financiers existants. */
@Service
public class PartyCorrectionService {
    private final BillingPartyRepository parties;
    private final EstablishmentAccess access;
    private final AuditTrail audit;
    private final Clock clock;

    PartyCorrectionService(BillingPartyRepository parties, EstablishmentAccess access, AuditTrail audit, Clock clock) {
        this.parties=parties; this.access=access; this.audit=audit; this.clock=clock;
    }

    @Transactional
    public Correction correct(UUID establishmentId, UUID partyId, int expectedRevision, String name, String identifier, String reason) {
        UUID actorId=access.require(establishmentId,Permission.BILLING_PARTIES_MANAGE);
        String normalizedName=name==null ? "" : name.strip();
        String normalizedIdentifier=identifier==null ? null : identifier.strip();
        String normalizedReason=reason==null ? "" : reason.strip();
        if (normalizedName.length()<2 || normalizedName.length()>200 || normalizedReason.length()<3 || normalizedReason.length()>500
                || (normalizedIdentifier!=null && (normalizedIdentifier.isBlank() || normalizedIdentifier.length()>100))) {
            throw ApiException.invalid("INVALID_PARTY_CORRECTION","Le nom, l'identifiant ou le motif de correction n'est pas valide.");
        }
        Party previous=parties.lockParty(establishmentId,partyId)
                .orElseThrow(() -> ApiException.notFound("PARTY_NOT_FOUND","Personne introuvable."));
        if (previous.revision()!=expectedRevision) {
            throw ApiException.conflict("PARTY_CORRECTION_OUTDATED","La personne a été corrigée entre-temps : relisez ses informations.");
        }
        if (previous.displayName().equals(normalizedName) && Objects.equals(previous.identifier(),normalizedIdentifier)) {
            throw ApiException.conflict("PARTY_UNCHANGED","La correction ne change aucune information.");
        }
        Correction correction=new Correction(UUID.randomUUID(),partyId,previous.revision(),Math.incrementExact(previous.revision()),
                previous.displayName(),previous.identifier(),normalizedName,normalizedIdentifier,normalizedReason,clock.instant(),actorId);
        parties.correctParty(establishmentId,correction);
        // Les textes confidentiels restent dans le journal dédié, pas dans les métadonnées d'audit.
        audit.record(establishmentId,actorId,"billing.party.corrected","billing_party",partyId,
                Map.of("correctionId",correction.id(),"revision",correction.revision()));
        return correction;
    }

    @Transactional(readOnly=true)
    public Party read(UUID establishmentId, UUID partyId) {
        access.require(establishmentId,Permission.BILLING_READ);
        return parties.findParty(establishmentId,partyId).orElseThrow(() -> ApiException.notFound("PARTY_NOT_FOUND","Personne introuvable."));
    }

    @Transactional(readOnly=true)
    public List<Correction> history(UUID establishmentId, UUID partyId, PageQuery page) {
        access.require(establishmentId,Permission.BILLING_READ);
        access.require(establishmentId,Permission.BILLING_REASONS_READ);
        if (parties.findParty(establishmentId,partyId).isEmpty()) throw ApiException.notFound("PARTY_NOT_FOUND","Personne introuvable.");
        return parties.correctionsOf(establishmentId,partyId,page);
    }
}
