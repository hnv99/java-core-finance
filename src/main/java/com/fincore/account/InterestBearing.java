package com.fincore.account;

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * Capability interface for products that earn interest.
 * <p>
 * Demonstrates interface constants (implicitly {@code public static final}),
 * a {@code default} method and a {@code static} helper method.
 */
public interface InterestBearing {

    /** Day-count convention "Actual/365": interest per day = annual rate / 365. */
    int DAYS_IN_YEAR = 365;

    /** Intermediate precision for interest math; rounding to currency units happens only at posting. */
    MathContext PRECISION = MathContext.DECIMAL64;

    /** Annual nominal rate as a fraction, e.g. 0.05 for 5%. */
    BigDecimal getAnnualRate();

    default BigDecimal dailyRate() {
        return getAnnualRate().divide(BigDecimal.valueOf(DAYS_IN_YEAR), PRECISION);
    }

    /** Simple (non-compounded) interest, unrounded. */
    static BigDecimal simpleInterest(BigDecimal principal, BigDecimal annualRate, long days) {
        return principal.multiply(annualRate)
                .multiply(BigDecimal.valueOf(days))
                .divide(BigDecimal.valueOf(DAYS_IN_YEAR), PRECISION);
    }
}
