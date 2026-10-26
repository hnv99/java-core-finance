package com.fincore.account;

import com.fincore.common.Money;
import com.fincore.exception.BankingException;
import com.fincore.exception.InsufficientFundsException;

import java.time.LocalDate;
import java.util.Currency;
import java.util.Objects;

/**
 * Everyday transactional account: unlimited withdrawals, an approved overdraft line,
 * and a monthly maintenance fee waived when the balance is high enough.
 */
public final class CheckingAccount extends Account {

    private final Money overdraftLimit;
    private final Money monthlyFee;
    private final Money feeWaiverBalance;

    public CheckingAccount(String accountNumber, String ownerId, Currency currency, LocalDate openedOn,
                           Money balance, AccountStatus status,
                           Money overdraftLimit, Money monthlyFee, Money feeWaiverBalance) {
        super(accountNumber, ownerId, currency, openedOn, balance, status);
        this.overdraftLimit = Objects.requireNonNull(overdraftLimit);
        this.monthlyFee = Objects.requireNonNull(monthlyFee);
        this.feeWaiverBalance = Objects.requireNonNull(feeWaiverBalance);
    }

    @Override
    public AccountType getType() {
        return AccountType.CHECKING;
    }

    /** Balance plus the overdraft line. */
    @Override
    public Money getAvailableBalance() {
        return getBalance().plus(overdraftLimit);
    }

    @Override
    protected void validateWithdrawal(Money amount) throws BankingException {
        Money available = getAvailableBalance();
        if (amount.isGreaterThan(available)) {
            throw new InsufficientFundsException(getId(), amount, available);
        }
    }

    /** Fee charged at month end; zero when the balance meets the waiver threshold. */
    public Money monthlyFeeDue() {
        return getBalance().compareTo(feeWaiverBalance) >= 0 ? Money.zero(getCurrency()) : monthlyFee;
    }

    public boolean isOverdrawn() {
        return getBalance().isNegative();
    }

    public Money getOverdraftLimit() {
        return overdraftLimit;
    }

    public Money getMonthlyFee() {
        return monthlyFee;
    }

    public Money getFeeWaiverBalance() {
        return feeWaiverBalance;
    }
}
