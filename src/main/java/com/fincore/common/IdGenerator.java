package com.fincore.common;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Generates business identifiers.
 * <p>
 * {@link AtomicLong} makes the sequences thread-safe without explicit locking.
 * Sequences are persisted so numbers are never reused after a restart.
 */
public final class IdGenerator {

    private final AtomicLong customerSequence = new AtomicLong();
    private final AtomicLong accountSequence = new AtomicLong();

    /** Customer id such as {@code CUS000001}. */
    public String nextCustomerId() {
        return "CUS%06d".formatted(customerSequence.incrementAndGet());
    }

    /** Account number such as {@code 1000000001}: a 2-digit product prefix plus an 8-digit sequence. */
    public String nextAccountNumber(String productPrefix) {
        return productPrefix + "%08d".formatted(accountSequence.incrementAndGet());
    }

    /** Internal unique transaction id. */
    public String nextTransactionId() {
        return UUID.randomUUID().toString();
    }

    /** Short reference shown to customers and shared by all legs of one operation, e.g. a transfer. */
    public String nextReference() {
        return "FT" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
    }

    public long currentCustomerSequence() {
        return customerSequence.get();
    }

    public long currentAccountSequence() {
        return accountSequence.get();
    }

    public void restore(long customerSeq, long accountSeq) {
        customerSequence.set(customerSeq);
        accountSequence.set(accountSeq);
    }
}
