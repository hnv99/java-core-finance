package com.fincore.account;

import com.fincore.common.Money;
import com.fincore.exception.AccountStateException;
import com.fincore.exception.BankingException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Currency;
import java.util.Objects;

/**
 * Fixed-term deposit (certificate of deposit).
 * <ul>
 *   <li>Funded once with the principal, no further deposits.</li>
 *   <li>No partial withdrawals: the whole deposit is settled at maturity or closed early.</li>
 *   <li>At maturity the contract rate applies; early closure earns only the lower demand rate.</li>
 *   <li>Proceeds go to a settlement account chosen at opening.</li>
 * </ul>
 */
public final class TermDepositAccount extends Account implements InterestBearing {

    private final Money principal;
    private final BigDecimal annualRate;
    private final BigDecimal earlyWithdrawalRate;
    private final int termMonths;
    private final LocalDate maturityDate;
    private final String settlementAccountNumber;

    public TermDepositAccount(String accountNumber, String ownerId, Currency currency, LocalDate openedOn,
                              Money balance, AccountStatus status,
                              Money principal, BigDecimal annualRate, BigDecimal earlyWithdrawalRate,
                              int termMonths, LocalDate maturityDate, String settlementAccountNumber) {
        super(accountNumber, ownerId, currency, openedOn, balance, status);
        this.principal = Objects.requireNonNull(principal);
        this.annualRate = Objects.requireNonNull(annualRate);
        this.earlyWithdrawalRate = Objects.requireNonNull(earlyWithdrawalRate);
        this.termMonths = termMonths;
        this.maturityDate = Objects.requireNonNull(maturityDate);
        this.settlementAccountNumber = Objects.requireNonNull(settlementAccountNumber);
    }

    @Override
    public AccountType getType() {
        return AccountType.TERM_DEPOSIT;
    }

    /** Only the single initial funding equal to the principal is accepted. */
    @Override
    protected void validateDeposit(Money amount) throws BankingException {
        if (!getBalance().isZero() || !amount.equals(principal)) {
            throw new AccountStateException("Term deposits accept only one funding deposit equal to the principal");
        }
    }

    @Override
    protected void validateWithdrawal(Money amount) throws BankingException {
        throw new AccountStateException("Partial withdrawals are not allowed on term deposits; close the deposit instead");
    }

    @Override
    public Money getAvailableBalance() {
        return Money.zero(getCurrency());
    }

    @Override
    public BigDecimal getAnnualRate() {
        return annualRate;
    }

    public boolean isMaturedOn(LocalDate date) {
        return !date.isBefore(maturityDate);
    }

    /**
     * Interest due if the deposit is settled on {@code settlementDate}.
     * Interest never runs past maturity because the deposit is not renewed automatically.
     */
    public Money interestDueOn(LocalDate settlementDate) {
        boolean matured = isMaturedOn(settlementDate);
        LocalDate endDate = matured ? maturityDate : settlementDate;
        BigDecimal rate = matured ? annualRate : earlyWithdrawalRate;
        long days = ChronoUnit.DAYS.between(getOpenedOn(), endDate);
        return Money.of(InterestBearing.simpleInterest(principal.amount(), rate, days), getCurrency());
    }

    public Money getPrincipal() {
        return principal;
    }

    public BigDecimal getEarlyWithdrawalRate() {
        return earlyWithdrawalRate;
    }

    public int getTermMonths() {
        return termMonths;
    }

    public LocalDate getMaturityDate() {
        return maturityDate;
    }

    public String getSettlementAccountNumber() {
        return settlementAccountNumber;
    }
}
