package com.scolapay.core.payments.internal;

import java.time.Instant;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/establishments/{establishmentId}/payments")
class CashIdentityReadController {
    record RemitterView(UUID receptionId,String state,String fullName,String identificationDetails,String statementReference,
            Instant recordedAt,UUID recordedByAccountId,boolean physicalHandoverAvailable) {
        static RemitterView of(UUID source,CashIdentityService.Read read){
            var r=read.remitter();var d=read.details();
            return new RemitterView(source,read.state(),d==null?null:d.fullName(),d==null?null:d.identificationDetails(),
                    r==null?null:r.statementReference(),r==null?null:r.recordedAt(),r==null?null:r.recordedBy(),false);
        }
    }
    private final CashIdentityService service;
    CashIdentityReadController(CashIdentityService service){this.service=service;}
    @GetMapping("/cash-receptions/{source}/remitter")
    RemitterView read(@PathVariable UUID establishmentId,@PathVariable UUID source){return RemitterView.of(source,service.read(establishmentId,source));}
}
