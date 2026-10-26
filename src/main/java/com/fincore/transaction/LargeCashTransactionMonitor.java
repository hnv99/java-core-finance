package com.fincore.transaction;

import com.fincore.common.Money;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Currency;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Compliance listener: banks must report cash transactions at or above a legal threshold
 * to the financial intelligence unit. This monitor appends such transactions to an alert file
 * that the compliance team reviews.
 */
public final class LargeCashTransactionMonitor implements TransactionListener {

    private static final Logger LOG = Logger.getLogger(LargeCashTransactionMonitor.class.getName());

    private final Map<Currency, Money> thresholds;
    private final Path alertFile;

    public LargeCashTransactionMonitor(Map<Currency, Money> thresholds, Path alertFile) {
        this.thresholds = Map.copyOf(thresholds);
        this.alertFile = alertFile;
    }

    @Override
    public void onTransaction(Transaction t) {
        if (!t.type().isCash()) {
            return;
        }
        Money threshold = thresholds.get(t.amount().currency());
        if (threshold == null || t.amount().isLessThan(threshold)) {
            return;
        }
        String line = "%s | LARGE_CASH | account=%s | %s | %s | txn=%s%n"
                .formatted(Instant.now(), t.accountNumber(), t.type(), t.amount(), t.id());
        try {
            Files.createDirectories(alertFile.toAbsolutePath().getParent());
            Files.writeString(alertFile, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            // The money movement is already committed; a failed alert must not undo it,
            // but it must be visible to operations.
            LOG.log(Level.SEVERE, "Could not write compliance alert for transaction " + t.id(), e);
        }
    }
}
