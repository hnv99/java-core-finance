package com.fincore.exception;

/** Thrown when a transaction request itself is invalid (non-positive amount, wrong currency, ...). */
public class InvalidTransactionException extends BankingException {

    public InvalidTransactionException(String message) {
        super(message);
    }
}
