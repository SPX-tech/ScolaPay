package com.scolapay.core.identity.internal;

import com.scolapay.core.audit.AuditTrail;
import com.scolapay.core.identity.Identifier;
import com.scolapay.core.shared.ApiException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticationService {

    public record IssuedToken(String accessToken, long expiresInSeconds) {}

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    private static final int MIN_PASSWORD_LENGTH = 12;
    private static final int MAX_PASSWORD_BYTES = 72;

    private final AccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final SecurityProperties properties;
    private final AuditTrail audit;
    private final Clock clock;
    private final String decoyHash;

    AuthenticationService(
            AccountRepository accounts,
            PasswordEncoder passwordEncoder,
            JwtEncoder jwtEncoder,
            SecurityProperties properties,
            AuditTrail audit,
            Clock clock) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.audit = audit;
        this.clock = clock;
        this.decoyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /**
     * Renvoie vide au lieu de lever une exception : le compteur d'échecs doit être validé.
     * Identifiant inconnu, mot de passe faux, compte verrouillé ou désactivé sont
     * indiscernables pour l'appelant, y compris en temps de réponse.
     */
    @Transactional
    public Optional<IssuedToken> login(String rawIdentifier, String password) {
        Identifier identifier;
        try {
            identifier = Identifier.parse(rawIdentifier);
        } catch (ApiException invalid) {
            return reject(password);
        }

        Optional<AccountRepository.Account> found = accounts.lockByIdentifier(identifier);
        Instant now = clock.instant();
        if (found.isEmpty()
                || !found.get().active()
                || (found.get().lockedUntil() != null && found.get().lockedUntil().isAfter(now))) {
            return reject(password);
        }

        AccountRepository.Account account = found.get();
        if (passwordEncoder.matches(password, account.passwordHash())) {
            accounts.clearFailedLogins(account.id(), now);
            return Optional.of(issue(account, now));
        }

        int attempts = account.failedLoginAttempts() + 1;
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            accounts.recordFailedLogin(account.id(), 0, now.plus(LOCK_DURATION), now);
            audit.record(null, account.id(), "identity.account.locked", "account", account.id(), Map.of());
        } else {
            accounts.recordFailedLogin(account.id(), attempts, null, now);
        }
        return Optional.empty();
    }

    private Optional<IssuedToken> reject(String password) {
        passwordEncoder.matches(password, decoyHash);
        return Optional.empty();
    }

    private IssuedToken issue(AccountRepository.Account account, Instant now) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(account.id().toString())
                .audience(List.of(SecurityConfiguration.AUDIENCE))
                .issuedAt(now)
                .expiresAt(now.plus(properties.accessTokenTtl()))
                .id(UUID.randomUUID().toString())
                .claim(SecurityConfiguration.TOKEN_VERSION_CLAIM, account.tokenVersion())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, properties.accessTokenTtl().toSeconds());
    }

    @Transactional
    public void changePassword(UUID accountId, String currentPassword, String newPassword) {
        AccountRepository.Account account = accounts.find(accountId)
                .orElseThrow(() -> ApiException.unauthorized("AUTHENTICATION_REQUIRED", "Authentification requise."));
        if (!passwordEncoder.matches(currentPassword, account.passwordHash())) {
            throw ApiException.forbidden("INVALID_CURRENT_PASSWORD", "Le mot de passe actuel est incorrect.");
        }
        validateNewPassword(newPassword);
        accounts.updatePassword(accountId, passwordEncoder.encode(newPassword), clock.instant());
        audit.record(null, accountId, "identity.password.changed", "account", accountId, Map.of());
    }

    public void validateNewPassword(String password) {
        // BCrypt ignorerait silencieusement tout ce qui dépasse 72 octets.
        if (password == null
                || password.length() < MIN_PASSWORD_LENGTH
                || password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw ApiException.invalid(
                    "WEAK_PASSWORD",
                    "Le mot de passe doit contenir au moins " + MIN_PASSWORD_LENGTH + " caractères et au plus "
                            + MAX_PASSWORD_BYTES + " octets.");
        }
    }
}
