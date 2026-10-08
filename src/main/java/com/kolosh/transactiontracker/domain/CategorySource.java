package com.kolosh.transactiontracker.domain;

/** Who assigned a transaction's category. Mirrors the CHECK constraint in the schema. */
public enum CategorySource {
    NONE,
    RULE,
    MANUAL
}
