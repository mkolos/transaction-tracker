package com.kolosh.transactiontracker.parsing;

import java.util.regex.Pattern;

public final class CurrencyCode {

    private static final Pattern THREE_LETTERS = Pattern.compile("[A-Z]{3}");

    private CurrencyCode() {
    }

    public static String normalize(String column, String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidRowException(column + " is empty");
        }
        String code = value.strip().toUpperCase(java.util.Locale.ROOT);
        if (!THREE_LETTERS.matcher(code).matches()) {
            throw new InvalidRowException("Invalid " + column + " '" + value + "'");
        }
        return code;
    }
}
