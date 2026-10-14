package com.kolosh.transactiontracker.parsing;

/** A single row is bad. The parser records a {@link RowError} and keeps going. */
public class InvalidRowException extends RuntimeException {
    public InvalidRowException(String message) {
        super(message);
    }
}
