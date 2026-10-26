package com.fincore.persistence;

import com.fincore.account.Account;
import com.fincore.account.AccountRepository;
import com.fincore.account.AccountStatus;
import com.fincore.account.AccountType;
import com.fincore.account.CheckingAccount;
import com.fincore.account.SavingsAccount;
import com.fincore.account.TermDepositAccount;
import com.fincore.common.BusinessCalendar;
import com.fincore.common.IdGenerator;
import com.fincore.common.Money;
import com.fincore.customer.Customer;
import com.fincore.customer.CustomerRepository;
import com.fincore.exception.DataAccessException;
import com.fincore.transaction.Transaction;
import com.fincore.transaction.TransactionRepository;
import com.fincore.transaction.TransactionType;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Properties;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Saves and loads the whole bank state as CSV files in a data directory.
 * <p>
 * Each file is written to a temporary file first and then moved over the old one, so a
 * crash in the middle of saving never leaves a half-written file behind.
 */
public class CsvDataStore {

    private static final String CUSTOMERS_FILE = "customers.csv";
    private static final String ACCOUNTS_FILE = "accounts.csv";
    private static final String TRANSACTIONS_FILE = "transactions.csv";
    private static final String SYSTEM_FILE = "system.properties";

    private static final String CUSTOMERS_HEADER = "id,fullName,dateOfBirth,email,phone,registeredOn";
    private static final String ACCOUNTS_HEADER =
            "type,accountNumber,ownerId,currency,openedOn,balance,status,productFields...";
    private static final String TRANSACTIONS_HEADER =
            "id,accountNumber,type,amount,currency,balanceAfter,valueDate,createdAt,description,reference";

    private final Path directory;
    private final CustomerRepository customers;
    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final IdGenerator ids;
    private final BusinessCalendar calendar;

    public CsvDataStore(Path directory, CustomerRepository customers, AccountRepository accounts,
                        TransactionRepository transactions, IdGenerator ids, BusinessCalendar calendar) {
        this.directory = directory;
        this.customers = customers;
        this.accounts = accounts;
        this.transactions = transactions;
        this.ids = ids;
        this.calendar = calendar;
    }

    public boolean hasSavedData() {
        return Files.exists(directory.resolve(SYSTEM_FILE));
    }

    public Path getDirectory() {
        return directory;
    }

    // ------------------------------------------------------------------ save

    public synchronized void save() {
        try {
            Files.createDirectories(directory);
            writeCsv(CUSTOMERS_FILE, CUSTOMERS_HEADER, customers.findAll(), this::customerToRow);
            writeCsv(ACCOUNTS_FILE, ACCOUNTS_HEADER, accounts.findAll(), this::accountToRow);
            writeCsv(TRANSACTIONS_FILE, TRANSACTIONS_HEADER, transactions.findAll(), this::transactionToRow);
            writeSystemProperties();
        } catch (IOException e) {
            throw new DataAccessException("Could not save data to " + directory.toAbsolutePath(), e);
        }
    }

    /** Generic helper: works for any entity type given a function that turns it into CSV fields. */
    private <T> void writeCsv(String fileName, String header, List<T> items, Function<T, List<String>> toRow)
            throws IOException {
        Path target = directory.resolve(fileName);
        Path temp = directory.resolve(fileName + ".tmp");
        try (BufferedWriter writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
            writer.write(header);
            writer.newLine();
            for (T item : items) {
                writer.write(CsvCodec.join(toRow.apply(item)));
                writer.newLine();
            }
        }
        replace(temp, target);
    }

    private void writeSystemProperties() throws IOException {
        Properties props = new Properties();
        props.setProperty("businessDate", calendar.today().toString());
        props.setProperty("customerSequence", String.valueOf(ids.currentCustomerSequence()));
        props.setProperty("accountSequence", String.valueOf(ids.currentAccountSequence()));
        Path target = directory.resolve(SYSTEM_FILE);
        Path temp = directory.resolve(SYSTEM_FILE + ".tmp");
        try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
            props.store(writer, "FinCore system state");
        }
        replace(temp, target);
    }

    private static void replace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private List<String> customerToRow(Customer c) {
        return List.of(c.getId(), c.getFullName(), c.getDateOfBirth().toString(), c.getEmail(), c.getPhone(),
                c.getRegisteredOn().toString());
    }

    private List<String> accountToRow(Account account) {
        List<String> row = new ArrayList<>(List.of(
                account.getType().name(),
                account.getId(),
                account.getOwnerId(),
                account.getCurrency().getCurrencyCode(),
                account.getOpenedOn().toString(),
                plain(account.getBalance()),
                account.getStatus().name()));
        List<String> productFields = switch (account) {
            case CheckingAccount c -> List.of(
                    plain(c.getOverdraftLimit()), plain(c.getMonthlyFee()), plain(c.getFeeWaiverBalance()));
            case SavingsAccount s -> List.of(
                    s.getAnnualRate().toPlainString(), plain(s.getMinimumBalance()),
                    String.valueOf(s.getMaxWithdrawalsPerMonth()), String.valueOf(s.getWithdrawalsThisMonth()),
                    s.getAccruedInterest().toPlainString());
            case TermDepositAccount t -> List.of(
                    plain(t.getPrincipal()), t.getAnnualRate().toPlainString(),
                    t.getEarlyWithdrawalRate().toPlainString(), String.valueOf(t.getTermMonths()),
                    t.getMaturityDate().toString(), t.getSettlementAccountNumber());
        };
        row.addAll(productFields);
        return row;
    }

    private List<String> transactionToRow(Transaction t) {
        return List.of(t.id(), t.accountNumber(), t.type().name(), plain(t.amount()),
                t.amount().currency().getCurrencyCode(), plain(t.balanceAfter()), t.valueDate().toString(),
                t.createdAt().toString(), t.description(), t.referenceOptional().orElse(""));
    }

    private static String plain(Money money) {
        return money.amount().toPlainString();
    }

    // ------------------------------------------------------------------ load

    public synchronized void load() {
        try {
            readCsv(CUSTOMERS_FILE, this::rowToCustomer).forEach(customers::save);
            readCsv(ACCOUNTS_FILE, this::rowToAccount).forEach(accounts::save);
            transactions.appendAll(readCsv(TRANSACTIONS_FILE, this::rowToTransaction));
            readSystemProperties();
        } catch (IOException e) {
            throw new DataAccessException("Could not load data from " + directory.toAbsolutePath(), e);
        } catch (RuntimeException e) {
            throw new DataAccessException("Data files in " + directory.toAbsolutePath() + " are corrupted: "
                    + e.getMessage(), e);
        }
    }

    private <T> List<T> readCsv(String fileName, Function<List<String>, T> fromRow) throws IOException {
        Path file = directory.resolve(fileName);
        if (!Files.exists(file)) {
            return List.of();
        }
        // Files.lines is lazy and holds the file open, so it must be closed: try-with-resources.
        try (Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
            return lines.skip(1)
                    .filter(line -> !line.isBlank())
                    .map(CsvCodec::split)
                    .map(fromRow)
                    .toList();
        }
    }

    private void readSystemProperties() throws IOException {
        Properties props = new Properties();
        try (Reader reader = new BufferedReader(Files.newBufferedReader(directory.resolve(SYSTEM_FILE),
                StandardCharsets.UTF_8))) {
            props.load(reader);
        }
        calendar.restore(LocalDate.parse(props.getProperty("businessDate")));
        ids.restore(Long.parseLong(props.getProperty("customerSequence", "0")),
                Long.parseLong(props.getProperty("accountSequence", "0")));
    }

    private Customer rowToCustomer(List<String> f) {
        return new Customer(f.get(0), f.get(1), LocalDate.parse(f.get(2)), f.get(3), f.get(4), LocalDate.parse(f.get(5)));
    }

    private Account rowToAccount(List<String> f) {
        AccountType type = AccountType.valueOf(f.get(0));
        String number = f.get(1);
        String owner = f.get(2);
        Currency currency = Currency.getInstance(f.get(3));
        LocalDate openedOn = LocalDate.parse(f.get(4));
        Money balance = money(f.get(5), currency);
        AccountStatus status = AccountStatus.valueOf(f.get(6));
        // Switch expression over an enum: exhaustive, so no default branch is required.
        return switch (type) {
            case CHECKING -> new CheckingAccount(number, owner, currency, openedOn, balance, status,
                    money(f.get(7), currency), money(f.get(8), currency), money(f.get(9), currency));
            case SAVINGS -> new SavingsAccount(number, owner, currency, openedOn, balance, status,
                    new BigDecimal(f.get(7)), money(f.get(8), currency), Integer.parseInt(f.get(9)),
                    Integer.parseInt(f.get(10)), new BigDecimal(f.get(11)));
            case TERM_DEPOSIT -> new TermDepositAccount(number, owner, currency, openedOn, balance, status,
                    money(f.get(7), currency), new BigDecimal(f.get(8)), new BigDecimal(f.get(9)),
                    Integer.parseInt(f.get(10)), LocalDate.parse(f.get(11)), f.get(12));
        };
    }

    private Transaction rowToTransaction(List<String> f) {
        Currency currency = Currency.getInstance(f.get(4));
        return new Transaction(f.get(0), f.get(1), TransactionType.valueOf(f.get(2)), money(f.get(3), currency),
                money(f.get(5), currency), LocalDate.parse(f.get(6)), Instant.parse(f.get(7)), f.get(8), f.get(9));
    }

    private static Money money(String value, Currency currency) {
        return Money.of(new BigDecimal(value), currency);
    }
}
