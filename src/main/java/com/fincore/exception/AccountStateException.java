package com.fincore.exception;

/** Thrown when an operation is not allowed in the account's current status or product type. */
public class AccountStateException extends BankingException {

    public AccountStateException(String message) {
        super(message);
    }
}
