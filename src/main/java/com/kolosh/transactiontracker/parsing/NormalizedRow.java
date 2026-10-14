package com.kolosh.transactiontracker.parsing;

import java.math.BigDecimal;
import java.time.LocalDate;

public record NormalizedRow(LocalDate bookedOn, BigDecimal amount, String currency, String description)
        implements ParsedRecord {
}
