package com.scolapay.core.identity.internal;

import com.scolapay.core.identity.AccountDirectory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountDirectoryService implements AccountDirectory {

    private final AccountRepository accounts;

    AccountDirectoryService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, AccountSummary> findAll(Collection<UUID> accountIds) {
        if (accountIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> names = new LinkedHashMap<>();
        Map<UUID, List<String>> contacts = new LinkedHashMap<>();
        for (AccountRepository.IdentifiedAccount row : accounts.findAll(accountIds)) {
            names.put(row.id(), row.fullName());
            List<String> list = contacts.computeIfAbsent(row.id(), id -> new ArrayList<>());
            if (row.contact() != null) {
                list.add(row.contact());
            }
        }
        Map<UUID, AccountSummary> result = new LinkedHashMap<>();
        names.forEach((id, name) -> result.put(id, new AccountSummary(id, name, List.copyOf(contacts.get(id)))));
        return result;
    }
}
