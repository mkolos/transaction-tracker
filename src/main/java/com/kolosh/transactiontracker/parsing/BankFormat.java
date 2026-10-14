package com.kolosh.transactiontracker.parsing;

import java.util.List;
import java.util.Map;

public interface BankFormat {

    String name();

    List<String> requiredColumns();

    int detect(List<String> headers);

    /** Convert one record (header name to cell value) or throw {@link InvalidRowException}. */
    NormalizedRow parse(Map<String, String> record);
}
