package com.scolapay.core.payments.internal;

import com.scolapay.core.audit.AuditTrail;
import com.scolapay.core.establishment.EstablishmentAccess;
import com.scolapay.core.establishment.Permission;
import com.scolapay.core.payments.internal.CashExcessRepository.Decision;
import com.scolapay.core.payments.internal.CashExcessRepository.Request;
import com.scolapay.core.shared.ApiException;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
class CashExcessService {
    record Requested(Request request,boolean created) {}
    record Decided(Decision decision,boolean created) {}
    record ReadRequest(Request request,Decision decision,UUID actor,boolean privateReasons) {}
    record ReadDecision(Decision decision,UUID actor,boolean privateReasons) {}
    private final CashExcessRepository excess;
    private final CashReceptionRepository receptions;
    private final EstablishmentAccess access;
    private final AuditTrail audit;
    private final CashProperties properties;
    private final Clock clock;
    CashExcessService(CashExcessRepository excess,CashReceptionRepository receptions,EstablishmentAccess access,
            AuditTrail audit,CashProperties properties,Clock clock) {
        this.excess=excess;this.receptions=receptions;this.access=access;this.audit=audit;this.properties=properties;this.clock=clock;
    }
    @Transactional(timeout=10)
    public Requested request(UUID tenant,UUID source,UUID key,long amount,String reason) {
        UUID actor=access.require(tenant,Permission.CASH_EXCESS_REQUEST);requireEnabled();
        if(amount<1||amount>999_999_999_999_999L) throw ApiException.invalid("INVALID_AMOUNT","Montant invalide.");
        String normalized=requiredReason(reason);
        return guarded(()->{
            var prior=excess.byKey(tenant,key);
            if(prior.isPresent()) return replay(prior.get(),source,actor,amount,normalized);
            var cash=receptions.lock(tenant,source).orElseThrow(()->ApiException.notFound("CASH_RECEPTION_NOT_FOUND","Déclaration introuvable."));
            prior=excess.byKey(tenant,key);
            if(prior.isPresent()) return replay(prior.get(),source,actor,amount,normalized);
            if(!cash.recordedByAccountId().equals(actor)) throw ApiException.forbidden("CASH_RECEPTION_AUTHOR_REQUIRED","Seul le déclarant réserve cette réception dans cette tranche.");
            if(amount>cash.availableForAllocationMinor()) throw ApiException.invalid("EXCESS_BUDGET_EXCEEDED","Le montant dépasse le budget jamais affecté disponible.");
            UUID id=UUID.randomUUID();
            Request r=new Request(id,source,cash.currency(),amount,normalized,key,"XQ-"+id,"RESERVATION_ONLY_V1",cash.amountRetainedMinor(),
                    cash.allocatedMinor(),cash.reservedMinor(),clock.instant().truncatedTo(ChronoUnit.MICROS),actor,"REQUESTED");
            if(!excess.insert(tenant,r)) return replay(excess.byKey(tenant,key).orElseThrow(),source,actor,amount,normalized);
            audit.record(tenant,actor,"payments.excess.requested","cash_excess_request",id,Map.of("reference",r.reference(),"receptionId",source));
            return new Requested(r,true);
        });
    }
    private Requested replay(Request r,UUID source,UUID actor,long amount,String reason) {
        if(!r.receptionId().equals(source)||!r.createdBy().equals(actor)||r.amountMinor()!=amount||!r.reason().equals(reason)) throw keyConflict();
        return new Requested(r,false);
    }
    @Transactional(timeout=10)
    public Decided decide(UUID tenant,UUID request,UUID key,String kind,String note) {
        boolean withdrawal="WITHDRAWN".equals(kind);
        if(!withdrawal&&!"APPROVED".equals(kind)&&!"REJECTED".equals(kind)) throw ApiException.invalid("DECISION_UNAVAILABLE","Cette décision reste indisponible.");
        UUID actor=access.require(tenant,withdrawal?Permission.CASH_EXCESS_REQUEST:Permission.CASH_EXCESS_APPROVE);requireEnabled();
        String normalized="APPROVED".equals(kind)?null:requiredReason(note);
        return guarded(()->{
            var prior=excess.decisionByKey(tenant,key);
            if(prior.isPresent()) return replay(prior.get(),request,actor,kind,normalized);
            Request original=required(tenant,request);
            receptions.lock(tenant,original.receptionId()).orElseThrow(()->ApiException.notFound("CASH_RECEPTION_NOT_FOUND","Déclaration introuvable."));
            prior=excess.decisionByKey(tenant,key);
            if(prior.isPresent()) return replay(prior.get(),request,actor,kind,normalized);
            Request r=required(tenant,request);
            if(withdrawal&&!r.createdBy().equals(actor)) throw ApiException.forbidden("REQUEST_AUTHOR_REQUIRED","Seul l'auteur retire sa réservation non approuvée.");
            if(!withdrawal&&r.createdBy().equals(actor)) throw ApiException.forbidden("SELF_APPROVAL_FORBIDDEN","Une autre personne doit examiner la réservation.");
            if(!"REQUESTED".equals(r.state())) throw ApiException.conflict("EXCESS_REQUEST_ALREADY_DECIDED","Cette réservation a déjà une décision ; aucune libération après approbation n'est disponible.");
            UUID id=UUID.randomUUID();
            Decision d=new Decision(id,r.id(),r.receptionId(),r.currency(),r.amountMinor(),r.authorizationScope(),key,kind,normalized,
                    "XD-"+id,clock.instant().truncatedTo(ChronoUnit.MICROS),actor);
            if(!excess.insert(tenant,d)) return replay(excess.decisionByKey(tenant,key).orElseThrow(),request,actor,kind,normalized);
            audit.record(tenant,actor,"payments.excess."+kind.toLowerCase(Locale.ROOT),"cash_excess_decision",id,
                    Map.of("reference",d.reference(),"requestId",r.id()));
            return new Decided(d,true);
        });
    }
    private Decided replay(Decision d,UUID request,UUID actor,String kind,String note) {
        if(!d.requestId().equals(request)||!d.decidedBy().equals(actor)||!d.kind().equals(kind)||!Objects.equals(d.note(),note)) throw keyConflict();
        return new Decided(d,false);
    }
    private static ApiException keyConflict(){return ApiException.conflict("CASH_EXCESS_KEY_CONFLICT","Cette clé désigne une autre intention.");}
    private static String requiredReason(String value) {
        if(value==null||value.isBlank()||value.length()>500) throw ApiException.invalid("INVALID_REASON","Un motif de 1 à 500 caractères est requis.");
        return value.trim();
    }
    private void requireEnabled() {
        if(!properties.cashExcessReservationsEnabled()) throw ApiException.notFound("CASH_EXCESS_RESERVATIONS_DISABLED","Les réservations d'excédents sont désactivées.");
    }
    private static <T> T guarded(Supplier<T> operation) {
        try{return operation.get();}
        catch(TransientDataAccessException busy){throw busy();}
        catch(DataIntegrityViolationException conflict){throw ApiException.conflict("CASH_EXCESS_CONFLICT","La réservation n'a pas pu être confirmée.");}
        catch(UncategorizedSQLException failure){
            if("55P03".equals(failure.getSQLException().getSQLState()))throw busy();
            throw failure;
        }
    }
    private static ApiException busy(){return ApiException.conflict("CASH_EXCESS_BUSY","Traitement en cours. Rejouez la même intention.");}
    private Request required(UUID tenant,UUID request){return excess.find(tenant,request).orElseThrow(()->ApiException.notFound("CASH_EXCESS_NOT_FOUND","Demande introuvable."));}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public ReadRequest read(UUID tenant,UUID id,boolean byKey) {
        UUID actor=access.require(tenant,Permission.CASH_EXCESS_READ);
        Request r=(byKey?excess.byKey(tenant,id):excess.find(tenant,id)).orElseThrow(()->ApiException.notFound("CASH_EXCESS_NOT_FOUND","Demande introuvable."));
        return new ReadRequest(r,excess.decisionFor(tenant,r.id()).orElse(null),actor,access.allows(tenant,Permission.CASH_EXCESS_REASONS_READ));
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public ReadDecision readDecision(UUID tenant,UUID key) {
        UUID actor=access.require(tenant,Permission.CASH_EXCESS_READ);
        return new ReadDecision(excess.decisionByKey(tenant,key).orElseThrow(()->ApiException.notFound("CASH_EXCESS_DECISION_NOT_FOUND","Décision introuvable.")),
                actor,access.allows(tenant,Permission.CASH_EXCESS_REASONS_READ));
    }
}
