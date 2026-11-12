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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.simple.JdbcClient;

class CashExcessRecoveryTest {
    final List<Process> processes=new ArrayList<>();
    String url;JdbcClient jdbc;
    record Instance(Process process,TestApi api) {
        void kill() throws Exception {process.destroyForcibly();assertThat(process.waitFor(10,TimeUnit.SECONDS)).isTrue();}
    }
    @BeforeEach void prepare(){url=TestDatabases.createEmpty();jdbc=TestDatabases.client(url);}
    @AfterEach void stop() throws Exception {
        for(Process p:processes){p.destroyForcibly();assertThat(p.waitFor(10,TimeUnit.SECONDS)).isTrue();}
    }
    UUID reception(TestApi api,TestApi.School school) {
        var response=api.post(school.path("/payments/cash-receptions"),school.adminToken(),Map.of("idempotencyKey",UUID.randomUUID(),
                "amountReceivedMinor","20000","changeGivenMinor","0","cashReceivedConfirmed",true));
        assertThat(response.status()).as(response.raw()).isEqualTo(201);return response.id("id");
    }
    Map<String,Object> request(UUID key){return Map.of("idempotencyKey",key,"amountMinor","15000","reason","Réservation technique");}
    String requestPath(TestApi.School school,UUID source){return school.path("/payments/cash-receptions/"+source+"/excess-requests");}
    String director(TestApi api,TestApi.School school){
        String contact=IntegrationTest.uniqueEmail();
        var invitation=api.post(school.path("/invitations"),school.adminToken(),Map.of("contact",contact,"role","DIRECTOR"));
        assertThat(invitation.status()).as(invitation.raw()).isEqualTo(201);
        String token=jdbc.sql("SELECT token FROM test_invitation_deliveries WHERE id=:id").param("id",invitation.id("id")).query(String.class).single();
        assertThat(api.accept(token).status()).isEqualTo(200);return api.login(contact,IntegrationTest.PASSWORD);
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void laPerteDeReponseApresCommitEstRecuperableSansNouvelleReservationNiSortie(boolean approval) throws Exception {
        Instance first=start(true,!approval,approval);var school=first.api().newSchool();UUID source=reception(first.api(),school),key=UUID.randomUUID();
        String path=requestPath(school,source),actor=school.adminToken();Map<String,Object> body=request(key);
        if(approval){
            var created=first.api().post(path,actor,request(UUID.randomUUID()));assertThat(created.status()).as(created.raw()).isEqualTo(201);
            path=school.path("/payments/excess-requests/"+created.id("id")+"/approval");actor=director(first.api(),school);body=Map.of("idempotencyKey",key);
        }
        var installed=history();
        assertThat(installed).extracting(row->row.get("version"))
                .containsExactly("1","2","3","4","5","6","7","7.1","7.2","8","9","10");
        assertThat(installed).allSatisfy(row->{assertThat(row.get("checksum")).isNotNull();assertThat(row.get("success")).isEqualTo(true);});
        final String writePath=path,writer=actor;final Map<String,Object> command=body;
        String table=approval?"cash_excess_decisions":"cash_excess_requests";
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()){
            var pending=executor.submit(()->first.api().post(writePath,writer,command));
            CashSettlementRecoveryTest.await(()->count(table,school.id())==1);first.kill();
            assertThatThrownBy(()->pending.get(10,TimeUnit.SECONDS)).isInstanceOf(java.util.concurrent.ExecutionException.class);
        }
        Instance disabled=start(false,false,false);
        String readPath=school.path("/payments/"+(approval?"excess-decisions":"excess-requests")+"/by-key/"+key);
        var proof=disabled.api().get(readPath,actor);assertThat(proof.status()).as(proof.raw()).isEqualTo(200);
        assertThat(proof.text("restitutedMinor")).isEqualTo("0");
        assertThat(disabled.api().post(path,actor,body).status()).isEqualTo(404);
        assertThat(disabled.api().get("/api/v2/me/students",actor).status()).isEqualTo(404);
        assertThat(history()).isEqualTo(installed);disabled.kill();
        Instance enabled=start(true,false,false);
        var replay=enabled.api().post(path,actor,body);assertThat(replay.status()).as(replay.raw()).isEqualTo(200);
        assertThat(replay.body()).isEqualTo(proof.body());
        assertThat(count("cash_excess_requests",school.id())).isEqualTo(1);
        assertThat(count("cash_excess_decisions",school.id())).isEqualTo(approval?1:0);
        assertThat(jdbc.sql("SELECT count(*) FROM audit_events WHERE establishment_id=:id AND action=:action")
                .param("id",school.id()).param("action",approval?"payments.excess.approved":"payments.excess.requested").query(Long.class).single()).isEqualTo(1);
        var cash=enabled.api().get(school.path("/payments/cash-receptions/"+source),school.adminToken());
        assertThat(cash.text("unallocatedMinor")).isEqualTo("20000");assertThat(cash.text("reservedMinor")).isEqualTo("15000");
        assertThat(history()).isEqualTo(installed);
        Flyway.configure().dataSource(url,TestDatabases.POSTGRES.getUsername(),TestDatabases.POSTGRES.getPassword())
                .locations("classpath:db/migration").load().validate();
    }

    @Test void deuxInstancesRejouentLaMemeDemandeSansDoubleReservation() throws Exception {
        Instance first=start(true,false,false),second=start(true,false,false);var school=first.api().newSchool();
        UUID source=reception(first.api(),school),key=UUID.randomUUID();CountDownLatch barrier=new CountDownLatch(1);
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()){
            var a=executor.submit(()->{barrier.await();return first.api().post(requestPath(school,source),school.adminToken(),request(key));});
            var b=executor.submit(()->{barrier.await();return second.api().post(requestPath(school,source),school.adminToken(),request(key));});
            barrier.countDown();var ra=a.get(15,TimeUnit.SECONDS);var rb=b.get(15,TimeUnit.SECONDS);
            assertThat(List.of(ra.status(),rb.status())).containsExactlyInAnyOrder(201,200);assertThat(ra.body()).isEqualTo(rb.body());
        }
        assertThat(count("cash_excess_requests",school.id())).isEqualTo(1);
    }

    @Test void deuxInstancesNePeuventAffecterEtReserverLeMemeBudget() throws Exception {
        Instance first=start(true,false,false),second=start(true,false,false);var school=first.api().newSchool();
        UUID obligation=invoice(first.api(),school),source=reception(first.api(),school);CountDownLatch barrier=new CountDownLatch(1);
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()){
            var a=executor.submit(()->{barrier.await();return first.api().post(school.path("/payments/cash-receptions/"+source+"/allocations"),school.adminToken(),
                    Map.of("idempotencyKey",UUID.randomUUID(),"obligationId",obligation,"amountMinor","15000","expectedJournalSize",1));});
            var b=executor.submit(()->{barrier.await();return second.api().post(requestPath(school,source),school.adminToken(),request(UUID.randomUUID()));});
            barrier.countDown();assertThat(List.of(a.get(15,TimeUnit.SECONDS).status(),b.get(15,TimeUnit.SECONDS).status()))
                    .containsExactlyInAnyOrder(201,422);
        }
        var funds=first.api().get(school.path("/payments/cash-receptions/"+source),school.adminToken());
        assertThat(Long.parseLong(funds.text("historicalAllocatedMinor"))+Long.parseLong(funds.text("reservedMinor"))).isEqualTo(15000);
        assertThat(funds.text("availableForAllocationMinor")).isEqualTo("5000");
    }

    UUID invoice(TestApi api,TestApi.School school) throws Exception {
        String actor=school.adminToken();
        var period=api.post(school.path("/billing/periods"),actor,Map.of("academicYearId",school.yearId(),"code","ANNEE","name","Année",
                "startsOn","2026-09-01","endsOn","2027-07-15"));assertThat(period.status()).as(period.raw()).isEqualTo(201);
        var fee=api.post(school.path("/billing/fee-definitions"),actor,Map.of("academicYearId",school.yearId(),"billingPeriodId",period.id("id"),"name","Scolarité"));
        var catalog=api.post(school.path("/billing/fee-catalogs"),actor,Map.of("academicYearId",school.yearId()));
        assertThat(fee.status()).as(fee.raw()).isEqualTo(201);assertThat(catalog.status()).as(catalog.raw()).isEqualTo(201);
        assertThat(api.post(school.path("/billing/fee-catalogs/"+catalog.id("id")+"/items"),actor,
                BillingScenarioTest.fee(fee.id("id"),"SCOLARITE",null,100000)).status()).isEqualTo(201);
        assertThat(api.post(school.path("/billing/fee-catalogs/"+catalog.id("id")+"/publish"),actor,Map.of()).status()).isEqualTo(200);
        UUID student=school.newStudent();
        assertThat(api.post(school.path("/students/"+student+"/enrollments"),actor,Map.of("academicYearId",school.yearId(),
                "academicUnitId",school.levelId(),"confirmedFeeCatalogVersionId",catalog.id("id"))).status()).isEqualTo(201);
        CashSettlementRecoveryTest.await(()->jdbc.sql("SELECT count(*) FROM obligations WHERE establishment_id=:tenant AND student_record_id=:student")
                .param("tenant",school.id()).param("student",student).query(Long.class).single()==1);
        return jdbc.sql("SELECT id FROM obligations WHERE establishment_id=:tenant AND student_record_id=:student")
                .param("tenant",school.id()).param("student",student).query(UUID.class).single();
    }
    long count(String table,UUID school){return jdbc.sql("SELECT count(*) FROM "+table+" WHERE establishment_id=:id").param("id",school).query(Long.class).single();}
    List<Map<String,Object>> history(){return jdbc.sql("SELECT installed_rank,version,script,checksum,installed_on,success FROM flyway_schema_history ORDER BY installed_rank").query().listOfRows();}
    Instance start(boolean enabled,boolean dropRequest,boolean dropDecision) throws Exception {
        int port;try(ServerSocket socket=new ServerSocket(0)){port=socket.getLocalPort();}
        Path log=Path.of("target","excess-probe","instance-"+port+".log");Files.createDirectories(log.getParent());
        var command=new ArrayList<>(List.of(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-Xmx256m","-XX:TieredStopAtLevel=1",
                "-cp",System.getProperty("surefire.test.class.path",System.getProperty("java.class.path")),CashProbeApplication.class.getName()));
        command.addAll(TestDatabases.applicationArguments(url));
        command.addAll(List.of("--server.port="+port,"--scolapay.payments.cash-receptions-enabled=true","--scolapay.payments.cash-allocations-enabled=true",
                "--scolapay.payments.cash-excess-reservations-enabled="+enabled,"--probe.drop-excess-request-response="+dropRequest,
                "--probe.drop-excess-decision-response="+dropDecision));
        Process p=new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();processes.add(p);
        TestApi api=new TestApi(port,jdbc);CashSettlementRecoveryTest.await(()->{if(!p.isAlive())throw new AssertionError("Sonde arrêtée : "+log);return api.healthy();});
        return new Instance(p,api);
    }
}
