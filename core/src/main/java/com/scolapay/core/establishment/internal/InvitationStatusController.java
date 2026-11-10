package com.scolapay.core.establishment.internal;

import com.scolapay.core.establishment.EstablishmentAccess;
import com.scolapay.core.establishment.Permission;
import com.scolapay.core.identity.InvitationDelivery;
import com.scolapay.core.identity.Invitations;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Suivi technique sans destinataire ni secret, réservé à la gestion des invitations. */
@RestController
class InvitationStatusController {
    private final EstablishmentAccess access;
    private final Invitations invitations;

    InvitationStatusController(EstablishmentAccess access, Invitations invitations) {
        this.access = access;
        this.invitations = invitations;
    }

    @GetMapping("/api/v2/establishments/{establishmentId}/invitations/{invitationId}/delivery")
    InvitationDelivery.Report status(@PathVariable UUID establishmentId, @PathVariable UUID invitationId) {
        access.require(establishmentId, Permission.MEMBERS_MANAGE);
        return invitations.deliveryStatus(establishmentId, invitationId);
    }
}
