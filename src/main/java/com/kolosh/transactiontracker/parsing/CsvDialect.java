package com.kolosh.transactiontracker.parsing;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public record CsvDialect(Charset charset, char delimiter) {

    public static final CsvDialect UTF8_COMMA = new CsvDialect(StandardCharsets.UTF_8, ',');
}
