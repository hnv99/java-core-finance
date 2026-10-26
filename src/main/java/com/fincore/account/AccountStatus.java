package com.fincore.account;

/**
 * Account lifecycle status. The rules live in the enum itself, so callers ask the
 * status what it allows instead of repeating if/else chains everywhere.
 */
public enum AccountStatus {

    /** Normal operation. */
    ACTIVE(true, true),
    /** Debit block (e.g. suspected fraud, court order): incoming funds are still accepted. */
    FROZEN(true, false),
    /** Terminal state. */
    CLOSED(false, false);

    private final boolean creditAllowed;
    private final boolean debitAllowed;

    AccountStatus(boolean creditAllowed, boolean debitAllowed) {
        this.creditAllowed = creditAllowed;
        this.debitAllowed = debitAllowed;
    }

    public boolean allowsCredit() {
        return creditAllowed;
    }

    public boolean allowsDebit() {
        return debitAllowed;
    }
}
