package com.kolosh.transactiontracker.parsing;

import java.text.ParsePosition;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.TemporalAccessor;

public final class DateParser {

    private final DateTimeFormatter formatter;
    private final boolean allowTrailingText;

    public DateParser(String pattern) {
        this(pattern, false);
    }

    private DateParser(String pattern, boolean allowTrailingText) {
        this.formatter = DateTimeFormatter.ofPattern(pattern).withResolverStyle(ResolverStyle.STRICT);
        this.allowTrailingText = allowTrailingText;
    }

    public static DateParser allowingTrailingText(String pattern) {
        return new DateParser(pattern, true);
    }

    public LocalDate parse(String column, String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidRowException(column + " is empty");
        }
        String text = value.strip();
        try {
            TemporalAccessor parsed;
            if (allowTrailingText) {
                ParsePosition position = new ParsePosition(0);
                parsed = formatter.parse(text, position);
                String rest = text.substring(position.getIndex());
                if (!rest.isEmpty() && rest.charAt(0) != ',' && !Character.isWhitespace(rest.charAt(0))) {
                    throw invalid(column, value);
                }
            } else {
                parsed = formatter.parse(text);
            }
            return LocalDate.from(parsed);
        } catch (DateTimeParseException e) {
            throw invalid(column, value);
        }
    }

    private static InvalidRowException invalid(String column, String value) {
        return new InvalidRowException("Invalid " + column + " '" + value + "'");
    }
}
