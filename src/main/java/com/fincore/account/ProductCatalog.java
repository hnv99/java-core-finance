package com.fincore.account;

import com.fincore.common.Money;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Currency;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Product parameters (rates, fees, limits) per currency.
 * <p>
 * Real banks keep these in a product configuration table instead of hard-coding them
 * inside account classes, so a rate can change without a code change. Accounts copy the
 * terms valid on their opening date. The rates here are illustrative only.
 */
public final class ProductCatalog {

    public static final Currency VND = Currency.getInstance("VND");
    public static final Currency USD = Currency.getInstance("USD");
    public static final Set<Currency> SUPPORTED_CURRENCIES = Set.of(VND, USD);

    /** Nested records: small immutable value types that belong to the catalog. */
    public record CheckingTerms(Money overdraftLimit, Money monthlyFee, Money feeWaiverBalance) {
    }

    public record SavingsTerms(BigDecimal annualRate, Money minimumBalance, int maxWithdrawalsPerMonth) {
    }

    public record TermDepositTerms(Money minimumPrincipal, BigDecimal earlyWithdrawalRate,
                                   SortedMap<Integer, BigDecimal> ratesByTermMonths) {

        public TermDepositTerms {
            ratesByTermMonths = Collections.unmodifiableSortedMap(new TreeMap<>(ratesByTermMonths));
        }

        public BigDecimal rateFor(int termMonths) {
            BigDecimal rate = ratesByTermMonths.get(termMonths);
            if (rate == null) {
                throw new IllegalArgumentException("A %d-month term is not offered. Available terms: %s"
                        .formatted(termMonths, ratesByTermMonths.keySet()));
            }
            return rate;
        }
    }

    public CheckingTerms checkingTerms(Currency currency) {
        return switch (requireSupported(currency).getCurrencyCode()) {
            case "VND" -> new CheckingTerms(vnd(5_000_000), vnd(11_000), vnd(2_000_000));
            case "USD" -> new CheckingTerms(usd("200"), usd("2"), usd("500"));
            default -> throw unsupported(currency);
        };
    }

    public SavingsTerms savingsTerms(Currency currency) {
        return switch (requireSupported(currency).getCurrencyCode()) {
            case "VND" -> new SavingsTerms(new BigDecimal("0.005"), vnd(100_000), 6);
            case "USD" -> new SavingsTerms(new BigDecimal("0.010"), usd("10"), 6);
            default -> throw unsupported(currency);
        };
    }

    public TermDepositTerms termDepositTerms(Currency currency) {
        return switch (requireSupported(currency).getCurrencyCode()) {
            case "VND" -> new TermDepositTerms(vnd(1_000_000), new BigDecimal("0.002"), new TreeMap<>(Map.of(
                    1, new BigDecimal("0.030"),
                    3, new BigDecimal("0.035"),
                    6, new BigDecimal("0.045"),
                    12, new BigDecimal("0.050"))));
            case "USD" -> new TermDepositTerms(usd("100"), new BigDecimal("0.005"), new TreeMap<>(Map.of(
                    1, new BigDecimal("0.020"),
                    3, new BigDecimal("0.025"),
                    6, new BigDecimal("0.030"),
                    12, new BigDecimal("0.035"))));
            default -> throw unsupported(currency);
        };
    }

    public static Currency requireSupported(Currency currency) {
        if (!SUPPORTED_CURRENCIES.contains(currency)) {
            throw unsupported(currency);
        }
        return currency;
    }

    private static IllegalArgumentException unsupported(Currency currency) {
        return new IllegalArgumentException("Unsupported currency: " + currency);
    }

    private static Money vnd(long amount) {
        return Money.of(amount, VND);
    }

    private static Money usd(String amount) {
        return Money.of(new BigDecimal(amount), USD);
    }
}
