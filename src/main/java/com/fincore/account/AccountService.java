package com.fincore.account;

import com.fincore.common.BusinessCalendar;
import com.fincore.common.IdGenerator;
import com.fincore.common.Money;
import com.fincore.customer.Customer;
import com.fincore.customer.CustomerService;
import com.fincore.exception.AccountNotFoundException;
import com.fincore.exception.AccountStateException;
import com.fincore.exception.BankingException;
import com.fincore.exception.BusinessRuleException;
import com.fincore.transaction.TransactionService;
import com.fincore.transaction.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Account lifecycle: opening, freezing, closing and term deposit settlement. */
public class AccountService {

    private final AccountRepository accounts;
    private final CustomerService customerService;
    private final TransactionService transactionService;
    private final ProductCatalog catalog;
    private final IdGenerator ids;
    private final BusinessCalendar calendar;

    public AccountService(AccountRepository accounts, CustomerService customerService,
                          TransactionService transactionService, ProductCatalog catalog,
                          IdGenerator ids, BusinessCalendar calendar) {
        this.accounts = accounts;
        this.customerService = customerService;
        this.transactionService = transactionService;
        this.catalog = catalog;
        this.ids = ids;
        this.calendar = calendar;
    }

    public CheckingAccount openChecking(String customerId, Currency currency) throws BankingException {
        Customer owner = customerService.getById(customerId);
        ProductCatalog.CheckingTerms terms = catalog.checkingTerms(currency);
        var account = new CheckingAccount(nextNumber(AccountType.CHECKING), owner.getId(), currency,
                calendar.today(), Money.zero(currency), AccountStatus.ACTIVE,
                terms.overdraftLimit(), terms.monthlyFee(), terms.feeWaiverBalance());
        accounts.save(account);
        return account;
    }

    public SavingsAccount openSavings(String customerId, Currency currency) throws BankingException {
        Customer owner = customerService.getById(customerId);
        ProductCatalog.SavingsTerms terms = catalog.savingsTerms(currency);
        var account = new SavingsAccount(nextNumber(AccountType.SAVINGS), owner.getId(), currency,
                calendar.today(), Money.zero(currency), AccountStatus.ACTIVE,
                terms.annualRate(), terms.minimumBalance(), terms.maxWithdrawalsPerMonth(), 0, BigDecimal.ZERO);
        accounts.save(account);
        return account;
    }

    /**
     * Opens a term deposit funded from one of the customer's own accounts, which also
     * becomes the settlement account receiving principal and interest.
     */
    public TermDepositAccount openTermDeposit(String customerId, String fundingAccountNumber,
                                              Money principal, int termMonths) throws BankingException {
        Customer owner = customerService.getById(customerId);
        Account funding = getAccount(fundingAccountNumber);
        if (!funding.getOwnerId().equals(owner.getId())) {
            throw new BusinessRuleException("Funding account must belong to the same customer");
        }
        if (funding instanceof TermDepositAccount) {
            throw new BusinessRuleException("A term deposit cannot be funded from another term deposit");
        }
        Currency currency = funding.getCurrency();
        ProductCatalog.TermDepositTerms terms = catalog.termDepositTerms(currency);
        if (!principal.currency().equals(currency)) {
            throw new BusinessRuleException("Principal currency must match the funding account (" + currency + ")");
        }
        if (principal.isLessThan(terms.minimumPrincipal())) {
            throw new BusinessRuleException("Minimum principal is " + terms.minimumPrincipal());
        }
        BigDecimal rate = terms.rateFor(termMonths);
        LocalDate today = calendar.today();

        var deposit = new TermDepositAccount(nextNumber(AccountType.TERM_DEPOSIT), owner.getId(), currency,
                today, Money.zero(currency), AccountStatus.ACTIVE,
                principal, rate, terms.earlyWithdrawalRate(), termMonths,
                today.plusMonths(termMonths), funding.getId());
        accounts.save(deposit);
        try {
            transactionService.transfer(funding.getId(), deposit.getId(), principal,
                    "Term deposit funding (%d months)".formatted(termMonths));
        } catch (BankingException e) {
            // Funding failed (e.g. insufficient funds): the unfunded deposit must not remain.
            accounts.deleteById(deposit.getId());
            throw e;
        }
        return deposit;
    }

    /**
     * Pays out a term deposit to its settlement account and closes it. Used for early
     * closure by the customer and for automatic settlement at maturity.
     *
     * @return total amount paid out (principal + interest)
     */
    public Money settleTermDeposit(String accountNumber) throws BankingException {
        if (!(getAccount(accountNumber) instanceof TermDepositAccount deposit)) {
            throw new AccountStateException("Account %s is not a term deposit".formatted(accountNumber));
        }
        if (deposit.isClosed()) {
            throw new AccountStateException("Term deposit %s is already closed".formatted(accountNumber));
        }
        Account settlement = getAccount(deposit.getSettlementAccountNumber());
        if (!settlement.getStatus().allowsCredit()) {
            throw new AccountStateException("Settlement account %s is %s".formatted(settlement.getId(), settlement.getStatus()));
        }

        LocalDate today = calendar.today();
        boolean matured = deposit.isMaturedOn(today);
        String reference = ids.nextReference();
        Money interest = deposit.interestDueOn(today);
        if (interest.isPositive()) {
            transactionService.postSystemCredit(deposit, TransactionType.INTEREST, interest,
                    matured ? "Interest at maturity" : "Interest at demand rate (early closure)", reference);
        }
        Money payout = deposit.getBalance();
        transactionService.postSystemDebit(deposit, TransactionType.TRANSFER_OUT, payout,
                "Term deposit payout to " + settlement.getId(), reference);
        transactionService.postSystemCredit(settlement, TransactionType.TRANSFER_IN, payout,
                "Term deposit payout from " + deposit.getId(), reference);
        deposit.close();
        return payout;
    }

    /**
     * Closes a checking or savings account. Accrued savings interest is credited first,
     * then any remaining balance is paid out in cash, as a teller would do.
     *
     * @return the amount paid out to the customer
     */
    public Money closeAccount(String accountNumber) throws BankingException {
        Account account = getAccount(accountNumber);
        if (account.isClosed()) {
            throw new AccountStateException("Account %s is already closed".formatted(accountNumber));
        }
        if (account.getStatus() == AccountStatus.FROZEN) {
            throw new AccountStateException("Frozen accounts cannot be closed");
        }
        if (account instanceof TermDepositAccount) {
            throw new AccountStateException("Use term deposit settlement to close a term deposit");
        }
        boolean usedForSettlement = accounts.findAllOfType(TermDepositAccount.class).stream()
                .anyMatch(td -> !td.isClosed() && td.getSettlementAccountNumber().equals(accountNumber));
        if (usedForSettlement) {
            throw new AccountStateException("Account is the settlement account of an active term deposit");
        }
        if (account.getBalance().isNegative()) {
            throw new AccountStateException("Overdrawn account must be repaid before closing (balance %s)"
                    .formatted(account.getBalance()));
        }
        if (account instanceof SavingsAccount savings) {
            Money interest = savings.takeAccruedInterest();
            if (interest.isPositive()) {
                transactionService.postSystemCredit(savings, TransactionType.INTEREST, interest,
                        "Accrued interest on closure", null);
            }
        }
        Money payout = account.getBalance();
        if (payout.isPositive()) {
            transactionService.postSystemDebit(account, TransactionType.WITHDRAWAL, payout,
                    "Closing balance paid out", null);
        }
        account.close();
        return payout;
    }

    public void freeze(String accountNumber) throws BankingException {
        getAccount(accountNumber).freeze();
    }

    public void unfreeze(String accountNumber) throws BankingException {
        getAccount(accountNumber).unfreeze();
    }

    public Account getAccount(String accountNumber) throws AccountNotFoundException {
        return accounts.findById(accountNumber.trim())
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));
    }

    public List<Account> accountsOf(String customerId) throws BankingException {
        return accounts.findByOwner(customerService.getById(customerId).getId());
    }

    public List<Account> listAll() {
        return accounts.findAll().stream()
                .sorted(Comparator.comparing(Account::getId))
                .toList();
    }

    /** Sum of balances of the customer's open accounts, one total per currency. */
    public Map<Currency, Money> totalBalanceByCurrency(String customerId) throws BankingException {
        return accountsOf(customerId).stream()
                .filter(account -> !account.isClosed())
                .collect(Collectors.toMap(Account::getCurrency, Account::getBalance, Money::plus, LinkedHashMap::new));
    }

    public ProductCatalog getCatalog() {
        return catalog;
    }

    private String nextNumber(AccountType type) {
        return ids.nextAccountNumber(type.getNumberPrefix());
    }
}
