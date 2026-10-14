package com.kolosh.transactiontracker.parsing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CurrencyCodeTest {

    @Test
    void normalizesCaseAndWhitespace() {
        assertThat(CurrencyCode.normalize("Currency", "EUR")).isEqualTo("EUR");
        assertThat(CurrencyCode.normalize("Currency", " huf ")).isEqualTo("HUF");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "EU", "EURO", "eur1", "€", "E R"})
    void rejectsAnythingThatIsNotThreeLetters(String input) {
        assertThatThrownBy(() -> CurrencyCode.normalize("Currency", input)).isInstanceOf(InvalidRowException.class);
    }

    @Test
    void messagesNameTheColumn() {
        assertThatThrownBy(() -> CurrencyCode.normalize("Währung", "eur1")).hasMessage("Invalid Währung 'eur1'");
        assertThatThrownBy(() -> CurrencyCode.normalize("Währung", "")).hasMessage("Währung is empty");
    }
}
