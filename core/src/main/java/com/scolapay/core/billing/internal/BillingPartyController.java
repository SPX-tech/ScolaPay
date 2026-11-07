package com.scolapay.core.billing.internal;

import com.scolapay.core.billing.internal.BillingPartyRepository.Designation;
import com.scolapay.core.billing.internal.BillingPartyRepository.Party;
import com.scolapay.core.billing.internal.ObligationController.ObligationView;
import com.scolapay.core.shared.PageQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/establishments/{establishmentId}/billing")
class BillingPartyController {

    private static final String ROLE = "^(CONTRACTUAL_DEBTOR|FINANCIAL_RESPONSIBLE|VOLUNTARY_CONTRIBUTOR)$";

    record PartyRequest(
            @NotNull @Pattern(regexp = "^(PERSON|ORGANIZATION)$") String kind,
            @NotBlank @Size(min = 2, max = 200) String displayName,
            @Size(min = 1, max = 100) String identifier,
            UUID grantId) {}

    record DesignationRequest(@NotNull UUID partyId, @NotNull @Pattern(regexp = ROLE) String role) {}

    record StudentAssignmentRequest(@NotNull UUID academicYearId, @NotNull UUID partyId, @NotNull @Pattern(regexp = ROLE) String role) {}

    record ConfirmationRequest(@NotBlank @Size(max = 200) String commitmentReference) {}

    record EndRequest(@NotBlank @Size(min = 3, max = 500) String reason) {}

    record PartyView(UUID id, String kind, String displayName, String identifier, boolean hasAccount, Instant createdAt) {

        static PartyView of(Party party) {
            return new PartyView(party.id(), party.kind(), party.displayName(), party.identifier(), party.accountId() != null, party.createdAt());
        }
    }

    record DesignationView(
            UUID id,
            UUID obligationId,
            UUID partyId,
            String partyName,
            String partyKind,
            String role,
            String debtorStatus,
            String commitmentReference,
            Instant designatedAt,
            UUID designatedBy,
            Instant confirmedAt,
            UUID confirmedBy,
            Instant endedAt,
            UUID endedBy,
            String endReason) {

        static DesignationView of(Designation designation) {
            return new DesignationView(
                    designation.id(), designation.obligationId(), designation.partyId(), designation.partyName(), designation.partyKind(),
                    designation.role(), designation.debtorStatus(), designation.commitmentReference(), designation.designatedAt(), designation.designatedBy(),
                    designation.confirmedAt(), designation.confirmedBy(), designation.endedAt(), designation.endedBy(), designation.endReason());
        }
    }

    record ByDebtorStateView(long total, List<ObligationView> obligations) {}

    private final BillingPartyService parties;

    BillingPartyController(BillingPartyService parties) {
        this.parties = parties;
    }

    @PostMapping("/parties")
    @ResponseStatus(HttpStatus.CREATED)
    PartyView createParty(@PathVariable UUID establishmentId, @Valid @RequestBody PartyRequest request) {
        return PartyView.of(parties.createParty(
                establishmentId, request.kind(), request.displayName().trim(), request.identifier(), request.grantId()));
    }

    @GetMapping("/parties")
    List<PartyView> listParties(
            @PathVariable UUID establishmentId, @RequestParam(required = false) Integer limit, @RequestParam(required = false) Integer offset) {
        return parties.listParties(establishmentId, PageQuery.of(limit, offset)).stream().map(PartyView::of).toList();
    }

    /** Idempotent : 201 à la création, 200 si cette personne tient déjà ce rôle. */
    @PostMapping("/obligations/{obligationId}/parties")
    ResponseEntity<DesignationView> designate(
            @PathVariable UUID establishmentId, @PathVariable UUID obligationId, @Valid @RequestBody DesignationRequest request) {
        BillingPartyService.Designated designated = parties.designate(establishmentId, obligationId, request.partyId(), request.role());
        return ResponseEntity.status(designated.created() ? HttpStatus.CREATED : HttpStatus.OK).body(DesignationView.of(designated.designation()));
    }

    @GetMapping("/obligations/{obligationId}/parties")
    List<DesignationView> designations(@PathVariable UUID establishmentId, @PathVariable UUID obligationId) {
        return parties.designationsOf(establishmentId, obligationId).stream().map(DesignationView::of).toList();
    }

    @PostMapping("/students/{studentId}/parties")
    BillingPartyService.StudentAssignment assignToStudent(
            @PathVariable UUID establishmentId, @PathVariable UUID studentId, @Valid @RequestBody StudentAssignmentRequest request) {
        return parties.assignToStudent(establishmentId, studentId, request.academicYearId(), request.partyId(), request.role());
    }

    @PostMapping("/obligation-parties/{designationId}/confirmation")
    DesignationView confirm(
            @PathVariable UUID establishmentId, @PathVariable UUID designationId, @Valid @RequestBody ConfirmationRequest request) {
        return DesignationView.of(parties.confirmDebtor(establishmentId, designationId, request.commitmentReference().trim()));
    }

    @PostMapping("/obligation-parties/{designationId}/end")
    DesignationView end(@PathVariable UUID establishmentId, @PathVariable UUID designationId, @Valid @RequestBody EndRequest request) {
        return DesignationView.of(parties.endDesignation(establishmentId, designationId, request.reason().trim()));
    }

    /** NONE : encore dues sans redevable. UNCONFIRMED : leur redevable n'est pas confirmé. */
    @GetMapping("/obligations-by-debtor-state")
    ByDebtorStateView byDebtorState(
            @PathVariable UUID establishmentId,
            @RequestParam UUID academicYearId,
            @RequestParam @Pattern(regexp = "^(NONE|UNCONFIRMED)$") String state,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset) {
        BillingPartyService.ByDebtorState result = parties.byDebtorState(establishmentId, academicYearId, state, PageQuery.of(limit, offset));
        return new ByDebtorStateView(result.total(), result.obligations().stream().map(ObligationView::of).toList());
    }
}
