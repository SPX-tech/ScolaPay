package com.scolapay.core.identity.internal;

import com.scolapay.core.audit.AuditTrail;
import com.scolapay.core.identity.Identifier;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

/**
 * Crée le premier opérateur, sans quoi aucun établissement ne pourrait exister.
 * Aucun identifiant par défaut : sans configuration explicite, rien n'est créé,
 * et un opérateur existant n'est jamais écrasé.
 */
@Component
class BootstrapOperator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapOperator.class);

    private final AccountRepository accounts;
    private final AuthenticationService authentication;
    private final PasswordEncoder passwordEncoder;
    private final AuditTrail audit;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final String contact;
    private final String password;

    BootstrapOperator(
            AccountRepository accounts,
            AuthenticationService authentication,
            PasswordEncoder passwordEncoder,
            AuditTrail audit,
            TransactionTemplate transaction,
            Clock clock,
            @Value("${scolapay.bootstrap.operator-contact:}") String contact,
            @Value("${scolapay.bootstrap.operator-password:}") String password) {
        this.accounts = accounts;
        this.authentication = authentication;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.transaction = transaction;
        this.clock = clock;
        this.contact = contact;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(contact) || !StringUtils.hasText(password) || accounts.operatorExists()) {
            return;
        }
        authentication.validateNewPassword(password);
        Identifier identifier = Identifier.parse(contact);
        transaction.executeWithoutResult(status -> {
            UUID id = UUID.randomUUID();
            Instant now = clock.instant();
            accounts.create(id, "Opérateur de la plateforme", passwordEncoder.encode(password), true, now);
            accounts.addIdentifier(id, identifier, now);
            audit.record(null, id, "identity.operator.bootstrapped", "account", id, Map.of());
        });
        log.info("Premier opérateur de la plateforme créé.");
    }
}
