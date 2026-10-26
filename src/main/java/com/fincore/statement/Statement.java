package com.fincore.statement;

import com.fincore.account.AccountType;
import com.fincore.common.Money;
import com.fincore.transaction.Transaction;

import java.time.YearMonth;
import java.util.List;

/** Monthly account statement. Immutable snapshot, safe to share or export. */
public record Statement(String accountNumber,
                        AccountType accountType,
                        String customerName,
                        YearMonth period,
                        Money openingBalance,
                        Money totalCredits,
                        Money totalDebits,
                        Money closingBalance,
                        List<Transaction> transactions) {

    public Statement {
        // Defensive copy: the record stays immutable even if the caller later changes its list.
        transactions = List.copyOf(transactions);
    }
}
