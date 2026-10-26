package com.fincore.batch;

import com.fincore.account.Account;
import com.fincore.account.AccountRepository;
import com.fincore.account.AccountService;
import com.fincore.account.CheckingAccount;
import com.fincore.account.SavingsAccount;
import com.fincore.account.TermDepositAccount;
import com.fincore.common.BusinessCalendar;
import com.fincore.common.Money;
import com.fincore.exception.BankingException;
import com.fincore.transaction.TransactionService;
import com.fincore.transaction.TransactionType;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * End-of-Day (EOD) batch, the nightly job of every core banking system:
 * <ol>
 *   <li>Accrue one day of interest on every savings account.</li>
 *   <li>On the last day of the month: credit interest, charge maintenance fees, reset limits.</li>
 *   <li>Advance the business date.</li>
 *   <li>Settle term deposits maturing on the new date.</li>
 * </ol>
 * Accounts are processed in parallel on virtual threads (Java 21). A failure on one account
 * is reported and does not stop the batch for the others.
 */
public class EndOfDayProcessor {

    /** Summary of one EOD run. */
    public record EodReport(LocalDate closedDate, LocalDate newBusinessDate, boolean monthEnd,
                            int accountsProcessed, int interestPostings, int feePostings,
                            int maturedDeposits, List<String> errors) {

        public EodReport {
            errors = List.copyOf(errors);
        }
    }

    private final AccountRepository accounts;
    private final AccountService accountService;
    private final TransactionService transactionService;
    private final BusinessCalendar calendar;

    public EndOfDayProcessor(AccountRepository accounts, AccountService accountService,
                             TransactionService transactionService, BusinessCalendar calendar) {
        this.accounts = accounts;
        this.accountService = accountService;
        this.transactionService = transactionService;
        this.calendar = calendar;
    }

    public EodReport run() {
        LocalDate closingDate = calendar.today();
        boolean monthEnd = BusinessCalendar.isMonthEnd(closingDate);
        List<Account> openAccounts = accounts.findAll().stream()
                .filter(account -> !account.isClosed())
                .toList();

        AtomicInteger interestPostings = new AtomicInteger();
        AtomicInteger feePostings = new AtomicInteger();
        Queue<String> errors = new ConcurrentLinkedQueue<>();

        // ExecutorService is AutoCloseable (Java 19+): close() waits until all tasks finish.
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Account account : openAccounts) {
                executor.submit(() -> {
                    try {
                        processAccount(account, closingDate, monthEnd, interestPostings, feePostings);
                    } catch (BankingException | RuntimeException e) {
                        errors.add(account.getId() + ": " + e.getMessage());
                    }
                });
            }
        }

        LocalDate newDate = calendar.advance();
        int matured = settleMaturedDeposits(newDate, errors);

        return new EodReport(closingDate, newDate, monthEnd, openAccounts.size(),
                interestPostings.get(), feePostings.get(), matured, new ArrayList<>(errors));
    }

    private void processAccount(Account account, LocalDate date, boolean monthEnd,
                                AtomicInteger interestPostings, AtomicInteger feePostings) throws BankingException {
        // Pattern matching switch over a sealed hierarchy: no default branch needed,
        // and the compiler reports an error if a new account type is added but not handled here.
        switch (account) {
            case SavingsAccount savings -> {
                Money interest;
                savings.getLock().lock();
                try {
                    savings.accrueDailyInterest();
                    interest = monthEnd ? savings.takeAccruedInterest() : Money.zero(savings.getCurrency());
                    if (monthEnd) {
                        savings.resetMonthlyCounters();
                    }
                } finally {
                    savings.getLock().unlock();
                }
                if (interest.isPositive()) {
                    transactionService.postSystemCredit(savings, TransactionType.INTEREST, interest,
                            "Monthly interest " + YearMonth.from(date), null);
                    interestPostings.incrementAndGet();
                }
            }
            case CheckingAccount checking -> {
                Money fee = monthEnd ? checking.monthlyFeeDue() : Money.zero(checking.getCurrency());
                if (fee.isPositive()) {
                    transactionService.postSystemDebit(checking, TransactionType.FEE, fee,
                            "Monthly maintenance fee " + YearMonth.from(date), null);
                    feePostings.incrementAndGet();
                }
            }
            case TermDepositAccount ignored -> {
                // Interest is paid in one sum at maturity, nothing to do daily.
            }
        }
    }

    private int settleMaturedDeposits(LocalDate date, Queue<String> errors) {
        int settled = 0;
        for (TermDepositAccount deposit : accounts.findAllOfType(TermDepositAccount.class)) {
            if (deposit.isClosed() || !deposit.isMaturedOn(date)) {
                continue;
            }
            try {
                accountService.settleTermDeposit(deposit.getId());
                settled++;
            } catch (BankingException e) {
                errors.add(deposit.getId() + ": " + e.getMessage());
            }
        }
        return settled;
    }
}
