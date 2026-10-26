package com.fincore.common;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Objects;

/**
 * Holds the bank's current business date.
 * <p>
 * Core banking systems do not use the wall clock for accounting. Every posting is
 * booked on the business date, which only moves forward when the End-of-Day batch
 * completes. This also makes interest and fee logic deterministic and testable.
 */
public final class BusinessCalendar {

    private LocalDate currentDate;

    public BusinessCalendar(LocalDate startDate) {
        this.currentDate = Objects.requireNonNull(startDate, "startDate must not be null");
    }

    public synchronized LocalDate today() {
        return currentDate;
    }

    /** Moves to the next business date and returns it. Called only by the End-of-Day batch. */
    public synchronized LocalDate advance() {
        currentDate = currentDate.plusDays(1);
        return currentDate;
    }

    /** Restores the date loaded from storage. */
    public synchronized void restore(LocalDate date) {
        this.currentDate = Objects.requireNonNull(date, "date must not be null");
    }

    public static boolean isMonthEnd(LocalDate date) {
        return date.equals(date.with(TemporalAdjusters.lastDayOfMonth()));
    }
}
