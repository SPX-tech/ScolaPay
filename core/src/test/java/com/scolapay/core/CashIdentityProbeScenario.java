package com.scolapay.core;

import static org.assertj.core.api.Assertions.assertThat;
import com.scolapay.cashprobe.CashProbeApplication;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

abstract class CashIdentityProbeScenario {
    final List<Process> processes=new ArrayList<>();String url;JdbcClient jdbc;TransactionTemplate tx;
    record Instance(Process process,TestApi api){void kill() throws Exception{process.destroyForcibly();assertThat(process.waitFor(10,TimeUnit.SECONDS)).isTrue();}}
    record Legacy(TestApi.School school,UUID source,UUID originalKey,UUID requestId,String reviewerToken){}
    Map<String,Object> legacyReceipt,legacyRequest,legacyDecision;
    List<Map<String,Object>> legacyHistory;
    @BeforeEach void prepare(){
        url=TestDatabases.createEmpty();var ds=new DriverManagerDataSource(url,TestDatabases.POSTGRES.getUsername(),TestDatabases.POSTGRES.getPassword());
        jdbc=JdbcClient.create(ds);tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
    }
    @AfterEach void stop() throws Exception{for(Process p:processes){p.destroyForcibly();assertThat(p.waitFor(10,TimeUnit.SECONDS)).isTrue();}}
    Instance start(String target,String dropSuffix,boolean enabled) throws Exception{
        int port;try(ServerSocket socket=new ServerSocket(0)){port=socket.getLocalPort();}
        Path log=Path.of("target","identity-probe","instance-"+port+".log");Files.createDirectories(log.getParent());
        var command=new ArrayList<>(List.of(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-Xmx256m","-XX:TieredStopAtLevel=1",
                "-cp",System.getProperty("surefire.test.class.path",System.getProperty("java.class.path")),CashProbeApplication.class.getName()));
        command.addAll(TestDatabases.applicationArguments(url));
        command.addAll(List.of("--server.port="+port,"--scolapay.payments.cash-receptions-enabled="+enabled,
                "--scolapay.payments.cash-excess-reservations-enabled="+enabled,"--scolapay.payments.cash-identities-enabled="+enabled,
                "--probe.drop-identity-suffix="+dropSuffix));
        if(target!=null)command.add("--spring.flyway.target="+target);
        Process p=new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();processes.add(p);
        TestApi api=new TestApi(port,jdbc);CashSettlementRecoveryTest.await(()->{if(!p.isAlive())throw new AssertionError("Sonde arrêtée : "+log);return api.healthy();});
        return new Instance(p,api);
    }
    Legacy legacy() throws Exception{
        Instance old=start("10","",false);var school=old.api().newSchool();UUID id=UUID.randomUUID(),key=UUID.randomUUID();
        UUID actor=old.api().get("/api/v2/me",school.adminToken()).id("id");
        tx.executeWithoutResult(status->{
            jdbc.sql("""
                    INSERT INTO cash_receptions(id,establishment_id,reference,idempotency_key,currency,amount_received_minor,
                      change_given_minor,cash_received_confirmed,recorded_at,recorded_by_account_id,business_date,timezone)
                    VALUES (:id,:tenant,:reference,:key,'XAF',20000,0,true,'2026-11-11T09:00:00Z',:actor,'2026-11-11','Africa/Ndjamena')
                    """).param("id",id).param("tenant",school.id()).param("reference","CR-"+id).param("key",key).param("actor",actor).update();
            jdbc.sql("""
                    INSERT INTO audit_events(id,occurred_at,establishment_id,actor_account_id,action,target_type,target_id,details)
                    VALUES (:event,now(),:tenant,:actor,'payments.cash.received','cash_reception',:id,jsonb_build_object('reference',CAST(:reference AS text)))
                    """).param("event",UUID.randomUUID()).param("tenant",school.id()).param("actor",actor).param("id",id).param("reference","CR-"+id).update();
        });
        String reviewer=reviewer(old.api(),school);UUID reviewerId=old.api().get("/api/v2/me",reviewer).id("id"),request=UUID.randomUUID(),decision=UUID.randomUUID();
        tx.executeWithoutResult(status->{
            jdbc.sql("""
                    INSERT INTO cash_excess_requests(id,establishment_id,reception_id,currency,amount_minor,reason,idempotency_key,reference,
                      authorization_scope,retained_minor,historical_allocated_before_minor,reserved_before_minor,created_at,created_by)
                    VALUES (:id,:tenant,:source,'XAF',15000,'Excédent historique',:key,:reference,'RESERVATION_ONLY_V1',20000,0,0,now(),:actor)
                    """).param("id",request).param("tenant",school.id()).param("source",id).param("key",UUID.randomUUID()).param("reference","XQ-"+request).param("actor",actor).update();
            preparationAudit(school.id(),actor,"payments.excess.requested","cash_excess_request",request,"XQ-"+request,"receptionId",id);
            jdbc.sql("""
                    INSERT INTO cash_excess_decisions(id,establishment_id,request_id,kind,note,idempotency_key,reference,decided_at,decided_by)
                    VALUES (:id,:tenant,:request,'APPROVED',NULL,:key,:reference,now(),:actor)
                    """).param("id",decision).param("tenant",school.id()).param("request",request).param("key",UUID.randomUUID()).param("reference","XD-"+decision).param("actor",reviewerId).update();
            preparationAudit(school.id(),reviewerId,"payments.excess.approved","cash_excess_decision",decision,"XD-"+decision,"requestId",request);
        });
        legacyReceipt=jdbc.sql("SELECT * FROM cash_receptions WHERE id=:id").param("id",id).query().singleRow();
        legacyRequest=jdbc.sql("SELECT * FROM cash_excess_requests WHERE id=:id").param("id",request).query().singleRow();
        legacyDecision=jdbc.sql("SELECT * FROM cash_excess_decisions WHERE id=:id").param("id",decision).query().singleRow();
        legacyHistory=history();old.kill();return new Legacy(school,id,key,request,reviewer);
    }
    void preparationAudit(UUID tenant,UUID actor,String action,String target,UUID id,String reference,String link,UUID linked){
        jdbc.sql("""
                INSERT INTO audit_events(id,occurred_at,establishment_id,actor_account_id,action,target_type,target_id,details)
                VALUES (:event,now(),:tenant,:actor,:action,:target,:id,jsonb_build_object('reference',CAST(:reference AS text),CAST(:link AS text),CAST(:linked AS text)))
                """).param("event",UUID.randomUUID()).param("tenant",tenant).param("actor",actor).param("action",action).param("target",target).param("id",id)
                .param("reference",reference).param("link",link).param("linked",linked.toString()).update();
    }
    List<Map<String,Object>> history(){return jdbc.sql("SELECT installed_rank,version,script,checksum,installed_on,success FROM flyway_schema_history ORDER BY installed_rank").query().listOfRows();}
    Map<String,Object> declaration(UUID key){return Map.of("idempotencyKey",key,"remitter",IntegrationTest.REMITTER,
            "statementReference","SIGNED-"+key,"signedStatementConfirmed",true);}
    TestApi.Reply declare(TestApi api,Legacy legacy,UUID key){return api.post(legacy.school().path("/payments/cash-receptions/"+legacy.source()+"/remitter-declarations"),
            legacy.school().adminToken(),declaration(key));}
    String reviewer(TestApi api,TestApi.School school){
        String contact=IntegrationTest.uniqueEmail();var invitation=api.post(school.path("/invitations"),school.adminToken(),Map.of("contact",contact,"role","DIRECTOR"));
        assertThat(invitation.status()).as(invitation.raw()).isEqualTo(201);
        String token=jdbc.sql("SELECT token FROM test_invitation_deliveries WHERE id=:id").param("id",invitation.id("id")).query(String.class).single();
        assertThat(api.accept(token).status()).isEqualTo(200);return api.login(contact,IntegrationTest.PASSWORD);
    }
    Map<String,Object> review(UUID key){return Map.of("idempotencyKey",key,"originalReceiptMatchedConfirmed",true,"signedStatementCheckedConfirmed",true,"identityCheckedConfirmed",true);}
}
