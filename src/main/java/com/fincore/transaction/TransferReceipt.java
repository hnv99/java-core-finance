package com.fincore.transaction;

/** Result of a successful transfer: one shared reference and the two ledger legs. */
public record TransferReceipt(String reference, Transaction debit, Transaction credit) {
}
