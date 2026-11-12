package com.scolapay.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.scolapay.cashprobe.CashProbeApplication;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

class DigitalPaymentRecoveryTest {
    final List<Process> processes=new ArrayList<>();String url;JdbcClient jdbc;
    record Instance(Process process,TestApi api){void kill()throws Exception{process.destroyForcibly();assertThat(process.waitFor(10,TimeUnit.SECONDS)).isTrue();}}
    record Fixture(TestApi.School school,UUID obligation){}
    @BeforeEach void prepare(){url=TestDatabases.createEmpty();jdbc=TestDatabases.client(url);}
    @AfterEach void stop()throws Exception{for(Process p:processes){p.destroyForcibly();assertThat(p.waitFor(10,TimeUnit.SECONDS)).isTrue();}}
    Fixture setup(TestApi api)throws Exception{
        var s=api.newSchool();String actor=s.adminToken();
        var period=api.post(s.path("/billing/periods"),actor,Map.of("academicYearId",s.yearId(),"code","ANNEE","name","Année","startsOn","2026-09-01","endsOn","2027-07-15"));
        assertThat(period.status()).as(period.raw()).isEqualTo(201);
        var fee=api.post(s.path("/billing/fee-definitions"),actor,Map.of("academicYearId",s.yearId(),"billingPeriodId",period.id("id"),"name","Scolarité"));
        var catalog=api.post(s.path("/billing/fee-catalogs"),actor,Map.of("academicYearId",s.yearId()));
        assertThat(fee.status()).as(fee.raw()).isEqualTo(201);assertThat(catalog.status()).as(catalog.raw()).isEqualTo(201);
        assertThat(api.post(s.path("/billing/fee-catalogs/"+catalog.id("id")+"/items"),actor,BillingScenarioTest.fee(fee.id("id"),"SCOLARITE",null,100000)).status()).isEqualTo(201);
        assertThat(api.post(s.path("/billing/fee-catalogs/"+catalog.id("id")+"/publish"),actor,Map.of()).status()).isEqualTo(200);
        UUID student=s.newStudent();assertThat(api.post(s.path("/students/"+student+"/enrollments"),actor,Map.of("academicYearId",s.yearId(),"academicUnitId",s.levelId(),"confirmedFeeCatalogVersionId",catalog.id("id"))).status()).isEqualTo(201);
        CashSettlementRecoveryTest.await(()->jdbc.sql("SELECT count(*) FROM obligations WHERE establishment_id=:tenant AND student_record_id=:student").param("tenant",s.id()).param("student",student).query(Long.class).single()==1);
        return new Fixture(s,jdbc.sql("SELECT id FROM obligations WHERE establishment_id=:tenant AND student_record_id=:student").param("tenant",s.id()).param("student",student).query(UUID.class).single());
    }
    Map<String,Object> command(Fixture f,UUID key){return Map.of("obligationId",f.obligation(),"idempotencyKey",key,"amountMinor","40000","currency","XAF","expectedJournalSize",1);}
    @Test void uneReponsePerdueSeRecupereSansPrestataireOuCleCashEtSansRejouerLesMigrations()throws Exception{
        Instance first=start(true,true);Fixture f=setup(first.api());UUID key=UUID.randomUUID();var installed=history();String path=f.school().path("/payments/digital-intents");
        assertThat(installed).extracting(row->row.get("version")).containsExactly("1","2","3","4","5","6","7","7.1","7.2","8","9","10","11","12");
        assertThat(installed).allSatisfy(row->{assertThat(row.get("checksum")).isNotNull();assertThat(row.get("success")).isEqualTo(true);});
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()){
            var pending=executor.submit(()->first.api().post(path,f.school().adminToken(),command(f,key)));
            CashSettlementRecoveryTest.await(()->count(f)==1);first.kill();assertThatThrownBy(()->pending.get(10,TimeUnit.SECONDS)).isInstanceOf(java.util.concurrent.ExecutionException.class);
        }
        Instance disabled=start(false,false);var recovered=disabled.api().get(path+"/by-key/"+key,f.school().adminToken());
        assertThat(recovered.status()).as(recovered.raw()).isEqualTo(200);assertThat(recovered.body().path("paymentConfirmed").asBoolean()).isFalse();
        assertThat(disabled.api().post(path,f.school().adminToken(),command(f,key)).status()).isEqualTo(404);
        assertThat(disabled.api().get("/api/v2/me/students",f.school().adminToken()).status()).isEqualTo(404);assertThat(history()).isEqualTo(installed);disabled.kill();
        Instance enabled=start(true,false);var replay=enabled.api().post(path,f.school().adminToken(),command(f,key));
        assertThat(replay.status()).as(replay.raw()).isEqualTo(200);assertThat(replay.body()).isEqualTo(recovered.body());assertThat(count(f)).isEqualTo(1);
        assertThat(jdbc.sql("SELECT count(*) FROM audit_events WHERE establishment_id=:tenant AND action='payments.digital.intent_prepared'").param("tenant",f.school().id()).query(Long.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT count(*) FROM obligation_entries WHERE obligation_id=:id").param("id",f.obligation()).query(Long.class).single()).isEqualTo(1);
        assertThat(history()).isEqualTo(installed);Flyway.configure().dataSource(url,TestDatabases.POSTGRES.getUsername(),TestDatabases.POSTGRES.getPassword()).locations("classpath:db/migration").load().validate();
    }
    @Test void deuxInstancesPartagentLaMemeIntentionSansOperationExterne()throws Exception{
        Instance first=start(true,false),second=start(true,false);Fixture f=setup(first.api());UUID key=UUID.randomUUID();CountDownLatch barrier=new CountDownLatch(1);
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()){
            var a=executor.submit(()->{barrier.await();return first.api().post(f.school().path("/payments/digital-intents"),f.school().adminToken(),command(f,key));});
            var b=executor.submit(()->{barrier.await();return second.api().post(f.school().path("/payments/digital-intents"),f.school().adminToken(),command(f,key));});
            barrier.countDown();var ra=a.get(15,TimeUnit.SECONDS);var rb=b.get(15,TimeUnit.SECONDS);assertThat(List.of(ra.status(),rb.status())).containsExactlyInAnyOrder(201,200);assertThat(ra.body()).isEqualTo(rb.body());
        }
        assertThat(count(f)).isEqualTo(1);
    }
    long count(Fixture f){return jdbc.sql("SELECT count(*) FROM digital_payment_intents WHERE establishment_id=:id").param("id",f.school().id()).query(Long.class).single();}
    List<Map<String,Object>> history(){return jdbc.sql("SELECT installed_rank,version,script,checksum,installed_on,success FROM flyway_schema_history ORDER BY installed_rank").query().listOfRows();}
    Instance start(boolean enabled,boolean drop)throws Exception{
        int port;try(ServerSocket socket=new ServerSocket(0)){port=socket.getLocalPort();}Path log=Path.of("target","digital-probe","instance-"+port+".log");Files.createDirectories(log.getParent());
        var command=new ArrayList<>(List.of(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-Xmx256m","-XX:TieredStopAtLevel=1","-cp",
                System.getProperty("surefire.test.class.path",System.getProperty("java.class.path")),CashProbeApplication.class.getName()));
        command.addAll(TestDatabases.applicationArguments(url));command.removeIf(value->value.startsWith("--scolapay.payments.identity-encryption-key="));
        command.addAll(List.of("--server.port="+port,"--scolapay.payments.digital.intents-enabled="+enabled,"--probe.drop-digital-response="+drop));
        Process p=new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();processes.add(p);TestApi api=new TestApi(port,jdbc);
        CashSettlementRecoveryTest.await(()->{if(!p.isAlive())throw new AssertionError("Sonde arrêtée : "+log);return api.healthy();});return new Instance(p,api);
    }
}
