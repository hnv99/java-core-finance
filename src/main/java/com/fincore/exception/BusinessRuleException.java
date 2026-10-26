package com.fincore.exception;

/** Generic violation of a bank policy, e.g. minimum customer age or duplicate email. */
public class BusinessRuleException extends BankingException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
