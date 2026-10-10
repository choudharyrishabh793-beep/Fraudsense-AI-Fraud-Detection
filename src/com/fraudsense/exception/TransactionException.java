package com.fraudsense.exception;

/** Custom checked exception for invalid transaction data (EXCEPTION HANDLING). */
public class TransactionException extends Exception {
    public TransactionException(String message) { super(message); }
}