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
import java.util.function.BooleanSupplier;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

class CashSettlementRecoveryTest {
    final List<Process> processes=new ArrayList<>();
    String url; JdbcClient jdbc;
    record Fixture(TestApi.School school,UUID obligation,UUID reception) {}
    record Instance(Process process,TestApi api) {
        void kill() throws Exception { process.destroyForcibly();assertThat(process.waitFor(10,TimeUnit.SECONDS)).isTrue(); }
    }
    @BeforeEach void prepare() { url=TestDatabases.createEmpty();jdbc=TestDatabases.client(url); }
    @AfterEach void stop() throws Exception {
        for(Process p:processes) { p.destroyForcibly();assertThat(p.waitFor(10,TimeUnit.SECONDS)).isTrue(); }
    }
    Fixture setup(TestApi api) throws Exception {
        var school=api.newSchool(); String actor=school.adminToken();
        var period=api.post(school.path("/billing/periods"),actor,Map.of("academicYearId",school.yearId(),"code","ANNEE",
                "name","Année","startsOn","2026-09-01","endsOn","2027-07-15"));
        assertThat(period.status()).as(period.raw()).isEqualTo(201);
        var fee=api.post(school.path("/billing/fee-definitions"),actor,Map.of("academicYearId",school.yearId(),"billingPeriodId",period.id("id"),"name","Scolarité"));
        var catalog=api.post(school.path("/billing/fee-catalogs"),actor,Map.of("academicYearId",school.yearId()));
        assertThat(fee.status()).as(fee.raw()).isEqualTo(201); assertThat(catalog.status()).as(catalog.raw()).isEqualTo(201);
        assertThat(api.post(school.path("/billing/fee-catalogs/"+catalog.id("id")+"/items"),actor,
                BillingScenarioTest.fee(fee.id("id"),"SCOLARITE",null,100000)).status()).isEqualTo(201);
        assertThat(api.post(school.path("/billing/fee-catalogs/"+catalog.id("id")+"/publish"),actor,Map.of()).status()).isEqualTo(200);
        UUID student=school.newStudent();
        assertThat(api.post(school.path("/students/"+student+"/enrollments"),actor,Map.of("academicYearId",school.yearId(),
                "academicUnitId",school.levelId(),"confirmedFeeCatalogVersionId",catalog.id("id"))).status()).isEqualTo(201);
        await(()->jdbc.sql("SELECT count(*) FROM obligations WHERE establishment_id=:tenant AND student_record_id=:student")
                .param("tenant",school.id()).param("student",student).query(Long.class).single()==1);
        UUID obligation=jdbc.sql("SELECT id FROM obligations WHERE establishment_id=:tenant AND student_record_id=:student")
                .param("tenant",school.id()).param("student",student).query(UUID.class).single();
        var receipt=api.post(school.path("/payments/cash-receptions"),actor,Map.of("idempotencyKey",UUID.randomUUID(),
                "amountReceivedMinor","100000","changeGivenMinor","0","cashReceivedConfirmed",true,"remitter",IntegrationTest.REMITTER));
        assertThat(receipt.status()).as(receipt.raw()).isEqualTo(201);
        return new Fixture(school,obligation,receipt.id("id"));
    }
    Map<String,Object> command(Fixture f,UUID key) {
        return Map.of("idempotencyKey",key,"obligationId",f.obligation(),"amountMinor","40000","expectedJournalSize",1);
    }
    String path(Fixture f) { return f.school().path("/payments/cash-receptions/"+f.reception()+"/allocations"); }

    @Test void unArretApresCommitAvantReponseNePerdNiNeDupliqueLeReglement() throws Exception {
        Instance first=start(true,true,true); Fixture f=setup(first.api());
        UUID key=UUID.randomUUID(); var originalHistory=history();
        assertThat(originalHistory).extracting(row->row.get("version"))
                .containsExactly("1","2","3","4","5","6","7","7.1","7.2","8","9","10","11");
        assertThat(originalHistory.get(12)).containsEntry("script","V11__remitter_identity_and_restitution_authorization.sql");
        assertThat(originalHistory.get(11)).containsEntry("script","V10__cash_excess_reservations.sql");
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            var pending=executor.submit(()->first.api().post(path(f),f.school().adminToken(),command(f,key)));
            await(()->allocations(f)==1); first.kill();
            assertThatThrownBy(()->pending.get(10,TimeUnit.SECONDS)).isInstanceOf(java.util.concurrent.ExecutionException.class);
        }
        Instance disabled=start(false,false,false);
        var recovered=disabled.api().get(f.school().path("/payments/allocations/by-key/"+key),f.school().adminToken());
        assertThat(recovered.status()).as(recovered.raw()).isEqualTo(200);
        assertThat(recovered.text("dueAfterMinor")).isEqualTo("60000");
        assertThat(disabled.api().post(path(f),f.school().adminToken(),command(f,key)).status()).isEqualTo(404);
        assertThat(disabled.api().get("/api/v2/me/students",f.school().adminToken()).status()).isEqualTo(404);
        assertThat(history()).isEqualTo(originalHistory); disabled.kill();
        Instance enabled=start(false,true,false);
        var replay=enabled.api().post(path(f),f.school().adminToken(),command(f,key));
        assertThat(replay.status()).as(replay.raw()).isEqualTo(200);
        assertThat(replay.body()).isEqualTo(recovered.body());
        assertThat(allocations(f)).isEqualTo(1);
        assertThat(jdbc.sql("SELECT count(*) FROM obligation_entries WHERE establishment_id=:tenant AND obligation_id=:obligation AND kind='SETTLEMENT'")
                .param("tenant",f.school().id()).param("obligation",f.obligation()).query(Long.class).single()).isEqualTo(1);
        assertThat(enabled.api().get(f.school().path("/payments/cash-receptions/"+f.reception()),f.school().adminToken()).text("unallocatedMinor")).isEqualTo("60000");
        assertThat(history()).isEqualTo(originalHistory);
        Flyway.configure().dataSource(url,TestDatabases.POSTGRES.getUsername(),TestDatabases.POSTGRES.getPassword()).locations("classpath:db/migration").load().validate();
    }
    @Test void deuxInstancesNeCreentQuUneAffectationEtUneEcriture() throws Exception {
        Instance first=start(true,true,false),second=start(false,true,false); Fixture f=setup(first.api()); UUID key=UUID.randomUUID();
        CountDownLatch barrier=new CountDownLatch(1);
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            var a=executor.submit(()->{barrier.await();return first.api().post(path(f),f.school().adminToken(),command(f,key));});
            var b=executor.submit(()->{barrier.await();return second.api().post(path(f),f.school().adminToken(),command(f,key));});
            barrier.countDown();var ra=a.get(15,TimeUnit.SECONDS);var rb=b.get(15,TimeUnit.SECONDS);
            assertThat(List.of(ra.status(),rb.status())).containsExactlyInAnyOrder(201,200);
            assertThat(ra.body()).isEqualTo(rb.body());
        }
        assertThat(allocations(f)).isEqualTo(1);
        assertThat(jdbc.sql("SELECT sum(amount_minor) FROM obligation_entries WHERE obligation_id=:id")
                .param("id",f.obligation()).query(Long.class).single()).isEqualTo(60000);
    }
    long allocations(Fixture f) { return jdbc.sql("SELECT count(*) FROM cash_allocations WHERE establishment_id=:id").param("id",f.school().id()).query(Long.class).single(); }
    List<Map<String,Object>> history() {
        return jdbc.sql("SELECT installed_rank,version,script,checksum,installed_on,success FROM flyway_schema_history ORDER BY installed_rank").query().listOfRows();
    }
    static void await(BooleanSupplier condition) throws Exception {
        long limit=System.nanoTime()+TimeUnit.SECONDS.toNanos(25);
        while(!condition.getAsBoolean()&&System.nanoTime()<limit) Thread.sleep(50);
        assertThat(condition.getAsBoolean()).as("Etat durable attendu").isTrue();
    }
    Instance start(boolean receptions,boolean allocations,boolean drop) throws Exception {
        int port;try(ServerSocket socket=new ServerSocket(0)){port=socket.getLocalPort();}
        Path log=Path.of("target","settlement-probe","instance-"+port+".log"); Files.createDirectories(log.getParent());
        var command=new ArrayList<>(List.of(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-Xmx256m","-XX:TieredStopAtLevel=1",
                "-cp",System.getProperty("surefire.test.class.path",System.getProperty("java.class.path")),CashProbeApplication.class.getName()));
        command.addAll(TestDatabases.applicationArguments(url));
        command.addAll(List.of("--server.port="+port,"--scolapay.payments.cash-receptions-enabled="+receptions,
                "--scolapay.payments.cash-allocations-enabled="+allocations,"--probe.drop-allocation-response="+drop));
        Process p=new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();processes.add(p);
        TestApi api=new TestApi(port,jdbc);await(()->{if(!p.isAlive())throw new AssertionError("Sonde arretee : "+log);return api.healthy();});
        return new Instance(p,api);
    }
}
