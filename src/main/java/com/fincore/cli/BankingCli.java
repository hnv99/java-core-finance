package com.fincore.cli;

import com.fincore.account.Account;
import com.fincore.account.AccountService;
import com.fincore.account.CheckingAccount;
import com.fincore.account.ProductCatalog;
import com.fincore.account.SavingsAccount;
import com.fincore.account.TermDepositAccount;
import com.fincore.batch.EndOfDayProcessor;
import com.fincore.batch.EndOfDayProcessor.EodReport;
import com.fincore.common.BusinessCalendar;
import com.fincore.common.Money;
import com.fincore.customer.Customer;
import com.fincore.customer.CustomerService;
import com.fincore.exception.BankingException;
import com.fincore.exception.DataAccessException;
import com.fincore.persistence.CsvDataStore;
import com.fincore.statement.Statement;
import com.fincore.statement.StatementService;
import com.fincore.transaction.Transaction;
import com.fincore.transaction.TransactionService;
import com.fincore.transaction.TransferReceipt;

import java.math.RoundingMode;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Map;

/**
 * Interactive teller console. This class only translates user input into service calls
 * and formats results; all business rules live in the service and domain layers.
 */
public class BankingCli {

    /** A numbered menu entry. {@code mutating} actions trigger an automatic save. */
    private record MenuItem(String label, boolean mutating, CliAction action) {
    }

    private record MenuSection(String title, List<MenuItem> items) {
    }

    private final ConsoleIO io;
    private final CustomerService customerService;
    private final AccountService accountService;
    private final TransactionService transactionService;
    private final StatementService statementService;
    private final EndOfDayProcessor endOfDayProcessor;
    private final CsvDataStore dataStore;
    private final BusinessCalendar calendar;
    private final List<MenuSection> sections;
    private final List<MenuItem> numberedItems = new ArrayList<>();

    public BankingCli(ConsoleIO io, CustomerService customerService, AccountService accountService,
                      TransactionService transactionService, StatementService statementService,
                      EndOfDayProcessor endOfDayProcessor, CsvDataStore dataStore, BusinessCalendar calendar) {
        this.io = io;
        this.customerService = customerService;
        this.accountService = accountService;
        this.transactionService = transactionService;
        this.statementService = statementService;
        this.endOfDayProcessor = endOfDayProcessor;
        this.dataStore = dataStore;
        this.calendar = calendar;
        // Method references (this::registerCustomer) are compact lambdas implementing CliAction.
        this.sections = List.of(
                new MenuSection("Customers", List.of(
                        new MenuItem("Register customer", true, this::registerCustomer),
                        new MenuItem("List customers", false, this::listCustomers),
                        new MenuItem("Customer portfolio", false, this::showPortfolio),
                        new MenuItem("Update contact details", true, this::updateContact))),
                new MenuSection("Accounts", List.of(
                        new MenuItem("Open checking / savings account", true, this::openAccount),
                        new MenuItem("Open term deposit", true, this::openTermDeposit),
                        new MenuItem("Account details", false, this::showAccount),
                        new MenuItem("List all accounts", false, this::listAccounts),
                        new MenuItem("Freeze / unfreeze account", true, this::toggleFreeze),
                        new MenuItem("Close account / settle term deposit", true, this::closeAccount))),
                new MenuSection("Transactions", List.of(
                        new MenuItem("Cash deposit", true, this::deposit),
                        new MenuItem("Cash withdrawal", true, this::withdraw),
                        new MenuItem("Transfer", true, this::transfer),
                        new MenuItem("Transaction history", false, this::history))),
                new MenuSection("Reports & operations", List.of(
                        new MenuItem("Monthly statement", false, this::statement),
                        new MenuItem("Run End-of-Day", true, this::runEndOfDay))));
        sections.forEach(section -> numberedItems.addAll(section.items()));
    }

    public void run() {
        io.println("""

                  _____ _        ____
                 |  ___(_)_ __  / ___|___  _ __ ___
                 | |_  | | '_ \\| |   / _ \\| '__/ _ \\
                 |  _| | | | | | |__| (_) | | |  __/
                 |_|   |_|_| |_|\\____\\___/|_|  \\___|   Core Banking Teller Console
                """);
        try {
            while (true) {
                printMenu();
                String choice = io.readLine("Select an option: ");
                if (choice.equals("0")) {
                    break;
                }
                MenuItem item = parseChoice(choice);
                if (item == null) {
                    io.println("Unknown option: " + choice);
                    continue;
                }
                io.println();
                io.println("--- " + item.label() + " ---");
                execute(item);
            }
        } catch (ConsoleIO.EndOfInputException e) {
            io.println();
        }
        dataStore.save();
        io.println("Data saved to " + dataStore.getDirectory().toAbsolutePath() + ". Goodbye.");
    }

    private void printMenu() {
        io.println();
        io.println("=".repeat(56));
        io.printf(" Business date: %s%n", calendar.today());
        io.println("=".repeat(56));
        int number = 1;
        for (MenuSection section : sections) {
            io.println(" " + section.title());
            for (MenuItem item : section.items()) {
                io.printf("  %2d) %s%n", number++, item.label());
            }
        }
        io.println("   0) Save and exit");
    }

    private MenuItem parseChoice(String choice) {
        try {
            int index = Integer.parseInt(choice) - 1;
            return index >= 0 && index < numberedItems.size() ? numberedItems.get(index) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Central error handling: one place decides how each failure category is shown. */
    private void execute(MenuItem item) {
        try {
            item.action().execute();
            if (item.mutating()) {
                dataStore.save();
            }
        } catch (BankingException e) {
            io.println("[REJECTED] " + e.getMessage());
        } catch (IllegalArgumentException e) {
            io.println("[INVALID] " + e.getMessage());
        } catch (DataAccessException e) {
            io.println("[STORAGE ERROR] " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ customers

    private void registerCustomer() throws BankingException {
        String name = io.readRequired("Full name: ");
        LocalDate dob = io.readDate("Date of birth");
        String email = io.readRequired("Email: ");
        String phone = io.readRequired("Phone: ");
        Customer customer = customerService.register(name, dob, email, phone);
        io.println("Customer registered with id " + customer.getId());
    }

    private void listCustomers() {
        List<Customer> customers = customerService.listAll();
        if (customers.isEmpty()) {
            io.println("No customers yet.");
            return;
        }
        io.printf("%-10s %-25s %-12s %-28s %-14s%n", "ID", "Name", "Born", "Email", "Phone");
        for (Customer c : customers) {
            io.printf("%-10s %-25s %-12s %-28s %-14s%n",
                    c.getId(), c.getFullName(), c.getDateOfBirth(), c.getEmail(), c.getPhone());
        }
    }

    private void showPortfolio() throws BankingException {
        Customer customer = customerService.getById(io.readRequired("Customer id: "));
        io.printf("%s (%s), age %d%n", customer.getFullName(), customer.getId(), customer.ageOn(calendar.today()));
        List<Account> accounts = accountService.accountsOf(customer.getId());
        if (accounts.isEmpty()) {
            io.println("No accounts.");
            return;
        }
        printAccountTable(accounts);
        Map<Currency, Money> totals = accountService.totalBalanceByCurrency(customer.getId());
        totals.forEach((currency, total) -> io.printf("Total %s: %s%n", currency.getCurrencyCode(), total));
    }

    private void updateContact() throws BankingException {
        Customer customer = customerService.getById(io.readRequired("Customer id: "));
        io.printf("Current: %s / %s%n", customer.getEmail(), customer.getPhone());
        String email = io.readOptional("New email (Enter to keep): ");
        String phone = io.readOptional("New phone (Enter to keep): ");
        customerService.updateContact(customer.getId(),
                email.isEmpty() ? customer.getEmail() : email,
                phone.isEmpty() ? customer.getPhone() : phone);
        io.println("Contact details updated.");
    }

    // ------------------------------------------------------------------ accounts

    private void openAccount() throws BankingException {
        String customerId = io.readRequired("Customer id: ");
        customerService.getById(customerId);
        io.println("  1) Checking");
        io.println("  2) Savings");
        int type = io.readInt("Product: ", 1, 2);
        Currency currency = io.readCurrency("Currency");
        Account account = type == 1
                ? accountService.openChecking(customerId, currency)
                : accountService.openSavings(customerId, currency);
        io.println("Opened account " + account.getId());
        printAccountDetails(account);
    }

    private void openTermDeposit() throws BankingException {
        String customerId = io.readRequired("Customer id: ");
        Account funding = accountService.getAccount(io.readRequired("Funding / settlement account: "));
        ProductCatalog.TermDepositTerms terms = accountService.getCatalog().termDepositTerms(funding.getCurrency());
        io.println("Available terms (" + funding.getCurrency().getCurrencyCode() + "):");
        terms.ratesByTermMonths().forEach((months, rate) ->
                io.printf("  %2d months : %s per year%n", months, Formats.percent(rate)));
        io.printf("Early closure rate: %s, minimum principal: %s%n",
                Formats.percent(terms.earlyWithdrawalRate()), terms.minimumPrincipal());
        int months = io.readValid("Term in months: ", value -> {
            int m = Integer.parseInt(value);
            terms.rateFor(m);
            return m;
        });
        Money principal = io.readMoney("Principal", funding.getCurrency());
        TermDepositAccount deposit = accountService.openTermDeposit(customerId, funding.getId(), principal, months);
        io.println("Opened term deposit " + deposit.getId());
        printAccountDetails(deposit);
    }

    private void showAccount() throws BankingException {
        Account account = accountService.getAccount(io.readRequired("Account number: "));
        printAccountDetails(account);
    }

    private void listAccounts() {
        List<Account> accounts = accountService.listAll();
        if (accounts.isEmpty()) {
            io.println("No accounts yet.");
            return;
        }
        printAccountTable(accounts);
    }

    private void toggleFreeze() throws BankingException {
        Account account = accountService.getAccount(io.readRequired("Account number: "));
        switch (account.getStatus()) {
            case ACTIVE -> {
                if (io.confirm("Freeze account " + account.getId() + "? Debits will be blocked")) {
                    accountService.freeze(account.getId());
                    io.println("Account frozen.");
                }
            }
            case FROZEN -> {
                if (io.confirm("Unfreeze account " + account.getId() + "?")) {
                    accountService.unfreeze(account.getId());
                    io.println("Account is active again.");
                }
            }
            case CLOSED -> io.println("Account is closed.");
        }
    }

    private void closeAccount() throws BankingException {
        Account account = accountService.getAccount(io.readRequired("Account number: "));
        if (account instanceof TermDepositAccount deposit) {
            LocalDate today = calendar.today();
            String kind = deposit.isMaturedOn(today) ? "at maturity" : "EARLY (demand rate applies)";
            io.printf("Settle term deposit %s %s. Interest: %s, paid to %s%n", deposit.getId(), kind,
                    deposit.interestDueOn(today), deposit.getSettlementAccountNumber());
            if (io.confirm("Proceed?")) {
                Money payout = accountService.settleTermDeposit(deposit.getId());
                io.println("Paid out " + payout + ". Term deposit closed.");
            }
            return;
        }
        if (io.confirm("Close account %s? Remaining balance %s will be paid out in cash"
                .formatted(account.getId(), account.getBalance()))) {
            Money payout = accountService.closeAccount(account.getId());
            io.println("Account closed. Cash paid out: " + payout);
        }
    }

    // ------------------------------------------------------------------ transactions

    private void deposit() throws BankingException {
        Account account = accountService.getAccount(io.readRequired("Account number: "));
        Money amount = io.readMoney("Amount", account.getCurrency());
        Transaction t = transactionService.deposit(account.getId(), amount, "Cash deposit");
        io.printf("Deposited %s. New balance: %s%n", t.amount(), t.balanceAfter());
    }

    private void withdraw() throws BankingException {
        Account account = accountService.getAccount(io.readRequired("Account number: "));
        io.println("Available: " + account.getAvailableBalance());
        Money amount = io.readMoney("Amount", account.getCurrency());
        Transaction t = transactionService.withdraw(account.getId(), amount, "Cash withdrawal");
        io.printf("Withdrew %s. New balance: %s%n", t.amount(), t.balanceAfter());
    }

    private void transfer() throws BankingException {
        Account from = accountService.getAccount(io.readRequired("From account: "));
        io.println("Available: " + from.getAvailableBalance());
        String to = io.readRequired("To account: ");
        Money amount = io.readMoney("Amount", from.getCurrency());
        String memo = io.readOptional("Description (optional): ");
        TransferReceipt receipt = transactionService.transfer(from.getId(), to, amount, memo);
        io.printf("Transfer completed. Reference %s. Your new balance: %s%n",
                receipt.reference(), receipt.debit().balanceAfter());
    }

    private void history() throws BankingException {
        String number = io.readRequired("Account number: ");
        List<Transaction> transactions = transactionService.history(number);
        if (transactions.isEmpty()) {
            io.println("No transactions.");
            return;
        }
        io.printf("%-10s %-13s %18s %18s  %-14s %s%n", "Date", "Type", "Amount", "Balance", "Reference", "Description");
        for (Transaction t : transactions) {
            io.printf("%-10s %-13s %18s %18s  %-14s %s%n", t.valueDate(), t.type(), t.signedAmount(),
                    t.balanceAfter(), t.referenceOptional().orElse("-"), t.description());
        }
    }

    // ------------------------------------------------------------------ reports & operations

    private void statement() throws BankingException {
        String number = io.readRequired("Account number: ");
        YearMonth period = io.readYearMonth("Period", YearMonth.from(calendar.today()));
        Statement statement = statementService.generate(number, period);
        io.println(statementService.render(statement));
        if (io.confirm("Export to file?")) {
            Path file = statementService.export(statement, dataStore.getDirectory().resolve("statements"));
            io.println("Saved to " + file.toAbsolutePath());
        }
    }

    private void runEndOfDay() {
        int days = io.readInt("Number of days to process (1-366): ", 1, 366);
        for (int i = 0; i < days; i++) {
            EodReport report = endOfDayProcessor.run();
            boolean interesting = report.monthEnd() || report.maturedDeposits() > 0 || !report.errors().isEmpty();
            if (days == 1 || interesting) {
                io.printf("EOD %s%s -> %s | accounts: %d, interest: %d, fees: %d, matured deposits: %d%n",
                        report.closedDate(), report.monthEnd() ? " (month end)" : "", report.newBusinessDate(),
                        report.accountsProcessed(), report.interestPostings(), report.feePostings(),
                        report.maturedDeposits());
                report.errors().forEach(error -> io.println("  ERROR " + error));
            }
        }
        io.println("Business date is now " + calendar.today());
    }

    // ------------------------------------------------------------------ rendering

    private void printAccountTable(List<Account> accounts) {
        io.printf("%-11s %-13s %-10s %-7s %20s %20s%n", "Number", "Type", "Owner", "Status", "Balance", "Available");
        for (Account a : accounts) {
            io.printf("%-11s %-13s %-10s %-7s %20s %20s%n", a.getId(), a.getType().getDisplayName(), a.getOwnerId(),
                    a.getStatus(), a.getBalance(), a.getAvailableBalance());
        }
    }

    private void printAccountDetails(Account account) {
        io.printf("Account   : %s (%s)%n", account.getId(), account.getType().getDisplayName());
        io.printf("Owner     : %s%n", account.getOwnerId());
        io.printf("Opened on : %s%n", account.getOpenedOn());
        io.printf("Status    : %s%n", account.getStatus());
        io.printf("Balance   : %s (available %s)%n", account.getBalance(), account.getAvailableBalance());
        // Exhaustive pattern matching switch over the sealed Account hierarchy.
        String productDetails = switch (account) {
            case CheckingAccount c -> "Overdraft limit: %s | Monthly fee: %s (waived from %s)"
                    .formatted(c.getOverdraftLimit(), c.getMonthlyFee(), c.getFeeWaiverBalance());
            case SavingsAccount s -> "Rate: %s | Accrued interest: %s %s | Withdrawals this month: %d/%d | Min balance: %s"
                    .formatted(Formats.percent(s.getAnnualRate()),
                            s.getAccruedInterest().setScale(4, RoundingMode.DOWN).toPlainString(),
                            s.getCurrency().getCurrencyCode(), s.getWithdrawalsThisMonth(),
                            s.getMaxWithdrawalsPerMonth(), s.getMinimumBalance());
            case TermDepositAccount t -> "Principal: %s | Rate: %s | Term: %d months | Matures: %s | Interest at maturity: %s | Settles to: %s"
                    .formatted(t.getPrincipal(), Formats.percent(t.getAnnualRate()), t.getTermMonths(),
                            t.getMaturityDate(), t.interestDueOn(t.getMaturityDate()), t.getSettlementAccountNumber());
        };
        io.println(productDetails);
    }
}
