package com.scolapay.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Une correction ne change ni la personne financière, ni son compte, ni ses désignations. */
class PartyCorrectionTest extends BillingScenarioTest {
    @Autowired PlatformTransactionManager transactions;
    Map<String,Object> request(int revision,String name,String identifier,String reason) {
        Map<String,Object> body=new HashMap<>();
        body.put("expectedRevision",revision);body.put("displayName",name);body.put("identifier",identifier);body.put("reason",reason);
        return body;
    }

    Response correct(Tenant tenant,String token,UUID party,int revision,String name,String identifier,String reason) {
        return post(tenant.path("/billing/parties/"+party+"/corrections"),token,request(revision,name,identifier,reason));
    }

    @Test
    void corrigerUnePersonneLieeConserveSonIdentiteEtSesDroits() throws Exception {
        School school=school();
        Tenant tenant=school.tenant();
        UUID student=enrolledAndBilled(school);
        Linked linked=linkedThirdParty(tenant,student,false);
        UUID party=partyId(createParty(tenant,"PERSON","Nom initial","REF-1",linked.grant()));
        String designation=designate(tenant,tenant.adminToken(),obligationOf(student),party,"CONTRACTUAL_DEBTOR").text("id");
        Response corrected=post(tenant.path("/billing/parties/"+party+"/corrections"),tenant.adminToken(),Map.of(
                "expectedRevision",0,"displayName","Nom corrigé","identifier","REF-2","reason","Correction de saisie validée"));
        assertThat(corrected.status()).as(corrected.raw()).isEqualTo(201);
        var stored=jdbc.sql("SELECT id,account_id,display_name,identifier FROM billing_parties WHERE id=:id").param("id",party).query().singleRow();
        assertThat(stored.get("id")).isEqualTo(party);
        assertThat(stored.get("account_id")).isEqualTo(linked.account());
        assertThat(stored.get("display_name")).isEqualTo("Nom corrigé");
        assertThat(stored.get("identifier")).isEqualTo("REF-2");
        Response history=get(tenant.path("/billing/parties/"+party+"/corrections"),tenant.adminToken());
        assertThat(history.status()).isEqualTo(200);
        assertThat(history.body().size()).isEqualTo(1);
        assertThat(history.body().get(0).path("previousName").asString()).isEqualTo("Nom initial");
        assertThat(history.body().get(0).path("newName").asString()).isEqualTo("Nom corrigé");
        assertThat(get(tenant.path("/billing/obligations/"+obligationOf(student)+"/parties"),tenant.adminToken()).body().get(0).path("id").asString()).isEqualTo(designation);
        assertThat(get("/api/v2/me/billing/obligations/"+obligationOf(student),linked.token()).status()).isEqualTo(200);
    }

    @Test
    void uneCorrectionExigeAutorisationMotifEtVersionCourante() {
        Tenant tenant=school().tenant();
        UUID party=partyId(createParty(tenant,"PERSON","Nom initial",null,null));
        String path=tenant.path("/billing/parties/"+party+"/corrections");
        for(String role:List.of("CASHIER","AUDITOR","DIRECTOR","REGISTRAR")) {
            assertThat(correct(tenant,newStaff(tenant,role),party,0,"Nom corrigé",null,"Correction autorisée").status()).as(role).isEqualTo(403);
        }
        Map<String,Object> missing=request(0,"Nom corrigé",null,"Correction autorisée");
        missing.remove("reason");
        assertThat(post(path,tenant.adminToken(),missing).status()).isEqualTo(400);
        assertThat(correct(tenant,tenant.adminToken(),party,0,"Nom corrigé",null,"   ").status()).isEqualTo(400);
        assertThat(correct(tenant,tenant.adminToken(),party,0," a ",null,"Correction autorisée").status()).isEqualTo(422);
        assertThat(correct(tenant,newStaff(tenant,"ACCOUNTANT"),party,0,"Nom corrigé",null,"Correction autorisée").status()).isEqualTo(201);
        Response replay=correct(tenant,tenant.adminToken(),party,0,"Nom corrigé",null,"Correction autorisée");
        assertThat(replay.status()).isEqualTo(409);
        assertThat(replay.code()).isEqualTo("PARTY_CORRECTION_OUTDATED");
        assertThat(get(path,tenant.adminToken()).body().size()).isEqualTo(1);
        assertThat(correct(tenant,tenant.adminToken(),party,1,"Nom corrigé",null,"Aucune différence").code()).isEqualTo("PARTY_UNCHANGED");
    }

    @Test
    void deuxCorrectionsSimultaneesNePerdentPasUneVersion() throws Exception {
        Tenant tenant=school().tenant();
        UUID party=partyId(createParty(tenant,"PERSON","Nom initial",null,null));
        AtomicInteger turn=new AtomicInteger();
        List<Response> replies=concurrently(2,() -> correct(tenant,tenant.adminToken(),party,0,"Nom "+turn.incrementAndGet(),"REF","Correction concurrente"));
        assertThat(replies.stream().filter(reply -> reply.status()==201).count()).isEqualTo(1);
        assertThat(replies.stream().filter(reply -> reply.code().equals("PARTY_CORRECTION_OUTDATED")).count()).isEqualTo(1);
        Response current=get(tenant.path("/billing/parties/"+party),tenant.adminToken());
        assertThat(current.status()).isEqualTo(200);
        assertThat(current.body().path("revision").asInt()).isEqualTo(1);
        assertThat(get(tenant.path("/billing/parties/"+party+"/corrections"),tenant.adminToken()).body().size()).isEqualTo(1);
    }

    @Test
    void laCorrectionNePeutPasReassocierOuRetirerUnCompte() {
        School school=school();
        Tenant tenant=school.tenant();
        UUID student=newStudent(tenant);
        Linked linked=linkedThirdParty(tenant,student,false);
        UUID party=partyId(createParty(tenant,"PERSON","Identité liée",null,linked.grant()));
        for(String key:List.of("accountId","grantId","kind")) {
            Map<String,Object> body=request(0,"Nom corrigé",null,"Correction autorisée");
            body.put(key,key.equals("kind")?"ORGANIZATION":UUID.randomUUID().toString());
            assertThat(post(tenant.path("/billing/parties/"+party+"/corrections"),tenant.adminToken(),body).status()).as(key).isEqualTo(400);
        }
        assertThatThrownBy(() -> jdbc.sql("UPDATE billing_parties SET account_id=NULL WHERE id=:id").param("id",party).update())
                .isInstanceOf(DataAccessException.class);
        assertThat(jdbc.sql("SELECT account_id FROM billing_parties WHERE id=:id").param("id",party).query(UUID.class).single()).isEqualTo(linked.account());
        UUID withoutAccount=partyId(createParty(tenant,"PERSON","Sans compte",null,null));
        assertThatThrownBy(() -> jdbc.sql("UPDATE billing_parties SET account_id=:account WHERE id=:id")
                .param("account",get("/api/v2/me",tenant.adminToken()).id("id")).param("id",withoutAccount).update())
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void leMotifEtLesAnciennesValeursRestentConfidentiels() {
        Tenant tenant=school().tenant();
        UUID party=partyId(createParty(tenant,"PERSON","Ancien nom","IDENTIFIANT-ANCIEN",null));
        String secret="MOTIF-RECTIFICATION-CONFIDENTIEL";
        assertThat(correct(tenant,tenant.adminToken(),party,0,"Nouveau nom","IDENTIFIANT-NOUVEAU",secret).status()).isEqualTo(201);
        String path=tenant.path("/billing/parties/"+party+"/corrections");
        assertThat(get(path,newStaff(tenant,"CASHIER")).status()).isEqualTo(403);
        assertThat(get(path,newStaff(tenant,"AUDITOR")).raw()).contains(secret,"Ancien nom");
        assertThat(jdbc.sql("SELECT count(*) FROM audit_events WHERE establishment_id=:id AND action='billing.party.corrected'")
                .param("id",tenant.id()).query(Long.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT details::text FROM audit_events WHERE establishment_id=:id AND action='billing.party.corrected'")
                .param("id",tenant.id()).query(String.class).single()).doesNotContain(secret,"Ancien nom","IDENTIFIANT-ANCIEN","Nouveau nom");
    }

    @Test
    void uneCorrectionNeTraversePasLesEtablissementsEtUneFamilleNeLitPasLHistorique() {
        Tenant owner=school().tenant();
        Tenant other=school().tenant();
        UUID student=newStudent(owner);
        Linked family=linkedThirdParty(owner,student,false);
        UUID party=partyId(createParty(owner,"PERSON","Personne liée",null,family.grant()));
        assertThat(correct(other,other.adminToken(),party,0,"Intrusion",null,"Tentative non autorisée").status()).isEqualTo(404);
        assertThat(get(other.path("/billing/parties/"+party+"/corrections"),other.adminToken()).status()).isEqualTo(404);
        assertThat(get(owner.path("/billing/parties/"+party+"/corrections"),family.token()).status()).isEqualTo(404);
        assertThat(correct(owner,family.token(),party,0,"Intrusion",null,"Tentative non autorisée").status()).isEqualTo(404);
        assertThat(get(owner.path("/billing/parties/"+party+"/corrections"),owner.adminToken()).body().size()).isZero();
    }

    @Test
    void uneDefaillanceDAuditAnnuleLaCorrectionEtSaTrace() {
        Tenant tenant=school().tenant();
        UUID party=partyId(createParty(tenant,"PERSON","Nom initial","REF-INITIAL",null));
        jdbc.sql("""
                CREATE FUNCTION test_party_correction_audit_failure() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                  IF NEW.action='billing.party.corrected' THEN RAISE EXCEPTION 'Defaillance audit injectee'; END IF;
                  RETURN NEW;
                END; $$
                """).update();
        jdbc.sql("CREATE TRIGGER test_party_correction_audit_failure BEFORE INSERT ON audit_events FOR EACH ROW EXECUTE FUNCTION test_party_correction_audit_failure()").update();
        Response reply;
        try { reply=correct(tenant,tenant.adminToken(),party,0,"Nom annulé","REF-ANNULEE","Correction à annuler"); }
        finally {
            jdbc.sql("DROP TRIGGER test_party_correction_audit_failure ON audit_events").update();
            jdbc.sql("DROP FUNCTION test_party_correction_audit_failure()").update();
        }
        assertThat(reply.status()).isEqualTo(500);
        assertThat(reply.raw()).doesNotContain("Nom annulé","Correction à annuler");
        assertThat(jdbc.sql("SELECT display_name FROM billing_parties WHERE id=:id").param("id",party).query(String.class).single()).isEqualTo("Nom initial");
        assertThat(jdbc.sql("SELECT revision FROM billing_parties WHERE id=:id").param("id",party).query(Integer.class).single()).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM billing_party_corrections WHERE party_id=:id").param("id",party).query(Long.class).single()).isZero();
    }

    @Test
    void leJournalDeCorrectionEstImmuableEtUneMiseAJourSansTraceEstRefusee() {
        Tenant tenant=school().tenant();
        UUID party=partyId(createParty(tenant,"PERSON","Nom initial",null,null));
        assertThatThrownBy(() -> jdbc.sql("UPDATE billing_parties SET display_name='Nom sans trace',revision=1 WHERE id=:id")
                .param("id",party).update()).isInstanceOf(DataAccessException.class);
        Response correction=correct(tenant,tenant.adminToken(),party,0,"Nom corrigé",null,"Correction autorisée");
        assertThat(correction.status()).isEqualTo(201);
        UUID id=correction.id("id");
        assertThatThrownBy(() -> jdbc.sql("UPDATE billing_party_corrections SET reason='Réécrit' WHERE id=:id").param("id",id).update()).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.sql("DELETE FROM billing_party_corrections WHERE id=:id").param("id",id).update()).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.sql("TRUNCATE billing_party_corrections").update()).isInstanceOf(DataAccessException.class);
    }

    @Test
    void uneTraceSeuleOuUnAncienEtatFalsifieNePeutPasEtreValide() {
        Tenant tenant=school().tenant();
        UUID party=partyId(createParty(tenant,"PERSON","Nom initial",null,null));
        UUID actor=get("/api/v2/me",tenant.adminToken()).id("id");
        for(String previous:List.of("Nom initial","Ancien nom inventé")) {
            assertThatThrownBy(() -> jdbc.sql("""
                    INSERT INTO billing_party_corrections(id,establishment_id,party_id,previous_revision,revision,
                        previous_name,new_name,reason,corrected_at,corrected_by)
                    VALUES(gen_random_uuid(),:establishment,:party,0,1,:previous,'Nom sans mise a jour','Motif explicite',now(),:actor)
                    """).param("establishment",tenant.id()).param("party",party).param("previous",previous).param("actor",actor).update())
                    .isInstanceOf(RuntimeException.class);
        }
        assertThat(jdbc.sql("SELECT count(*) FROM billing_party_corrections WHERE party_id=:id").param("id",party).query(Long.class).single()).isZero();
    }

    @Test
    void laBaseRefuseLeTransfertDeCompteMemeAvecUneTraceValide() {
        Tenant tenant=school().tenant();
        Linked linked=linkedThirdParty(tenant,newStudent(tenant),false);
        UUID party=partyId(createParty(tenant,"PERSON","Nom initial",null,linked.grant()));
        UUID actor=get("/api/v2/me",tenant.adminToken()).id("id");
        for(UUID account:new UUID[]{actor,null}) {
            assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                insertTrace(tenant,party,actor,"Nom initial");
                jdbc.sql("UPDATE billing_parties SET display_name='Nom corrigé',revision=1,account_id=:account WHERE id=:id")
                        .param("account",account).param("id",party).update();
                tx.setRollbackOnly();
            })).isInstanceOf(DataAccessException.class);
        }
        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            insertTrace(tenant,party,actor,"Nom initial");
            jdbc.sql("UPDATE billing_parties SET display_name='Nom corrigé',revision=1,kind='ORGANIZATION' WHERE id=:id").param("id",party).update();
            tx.setRollbackOnly();
        })).isInstanceOf(DataAccessException.class);
        UUID withoutAccount=partyId(createParty(tenant,"PERSON","Sans compte",null,null));
        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            insertTrace(tenant,withoutAccount,actor,"Sans compte");
            jdbc.sql("UPDATE billing_parties SET display_name='Nom corrigé',revision=1,account_id=:account WHERE id=:id")
                    .param("account",actor).param("id",withoutAccount).update();
            tx.setRollbackOnly();
        })).isInstanceOf(DataAccessException.class);
    }

    void insertTrace(Tenant tenant,UUID party,UUID actor,String previousName) {
        jdbc.sql("""
                INSERT INTO billing_party_corrections(id,establishment_id,party_id,previous_revision,revision,
                    previous_name,new_name,reason,corrected_at,corrected_by)
                VALUES(gen_random_uuid(),:establishment,:party,0,1,:previous,'Nom corrigé','Correction contrôlée',now(),:actor)
                """).param("establishment",tenant.id()).param("party",party).param("previous",previousName).param("actor",actor).update();
    }
}
