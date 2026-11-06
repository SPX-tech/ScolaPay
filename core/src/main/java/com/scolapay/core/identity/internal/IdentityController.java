package com.scolapay.core.identity.internal;

import com.scolapay.core.identity.CurrentActor;
import com.scolapay.core.identity.Identifier;
import com.scolapay.core.shared.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2")
class IdentityController {

    record LoginRequest(@NotBlank @Size(max = 254) String identifier, @NotBlank @Size(max = 200) String password) {}

    record TokenResponse(String accessToken, String tokenType, long expiresInSeconds) {}

    record AcceptInvitationRequest(
            @NotBlank @Size(max = 200) String token,
            @Size(min = 2, max = 200) String fullName,
            @Size(max = 200) String password) {}

    record ChangePasswordRequest(
            @NotBlank @Size(max = 200) String currentPassword,
            @NotBlank @Size(max = 200) String newPassword) {}

    record ProfileResponse(UUID id, String fullName, List<String> contacts, boolean platformOperator) {}

    private final AuthenticationService authentication;
    private final InvitationService invitations;
    private final AccountRepository accounts;
    private final CurrentActor actor;

    IdentityController(
            AuthenticationService authentication,
            InvitationService invitations,
            AccountRepository accounts,
            CurrentActor actor) {
        this.authentication = authentication;
        this.invitations = invitations;
        this.accounts = accounts;
        this.actor = actor;
    }

    @PostMapping("/auth/login")
    TokenResponse login(@Valid @RequestBody LoginRequest request) {
        AuthenticationService.IssuedToken token = authentication.login(request.identifier(), request.password())
                .orElseThrow(() -> ApiException.unauthorized("INVALID_CREDENTIALS", "Identifiants incorrects."));
        return new TokenResponse(token.accessToken(), "Bearer", token.expiresInSeconds());
    }

    @PostMapping("/invitations/accept")
    InvitationService.AcceptanceResult acceptInvitation(@Valid @RequestBody AcceptInvitationRequest request) {
        return invitations.accept(request.token(), request.fullName(), request.password());
    }

    @GetMapping("/me")
    ProfileResponse profile() {
        UUID accountId = actor.requireAccountId();
        AccountRepository.Account account = accounts.find(accountId)
                .orElseThrow(() -> ApiException.unauthorized("AUTHENTICATION_REQUIRED", "Authentification requise."));
        List<String> contacts = accounts.identifiers(accountId).stream().map(Identifier::value).toList();
        return new ProfileResponse(account.id(), account.fullName(), contacts, account.platformOperator());
    }

    @PostMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authentication.changePassword(actor.requireAccountId(), request.currentPassword(), request.newPassword());
    }
}
