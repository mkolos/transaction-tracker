package com.kolosh.transactiontracker.parsing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class RevolutFormatTest {

    private final RevolutFormat format = new RevolutFormat();

    private static Map<String, String> record(String... overrides) {
        Map<String, String> r = new HashMap<>(Map.of(
                "Type", "Card Payment", "Product", "Current",
                "Started Date", "2026-08-01 12:00:00", "Completed Date", "2026-08-02 09:00:00",
                "Description", "Sample Cafe", "Amount", "-4.50", "Fee", "0.00",
                "Currency", "EUR", "State", "COMPLETED", "Balance", "95.50"));
        for (int i = 0; i < overrides.length; i += 2) {
            r.put(overrides[i], overrides[i + 1]);
        }
        return r;
    }

    @Test
    void usesCompletedDateAsBookingDate() {
        NormalizedRow row = format.parse(record("Started Date", "2026-07-31 23:59:00"));

        assertThat(row.bookedOn()).isEqualTo(LocalDate.of(2026, 8, 2));
    }

    @Test
    void subtractsFeeFromAmount() {
        NormalizedRow row = format.parse(record("Amount", "-19453.31", "Fee", "194.53"));

        assertThat(row.amount()).isEqualByComparingTo("-19647.84");
    }

    @Test
    void chargeRowWithZeroAmountBecomesNegativeFee() {
        NormalizedRow row = format.parse(record("Type", "Charge", "Amount", "0.00", "Fee", "3000.00", "Currency", "HUF"));

        assertThat(row.amount()).isEqualByComparingTo("-3000.00");
        assertThat(row.currency()).isEqualTo("HUF");
    }

    @Test
    void positiveAmountMinusFee() {
        assertThat(format.parse(record("Amount", "100.00", "Fee", "0.50")).amount()).isEqualByComparingTo("99.50");
    }

    @Test
    void trimsDescription() {
        assertThat(format.parse(record("Description", "  WIEN ENERGIE  ")).description()).isEqualTo("WIEN ENERGIE");
    }

    @Test
    void rejectsNonCompletedStates() {
        for (String state : List.of("PENDING", "REVERTED", "DECLINED", "")) {
            assertThatThrownBy(() -> format.parse(record("State", state)))
                    .isInstanceOf(InvalidRowException.class)
                    .hasMessageContaining("State");
        }
    }

    @Test
    void rejectsBadValues() {
        Stream.of(
                record("Completed Date", ""),
                record("Completed Date", "2026-13-45 00:00:00"),
                record("Completed Date", "02/08/2026"),
                record("Amount", "twelve"),
                record("Amount", ""),
                record("Fee", "abc"),
                record("Description", "   "),
                record("Currency", "eur1"),
                record("Currency", "")
        ).forEach(bad -> assertThatThrownBy(() -> format.parse(bad)).isInstanceOf(InvalidRowException.class));
    }

    @Test
    void detectsOwnHeaderOnly() {
        List<String> revolut = List.of("Type", "Product", "Started Date", "Completed Date", "Description",
                "Amount", "Fee", "Currency", "State", "Balance");

        assertThat(format.detect(revolut)).isPositive();
        assertThat(format.detect(List.of("Datum", "Betrag"))).isZero();
    }
}
