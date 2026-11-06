package com.scolapay.core.identity;

import com.scolapay.core.identity.internal.AccountRepository;
import com.scolapay.core.shared.ApiException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/** Le compte à l'origine de la requête, tel qu'établi par le jeton signé. */
@Component
public class CurrentActor {

    private final AccountRepository accounts;

    public CurrentActor(AccountRepository accounts) {
        this.accounts = accounts;
    }

    public Optional<UUID> accountId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token && token.isAuthenticated()) {
            return Optional.of(UUID.fromString(token.getName()));
        }
        return Optional.empty();
    }

    public UUID requireAccountId() {
        return accountId().orElseThrow(() -> ApiException.unauthorized("AUTHENTICATION_REQUIRED", "Authentification requise."));
    }

    /** Autorise l'administration de la plateforme uniquement. Ne donne aucun accès aux données d'un établissement. */
    public UUID requireOperator() {
        UUID accountId = requireAccountId();
        boolean operator = accounts.find(accountId).map(account -> account.platformOperator()).orElse(false);
        if (!operator) {
            throw ApiException.forbidden("OPERATOR_REQUIRED", "Réservé aux opérateurs de la plateforme.");
        }
        return accountId;
    }
}
