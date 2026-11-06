package com.scolapay.core.identity;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Lecture des comptes par les autres modules, sans exposer leurs données d'authentification. */
public interface AccountDirectory {

    record AccountSummary(UUID id, String fullName, List<String> contacts) {}

    Map<UUID, AccountSummary> findAll(Collection<UUID> accountIds);
}
