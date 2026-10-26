package com.fincore.account;

import com.fincore.common.Identifiable;
import com.fincore.common.Money;
import com.fincore.exception.AccountStateException;
import com.fincore.exception.BankingException;
import com.fincore.exception.InvalidTransactionException;

import java.time.LocalDate;
import java.util.Currency;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Base class of every account product.
 * <ul>
 *   <li><b>Abstraction</b>: the class is abstract, a plain "account" cannot be opened.</li>
 *   <li><b>Encapsulation</b>: the balance is private and has no setter; it only changes
 *       through operations that enforce the business rules.</li>
 *   <li><b>Inheritance / sealed</b>: only the permitted products may extend it, which lets
 *       {@code switch} over accounts be checked for exhaustiveness by the compiler.</li>
 *   <li><b>Polymorphism / Template Method</b>: {@link #withdraw(Money)} defines the fixed
 *       algorithm, subclasses plug in their own rules via {@link #validateWithdrawal(Money)}.</li>
 * </ul>
 */
public abstract sealed class Account implements Identifiable<String>
        permits CheckingAccount, SavingsAccount, TermDepositAccount {

    private final String accountNumber;
    private final String ownerId;
    private final Currency currency;
    private final LocalDate openedOn;
    private final ReentrantLock lock = new ReentrantLock();
    private Money balance;
    private AccountStatus status;

    protected Account(String accountNumber, String ownerId, Currency currency, LocalDate openedOn,
                      Money balance, AccountStatus status) {
        this.accountNumber = Objects.requireNonNull(accountNumber, "accountNumber must not be null");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId must not be null");
        this.currency = Objects.requireNonNull(currency, "currency must not be null");
        this.openedOn = Objects.requireNonNull(openedOn, "openedOn must not be null");
        this.balance = Objects.requireNonNull(balance, "balance must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        if (!balance.currency().equals(currency)) {
            throw new IllegalArgumentException("Balance currency does not match account currency");
        }
    }

    public abstract AccountType getType();

    /** Funds the customer can withdraw right now. Products override this (overdraft, minimum balance). */
    public Money getAvailableBalance() {
        return balance;
    }

    /** Customer-initiated credit (cash deposit or incoming transfer). */
    public final void deposit(Money amount) throws BankingException {
        validateAmount(amount);
        if (!status.allowsCredit()) {
            throw new AccountStateException("Account %s is %s and cannot receive funds".formatted(accountNumber, status));
        }
        validateDeposit(amount);
        balance = balance.plus(amount);
    }

    /** Customer-initiated debit. {@code final}: subclasses customize the rules, not the algorithm. */
    public final void withdraw(Money amount) throws BankingException {
        validateAmount(amount);
        if (!status.allowsDebit()) {
            throw new AccountStateException("Account %s is %s and cannot be debited".formatted(accountNumber, status));
        }
        validateWithdrawal(amount);
        balance = balance.minus(amount);
        afterWithdrawal(amount);
    }

    /**
     * Bank-initiated credit such as interest. Product rules (e.g. term deposit restrictions)
     * do not apply; the account only must not be closed.
     */
    public final void postSystemCredit(Money amount) throws AccountStateException {
        requireSystemPostingAllowed(amount);
        balance = balance.plus(amount);
    }

    /**
     * Bank-initiated debit such as a monthly fee. It may take a checking account below zero,
     * exactly like real fees can, and it is allowed on frozen accounts.
     */
    public final void postSystemDebit(Money amount) throws AccountStateException {
        requireSystemPostingAllowed(amount);
        balance = balance.minus(amount);
    }

    /** Hook with a default (no extra rules); products override it when needed. */
    protected void validateDeposit(Money amount) throws BankingException {
    }

    /** Every product must define its own withdrawal rules. */
    protected abstract void validateWithdrawal(Money amount) throws BankingException;

    /** Hook called after a successful customer withdrawal. */
    protected void afterWithdrawal(Money amount) {
    }

    public void freeze() throws AccountStateException {
        if (status != AccountStatus.ACTIVE) {
            throw new AccountStateException("Only an ACTIVE account can be frozen (current: %s)".formatted(status));
        }
        status = AccountStatus.FROZEN;
    }

    public void unfreeze() throws AccountStateException {
        if (status != AccountStatus.FROZEN) {
            throw new AccountStateException("Account %s is not frozen".formatted(accountNumber));
        }
        status = AccountStatus.ACTIVE;
    }

    public void close() throws AccountStateException {
        if (status == AccountStatus.CLOSED) {
            throw new AccountStateException("Account %s is already closed".formatted(accountNumber));
        }
        if (!balance.isZero()) {
            throw new AccountStateException("Account %s must have a zero balance to be closed (current: %s)"
                    .formatted(accountNumber, balance));
        }
        status = AccountStatus.CLOSED;
    }

    private void validateAmount(Money amount) throws InvalidTransactionException {
        Objects.requireNonNull(amount, "amount must not be null");
        if (!amount.currency().equals(currency)) {
            throw new InvalidTransactionException("Account %s only accepts %s"
                    .formatted(accountNumber, currency.getCurrencyCode()));
        }
        if (!amount.isPositive()) {
            throw new InvalidTransactionException("Amount must be greater than zero");
        }
    }

    private void requireSystemPostingAllowed(Money amount) throws AccountStateException {
        if (status == AccountStatus.CLOSED) {
            throw new AccountStateException("Account %s is closed".formatted(accountNumber));
        }
        if (!amount.currency().equals(currency) || !amount.isPositive()) {
            throw new IllegalArgumentException("System posting must be a positive "
                    + currency.getCurrencyCode() + " amount");
        }
    }

    /**
     * Lock guarding this account's balance. Services hold it for the whole
     * "change balance + record transaction" sequence so concurrent operations cannot interleave.
     */
    public ReentrantLock getLock() {
        return lock;
    }

    @Override
    public String getId() {
        return accountNumber;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public Currency getCurrency() {
        return currency;
    }

    public LocalDate getOpenedOn() {
        return openedOn;
    }

    public Money getBalance() {
        return balance;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public boolean isClosed() {
        return status == AccountStatus.CLOSED;
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof Account other && accountNumber.equals(other.accountNumber);
    }

    @Override
    public final int hashCode() {
        return accountNumber.hashCode();
    }

    @Override
    public String toString() {
        return "%s[%s, owner=%s, balance=%s, status=%s]"
                .formatted(getClass().getSimpleName(), accountNumber, ownerId, balance, status);
    }
}
