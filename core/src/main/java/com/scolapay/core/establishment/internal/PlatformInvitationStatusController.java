package com.scolapay.core.establishment.internal;

import com.scolapay.core.identity.InvitationDelivery;
import com.scolapay.core.identity.Invitations;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
class PlatformInvitationStatusController {
    private final Invitations invitations;

    PlatformInvitationStatusController(Invitations invitations) { this.invitations = invitations; }

    @GetMapping("/api/v2/platform/establishments/{establishmentId}/admin-invitations/{invitationId}/delivery")
    InvitationDelivery.Report status(@PathVariable UUID establishmentId, @PathVariable UUID invitationId) {
        return invitations.platformDeliveryStatus(establishmentId, invitationId);
    }
}
