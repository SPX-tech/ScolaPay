package com.scolapay.core.payments.internal;

import com.scolapay.core.audit.AuditTrail;
import com.scolapay.core.establishment.EstablishmentAccess;
import com.scolapay.core.establishment.Permission;
import com.scolapay.core.payments.internal.CashIdentityService.Written;
import com.scolapay.core.payments.internal.CashRestitutionRepository.Authorization;
import com.scolapay.core.payments.internal.CashRestitutionRepository.Beneficiary;
import com.scolapay.core.shared.ApiException;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
class CashRestitutionService {
    record ReadBeneficiary(Beneficiary verification,String fullName,String verificationDetails) {}
    private final CashRestitutionRepository repository;
    private final CashExcessRepository excess;
    private final CashReceptionRepository receptions;
    private final CashIdentityService identities;
    private final CashIdentityCipher cipher;
    private final EstablishmentAccess access;
    private final AuditTrail audit;
    private final CashProperties properties;
    private final Clock clock;
    CashRestitutionService(CashRestitutionRepository repository,CashExcessRepository excess,CashReceptionRepository receptions,
            CashIdentityService identities,CashIdentityCipher cipher,EstablishmentAccess access,AuditTrail audit,CashProperties properties,Clock clock){
        this.repository=repository;this.excess=excess;this.receptions=receptions;this.identities=identities;this.cipher=cipher;
        this.access=access;this.audit=audit;this.properties=properties;this.clock=clock;
    }
    @Transactional(timeout=10)
    public Written<Beneficiary> verify(UUID tenant,UUID requestId,UUID key,boolean remitter,boolean checked,String details){
        UUID actor=access.require(tenant,Permission.CASH_EXCESS_REQUEST);requireEnabled();
        if(!remitter)throw ApiException.invalid("ALTERNATE_BENEFICIARY_UNAVAILABLE","Le contrôle des mandats et droits des tiers reste fermé dans cette tranche.");
        if(!checked||details==null||details.isBlank()||details.length()>500)throw ApiException.invalid("BENEFICIARY_CHECK_REQUIRED","Le contrôle du remettant et ses éléments minimaux sont requis.");
        String normalized=details.trim();
        return CashIdentityService.guarded(()->{
            var prior=repository.beneficiaryByKey(tenant,key);if(prior.isPresent())return beneficiaryReplay(tenant,prior.get(),requestId,actor,normalized);
            var request=requiredRequest(tenant,requestId);lock(tenant,request.receptionId());
            prior=repository.beneficiaryByKey(tenant,key);if(prior.isPresent())return beneficiaryReplay(tenant,prior.get(),requestId,actor,normalized);
            request=requiredRequest(tenant,requestId);
            if(!request.createdBy().equals(actor))throw ApiException.forbidden("REQUEST_AUTHOR_REQUIRED","Le demandeur prépare le bénéficiaire de sa réservation.");
            requireApproved(request);identities.readyIdentity(tenant,request.receptionId());
            if(repository.beneficiaryFor(tenant,requestId).isPresent())throw ApiException.conflict("BENEFICIARY_ALREADY_RECORDED","Le bénéficiaire de cette réservation a déjà été préparé.");
            UUID id=UUID.randomUUID();var b=new Beneficiary(id,requestId,request.receptionId(),request.currency(),request.amountMinor(),key,"CBV-"+id,
                    cipher.encrypt(tenant,id,"beneficiary",normalized),clock.instant().truncatedTo(ChronoUnit.MICROS),actor);
            if(!repository.insert(tenant,b))return beneficiaryReplay(tenant,repository.beneficiaryByKey(tenant,key).orElseThrow(),requestId,actor,normalized);
            audit.record(tenant,actor,"payments.beneficiary.verified","cash_beneficiary_verification",id,
                    Map.of("reference",b.reference(),"receptionId",b.source(),"requestId",requestId));return new Written<>(b,true);
        });
    }
    private Written<Beneficiary> beneficiaryReplay(UUID tenant,Beneficiary b,UUID request,UUID actor,String details){
        if(!b.requestId().equals(request)||!b.verifiedBy().equals(actor)||!cipher.decrypt(tenant,b.id(),"beneficiary",b.encryptedPayload()).equals(details))throw keyConflict();
        return new Written<>(b,false);
    }
    @Transactional(timeout=10)
    public Written<Authorization> authorize(UUID tenant,UUID requestId,UUID verificationId,UUID key,boolean reviewed){
        UUID actor=access.require(tenant,Permission.CASH_EXCESS_APPROVE);requireEnabled();
        if(!reviewed)throw ApiException.invalid("BENEFICIARY_REVIEW_REQUIRED","Confirmez l'examen du bénéficiaire identifié.");
        return CashIdentityService.guarded(()->{
            var prior=repository.authorizationByKey(tenant,key);if(prior.isPresent())return authorizationReplay(prior.get(),requestId,verificationId,actor);
            var request=requiredRequest(tenant,requestId);lock(tenant,request.receptionId());
            prior=repository.authorizationByKey(tenant,key);if(prior.isPresent())return authorizationReplay(prior.get(),requestId,verificationId,actor);
            request=requiredRequest(tenant,requestId);
            if(request.createdBy().equals(actor))throw ApiException.forbidden("SELF_AUTHORIZATION_FORBIDDEN","Une autre personne doit autoriser la restitution.");
            requireApproved(request);identities.readyIdentity(tenant,request.receptionId());
            var b=repository.beneficiary(tenant,verificationId).orElseThrow(()->ApiException.notFound("BENEFICIARY_VERIFICATION_NOT_FOUND","Rapprochement introuvable."));
            if(!b.requestId().equals(requestId)||!b.source().equals(request.receptionId()))throw ApiException.conflict("BENEFICIARY_REQUEST_MISMATCH","Le bénéficiaire doit appartenir à cette réservation.");
            // Un contenu chiffré altéré ne peut être autorisé par le seul rattachement de ses identifiants.
            cipher.decrypt(tenant,b.id(),"beneficiary",b.encryptedPayload());
            if(repository.authorizationFor(tenant,requestId).isPresent())throw ApiException.conflict("RESTITUTION_ALREADY_AUTHORIZED","Cette réservation possède déjà son autorisation nominative.");
            UUID id=UUID.randomUUID();var a=new Authorization(id,requestId,verificationId,request.receptionId(),request.currency(),request.amountMinor(),key,
                    "CRA-"+id,"NAMED_REMITTER_V1",clock.instant().truncatedTo(ChronoUnit.MICROS),actor);
            if(!repository.insert(tenant,a))return authorizationReplay(repository.authorizationByKey(tenant,key).orElseThrow(),requestId,verificationId,actor);
            audit.record(tenant,actor,"payments.restitution.authorized","cash_restitution_authorization",id,
                    Map.of("reference",a.reference(),"receptionId",a.source(),"requestId",requestId,"beneficiaryVerificationId",verificationId));
            return new Written<>(a,true);
        });
    }
    private Written<Authorization> authorizationReplay(Authorization a,UUID request,UUID verification,UUID actor){
        if(!a.requestId().equals(request)||!a.verificationId().equals(verification)||!a.authorizedBy().equals(actor))throw keyConflict();
        return new Written<>(a,false);
    }
    private static ApiException keyConflict(){return ApiException.conflict("CASH_RESTITUTION_KEY_CONFLICT","Cette clé désigne une autre intention.");}
    private void lock(UUID tenant,UUID source){receptions.lock(tenant,source).orElseThrow(()->ApiException.notFound("CASH_RECEPTION_NOT_FOUND","Déclaration introuvable."));}
    private CashExcessRepository.Request requiredRequest(UUID tenant,UUID id){return excess.find(tenant,id).orElseThrow(()->ApiException.notFound("CASH_EXCESS_NOT_FOUND","Demande introuvable."));}
    private static void requireApproved(CashExcessRepository.Request r){if(!"APPROVED_RESERVED".equals(r.state()))throw ApiException.conflict("APPROVED_RESERVATION_REQUIRED","Une réservation techniquement approuvée est requise.");}
    private void requireEnabled(){if(!properties.cashIdentitiesEnabled())throw ApiException.notFound("CASH_IDENTITIES_DISABLED","La préparation nominative est désactivée.");}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public ReadBeneficiary readBeneficiary(UUID tenant,UUID id,boolean byKey){
        access.require(tenant,Permission.CASH_EXCESS_READ);
        var b=(byKey?repository.beneficiaryByKey(tenant,id):repository.beneficiary(tenant,id))
                .orElseThrow(()->ApiException.notFound("BENEFICIARY_VERIFICATION_NOT_FOUND","Rapprochement introuvable."));
        var identity=identities.read(tenant,b.source());
        return new ReadBeneficiary(b,identity.details().fullName(),cipher.decrypt(tenant,b.id(),"beneficiary",b.encryptedPayload()));
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Authorization readAuthorization(UUID tenant,UUID id,boolean byKey){
        access.require(tenant,Permission.CASH_EXCESS_READ);
        var a=(byKey?repository.authorizationByKey(tenant,id):repository.authorization(tenant,id))
                .orElseThrow(()->ApiException.notFound("RESTITUTION_AUTHORIZATION_NOT_FOUND","Autorisation introuvable."));
        identities.read(tenant,a.source());return a;
    }
}
