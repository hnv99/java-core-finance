package com.fincore.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Currency;
import java.util.Locale;
import java.util.Objects;

/**
 * Immutable monetary amount in a single currency.
 * <p>
 * {@link BigDecimal} is used instead of {@code double} because binary floating point
 * cannot represent most decimal fractions exactly (0.1 + 0.2 != 0.3), which is
 * unacceptable when handling money.
 * <p>
 * The amount is always normalized to the currency's minor unit scale
 * (0 digits for VND, 2 digits for USD), so record-generated {@code equals}
 * behaves correctly ({@code BigDecimal.equals} also compares scale).
 */
public record Money(BigDecimal amount, Currency currency) implements Comparable<Money> {

    /** Banker's rounding minimizes cumulative bias over many rounding operations. */
    public static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;

    public Money {
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        amount = amount.setScale(currency.getDefaultFractionDigits(), ROUNDING);
    }

    public static Money of(BigDecimal amount, Currency currency) {
        return new Money(amount, currency);
    }

    public static Money of(long amount, Currency currency) {
        return new Money(BigDecimal.valueOf(amount), currency);
    }

    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    /**
     * Parses user input strictly: more decimal places than the currency allows are
     * rejected instead of being silently rounded. Thousands separators are accepted.
     */
    public static Money parse(String text, Currency currency) {
        Objects.requireNonNull(text, "text must not be null");
        BigDecimal value;
        try {
            value = new BigDecimal(text.trim().replace(",", ""));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid amount: '" + text + "'");
        }
        int allowedDigits = currency.getDefaultFractionDigits();
        if (value.stripTrailingZeros().scale() > allowedDigits) {
            throw new IllegalArgumentException("%s allows at most %d decimal place(s)"
                    .formatted(currency.getCurrencyCode(), allowedDigits));
        }
        return new Money(value, currency);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    public Money multiply(BigDecimal factor) {
        return new Money(amount.multiply(factor), currency);
    }

    public Money negate() {
        return new Money(amount.negate(), currency);
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public boolean isGreaterThan(Money other) {
        return compareTo(other) > 0;
    }

    public boolean isLessThan(Money other) {
        return compareTo(other) < 0;
    }

    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount);
    }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("Currency mismatch: %s vs %s"
                    .formatted(currency.getCurrencyCode(), other.currency.getCurrencyCode()));
        }
    }

    /** Human readable form, e.g. {@code 1,500,000 VND} or {@code 1,234.50 USD}. */
    @Override
    public String toString() {
        // NumberFormat is not thread-safe, so a new instance is created per call.
        NumberFormat format = NumberFormat.getNumberInstance(Locale.US);
        int digits = currency.getDefaultFractionDigits();
        format.setMinimumFractionDigits(digits);
        format.setMaximumFractionDigits(digits);
        return format.format(amount) + " " + currency.getCurrencyCode();
    }
}
