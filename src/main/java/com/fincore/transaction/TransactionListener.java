package com.fincore.transaction;

/**
 * Observer notified after every posted transaction (notifications, fraud checks,
 * compliance reporting). A functional interface, so a lambda can be registered too.
 */
@FunctionalInterface
public interface TransactionListener {

    void onTransaction(Transaction transaction);
}
