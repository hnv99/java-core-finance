package com.fincore.cli;

import com.fincore.account.ProductCatalog;
import com.fincore.common.Money;

import java.io.PrintStream;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Currency;
import java.util.Locale;
import java.util.Scanner;
import java.util.function.Function;

/**
 * Console input/output helpers. All prompts re-ask until the user enters a valid value,
 * so the rest of the CLI never handles malformed input.
 */
public final class ConsoleIO {

    /** Thrown when standard input is closed (Ctrl+Z / Ctrl+D or end of piped input). */
    public static final class EndOfInputException extends RuntimeException {
        EndOfInputException() {
            super("End of input");
        }
    }

    private final Scanner scanner;
    private final PrintStream out;

    public ConsoleIO(Scanner scanner, PrintStream out) {
        this.scanner = scanner;
        this.out = out;
    }

    public String readLine(String prompt) {
        out.print(prompt);
        out.flush();
        if (!scanner.hasNextLine()) {
            throw new EndOfInputException();
        }
        return scanner.nextLine().trim();
    }

    public String readRequired(String prompt) {
        return readValid(prompt, value -> {
            if (value.isEmpty()) {
                throw new IllegalArgumentException("A value is required");
            }
            return value;
        });
    }

    public String readOptional(String prompt) {
        return readLine(prompt);
    }

    public int readInt(String prompt, int min, int max) {
        return readValid(prompt, value -> {
            int number;
            try {
                number = Integer.parseInt(value);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Please enter a whole number");
            }
            if (number < min || number > max) {
                throw new IllegalArgumentException("Please enter a number between %d and %d".formatted(min, max));
            }
            return number;
        });
    }

    public LocalDate readDate(String prompt) {
        return readValid(prompt + " (yyyy-MM-dd): ", LocalDate::parse);
    }

    public YearMonth readYearMonth(String prompt, YearMonth defaultValue) {
        return readValid("%s (yyyy-MM, Enter for %s): ".formatted(prompt, defaultValue),
                value -> value.isEmpty() ? defaultValue : YearMonth.parse(value));
    }

    public Money readMoney(String prompt, Currency currency) {
        return readValid("%s (%s): ".formatted(prompt, currency.getCurrencyCode()), value -> {
            Money money = Money.parse(value, currency);
            if (!money.isPositive()) {
                throw new IllegalArgumentException("Amount must be greater than zero");
            }
            return money;
        });
    }

    public Currency readCurrency(String prompt) {
        return readValid(prompt + " (VND/USD): ",
                value -> ProductCatalog.requireSupported(Currency.getInstance(value.toUpperCase(Locale.ROOT))));
    }

    /** Generic enum picker: works for any enum type, e.g. {@code readChoice("Type", AccountType.values())}. */
    public <E extends Enum<E>> E readChoice(String prompt, E[] options) {
        for (int i = 0; i < options.length; i++) {
            out.printf("  %d) %s%n", i + 1, options[i]);
        }
        return options[readInt(prompt + ": ", 1, options.length) - 1];
    }

    public boolean confirm(String prompt) {
        String answer = readLine(prompt + " [y/N]: ");
        return answer.equalsIgnoreCase("y") || answer.equalsIgnoreCase("yes");
    }

    /**
     * Higher-order helper: keeps asking until {@code parser} accepts the input.
     * Parsers signal invalid input by throwing an unchecked exception.
     */
    public <T> T readValid(String prompt, Function<String, T> parser) {
        while (true) {
            String input = readLine(prompt);
            try {
                return parser.apply(input);
            } catch (DateTimeParseException e) {
                out.println("  Invalid date format.");
            } catch (IllegalArgumentException e) {
                out.println("  " + e.getMessage());
            }
        }
    }

    public void println(String text) {
        out.println(text);
    }

    public void println() {
        out.println();
    }

    /** Varargs: accepts any number of arguments, like {@link PrintStream#printf}. */
    public void printf(String format, Object... args) {
        out.printf(format, args);
    }
}
