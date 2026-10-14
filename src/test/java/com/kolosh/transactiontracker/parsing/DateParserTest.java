package com.kolosh.transactiontracker.parsing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DateParserTest {

    private final DateParser austrian = new DateParser("dd.MM.uuuu");
    private final DateParser timestamp = new DateParser("uuuu-MM-dd HH:mm:ss");
    private final DateParser hungarian = DateParser.allowingTrailingText("uuuu.MM.dd.");

    @Test
    void parsesPlainDate() {
        assertThat(austrian.parse("Buchungsdatum", "31.08.2026")).isEqualTo(LocalDate.of(2026, 8, 31));
        assertThat(austrian.parse("Buchungsdatum", " 01.09.2026 ")).isEqualTo(LocalDate.of(2026, 9, 1));
    }

    @Test
    void timestampPatternYieldsItsDate() {
        assertThat(timestamp.parse("Completed Date", "2026-08-02 23:59:59")).isEqualTo(LocalDate.of(2026, 8, 2));
    }

    @Test
    void hungarianDateIgnoresWeekdaySuffix() {
        assertThat(hungarian.parse("Könyvelés dátuma", "2026.08.31., hétfő")).isEqualTo(LocalDate.of(2026, 8, 31));
        assertThat(hungarian.parse("Könyvelés dátuma", "2026.08.27., csütörtök")).isEqualTo(LocalDate.of(2026, 8, 27));
        assertThat(hungarian.parse("Könyvelés dátuma", "2026.08.31.")).isEqualTo(LocalDate.of(2026, 8, 31));
    }

    @ParameterizedTest
    @ValueSource(strings = {"31.02.2026", "32.01.2026", "00.01.2026", "31.13.2026", "2026-08-31", "31/08/2026",
            "31.8.2026", "31.08.26", "31.08.2026 extra", "abc"})
    void strictParserRejectsInvalidAndSloppyDates(String input) {
        assertThatThrownBy(() -> austrian.parse("Buchungsdatum", input))
                .isInstanceOf(InvalidRowException.class)
                .hasMessage("Invalid Buchungsdatum '" + input + "'");
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026.02.30., kedd", "2026.08.31.xyz", "08.31.2026., kedd", "kedd"})
    void trailingTextParserStillRejectsBadDatesAndGlue(String input) {
        assertThatThrownBy(() -> hungarian.parse("Könyvelés dátuma", input)).isInstanceOf(InvalidRowException.class);
    }

    @Test
    void blankIsReportedAsEmpty() {
        assertThatThrownBy(() -> austrian.parse("Buchungsdatum", "  "))
                .isInstanceOf(InvalidRowException.class)
                .hasMessage("Buchungsdatum is empty");
    }
}
