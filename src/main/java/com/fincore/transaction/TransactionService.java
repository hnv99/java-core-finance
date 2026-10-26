package com.fincore.transaction;

import com.fincore.account.Account;
import com.fincore.account.AccountRepository;
import com.fincore.common.BusinessCalendar;
import com.fincore.common.IdGenerator;
import com.fincore.common.Money;
import com.fincore.exception.AccountNotFoundException;
import com.fincore.exception.AccountStateException;
import com.fincore.exception.BankingException;
import com.fincore.exception.InvalidTransactionException;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Moves money and writes the ledger.
 * <p>
 * Every balance change and its ledger entry happen while holding the account lock, so a
 * concurrent operation never sees a balance without its matching transaction.
 */
public class TransactionService {

    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final IdGenerator ids;
    private final BusinessCalendar calendar;
    /** CopyOnWriteArrayList: listeners are rarely added but iterated on every posting. */
    private final List<TransactionListener> listeners = new CopyOnWriteArrayList<>();

    public TransactionService(AccountRepository accounts, TransactionRepository transactions,
                              IdGenerator ids, BusinessCalendar calendar) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.ids = ids;
        this.calendar = calendar;
    }

    public void addListener(TransactionListener listener) {
        listeners.add(listener);
    }

    public Transaction deposit(String accountNumber, Money amount, String description) throws BankingException {
        Account account = findAccount(accountNumber);
        Transaction transaction;
        account.getLock().lock();
        try {
            account.deposit(amount);
            transaction = record(account, TransactionType.DEPOSIT, amount, description, null);
        } finally {
            account.getLock().unlock();
        }
        publish(transaction);
        return transaction;
    }

    public Transaction withdraw(String accountNumber, Money amount, String description) throws BankingException {
        Account account = findAccount(accountNumber);
        Transaction transaction;
        account.getLock().lock();
        try {
            account.withdraw(amount);
            transaction = record(account, TransactionType.WITHDRAWAL, amount, description, null);
        } finally {
            account.getLock().unlock();
        }
        publish(transaction);
        return transaction;
    }

    /**
     * Internal transfer between two accounts of this bank. Either both legs are posted or none.
     */
    public TransferReceipt transfer(String fromNumber, String toNumber, Money amount, String description)
            throws BankingException {
        if (fromNumber.equals(toNumber)) {
            throw new InvalidTransactionException("Source and destination accounts must be different");
        }
        Account from = findAccount(fromNumber);
        Account to = findAccount(toNumber);
        if (!from.getCurrency().equals(to.getCurrency())) {
            throw new InvalidTransactionException("Cross-currency transfers are not supported (%s -> %s)"
                    .formatted(from.getCurrency(), to.getCurrency()));
        }

        // Deadlock prevention: always acquire locks in the same global order (by account number),
        // so transfers A->B and B->A running at the same time cannot wait on each other forever.
        Account first = from.getId().compareTo(to.getId()) < 0 ? from : to;
        Account second = first == from ? to : from;

        TransferReceipt receipt;
        first.getLock().lock();
        try {
            second.getLock().lock();
            try {
                if (!to.getStatus().allowsCredit()) {
                    throw new AccountStateException("Destination account %s is %s".formatted(to.getId(), to.getStatus()));
                }
                from.withdraw(amount);
                try {
                    to.deposit(amount);
                } catch (BankingException e) {
                    // Compensating action: return the funds so no money disappears.
                    from.postSystemCredit(amount);
                    throw e;
                }
                String reference = ids.nextReference();
                String memo = description == null || description.isBlank() ? "Transfer" : description;
                Transaction debit = record(from, TransactionType.TRANSFER_OUT, amount,
                        memo + " to " + to.getId(), reference);
                Transaction credit = record(to, TransactionType.TRANSFER_IN, amount,
                        memo + " from " + from.getId(), reference);
                receipt = new TransferReceipt(reference, debit, credit);
            } finally {
                second.getLock().unlock();
            }
        } finally {
            first.getLock().unlock();
        }
        publish(receipt.debit());
        publish(receipt.credit());
        return receipt;
    }

    /** Bank-initiated credit (interest, payouts) that bypasses customer product rules. */
    public Transaction postSystemCredit(Account account, TransactionType type, Money amount,
                                        String description, String reference) throws AccountStateException {
        requireDirection(type, TransactionType.Direction.CREDIT);
        Transaction transaction;
        account.getLock().lock();
        try {
            account.postSystemCredit(amount);
            transaction = record(account, type, amount, description, reference);
        } finally {
            account.getLock().unlock();
        }
        publish(transaction);
        return transaction;
    }

    /** Bank-initiated debit (fees, payouts) that bypasses customer product rules. */
    public Transaction postSystemDebit(Account account, TransactionType type, Money amount,
                                       String description, String reference) throws AccountStateException {
        requireDirection(type, TransactionType.Direction.DEBIT);
        Transaction transaction;
        account.getLock().lock();
        try {
            account.postSystemDebit(amount);
            transaction = record(account, type, amount, description, reference);
        } finally {
            account.getLock().unlock();
        }
        publish(transaction);
        return transaction;
    }

    public List<Transaction> history(String accountNumber) throws AccountNotFoundException {
        findAccount(accountNumber);
        return transactions.findByAccount(accountNumber);
    }

    private Account findAccount(String accountNumber) throws AccountNotFoundException {
        return accounts.findById(accountNumber.trim())
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));
    }

    private Transaction record(Account account, TransactionType type, Money amount,
                               String description, String reference) {
        Transaction transaction = new Transaction(ids.nextTransactionId(), account.getId(), type, amount,
                account.getBalance(), calendar.today(), Instant.now(), description, reference);
        transactions.append(transaction);
        return transaction;
    }

    /** Listeners run after locks are released, so slow listeners never block other postings. */
    private void publish(Transaction transaction) {
        listeners.forEach(listener -> listener.onTransaction(transaction));
    }

    private static void requireDirection(TransactionType type, TransactionType.Direction expected) {
        if (type.direction() != expected) {
            throw new IllegalArgumentException(type + " is not a " + expected + " transaction type");
        }
    }
}
