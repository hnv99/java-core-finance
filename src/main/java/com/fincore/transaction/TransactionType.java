package com.fincore.transaction;

import com.fincore.common.Money;

/** Kinds of ledger entries. Each type knows whether it adds to or subtracts from the balance. */
public enum TransactionType {

    DEPOSIT(Direction.CREDIT, true),
    WITHDRAWAL(Direction.DEBIT, true),
    TRANSFER_IN(Direction.CREDIT, false),
    TRANSFER_OUT(Direction.DEBIT, false),
    INTEREST(Direction.CREDIT, false),
    FEE(Direction.DEBIT, false);

    /** Nested enum: only meaningful in the context of a transaction type. */
    public enum Direction {
        CREDIT, DEBIT
    }

    private final Direction direction;
    private final boolean cash;

    TransactionType(Direction direction, boolean cash) {
        this.direction = direction;
        this.cash = cash;
    }

    public Direction direction() {
        return direction;
    }

    public boolean isCredit() {
        return direction == Direction.CREDIT;
    }

    /** Cash movements are subject to anti-money-laundering reporting. */
    public boolean isCash() {
        return cash;
    }

    /** Amount with sign as it affects the balance: positive for credits, negative for debits. */
    public Money signed(Money amount) {
        return isCredit() ? amount : amount.negate();
    }
}
