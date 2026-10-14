package com.kolosh.transactiontracker.parsing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class DecimalParserTest {

    private static final DecimalParser US = new DecimalParser('.', ',');
    private static final DecimalParser EU = new DecimalParser(',', '.');
    /** Raiffeisen groups thousands with a non-breaking space; be lenient about the other space flavours. */
    private static final DecimalParser HU = new DecimalParser(',', '.', ' ', ' ', ' ');

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {"1234.56|1234.56", "-1,234.56|-1234.56", "+5|5", "0.50|0.50", "-0.01|-0.01",
            "1,000,000.5|1000000.5"})
    void parsesUsStyle(String input, String expected) {
        assertThat(US.parse("Amount", input)).isEqualByComparingTo(expected);
    }

    @Test
    void parsesEuropeanStyle() {
        assertThat(EU.parse("Betrag", "-58,00")).isEqualByComparingTo("-58.00");
        assertThat(EU.parse("Betrag", "3.978,99")).isEqualByComparingTo("3978.99");
        assertThat(EU.parse("Betrag", "-1.100,00")).isEqualByComparingTo("-1100.00");
        assertThat(EU.parse("Betrag", "2500,00")).isEqualByComparingTo("2500.00");
        assertThat(EU.parse("Betrag", "2500")).isEqualByComparingTo("2500");
    }

    @Test
    void parsesSpaceGroupedHungarianStyle() {
        assertThat(HU.parse("Összeg", "-20 000,00")).isEqualByComparingTo("-20000.00");
        assertThat(HU.parse("Összeg", "-1 234 567,89")).isEqualByComparingTo("-1234567.89");
        assertThat(HU.parse("Összeg", "150 000,00")).isEqualByComparingTo("150000.00");
        assertThat(HU.parse("Összeg", "-400,00")).isEqualByComparingTo("-400.00");
    }

    @Test
    void keepsTheWrittenScale() {
        assertThat(EU.parse("Betrag", "1,50").toPlainString()).isEqualTo("1.50");
        assertThat(EU.parse("Betrag", "1,5").toPlainString()).isEqualTo("1.5");
    }

    @Test
    void sameTextMeansWhatTheProfileDeclares() {
        assertThat(US.parse("Amount", "1,234")).isEqualByComparingTo("1234");
        assertThat(EU.parse("Amount", "1.234")).isEqualByComparingTo("1234");
        assertThat(EU.parse("Amount", "1,234")).isEqualByComparingTo("1.234");
        assertThat(US.parse("Amount", "1.234")).isEqualByComparingTo("1.234");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "abc", "1.234.56", "12.34,5", "1.23.456", ",5", "5,", "--5", "+-5", "1e5",
            "NaN", "1 2", "1.2345,00", "12.345.67", "5 EUR", "-", "1,,5"})
    void europeanStyleRejectsAmbiguousOrMalformedInput(String input) {
        assertThatThrownBy(() -> EU.parse("Betrag", input))
                .isInstanceOf(InvalidRowException.class)
                .hasMessageContaining("Betrag");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1,23,456", "1,2345", ".5", "5.", "1.2.3", "1,000,00"})
    void usStyleRejectsMalformedGrouping(String input) {
        assertThatThrownBy(() -> US.parse("Amount", input)).isInstanceOf(InvalidRowException.class);
    }

    @Test
    void messageQuotesTheOffendingValue() {
        assertThatThrownBy(() -> EU.parse("Betrag", "twelve"))
                .hasMessage("Invalid Betrag 'twelve'");
        assertThatThrownBy(() -> EU.parse("Betrag", " "))
                .hasMessage("Betrag is empty");
    }

    @Test
    void decimalSeparatorCannotAlsoBeGrouping() {
        assertThatThrownBy(() -> new DecimalParser(',', ',')).isInstanceOf(IllegalArgumentException.class);
    }
}
