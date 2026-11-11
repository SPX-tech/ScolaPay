package com.scolapay.core.payments.internal;

import com.scolapay.core.payments.internal.CashAllocationController.Proof;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/establishments/{establishmentId}/payments/allocations")
class CashAllocationReadController {
    private final CashAllocationService service;
    CashAllocationReadController(CashAllocationService service) { this.service=service; }
    @GetMapping("/{id}")
    Proof find(@PathVariable UUID establishmentId,@PathVariable UUID id) { return Proof.of(service.find(establishmentId,id)); }
    @GetMapping("/by-key/{key}")
    Proof byKey(@PathVariable UUID establishmentId,@PathVariable UUID key) { return Proof.of(service.byKey(establishmentId,key)); }
}
