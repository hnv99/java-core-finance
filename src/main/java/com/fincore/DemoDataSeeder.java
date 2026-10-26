package com.fincore;

import com.fincore.account.AccountService;
import com.fincore.account.CheckingAccount;
import com.fincore.account.ProductCatalog;
import com.fincore.account.SavingsAccount;
import com.fincore.common.Money;
import com.fincore.customer.Customer;
import com.fincore.customer.CustomerService;
import com.fincore.exception.BankingException;
import com.fincore.transaction.TransactionService;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Creates a small realistic data set so the console can be explored right away. */
final class DemoDataSeeder {

    private final CustomerService customers;
    private final AccountService accounts;
    private final TransactionService transactions;

    DemoDataSeeder(CustomerService customers, AccountService accounts, TransactionService transactions) {
        this.customers = customers;
        this.accounts = accounts;
        this.transactions = transactions;
    }

    void seed() throws BankingException {
        var vnd = ProductCatalog.VND;
        var usd = ProductCatalog.USD;

        Customer alice = customers.register("Alice Nguyen", LocalDate.of(1992, 4, 18),
                "alice.nguyen@example.com", "0901234567");
        Customer bob = customers.register("Bob Tran", LocalDate.of(1985, 11, 2),
                "bob.tran@example.com", "+84912345678");

        CheckingAccount aliceChecking = accounts.openChecking(alice.getId(), vnd);
        SavingsAccount aliceSavings = accounts.openSavings(alice.getId(), vnd);
        CheckingAccount bobChecking = accounts.openChecking(bob.getId(), vnd);
        CheckingAccount bobUsd = accounts.openChecking(bob.getId(), usd);

        transactions.deposit(aliceChecking.getId(), Money.of(35_000_000, vnd), "Cash deposit");
        transactions.deposit(bobChecking.getId(), Money.of(12_000_000, vnd), "Cash deposit");
        transactions.deposit(bobUsd.getId(), Money.of(new BigDecimal("1500.00"), usd), "Cash deposit");
        transactions.transfer(aliceChecking.getId(), aliceSavings.getId(), Money.of(10_000_000, vnd), "Monthly saving");
        transactions.transfer(aliceChecking.getId(), bobChecking.getId(), Money.of(2_500_000, vnd), "Rent share");
        transactions.withdraw(bobChecking.getId(), Money.of(1_000_000, vnd), "ATM withdrawal");

        accounts.openTermDeposit(alice.getId(), aliceChecking.getId(), Money.of(15_000_000, vnd), 6);
    }
}
