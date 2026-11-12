package com.scolapay.core;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Map;
import java.util.UUID;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties={"scolapay.payments.cash-identities-enabled=true",
        "scolapay.payments.identity-encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="})
class CashIdentityContractTest extends CashSettlementScenario {
    static final Map<String,Object> REMITTER=Map.of("fullName","Remettant confidentiel",
            "identificationDetails","Référence minimale examinée au guichet","identityRecordedConfirmed",true);

    @Test void uneNouvelleReceptionSansRemettantEstRefuseeSansCreerDeFait() {
        Tenant t=newTenant();
        Response refused=post(t.path("/payments/cash-receptions"),t.adminToken(),Map.of("idempotencyKey",UUID.randomUUID(),
                "amountReceivedMinor","20000","changeGivenMinor","0","cashReceivedConfirmed",true));
        assertThat(refused.status()).as(refused.raw()).isEqualTo(400);
        assertThat(count("cash_receptions",t)).isZero();
    }

    @Test void uneReceptionIdentifieeConserveLaPreuveSansExposerLeRemettantDansLaVueGenerale() {
        Tenant t=newTenant();
        Response created=post(t.path("/payments/cash-receptions"),t.adminToken(),Map.of("idempotencyKey",UUID.randomUUID(),
                "amountReceivedMinor","20000","changeGivenMinor","0","cashReceivedConfirmed",true,"remitter",REMITTER));
        assertThat(created.status()).as(created.raw()).isEqualTo(201);
        assertThat(created.raw()).doesNotContain("Remettant confidentiel","Référence minimale");
        Response identity=get(t.path("/payments/cash-receptions/"+created.id("id")+"/remitter"),t.adminToken());
        assertThat(identity.status()).as(identity.raw()).isEqualTo(200);
        assertThat(identity.text("fullName")).isEqualTo("Remettant confidentiel");
        assertThat(identity.body().path("physicalHandoverAvailable").asBoolean()).isFalse();
    }
    Response identified(Tenant t,UUID key,Map<String,Object> remitter){return post(t.path("/payments/cash-receptions"),t.adminToken(),Map.of(
            "idempotencyKey",key,"amountReceivedMinor","20000","changeGivenMinor","0","cashReceivedConfirmed",true,"remitter",remitter));}

    @Test void leRejeuLieAussiLeRemettantSansCreerDeCompteOuDePersonneFinanciere(){
        Tenant t=newTenant();UUID key=UUID.randomUUID();long accounts=jdbc.sql("SELECT count(*) FROM accounts").query(Long.class).single();
        Response first=identified(t,key,REMITTER);assertThat(first.status()).as(first.raw()).isEqualTo(201);
        assertThat(identified(t,key,REMITTER).body()).isEqualTo(first.body());
        var changed=new HashMap<>(REMITTER);changed.put("fullName","AUTRE-PERSONNE-CONFIDENTIELLE");
        Response rejected=identified(t,key,changed);assertThat(rejected.code()).isEqualTo("CASH_RECEPTION_KEY_CONFLICT");
        assertThat(rejected.raw()).doesNotContain("AUTRE-PERSONNE","Remettant confidentiel");
        assertThat(jdbc.sql("SELECT count(*) FROM accounts").query(Long.class).single()).isEqualTo(accounts);
        assertThat(count("billing_parties",t)).isZero();assertThat(count("cash_receptions",t)).isEqualTo(1);
        assertThat(count("cash_remitter_identifications",t)).isEqualTo(1);
    }
    @Test void lesDonneesPriveesSontChiffreesEtLaLectureEstLimiteeAuDeclarantEtAuControle(){
        Tenant t=newTenant();UUID id=identified(t,UUID.randomUUID(),REMITTER).id("id");
        String encrypted=jdbc.sql("SELECT encrypted_payload FROM cash_remitter_identifications WHERE id=:id").param("id",id).query(String.class).single();
        assertThat(encrypted).startsWith("v1:").doesNotContain("Remettant confidentiel","Référence minimale");
        String audit=jdbc.sql("SELECT string_agg(details::text,',') FROM audit_events WHERE establishment_id=:id").param("id",t.id()).query(String.class).single();
        assertThat(audit).doesNotContain("Remettant confidentiel","Référence minimale");
        String path=t.path("/payments/cash-receptions/"+id+"/remitter");
        assertThat(get(path,newStaff(t,"CASHIER")).status()).isEqualTo(403);
        assertThat(get(path,newStaff(t,"REGISTRAR")).status()).isEqualTo(403);
        for(String role:List.of("DIRECTOR","ACCOUNTANT","AUDITOR"))assertThat(get(path,newStaff(t,role)).status()).isEqualTo(200);
        assertThat(get(path,newTenant().adminToken()).status()).isEqualTo(404);
        assertThat(get(path,newGuardian(t,newStudent(t))).status()).isEqualTo(404);
    }
    @Test void lesIdentitesIncompletesOuLesRattachementsImplicitesSontRefuses(){
        Tenant t=newTenant();
        for(String field:List.of("fullName","identificationDetails","identityRecordedConfirmed")){
            var invalid=new HashMap<>(REMITTER);invalid.put(field,null);assertThat(identified(t,UUID.randomUUID(),invalid).status()).isEqualTo(400);
        }
        for(var bad:List.of(Map.<String,Object>of("fullName"," \t "),Map.<String,Object>of("fullName","x".repeat(161)),
                Map.<String,Object>of("identificationDetails","x".repeat(301)),Map.<String,Object>of("identityRecordedConfirmed",false),
                Map.<String,Object>of("accountId",UUID.randomUUID()),Map.<String,Object>of("identityDocumentCopy","contenu"))){
            var invalid=new HashMap<>(REMITTER);invalid.putAll(bad);assertThat(identified(t,UUID.randomUUID(),invalid).status()).isEqualTo(400);
        }
        assertThat(count("cash_receptions",t)).isZero();assertThat(count("cash_remitter_identifications",t)).isZero();
    }
    @Test void unAuditDIdentiteDefaillantAnnuleLaReceptionEtLeMemeRejeuPeutReussir(){
        Tenant t=newTenant();UUID key=UUID.randomUUID();String constraint="identity_audit_"+UUID.randomUUID().toString().replace("-","");
        jdbc.sql("ALTER TABLE audit_events ADD CONSTRAINT "+constraint+" CHECK (NOT (establishment_id='"+t.id()+"'::uuid AND action='payments.remitter.recorded')) NOT VALID").update();
        try{
            Response rejected=identified(t,key,REMITTER);assertThat(rejected.status()).as(rejected.raw()).isEqualTo(409);
            assertThat(rejected.raw()).doesNotContain("Remettant confidentiel","Référence minimale","INSERT","SQLException");
            assertThat(count("cash_receptions",t)).isZero();assertThat(count("cash_remitter_identifications",t)).isZero();
        }finally{jdbc.sql("ALTER TABLE audit_events DROP CONSTRAINT "+constraint).update();}
        assertThat(identified(t,key,REMITTER).status()).isEqualTo(201);
        assertThat(count("cash_remitter_identifications",t)).isEqualTo(1);
    }
    @Test void huitRejeuxNeCreentQuUneIdentiteEtLaProcedureHistoriqueNeRemplacePasUneIdentiteInitiale() throws Exception{
        Tenant t=newTenant();UUID key=UUID.randomUUID();var replies=concurrently(8,()->identified(t,key,REMITTER));
        assertThat(replies).allSatisfy(r->assertThat(r.status()).as(r.raw()).isIn(200,201));
        assertThat(replies.stream().filter(r->r.status()==201)).hasSize(1);assertThat(count("cash_remitter_identifications",t)).isEqualTo(1);
        assertThat(post(t.path("/payments/cash-receptions/"+replies.getFirst().id("id")+"/remitter-declarations"),t.adminToken(),
                Map.of("idempotencyKey",UUID.randomUUID(),"remitter",REMITTER,"statementReference","SIGNED-EXAMPLE","signedStatementConfirmed",true)).status()).isEqualTo(422);
    }
}
