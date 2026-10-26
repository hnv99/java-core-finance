package com.fincore.exception;

/** Thrown when a product limit (for example monthly withdrawal count) would be exceeded. */
public class LimitExceededException extends BankingException {

    public LimitExceededException(String message) {
        super(message);
    }
}
