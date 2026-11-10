package com.scolapay.core.billing.internal;

import com.scolapay.core.billing.internal.BillingPartyController.PartyView;
import com.scolapay.core.billing.internal.BillingPartyRepository.Correction;
import com.scolapay.core.shared.PageQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/establishments/{establishmentId}/billing/parties/{partyId}")
class PartyCorrectionController {
    record CorrectionRequest(@NotNull @Min(0) Integer expectedRevision,
            @NotBlank @Size(min=2,max=200) String displayName, @Size(min=1,max=100) String identifier,
            @NotBlank @Size(min=3,max=500) String reason) {}

    private final PartyCorrectionService corrections;
    PartyCorrectionController(PartyCorrectionService corrections) { this.corrections=corrections; }

    @GetMapping
    PartyView read(@PathVariable UUID establishmentId,@PathVariable UUID partyId) {
        return PartyView.of(corrections.read(establishmentId,partyId));
    }

    @PostMapping("/corrections")
    @ResponseStatus(HttpStatus.CREATED)
    Correction correct(@PathVariable UUID establishmentId,@PathVariable UUID partyId,@Valid @RequestBody CorrectionRequest request) {
        return corrections.correct(establishmentId,partyId,request.expectedRevision(),request.displayName(),request.identifier(),request.reason());
    }

    @GetMapping("/corrections")
    List<Correction> history(@PathVariable UUID establishmentId,@PathVariable UUID partyId,
            @RequestParam(required=false) Integer limit,@RequestParam(required=false) Integer offset) {
        return corrections.history(establishmentId,partyId,PageQuery.of(limit,offset));
    }
}
