package com.fincore;

import com.fincore.account.AccountRepository;
import com.fincore.account.AccountService;
import com.fincore.account.ProductCatalog;
import com.fincore.batch.EndOfDayProcessor;
import com.fincore.cli.BankingCli;
import com.fincore.cli.ConsoleIO;
import com.fincore.common.BusinessCalendar;
import com.fincore.common.IdGenerator;
import com.fincore.common.Money;
import com.fincore.customer.CustomerRepository;
import com.fincore.customer.CustomerService;
import com.fincore.exception.BankingException;
import com.fincore.exception.DataAccessException;
import com.fincore.persistence.CsvDataStore;
import com.fincore.statement.StatementService;
import com.fincore.transaction.LargeCashTransactionMonitor;
import com.fincore.transaction.TransactionRepository;
import com.fincore.transaction.TransactionService;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;
import java.util.Scanner;

/**
 * Application entry point and composition root: the only place where objects are created
 * and wired together (manual constructor-based dependency injection, no framework).
 * <p>
 * Usage: {@code java -cp out com.fincore.App [dataDirectory]}
 */
public final class App {

    private App() {
    }

    public static void main(String[] args) {
        Path dataDir = Path.of(args.length > 0 ? args[0] : "data");

        // Infrastructure
        var calendar = new BusinessCalendar(LocalDate.now());
        var ids = new IdGenerator();
        var customerRepository = new CustomerRepository();
        var accountRepository = new AccountRepository();
        var transactionRepository = new TransactionRepository();
        var dataStore = new CsvDataStore(dataDir, customerRepository, accountRepository,
                transactionRepository, ids, calendar);

        // Services
        var customerService = new CustomerService(customerRepository, ids, calendar);
        var transactionService = new TransactionService(accountRepository, transactionRepository, ids, calendar);
        var accountService = new AccountService(accountRepository, customerService, transactionService,
                new ProductCatalog(), ids, calendar);
        var statementService = new StatementService(accountService, customerService, transactionRepository);
        var endOfDay = new EndOfDayProcessor(accountRepository, accountService, transactionService, calendar);

        // Observers
        transactionService.addListener(new LargeCashTransactionMonitor(Map.of(
                ProductCatalog.VND, Money.of(400_000_000, ProductCatalog.VND),
                ProductCatalog.USD, Money.of(10_000, ProductCatalog.USD)),
                dataDir.resolve("compliance-alerts.log")));

        var io = new ConsoleIO(new Scanner(System.in, StandardCharsets.UTF_8), System.out);
        try {
            if (dataStore.hasSavedData()) {
                dataStore.load();
                io.printf("Loaded %d customers, %d accounts, %d transactions from %s%n",
                        customerRepository.count(), accountRepository.count(), transactionRepository.count(),
                        dataDir.toAbsolutePath());
            } else if (io.confirm("No saved data found. Load demo customers and accounts?")) {
                new DemoDataSeeder(customerService, accountService, transactionService).seed();
                dataStore.save();
                io.println("Demo data created.");
            }
        } catch (DataAccessException e) {
            io.println("[FATAL] " + e.getMessage());
            System.exit(1);
        } catch (BankingException e) {
            io.println("[FATAL] Demo data could not be created: " + e.getMessage());
            System.exit(1);
        } catch (ConsoleIO.EndOfInputException e) {
            return;
        }

        new BankingCli(io, customerService, accountService, transactionService, statementService,
                endOfDay, dataStore, calendar).run();
    }
}
