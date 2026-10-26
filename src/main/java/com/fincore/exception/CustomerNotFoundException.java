package com.fincore.exception;

public class CustomerNotFoundException extends BankingException {

    public CustomerNotFoundException(String customerId) {
        super("Customer not found: " + customerId);
    }
}
