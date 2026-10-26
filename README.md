# FinCore Banking CLI

A mini **core banking** teller console written in plain Java 21 (no frameworks, no external libraries).
It is built for learning Java Core and OOP. The business rules follow how real banks actually work.

## Features

| Area | What it does | Real-world rule implemented |
|------|--------------|-----------------------------|
| Customers | Register, list, update contact, portfolio | Minimum age 18, unique email, email/phone validation |
| Checking account | Deposit, withdraw, transfer | Approved overdraft line; monthly maintenance fee waived above a balance threshold |
| Savings account | Deposit, withdraw, transfer | Daily interest accrual, monthly interest credit, max 6 withdrawals/month, minimum balance |
| Term deposit | Open from own account, settle | Fixed principal, contract rate at maturity, **demand rate on early closure**, payout to settlement account |
| Account lifecycle | Freeze / unfreeze / close | FROZEN = debit block (credits still accepted); closing pays out remaining balance |
| Transfers | Internal transfers | Same currency only, atomic (both legs or none), shared reference number |
| End-of-Day batch | Advance the business date | Accrual, month-end interest & fees, term deposit maturity |
| Statements | Monthly statement, export to file | Opening/closing balance, running balance per line |
| Compliance | Large cash transaction alerts | Cash >= 400,000,000 VND or 10,000 USD is logged to `compliance-alerts.log` |
| Persistence | CSV files in `data/` | Atomic write (temp file + move) so a crash never corrupts data |

Interest uses the **Actual/365** day-count convention and `BigDecimal` with banker's rounding.
Rates in `ProductCatalog` are illustrative.

## Run

Requires JDK 21.

```bash
./run.sh            # Linux / macOS / Git Bash
```

```powershell
.\run.ps1           # Windows PowerShell
```

Or manually:

```bash
javac -encoding UTF-8 --release 21 -d out $(find src/main/java -name '*.java')
java -cp out com.fincore.App
```

You can also open the folder in IntelliJ IDEA (it reads `pom.xml`) and run `com.fincore.App`.

On first start the app offers to create demo data (2 customers, 5 accounts).
An optional argument sets the data directory: `java -cp out com.fincore.App my-data`.

### Suggested walkthrough

1. `8` List all accounts.
2. `12` Withdraw more than the available balance and see the rejection.
3. `9` Freeze an account, then try to withdraw (rejected) and deposit (accepted).
4. `16` Run End-of-Day for 40 days. Savings interest and checking fees are posted at each month end.
5. `14` / `15` View the history or the monthly statement of the savings account.
6. `16` Run ~190 more days. The 6-month term deposit matures and is paid into the checking account.

## Project structure

```
src/main/java/com/fincore
├── App.java                     Entry point, wires all objects together (composition root)
├── DemoDataSeeder.java          Sample data
├── common/                      Money, BusinessCalendar, IdGenerator, generic Repository
├── exception/                   Checked business exceptions + unchecked DataAccessException
├── customer/                    Customer entity, repository, service
├── account/                     Account hierarchy, ProductCatalog, AccountService
├── transaction/                 Transaction record, ledger, TransactionService, listeners
├── statement/                   Monthly statements
├── batch/                       End-of-Day processor
├── persistence/                 CSV storage
└── cli/                         Console UI
```

Layers: `cli` -> services -> domain objects -> repositories. The CLI contains no business rules.

## Java concepts: where to find them

### The four OOP pillars

| Concept | Where |
|---------|-------|
| **Encapsulation** | `Account`: `balance` is private with no setter, it only changes via `deposit`/`withdraw`. `Customer.updateContact` validates both fields before changing either |
| **Abstraction** | `Account` is abstract; `InterestBearing` and `Repository` are interfaces |
| **Inheritance** | `CheckingAccount`, `SavingsAccount`, `TermDepositAccount` extend `Account`; exception hierarchy under `BankingException` |
| **Polymorphism** | `validateWithdrawal` and `getAvailableBalance` are overridden per product; the same `withdraw` call behaves differently |

### Language features

| Concept | Where |
|---------|-------|
| Sealed classes (17) | `Account ... permits CheckingAccount, SavingsAccount, TermDepositAccount` |
| Pattern matching `switch` (21) | `EndOfDayProcessor.processAccount`, `CsvDataStore.accountToRow`, `BankingCli.printAccountDetails` (exhaustive, no `default`) |
| `instanceof` pattern | `AccountService.settleTermDeposit`, `Account.equals` |
| Records + compact constructors | `Money`, `Transaction`, `Statement`, `TransferReceipt`, `EodReport` |
| Nested records / enums / classes | `ProductCatalog.CheckingTerms`, `TransactionType.Direction`, `ConsoleIO.EndOfInputException` |
| Enums with fields and behavior | `AccountStatus.allowsDebit()`, `TransactionType.signed()`, `AccountType` |
| Interfaces: constants, `default`, `static` | `InterestBearing` |
| Generics: bounded types | `Repository<T extends Identifiable<ID>, ID>`, `InMemoryRepository` |
| Generic methods | `AccountRepository.findAllOfType(Class<T>)`, `ConsoleIO.readChoice`, `CsvDataStore.writeCsv` |
| Functional interfaces & lambdas | `TransactionListener`, `CliAction` (custom: can throw a checked exception), `ConsoleIO.readValid(Function)` |
| Method references | `Money::plus`, `this::registerCustomer`, `type::isInstance` |
| Text blocks | `BankingCli.run` banner, `StatementService.render` |
| `var` | `App.main`, `AccountService.openChecking` |
| Varargs | `ConsoleIO.printf(String, Object...)` |
| `final` / immutability / defensive copies | `Money`, `List.copyOf` in records and repositories |
| `equals` / `hashCode` / `toString` | `Customer`, `Account` (identity-based) |
| `static` utility class | `CsvCodec`, `Formats` (private constructor) |

### Core APIs

| Concept | Where |
|---------|-------|
| `BigDecimal`, `RoundingMode`, `MathContext` | `Money`, `InterestBearing`, `SavingsAccount` |
| Collections: `List`, `Map`, `Set`, `SortedMap`, `Queue` | Repositories, `ProductCatalog`, `EndOfDayProcessor` |
| Streams: `filter`, `map`, `reduce`, `toMap` with merge | `StatementService`, `AccountService.totalBalanceByCurrency` |
| `Optional` | `Repository.findById`, `Transaction.referenceOptional` |
| Sequenced collections (21) | `List.getLast()` in `StatementService.generate` |
| `java.time` | `LocalDate`, `YearMonth`, `Period`, `ChronoUnit`, `Instant`, `TemporalAdjusters` |
| NIO files, try-with-resources | `CsvDataStore`, `StatementService.export` |
| `Properties` | `CsvDataStore` system state |
| Regex | `Customer` email/phone validation |
| Logging | `LargeCashTransactionMonitor` (`java.util.logging`) |

### Exceptions

| Concept | Where |
|---------|-------|
| Custom checked exceptions | `BankingException` and subclasses: business failures the caller must handle |
| Custom unchecked exception | `DataAccessException` wraps `IOException` (cause preserved) |
| Multi-catch | `EndOfDayProcessor.run`: `catch (BankingException \| RuntimeException e)` |
| Centralized handling | `BankingCli.execute` |
| Compensating action | `TransactionService.transfer`, `AccountService.openTermDeposit` |

### Concurrency

| Concept | Where |
|---------|-------|
| Virtual threads (21) | `EndOfDayProcessor`: `Executors.newVirtualThreadPerTaskExecutor()` |
| `ExecutorService` as `AutoCloseable` | `EndOfDayProcessor.run` |
| `ReentrantLock` + deadlock-free lock ordering | `TransactionService.transfer` |
| `synchronized` | `BusinessCalendar`, `TransactionRepository` |
| Atomics & concurrent collections | `IdGenerator` (`AtomicLong`), `ConcurrentHashMap`, `CopyOnWriteArrayList`, `ConcurrentLinkedQueue` |

### Design patterns

| Pattern | Where |
|---------|-------|
| Template Method | `Account.withdraw` / `deposit` + hooks |
| Observer | `TransactionListener`, `LargeCashTransactionMonitor` |
| Repository | `Repository`, `InMemoryRepository` |
| Service layer | `*Service` classes |
| Dependency Injection (manual, constructor-based) | `App.main` |
| Static factory methods | `Money.of`, `Money.zero`, `Money.parse` |

## Simplifications compared to a production system

These are deliberate, so the code stays readable:

- Single-user console, no authentication or role-based access.
- No overdraft interest; fees are charged on the month-end balance rather than the average daily balance.
- No foreign exchange; transfers must be in the same currency.
- Term deposits are not renewed automatically at maturity.
- CSV files instead of a relational database (the `Repository` interface makes swapping easy).
- No unit tests are included yet. JUnit 5 would be the next step.
