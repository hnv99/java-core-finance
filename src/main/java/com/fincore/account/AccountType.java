package com.fincore.account;

/** Banking products offered. The prefix becomes the first two digits of the account number. */
public enum AccountType {

    CHECKING("10", "Checking"),
    SAVINGS("20", "Savings"),
    TERM_DEPOSIT("30", "Term deposit");

    private final String numberPrefix;
    private final String displayName;

    AccountType(String numberPrefix, String displayName) {
        this.numberPrefix = numberPrefix;
        this.displayName = displayName;
    }

    public String getNumberPrefix() {
        return numberPrefix;
    }

    public String getDisplayName() {
        return displayName;
    }
}
