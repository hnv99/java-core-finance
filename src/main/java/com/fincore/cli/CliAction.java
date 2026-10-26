package com.fincore.cli;

import com.fincore.exception.BankingException;

/**
 * A menu action. The standard {@link Runnable} cannot throw checked exceptions,
 * so this custom functional interface declares {@link BankingException}.
 */
@FunctionalInterface
public interface CliAction {

    void execute() throws BankingException;
}
