package com.kolosh.transactiontracker.parsing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Revolut account statement export (one file per currency).
 * <ul>
 *   <li>booked_on is the <b>Completed Date</b>: that is when the money moved. Started Date can be days earlier.</li>
 *   <li>amount = Amount - Fee. Revolut reports the fee separately, but the Balance column moves by both
 *       (verified against real exports), so a "Charge" row with Amount 0 and Fee 3000 is a -3000 transaction.</li>
 *   <li>Only COMPLETED rows are imported. PENDING rows have no Completed Date; REVERTED/DECLINED never moved money.</li>
 * </ul>
 */
public class RevolutFormat implements BankFormat {

    private static final List<String> REQUIRED = List.of(
            "Type", "Completed Date", "Description", "Amount", "Fee", "Currency", "State");
    private static final DateParser DATE = new DateParser("uuuu-MM-dd HH:mm:ss");
    private static final DecimalParser DECIMAL = new DecimalParser('.');

    @Override
    public String name() {
        return "revolut";
    }

    @Override
    public List<String> requiredColumns() {
        return REQUIRED;
    }

    @Override
    public int detect(List<String> headers) {
        return headers.containsAll(REQUIRED) ? REQUIRED.size() : 0;
    }

    @Override
    public NormalizedRow parse(Map<String, String> record) {
        String state = record.get("State").trim();
        if (!state.equals("COMPLETED")) {
            throw new InvalidRowException("State is '" + state + "', only COMPLETED rows are imported");
        }
        LocalDate bookedOn = DATE.parse("Completed Date", record.get("Completed Date"));
        BigDecimal amount = DECIMAL.parse("Amount", record.get("Amount"))
                .subtract(DECIMAL.parse("Fee", record.get("Fee")));
        String currency = CurrencyCode.normalize("Currency", record.get("Currency"));
        String description = record.get("Description").trim();
        if (description.isEmpty()) {
            throw new InvalidRowException("Description is empty");
        }
        return new NormalizedRow(bookedOn, amount, currency, description);
    }
}
