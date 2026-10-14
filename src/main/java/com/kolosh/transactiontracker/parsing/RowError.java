package com.kolosh.transactiontracker.parsing;

/** A row that could not be turned into a {@link NormalizedRow}. Later persisted as an import_errors row. */
public record RowError(long rowNumber, String rawRow, String reason) implements ParsedRecord {
}
