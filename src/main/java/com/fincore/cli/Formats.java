package com.fincore.cli;

import java.math.BigDecimal;

/** Display formatting helpers. */
final class Formats {

    private Formats() {
    }

    /** 0.045 -> "4.5%". */
    static String percent(BigDecimal rate) {
        return rate.movePointRight(2).stripTrailingZeros().toPlainString() + "%";
    }
}
