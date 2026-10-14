package com.kolosh.transactiontracker.parsing;

public sealed interface ParsedRecord permits NormalizedRow, RowError {
}
