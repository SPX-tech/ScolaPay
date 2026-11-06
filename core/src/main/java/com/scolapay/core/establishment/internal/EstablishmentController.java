package com.scolapay.core.establishment.internal;

import com.scolapay.core.audit.AuditTrail;
import com.scolapay.core.establishment.EstablishmentAccess;
import com.scolapay.core.establishment.Establishments.Establishment;
import com.scolapay.core.establishment.Permission;
import com.scolapay.core.establishment.StaffRole;
import com.scolapay.core.identity.Invitations;
import com.scolapay.core.shared.PageQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class EstablishmentController {

    private static final String BASE = "/api/v2/establishments/{establishmentId}";

    record SettingsRequest(@NotBlank @Size(min = 2, max = 200) String name) {}

    record InviteStaffRequest(@NotBlank @Size(max = 254) String contact, @NotNull StaffRole role) {}

    record UpdateMemberRequest(@Size(max = 20) String status, Set<StaffRole> roles) {}

    record AffiliationResponse(Establishment establishment, Set<StaffRole> roles) {}

    private final EstablishmentService establishments;
    private final MembershipService memberships;
    private final EstablishmentAccess access;
    private final AuditTrail audit;

    EstablishmentController(
            EstablishmentService establishments,
            MembershipService memberships,
            EstablishmentAccess access,
            AuditTrail audit) {
        this.establishments = establishments;
        this.memberships = memberships;
        this.access = access;
        this.audit = audit;
    }

    @GetMapping("/api/v2/me/affiliations")
    List<AffiliationResponse> myAffiliations() {
        return memberships.myAffiliations().stream()
                .map(affiliation -> new AffiliationResponse(affiliation.establishment(), affiliation.roles()))
                .toList();
    }

    @GetMapping(BASE)
    Establishment read(@PathVariable UUID establishmentId) {
        return establishments.read(establishmentId);
    }

    @PatchMapping(BASE)
    Establishment updateSettings(@PathVariable UUID establishmentId, @Valid @RequestBody SettingsRequest request) {
        return establishments.updateSettings(establishmentId, request.name());
    }

    @GetMapping(BASE + "/members")
    List<MembershipService.Member> members(
            @PathVariable UUID establishmentId,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset) {
        return memberships.list(establishmentId, PageQuery.of(limit, offset));
    }

    @PatchMapping(BASE + "/members/{membershipId}")
    MembershipService.Member updateMember(
            @PathVariable UUID establishmentId,
            @PathVariable UUID membershipId,
            @Valid @RequestBody UpdateMemberRequest request) {
        return memberships.update(establishmentId, membershipId, request.status(), request.roles());
    }

    @PostMapping(BASE + "/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    Invitations.Issued inviteStaff(@PathVariable UUID establishmentId, @Valid @RequestBody InviteStaffRequest request) {
        return memberships.inviteStaff(establishmentId, request.contact(), request.role());
    }

    @DeleteMapping(BASE + "/invitations/{invitationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revokeInvitation(@PathVariable UUID establishmentId, @PathVariable UUID invitationId) {
        memberships.revokeInvitation(establishmentId, invitationId);
    }

    @GetMapping(BASE + "/audit-events")
    List<AuditTrail.Event> auditEvents(
            @PathVariable UUID establishmentId,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset) {
        access.require(establishmentId, Permission.AUDIT_READ);
        return audit.forEstablishment(establishmentId, PageQuery.of(limit, offset));
    }
}
