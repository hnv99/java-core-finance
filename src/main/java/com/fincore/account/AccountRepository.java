package com.fincore.account;

import com.fincore.common.repository.InMemoryRepository;

import java.util.Comparator;
import java.util.List;

public class AccountRepository extends InMemoryRepository<Account, String> {

    public List<Account> findByOwner(String ownerId) {
        return store.values().stream()
                .filter(account -> account.getOwnerId().equals(ownerId))
                .sorted(Comparator.comparing(Account::getId))
                .toList();
    }

    /**
     * Generic method: returns accounts of one concrete product type already cast,
     * e.g. {@code findAllOfType(SavingsAccount.class)} returns {@code List<SavingsAccount>}.
     */
    public <T extends Account> List<T> findAllOfType(Class<T> type) {
        return store.values().stream()
                .filter(type::isInstance)
                .map(type::cast)
                .sorted(Comparator.comparing(Account::getId))
                .toList();
    }
}
