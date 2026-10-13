package com.kolosh.transactiontracker.service;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionFilter(
        LocalDate from,
        LocalDate to,
        Long categoryId,
        String currency,
        String q,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        boolean uncategorized) {
}
