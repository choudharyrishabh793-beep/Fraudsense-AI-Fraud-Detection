package com.fraudsense.exception;

/** Wraps every SQLException so failures are descriptive (EXCEPTION HANDLING). */
public class DatabaseException extends Exception {
    public DatabaseException(String message, Throwable cause) { super(message, cause); }
}