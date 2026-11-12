package com.scolapay.core.payments.internal;

import com.scolapay.core.audit.AuditTrail;
import com.scolapay.core.billing.BillingPaymentPreparation;
import com.scolapay.core.establishment.EstablishmentAccess;
import com.scolapay.core.establishment.Permission;
import com.scolapay.core.payments.internal.DigitalPaymentIntentRepository.Intent;
import com.scolapay.core.shared.ApiException;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class DigitalPaymentIntentService {
    record Prepared(Intent intent,boolean created) {}
    private final DigitalPaymentIntentRepository repository;
    private final BillingPaymentPreparation billing;
    private final EstablishmentAccess access;
    private final AuditTrail audit;
    private final DigitalPaymentProperties properties;
    private final Clock clock;
    DigitalPaymentIntentService(DigitalPaymentIntentRepository repository,BillingPaymentPreparation billing,EstablishmentAccess access,
            AuditTrail audit,DigitalPaymentProperties properties,Clock clock){this.repository=repository;this.billing=billing;this.access=access;this.audit=audit;this.properties=properties;this.clock=clock;}
    @Transactional(timeout=10)
    public Prepared prepare(UUID tenant,UUID obligation,UUID key,String currency,long amount,long journal){
        UUID actor=access.require(tenant,Permission.DIGITAL_PAYMENT_PREPARE);
        if(!properties.intentsEnabled())throw ApiException.notFound("DIGITAL_INTENTS_DISABLED","La préparation numérique est désactivée.");
        if(amount<1||amount>999_999_999_999_999L||journal<1)throw ApiException.invalid("INVALID_PREPARATION","Montant ou version invalide.");
        var prior=repository.byKey(tenant,key);if(prior.isPresent())return replay(prior.get(),obligation,actor,currency,amount,journal);
        try{
            var source=billing.lockForPreparation(tenant,obligation);
            prior=repository.byKey(tenant,key);if(prior.isPresent())return replay(prior.get(),obligation,actor,currency,amount,journal);
            if(!source.allowed())throw ApiException.conflict(source.blockedReason(),"La préparation n'est pas disponible pour cette obligation.");
            if(!"XAF".equals(currency)||!currency.equals(source.currency()))throw ApiException.invalid("DIGITAL_CURRENCY_UNSUPPORTED","La devise n'est pas prise en charge pour cette préparation.");
            if(source.journalSize()!=journal)throw ApiException.conflict("DIGITAL_INTENT_OUTDATED","Le journal a changé. Relisez le dû avant de préparer.");
            if(amount>source.dueMinor())throw ApiException.invalid("AMOUNT_EXCEEDS_DUE","Le montant dépasse le reste à payer.");
            UUID id=UUID.randomUUID();Intent i=new Intent(id,obligation,key,"DPI-"+id,currency,amount,source.issuedMinor(),source.netBilledMinor(),source.settledMinor(),source.dueMinor(),
                    source.journalSize(),clock.instant().truncatedTo(ChronoUnit.MICROS),actor);
            if(!repository.insert(tenant,i))return replay(repository.byKey(tenant,key).orElseThrow(),obligation,actor,currency,amount,journal);
            audit.record(tenant,actor,"payments.digital.intent_prepared","digital_payment_intent",id,Map.of("reference",i.reference(),"obligationId",obligation));
            return new Prepared(i,true);
        }catch(TransientDataAccessException busy){throw busy();}
        catch(DataIntegrityViolationException refused){throw ApiException.conflict("DIGITAL_INTENT_CONFLICT","La préparation n'a pas pu être confirmée.");}
        catch(UncategorizedSQLException failure){if("55P03".equals(failure.getSQLException().getSQLState()))throw busy();throw failure;}
    }
    private Prepared replay(Intent i,UUID obligation,UUID actor,String currency,long amount,long journal){
        if(!i.obligationId().equals(obligation)||!i.preparedBy().equals(actor)||!i.currency().equals(currency)||i.amountMinor()!=amount||i.journalSizeBefore()!=journal)
            throw ApiException.conflict("DIGITAL_INTENT_KEY_CONFLICT","Cette clé désigne une autre intention.");
        return new Prepared(i,false);
    }
    private static ApiException busy(){return ApiException.conflict("DIGITAL_INTENT_BUSY","Traitement en cours. Rejouez la même intention.");}
    @Transactional(readOnly=true)
    public Intent read(UUID tenant,UUID id,boolean byKey){
        access.require(tenant,Permission.BILLING_READ);
        return (byKey?repository.byKey(tenant,id):repository.find(tenant,id)).orElseThrow(()->ApiException.notFound("DIGITAL_INTENT_NOT_FOUND","Intention introuvable."));
    }
}
