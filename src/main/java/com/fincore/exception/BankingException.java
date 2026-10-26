package com.fincore.exception;

/**
 * Base type for business rule violations.
 * <p>
 * It is a <b>checked</b> exception on purpose: callers (the CLI, batch jobs) are forced
 * by the compiler to decide how to handle an expected business failure such as
 * insufficient funds. Programming errors and infrastructure failures use unchecked
 * exceptions instead (see {@link DataAccessException}).
 */
public abstract class BankingException extends Exception {

    protected BankingException(String message) {
        super(message);
    }
}
