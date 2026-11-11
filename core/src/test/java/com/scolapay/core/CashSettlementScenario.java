package com.scolapay.core;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;

@TestPropertySource(properties={"scolapay.payments.cash-receptions-enabled=true","scolapay.payments.cash-allocations-enabled=true"})
abstract class CashSettlementScenario extends BillingScenarioTest {
    UUID receive(Tenant tenant,String actor,long amount) {
        Response response=post(tenant.path("/payments/cash-receptions"),actor,Map.of("idempotencyKey",UUID.randomUUID(),
                "amountReceivedMinor",Long.toString(amount),"changeGivenMinor","0","cashReceivedConfirmed",true));
        assertThat(response.status()).as(response.raw()).isEqualTo(201);
        return response.id("id");
    }
    Response allocate(Tenant tenant,String actor,UUID reception,UUID obligation,UUID key,long amount,long version) {
        return post(tenant.path("/payments/cash-receptions/"+reception+"/allocations"),actor,
                Map.of("idempotencyKey",key,"obligationId",obligation,"amountMinor",Long.toString(amount),"expectedJournalSize",version));
    }
    JsonNode position(Tenant tenant,UUID obligation) {
        Response response=get(tenant.path("/billing/obligations/"+obligation+"/financial-position"),tenant.adminToken());
        assertThat(response.status()).as(response.raw()).isEqualTo(200);
        return response.body();
    }
    long count(String table,Tenant tenant) {
        return jdbc.sql("SELECT count(*) FROM "+table+" WHERE establishment_id=:tenant").param("tenant",tenant.id()).query(Long.class).single();
    }
    School twoTermsSchool() {
        Tenant t=newTenant(); UUID year=openYear(t), level=newLevel(t);
        UUID annual=ok(post(t.path("/billing/periods"),t.adminToken(),Map.of("academicYearId",year,"code","ANN",
                "name","Année","startsOn","2026-09-01","endsOn","2027-07-15")));
        UUID charge=newCharge(t,year,annual,"Scolarité"), catalog=newDraft(t,year);
        Map<String,Object> item=new HashMap<>(fee(charge,"FRAIS",null,100000));
        item.put("installments",List.of(Map.of("dueOn","2026-10-01","amountMinor","40000"),Map.of("dueOn","2099-01-15","amountMinor","60000")));
        addFee(t,catalog,item); publish(t,catalog);
        return new School(t,year,level,annual,charge,catalog);
    }
}
