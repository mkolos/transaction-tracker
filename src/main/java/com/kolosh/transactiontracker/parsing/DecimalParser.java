package com.kolosh.transactiontracker.parsing;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strict decimal parser driven by a declared decimal separator and grouping separators. It never
 * guesses: "1,234" is 1234 with {@code (decimal '.', grouping ',')} and 1.234 with
 * {@code (decimal ',', grouping '.')}. Input that does not fit the declared shape is rejected
 * instead of silently misread, because a wrong amount is worse than a visible import error.
 * <p>
 * Accepted: optional sign, then either plain digits or digits in groups of exactly three
 * ("1.234.567"), then an optional fraction of at least one digit. Grouping is optional, since
 * exports mix "2500,00" and "2.500,00". The scale as written is kept ("1,50" gives 1.50).
 */
public final class DecimalParser {

    private final Pattern pattern;

    public DecimalParser(char decimalSeparator, char... groupingSeparators) {
        for (char g : groupingSeparators) {
            if (g == decimalSeparator) {
                throw new IllegalArgumentException("Decimal separator cannot also be a grouping separator: " + g);
            }
        }
        StringBuilder groupClass = new StringBuilder();
        for (char g : groupingSeparators) {
            groupClass.append(escape(g));
        }
        String grouped = groupingSeparators.length == 0
                ? "\\d+"
                : "(?:\\d{1,3}(?:[" + groupClass + "]\\d{3})+|\\d+)";
        this.pattern = Pattern.compile("([+-]?)(" + grouped + ")(?:" + escape(decimalSeparator) + "(\\d+))?");
    }

    public BigDecimal parse(String column, String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidRowException(column + " is empty");
        }
        Matcher m = pattern.matcher(value.strip());
        if (!m.matches()) {
            throw new InvalidRowException("Invalid " + column + " '" + value + "'");
        }
        String integerPart = m.group(2).replaceAll("[^0-9]", "");
        String fraction = m.group(3) == null ? "" : "." + m.group(3);
        return new BigDecimal(m.group(1).replace("+", "") + integerPart + fraction);
    }

    private static String escape(char c) {
        return "\\x{" + Integer.toHexString(c) + "}";
    }
}
