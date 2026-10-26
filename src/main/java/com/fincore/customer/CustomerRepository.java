package com.fincore.customer;

import com.fincore.common.repository.InMemoryRepository;

import java.util.Locale;
import java.util.Optional;

public class CustomerRepository extends InMemoryRepository<Customer, String> {

    public Optional<Customer> findByEmail(String email) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        return store.values().stream()
                .filter(customer -> customer.getEmail().equals(normalized))
                .findFirst();
    }
}
