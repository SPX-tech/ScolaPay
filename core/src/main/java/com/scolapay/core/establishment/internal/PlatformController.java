package com.scolapay.core.establishment.internal;

import com.scolapay.core.establishment.Establishments.Establishment;
import com.scolapay.core.identity.Invitations;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Surface des opérateurs : administre la plateforme, sans aucune route vers les dossiers. */
@RestController
@RequestMapping("/api/v2/platform/establishments")
class PlatformController {

    record CreateRequest(
            @NotBlank @Pattern(regexp = "^[A-Z0-9][A-Z0-9-]{1,30}[A-Z0-9]$") String code,
            @NotBlank @Size(min = 2, max = 200) String name,
            @NotBlank @Pattern(regexp = "^(SCHOOL|UNIVERSITY|TRAINING_CENTER|OTHER)$") String kind,
            @NotBlank @Size(max = 64) String timezone,
            @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency,
            @NotBlank @Pattern(regexp = "^[A-Z]{2}$") String countryCode,
            @NotBlank @Size(max = 254) String firstAdminContact) {}

    record CreateResponse(Establishment establishment, Invitations.Issued firstAdminInvitation) {}

    record AdminInvitationRequest(@NotBlank @Size(max = 254) String contact) {}

    record StatusRequest(@NotBlank @Size(max = 20) String status) {}

    private final EstablishmentService establishments;

    PlatformController(EstablishmentService establishments) {
        this.establishments = establishments;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CreateResponse create(@Valid @RequestBody CreateRequest request) {
        EstablishmentService.Created created = establishments.create(new EstablishmentService.NewEstablishment(
                request.code(),
                request.name(),
                request.kind(),
                request.timezone(),
                request.currency(),
                request.countryCode(),
                request.firstAdminContact()));
        return new CreateResponse(created.establishment(), created.firstAdminInvitation());
    }

    @PostMapping("/{id}/admin-invitations")
    @ResponseStatus(HttpStatus.CREATED)
    Invitations.Issued reissueAdminInvitation(@PathVariable UUID id, @Valid @RequestBody AdminInvitationRequest request) {
        return establishments.reissueAdminInvitation(id, request.contact());
    }

    @PostMapping("/{id}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void setStatus(@PathVariable UUID id, @Valid @RequestBody StatusRequest request) {
        establishments.setStatus(id, request.status());
    }
}
