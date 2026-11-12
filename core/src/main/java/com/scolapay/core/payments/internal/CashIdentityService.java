package com.scolapay.core.payments.internal;

import com.scolapay.core.audit.AuditTrail;
import com.scolapay.core.establishment.EstablishmentAccess;
import com.scolapay.core.establishment.Permission;
import com.scolapay.core.payments.internal.CashReceptionRepository.Reception;
import com.scolapay.core.shared.ApiException;
import java.util.Map;
import java.util.UUID;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.function.Supplier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
class CashIdentityService {
    record Details(String fullName,String identificationDetails) {}
    record Read(CashIdentityRepository.Remitter remitter,Details details,String state,CashIdentityRepository.Review review) {}
    record Written<T>(T value,boolean created) {}
    private final CashIdentityRepository identities;
    private final CashReceptionRepository receptions;
    private final CashIdentityCipher cipher;
    private final JsonMapper json;
    private final EstablishmentAccess access;
    private final AuditTrail audit;
    private final CashProperties properties;
    private final Clock clock;
    CashIdentityService(CashIdentityRepository identities,CashReceptionRepository receptions,CashIdentityCipher cipher,
            JsonMapper json,EstablishmentAccess access,AuditTrail audit,CashProperties properties,Clock clock){
        this.identities=identities;this.receptions=receptions;this.cipher=cipher;this.json=json;this.access=access;this.audit=audit;
        this.properties=properties;this.clock=clock;
    }
    static Details details(CashReceptionController.Remitter input){
        if(input==null||input.fullName()==null||input.fullName().isBlank()||input.fullName().length()>160
                ||input.identificationDetails()==null||input.identificationDetails().isBlank()||input.identificationDetails().length()>300
                ||!Boolean.TRUE.equals(input.identityRecordedConfirmed()))
            throw new ApiException(HttpStatus.BAD_REQUEST,"REMITTER_IDENTITY_REQUIRED","L'identité du remettant et sa confirmation sont requises.");
        return new Details(input.fullName().trim(),input.identificationDetails().trim());
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void recordAtReception(UUID tenant,Reception source,CashReceptionController.Remitter input){
        Details values=details(input);
        var r=new CashIdentityRepository.Remitter(source.id(),"AT_RECEPTION",cipher.encrypt(tenant,source.id(),"remitter",json.writeValueAsString(values)),
                source.idempotencyKey(),"CI-"+source.id(),null,source.recordedAt(),source.recordedByAccountId());
        if(!identities.insert(tenant,r))throw ApiException.conflict("CASH_IDENTITY_KEY_CONFLICT","Cette clé désigne une autre identification.");
        audit.record(tenant,r.recordedBy(),"payments.remitter.recorded","cash_remitter_identification",r.id(),
                Map.of("reference",r.reference(),"receptionId",r.id()));
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void verifyReplay(UUID tenant,UUID source,CashReceptionController.Remitter input){
        var prior=identities.find(tenant,source);
        if(prior.isEmpty()||!"AT_RECEPTION".equals(prior.get().origin())){
            if(input!=null)throw ApiException.conflict("CASH_RECEPTION_KEY_CONFLICT","Cette clé ne permet pas de modifier une déclaration historique.");
            return;
        }
        if(input==null||!decode(tenant,prior.get()).equals(details(input)))
            throw ApiException.conflict("CASH_RECEPTION_KEY_CONFLICT","Cette clé désigne une autre déclaration.");
    }
    Details decode(UUID tenant,CashIdentityRepository.Remitter r){
        String clear=cipher.decrypt(tenant,r.id(),"remitter",r.encryptedPayload());
        try{return json.readValue(clear,Details.class);}catch(RuntimeException unreadable){
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"CASH_IDENTITY_UNAVAILABLE","Les données d'identité ne sont pas disponibles.");
        }
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Read read(UUID tenant,UUID source){
        return loadRead(tenant,source);
    }
    private Read loadRead(UUID tenant,UUID source){
        UUID actor=access.require(tenant,Permission.CASH_RECEPTIONS_READ);
        Reception cash=receptions.find(tenant,source).orElseThrow(()->ApiException.notFound("CASH_RECEPTION_NOT_FOUND","Déclaration introuvable."));
        if(!cash.recordedByAccountId().equals(actor)&&!access.allows(tenant,Permission.CASH_IDENTITY_READ))
            throw ApiException.forbidden("CASH_IDENTITY_READ_DENIED","Votre rôle ne permet pas cette lecture d'identité.");
        var r=identities.find(tenant,source);
        if(r.isEmpty())return new Read(null,null,"LEGACY_UNIDENTIFIED",null);
        var value=r.get();var review=identities.reviewFor(tenant,source).orElse(null);
        String state="AT_RECEPTION".equals(value.origin())?"IDENTIFIED_AT_RECEPTION":review==null?"LEGACY_PENDING_REVIEW":"LEGACY_VERIFIED";
        return new Read(value,decode(tenant,value),state,review);
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public Read readyIdentity(UUID tenant,UUID source){
        Read r=loadRead(tenant,source);
        if(r.remitter()==null||"LEGACY_PENDING_REVIEW".equals(r.state()))
            throw ApiException.conflict("REMITTER_VERIFICATION_REQUIRED","La réception doit suivre sa procédure de vérification d'identité.");
        return r;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Read readByKey(UUID tenant,UUID key){
        access.require(tenant,Permission.CASH_RECEPTIONS_READ);
        var r=identities.byKey(tenant,key).orElseThrow(()->ApiException.notFound("CASH_IDENTITY_NOT_FOUND","Identification introuvable."));
        return loadRead(tenant,r.id());
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public CashIdentityRepository.Review readReviewByKey(UUID tenant,UUID key){
        access.require(tenant,Permission.CASH_RECEPTIONS_READ);
        var r=identities.reviewByKey(tenant,key).orElseThrow(()->ApiException.notFound("CASH_IDENTITY_REVIEW_NOT_FOUND","Examen introuvable."));
        loadRead(tenant,r.source());return r;
    }
    @Transactional(timeout=10)
    public Written<CashIdentityRepository.Remitter> declareLegacy(UUID tenant,UUID source,UUID key,CashReceptionController.Remitter input,String reference,boolean signed){
        UUID actor=access.require(tenant,Permission.CASH_RECEPTIONS_RECORD);requireEnabled();
        Details values=details(input);
        if(!signed||reference==null||!reference.matches("[A-Za-z0-9][A-Za-z0-9._/-]{0,79}"))
            throw ApiException.invalid("SIGNED_DECLARATION_REQUIRED","La déclaration signée et sa référence sont requises.");
        return guarded(()->{
            var prior=identities.byKey(tenant,key);if(prior.isPresent())return legacyReplay(tenant,prior.get(),source,actor,values,reference);
            Reception cash=receptions.lock(tenant,source).orElseThrow(()->ApiException.notFound("CASH_RECEPTION_NOT_FOUND","Déclaration introuvable."));
            prior=identities.byKey(tenant,key);if(prior.isPresent())return legacyReplay(tenant,prior.get(),source,actor,values,reference);
            if(!cash.recordedByAccountId().equals(actor))throw ApiException.forbidden("CASH_RECEPTION_AUTHOR_REQUIRED","Cette procédure est réservée au déclarant d'origine.");
            if(!identities.legacySource(tenant,source))throw ApiException.invalid("LEGACY_PROCEDURE_NOT_APPLICABLE","Cette réception possède déjà sa procédure d'identification initiale.");
            if(identities.find(tenant,source).isPresent())throw ApiException.conflict("CASH_IDENTITY_ALREADY_RECORDED","Une déclaration d'identité existe déjà pour cette réception.");
            var r=new CashIdentityRepository.Remitter(source,"LEGACY_DECLARATION",cipher.encrypt(tenant,source,"remitter",json.writeValueAsString(values)),
                    key,"CI-"+source,reference,clock.instant().truncatedTo(ChronoUnit.MICROS),actor);
            if(!identities.insert(tenant,r))return legacyReplay(tenant,identities.byKey(tenant,key).orElseThrow(),source,actor,values,reference);
            audit.record(tenant,actor,"payments.remitter.recorded","cash_remitter_identification",source,Map.of("reference",r.reference(),"receptionId",source));
            return new Written<>(r,true);
        });
    }
    private Written<CashIdentityRepository.Remitter> legacyReplay(UUID tenant,CashIdentityRepository.Remitter r,UUID source,UUID actor,Details values,String statement){
        if(!r.origin().equals("LEGACY_DECLARATION")||!r.id().equals(source)||!r.recordedBy().equals(actor)
                ||!statement.equals(r.statementReference())||!values.equals(decode(tenant,r)))throw keyConflict();
        return new Written<>(r,false);
    }
    @Transactional(timeout=10)
    public Written<CashIdentityRepository.Review> reviewLegacy(UUID tenant,UUID source,UUID key,boolean receiptMatched,boolean statementChecked,boolean identityChecked){
        UUID actor=access.require(tenant,Permission.CASH_EXCESS_APPROVE);requireEnabled();
        if(!receiptMatched||!statementChecked||!identityChecked)throw ApiException.invalid("LEGACY_REVIEW_INCOMPLETE","Les trois contrôles doivent être confirmés.");
        return guarded(()->{
            var prior=identities.reviewByKey(tenant,key);if(prior.isPresent())return reviewReplay(prior.get(),source,actor);
            receptions.lock(tenant,source).orElseThrow(()->ApiException.notFound("CASH_RECEPTION_NOT_FOUND","Déclaration introuvable."));
            prior=identities.reviewByKey(tenant,key);if(prior.isPresent())return reviewReplay(prior.get(),source,actor);
            Read read=loadRead(tenant,source);var r=read.remitter();
            if(r==null||!r.origin().equals("LEGACY_DECLARATION"))throw ApiException.conflict("LEGACY_DECLARATION_REQUIRED","La déclaration signée d'identité est requise.");
            if(r.recordedBy().equals(actor))throw ApiException.forbidden("SELF_REVIEW_FORBIDDEN","Une seconde personne doit examiner la déclaration.");
            if(read.review()!=null)throw ApiException.conflict("CASH_IDENTITY_ALREADY_REVIEWED","Cette déclaration a déjà été examinée.");
            UUID id=UUID.randomUUID();var review=new CashIdentityRepository.Review(id,source,key,"CIR-"+id,clock.instant().truncatedTo(ChronoUnit.MICROS),actor);
            if(!identities.insert(tenant,review))return reviewReplay(identities.reviewByKey(tenant,key).orElseThrow(),source,actor);
            audit.record(tenant,actor,"payments.remitter.reviewed","cash_remitter_review",id,Map.of("reference",review.reference(),"receptionId",source));
            return new Written<>(review,true);
        });
    }
    private Written<CashIdentityRepository.Review> reviewReplay(CashIdentityRepository.Review r,UUID source,UUID actor){
        if(!r.source().equals(source)||!r.reviewedBy().equals(actor))throw keyConflict();return new Written<>(r,false);
    }
    private void requireEnabled(){if(!properties.cashIdentitiesEnabled())throw ApiException.notFound("CASH_IDENTITIES_DISABLED","Les nouvelles procédures d'identité sont désactivées.");}
    static ApiException keyConflict(){return ApiException.conflict("CASH_IDENTITY_KEY_CONFLICT","Cette clé désigne une autre intention.");}
    static <T> T guarded(Supplier<T> operation){
        try{return operation.get();}
        catch(TransientDataAccessException busy){throw ApiException.conflict("CASH_IDENTITY_BUSY","Traitement en cours. Rejouez la même intention.");}
        catch(DataIntegrityViolationException conflict){throw ApiException.conflict("CASH_IDENTITY_CONFLICT","La procédure d'identité n'a pas pu être confirmée.");}
        catch(UncategorizedSQLException failure){
            if("55P03".equals(failure.getSQLException().getSQLState()))throw ApiException.conflict("CASH_IDENTITY_BUSY","Traitement en cours. Rejouez la même intention.");
            throw failure;
        }
    }
}
