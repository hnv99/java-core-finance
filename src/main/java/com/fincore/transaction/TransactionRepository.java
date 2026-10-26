package com.fincore.transaction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Append-only ledger. There is deliberately no update or delete operation.
 * Methods are {@code synchronized} because the End-of-Day batch posts from several threads.
 */
public class TransactionRepository {

    private final List<Transaction> ledger = new ArrayList<>();

    public synchronized void append(Transaction transaction) {
        ledger.add(transaction);
    }

    public synchronized void appendAll(Collection<Transaction> transactions) {
        ledger.addAll(transactions);
    }

    public synchronized List<Transaction> findAll() {
        return List.copyOf(ledger);
    }

    /** Entries of one account in posting order. */
    public synchronized List<Transaction> findByAccount(String accountNumber) {
        return ledger.stream()
                .filter(t -> t.accountNumber().equals(accountNumber))
                .toList();
    }

    public synchronized int count() {
        return ledger.size();
    }
}
