package com.fincore.transaction;

import com.fincore.common.Money;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable ledger entry.
 * <p>
 * A record is ideal here: a posted transaction must never change. Mistakes are corrected
 * with a new reversing entry, never by editing history.
 *
 * @param amount       always positive; the direction comes from {@link TransactionType}
 * @param balanceAfter account balance right after this entry (running balance on statements)
 * @param valueDate    business date the entry is booked on
 * @param createdAt    wall-clock timestamp, for audit
 * @param reference    links related entries (both legs of a transfer); may be {@code null}
 */
public record Transaction(String id,
                          String accountNumber,
                          TransactionType type,
                          Money amount,
                          Money balanceAfter,
                          LocalDate valueDate,
                          Instant createdAt,
                          String description,
                          String reference) {

    /** Compact canonical constructor: validation and normalization only. */
    public Transaction {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(accountNumber, "accountNumber");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(balanceAfter, "balanceAfter");
        Objects.requireNonNull(valueDate, "valueDate");
        Objects.requireNonNull(createdAt, "createdAt");
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("Transaction amount must be positive");
        }
        description = description == null ? "" : description.strip();
        reference = reference == null || reference.isBlank() ? null : reference;
    }

    public Optional<String> referenceOptional() {
        return Optional.ofNullable(reference);
    }

    public Money signedAmount() {
        return type.signed(amount);
    }
}
