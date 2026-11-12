package com.scolapay.core.payments.internal;

import com.scolapay.core.audit.AuditTrail;
import com.scolapay.core.establishment.EstablishmentAccess;
import com.scolapay.core.establishment.Permission;
import com.scolapay.core.payments.internal.CashReceptionRepository.Reception;
import com.scolapay.core.shared.ApiException;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
class CashIdentityService {
    record Details(String fullName,String identificationDetails) {}
    record Read(CashIdentityRepository.Remitter remitter,Details details,String state) {}
    private final CashIdentityRepository identities;
    private final CashReceptionRepository receptions;
    private final CashIdentityCipher cipher;
    private final JsonMapper json;
    private final EstablishmentAccess access;
    private final AuditTrail audit;
    CashIdentityService(CashIdentityRepository identities,CashReceptionRepository receptions,CashIdentityCipher cipher,
            JsonMapper json,EstablishmentAccess access,AuditTrail audit){
        this.identities=identities;this.receptions=receptions;this.cipher=cipher;this.json=json;this.access=access;this.audit=audit;
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
        UUID actor=access.require(tenant,Permission.CASH_RECEPTIONS_READ);
        Reception cash=receptions.find(tenant,source).orElseThrow(()->ApiException.notFound("CASH_RECEPTION_NOT_FOUND","Déclaration introuvable."));
        if(!cash.recordedByAccountId().equals(actor)&&!access.allows(tenant,Permission.CASH_IDENTITY_READ))
            throw ApiException.forbidden("CASH_IDENTITY_READ_DENIED","Votre rôle ne permet pas cette lecture d'identité.");
        var r=identities.find(tenant,source);
        return r.map(value->new Read(value,decode(tenant,value),"AT_RECEPTION".equals(value.origin())?"IDENTIFIED_AT_RECEPTION":"LEGACY_PENDING_REVIEW"))
                .orElseGet(()->new Read(null,null,"LEGACY_UNIDENTIFIED"));
    }
}
