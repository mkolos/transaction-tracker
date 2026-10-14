package com.kolosh.transactiontracker.parsing;

import java.util.List;

public class PermanentParseException extends RuntimeException {

    private final List<String> missingColumns;

    public PermanentParseException(String message) {
        this(message, List.of());
    }

    public PermanentParseException(String message, List<String> missingColumns) {
        super(message);
        this.missingColumns = List.copyOf(missingColumns);
    }

    public List<String> missingColumns() {
        return missingColumns;
    }
}
