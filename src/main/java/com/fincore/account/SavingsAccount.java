package com.fincore.account;

import com.fincore.common.Money;
import com.fincore.exception.BankingException;
import com.fincore.exception.InsufficientFundsException;
import com.fincore.exception.LimitExceededException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Objects;

/**
 * Demand savings account.
 * <ul>
 *   <li>Interest accrues daily on the end-of-day balance and is credited at month end.</li>
 *   <li>Limited number of withdrawals per month.</li>
 *   <li>A minimum balance must remain on the account.</li>
 * </ul>
 */
public final class SavingsAccount extends Account implements InterestBearing {

    private final BigDecimal annualRate;
    private final Money minimumBalance;
    private final int maxWithdrawalsPerMonth;
    private int withdrawalsThisMonth;
    /** Kept unrounded: rounding every day would lose money over a month. */
    private BigDecimal accruedInterest;

    public SavingsAccount(String accountNumber, String ownerId, Currency currency, LocalDate openedOn,
                          Money balance, AccountStatus status,
                          BigDecimal annualRate, Money minimumBalance, int maxWithdrawalsPerMonth,
                          int withdrawalsThisMonth, BigDecimal accruedInterest) {
        super(accountNumber, ownerId, currency, openedOn, balance, status);
        this.annualRate = Objects.requireNonNull(annualRate);
        this.minimumBalance = Objects.requireNonNull(minimumBalance);
        this.maxWithdrawalsPerMonth = maxWithdrawalsPerMonth;
        this.withdrawalsThisMonth = withdrawalsThisMonth;
        this.accruedInterest = Objects.requireNonNull(accruedInterest);
    }

    @Override
    public AccountType getType() {
        return AccountType.SAVINGS;
    }

    /** Everything above the required minimum balance, never negative. */
    @Override
    public Money getAvailableBalance() {
        Money available = getBalance().minus(minimumBalance);
        return available.isNegative() ? Money.zero(getCurrency()) : available;
    }

    @Override
    protected void validateWithdrawal(Money amount) throws BankingException {
        if (withdrawalsThisMonth >= maxWithdrawalsPerMonth) {
            throw new LimitExceededException("Monthly withdrawal limit of %d reached for account %s"
                    .formatted(maxWithdrawalsPerMonth, getId()));
        }
        Money available = getAvailableBalance();
        if (amount.isGreaterThan(available)) {
            throw new InsufficientFundsException(getId(), amount, available);
        }
    }

    @Override
    protected void afterWithdrawal(Money amount) {
        withdrawalsThisMonth++;
    }

    @Override
    public BigDecimal getAnnualRate() {
        return annualRate;
    }

    /** Adds one day of interest on the current balance. Called once per End-of-Day. */
    public void accrueDailyInterest() {
        if (getBalance().isPositive()) {
            BigDecimal dailyInterest = getBalance().amount().multiply(dailyRate(), PRECISION);
            accruedInterest = accruedInterest.add(dailyInterest);
        }
    }

    /**
     * Removes the whole minor units from the accrued interest and returns them so the
     * caller can post them. Fractions below one minor unit are carried to the next month.
     */
    public Money takeAccruedInterest() {
        BigDecimal payable = accruedInterest.setScale(getCurrency().getDefaultFractionDigits(), RoundingMode.DOWN);
        accruedInterest = accruedInterest.subtract(payable);
        return Money.of(payable, getCurrency());
    }

    public void resetMonthlyCounters() {
        withdrawalsThisMonth = 0;
    }

    public Money getMinimumBalance() {
        return minimumBalance;
    }

    public int getMaxWithdrawalsPerMonth() {
        return maxWithdrawalsPerMonth;
    }

    public int getWithdrawalsThisMonth() {
        return withdrawalsThisMonth;
    }

    public BigDecimal getAccruedInterest() {
        return accruedInterest;
    }
}
