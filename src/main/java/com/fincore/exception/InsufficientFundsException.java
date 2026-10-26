package com.fincore.exception;

import com.fincore.common.Money;

/** Thrown when a debit exceeds the funds available on an account. */
public class InsufficientFundsException extends BankingException {

    private final String accountNumber;
    private final Money requested;
    private final Money available;

    public InsufficientFundsException(String accountNumber, Money requested, Money available) {
        super("Insufficient funds on account %s: requested %s, available %s"
                .formatted(accountNumber, requested, available));
        this.accountNumber = accountNumber;
        this.requested = requested;
        this.available = available;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public Money getRequested() {
        return requested;
    }

    public Money getAvailable() {
        return available;
    }
}
