package com.fincore.statement;

import com.fincore.account.Account;
import com.fincore.account.AccountService;
import com.fincore.common.Money;
import com.fincore.customer.Customer;
import com.fincore.customer.CustomerService;
import com.fincore.exception.BankingException;
import com.fincore.exception.DataAccessException;
import com.fincore.transaction.Transaction;
import com.fincore.transaction.TransactionRepository;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Predicate;

/** Builds monthly statements from the ledger and exports them as text files. */
public class StatementService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private final AccountService accountService;
    private final CustomerService customerService;
    private final TransactionRepository transactions;

    public StatementService(AccountService accountService, CustomerService customerService,
                            TransactionRepository transactions) {
        this.accountService = accountService;
        this.customerService = customerService;
        this.transactions = transactions;
    }

    public Statement generate(String accountNumber, YearMonth period) throws BankingException {
        Account account = accountService.getAccount(accountNumber);
        Customer owner = customerService.getById(account.getOwnerId());
        LocalDate start = period.atDay(1);
        LocalDate end = period.atEndOfMonth();
        Money zero = Money.zero(account.getCurrency());

        List<Transaction> ledger = transactions.findByAccount(account.getId());
        List<Transaction> before = ledger.stream()
                .filter(t -> t.valueDate().isBefore(start))
                .toList();
        List<Transaction> inPeriod = ledger.stream()
                .filter(t -> !t.valueDate().isBefore(start) && !t.valueDate().isAfter(end))
                .toList();

        // getLast() comes from SequencedCollection, new in Java 21.
        Money opening = before.isEmpty() ? zero : before.getLast().balanceAfter();
        Money closing = inPeriod.isEmpty() ? opening : inPeriod.getLast().balanceAfter();
        Money credits = sum(inPeriod, t -> t.type().isCredit(), zero);
        Money debits = sum(inPeriod, t -> !t.type().isCredit(), zero);

        return new Statement(account.getId(), account.getType(), owner.getFullName(), period,
                opening, credits, debits, closing, inPeriod);
    }

    /** Renders a statement as plain text. Used for both console output and file export. */
    public String render(Statement s) {
        StringBuilder out = new StringBuilder();
        out.append("""
                ==================================================================================================
                ACCOUNT STATEMENT
                ==================================================================================================
                """);
        out.append("Account  : %s (%s)%n".formatted(s.accountNumber(), s.accountType().getDisplayName()));
        out.append("Customer : %s%n".formatted(s.customerName()));
        out.append("Period   : %s to %s%n".formatted(s.period().atDay(1).format(DATE), s.period().atEndOfMonth().format(DATE)));
        out.append("-".repeat(98)).append(System.lineSeparator());
        out.append("%-10s  %-13s  %-38s  %15s  %15s%n".formatted("Date", "Type", "Description", "Amount", "Balance"));
        out.append("-".repeat(98)).append(System.lineSeparator());
        out.append("%-10s  %-13s  %-38s  %15s  %15s%n".formatted("", "", "Opening balance", "", s.openingBalance()));
        for (Transaction t : s.transactions()) {
            out.append("%-10s  %-13s  %-38s  %15s  %15s%n".formatted(
                    t.valueDate().format(DATE), t.type(), truncate(t.description(), 38),
                    t.signedAmount(), t.balanceAfter()));
        }
        out.append("-".repeat(98)).append(System.lineSeparator());
        out.append("Total credits   : %s%n".formatted(s.totalCredits()));
        out.append("Total debits    : %s%n".formatted(s.totalDebits()));
        out.append("Closing balance : %s%n".formatted(s.closingBalance()));
        return out.toString();
    }

    public Path export(Statement statement, Path directory) {
        Path file = directory.resolve("statement-%s-%s.txt".formatted(statement.accountNumber(), statement.period()));
        try {
            Files.createDirectories(directory);
            // try-with-resources closes the writer even if writing fails.
            try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                writer.write(render(statement));
            }
            return file;
        } catch (IOException e) {
            throw new DataAccessException("Could not export statement to " + file, e);
        }
    }

    private static Money sum(List<Transaction> list, Predicate<Transaction> filter, Money zero) {
        return list.stream()
                .filter(filter)
                .map(Transaction::amount)
                .reduce(zero, Money::plus);
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 3) + "...";
    }
}
