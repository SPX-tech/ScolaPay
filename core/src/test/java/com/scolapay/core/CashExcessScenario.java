package com.scolapay.core;

import java.util.Map;
import java.util.UUID;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties="scolapay.payments.cash-excess-reservations-enabled=true")
abstract class CashExcessScenario extends CashSettlementScenario {
    Response request(Tenant t,UUID source,UUID key,long amount,String reason) {
        return request(t,t.adminToken(),source,key,amount,reason);
    }
    Response request(Tenant t,String actor,UUID source,UUID key,long amount,String reason) {
        return post(t.path("/payments/cash-receptions/"+source+"/excess-requests"),actor,
                Map.of("idempotencyKey",key,"amountMinor",Long.toString(amount),"reason",reason));
    }
    Response decision(Tenant t,String actor,UUID request,UUID key,String action,String note) {
        return post(t.path("/payments/excess-requests/"+request+"/"+action),actor,
                note==null?Map.of("idempotencyKey",key):Map.of("idempotencyKey",key,"note",note));
    }
    Response excess(Tenant t,UUID id) {return get(t.path("/payments/excess-requests/"+id),t.adminToken());}
    Response funds(Tenant t,UUID source) {return get(t.path("/payments/cash-receptions/"+source),t.adminToken());}
}
