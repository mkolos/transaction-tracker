package com.kolosh.transactiontracker.parsing;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

class CsvParserTest {

    private static final String HEADER =
            "Type,Product,Started Date,Completed Date,Description,Amount,Fee,Currency,State,Balance\n";
    private static final String GOOD =
            "Card Payment,Current,2026-08-01 12:00:00,2026-08-02 09:00:00,Sample Cafe,-4.50,0.00,EUR,COMPLETED,95.50\n";

    private final CsvParser parser = new CsvParser();
    private final BankFormat revolut = new RevolutFormat();

    private List<ParsedRecord> parse(InputStream in) {
        try (Stream<ParsedRecord> records = parser.parse(in, CsvDialect.UTF8_COMMA, revolut)) {
            return records.toList();
        }
    }

    private List<NormalizedRow> rows(List<ParsedRecord> records) {
        return records.stream().filter(NormalizedRow.class::isInstance).map(NormalizedRow.class::cast).toList();
    }

    private List<RowError> errors(List<ParsedRecord> records) {
        return records.stream().filter(RowError.class::isInstance).map(RowError.class::cast).toList();
    }

    @Test
    void parsesPlainFile() {
        List<ParsedRecord> records = parse(Fixtures.inline(HEADER + GOOD));

        assertThat(errors(records)).isEmpty();
        assertThat(rows(records)).containsExactly(
                new NormalizedRow(LocalDate.of(2026, 8, 2), new BigDecimal("-4.50"), "EUR", "Sample Cafe"));
    }

    @Test
    void stripsUtf8Bom() {
        List<ParsedRecord> records = parse(Fixtures.open("revolut_bom.csv"));

        assertThat(errors(records)).isEmpty();
        assertThat(rows(records)).hasSize(2);
    }

    @Test
    void ignoresBlankLines() {
        List<ParsedRecord> records = parse(Fixtures.open("revolut_blank_lines.csv"));

        assertThat(errors(records)).isEmpty();
        assertThat(rows(records)).hasSize(2);
    }

    @Test
    void ignoresRowsWhoseCellsAreAllEmpty() {
        List<ParsedRecord> records = parse(Fixtures.inline(HEADER + GOOD + ",,,,,,,,,\n"));

        assertThat(errors(records)).isEmpty();
        assertThat(rows(records)).hasSize(1);
    }

    @Test
    void keepsNewlineInsideQuotedField() {
        List<ParsedRecord> records = parse(Fixtures.open("revolut_quoted_newline.csv"));

        assertThat(errors(records)).isEmpty();
        assertThat(rows(records).getFirst().description()).isEqualTo("Sample Cafe\nDowntown Branch");
    }

    @Test
    void recordsBadRowsAndKeepsGoing() {
        List<ParsedRecord> records = parse(Fixtures.open("revolut_bad_rows.csv"));

        assertThat(rows(records)).hasSize(2);
        assertThat(errors(records)).extracting(RowError::rowNumber).containsExactly(3L, 4L, 5L, 6L, 7L);
        assertThat(errors(records)).extracting(RowError::reason).allSatisfy(reason -> assertThat(reason).isNotBlank());
    }

    @Test
    void errorCarriesRawRowText() {
        List<ParsedRecord> records = parse(Fixtures.open("revolut_bad_rows.csv"));

        assertThat(errors(records).get(3).rawRow()).contains("Too Few Cells");
    }

    @Test
    void rowNumberIsPhysicalLineSkippingBlankLines() {
        String bad = "Card Payment,Current,2026-08-01 12:00:00,2026-08-02 09:00:00,Bad,oops,0.00,EUR,COMPLETED,1\n";

        List<ParsedRecord> records = parse(Fixtures.inline(HEADER + "\n" + bad));

        assertThat(errors(records)).extracting(RowError::rowNumber).containsExactly(3L);
    }

    @Test
    void rowNumberIsPhysicalLineAfterMultiLineQuotedRecord() {
        String multiLine =
                "Card Payment,Current,2026-08-01 12:00:00,2026-08-02 09:00:00,\"Two\nLines\",-1.00,0.00,EUR,COMPLETED,1\n";
        String bad = "Card Payment,Current,2026-08-01 12:00:00,2026-08-02 09:00:00,Bad,oops,0.00,EUR,COMPLETED,1\n";

        List<ParsedRecord> records = parse(Fixtures.inline(HEADER + multiLine + bad));

        assertThat(errors(records)).extracting(RowError::rowNumber).containsExactly(4L);
    }

    @Test
    void rowNumberIsStartLineOfMultiLineBadRecord() {
        String badMultiLine =
                "Card Payment,Current,2026-08-01 12:00:00,2026-08-02 09:00:00,\"Two\nLines\",oops,0.00,EUR,COMPLETED,1\n";

        List<ParsedRecord> records = parse(Fixtures.inline(HEADER + GOOD + badMultiLine));

        assertThat(errors(records)).extracting(RowError::rowNumber).containsExactly(3L);
    }

    @Test
    void parsesRealStructureEurExport() {
        List<ParsedRecord> records = parse(Fixtures.open("revolut_eur.csv"));

        assertThat(rows(records)).hasSize(7);
        assertThat(rows(records)).extracting(NormalizedRow::description)
                .contains("Díjnet", "WIEN ENERGIE Vertrieb GmbH & Co KG");
        // PENDING and REVERTED rows are visible as errors, not silently dropped
        assertThat(errors(records)).extracting(RowError::rowNumber).containsExactly(9L, 10L);
        assertThat(errors(records)).extracting(RowError::reason).allSatisfy(r -> assertThat(r).contains("State"));
    }

    @Test
    void parsesRealStructureHufExportWithFees() {
        List<ParsedRecord> records = parse(Fixtures.open("revolut_huf.csv"));

        assertThat(errors(records)).isEmpty();
        assertThat(rows(records)).extracting(NormalizedRow::amount).usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("50000.00"), new BigDecimal("-2390.00"), new BigDecimal("-3000.00"),
                        new BigDecimal("-19647.84"), new BigDecimal("151497.54"), new BigDecimal("-150000.00"));
    }

    @Test
    void emptyFileIsPermanentFailure() {
        assertThatThrownBy(() -> parse(Fixtures.open("empty.csv")))
                .isInstanceOf(PermanentParseException.class)
                .hasMessageContaining("header");
    }

    @Test
    void missingRequiredColumnsListsThem() {
        assertThatThrownBy(() -> parse(Fixtures.open("revolut_missing_columns.csv")))
                .isInstanceOfSatisfying(PermanentParseException.class, e -> assertThat(e.missingColumns())
                        .containsExactlyInAnyOrder("Completed Date", "Amount", "Fee", "Currency"));
    }

    @Test
    void foreignHeaderIsPermanentFailure() {
        assertThatThrownBy(() -> parse(Fixtures.open("unknown_columns.csv")))
                .isInstanceOf(PermanentParseException.class);
    }

    @Test
    void unterminatedQuoteIsPermanentFailure() {
        String broken = "Card Payment,Current,2026-08-01 12:00:00,2026-08-02 09:00:00,\"never closed,-1.00,0.00,EUR,COMPLETED,1\n";

        assertThatThrownBy(() -> parse(Fixtures.inline(HEADER + GOOD + broken)))
                .isInstanceOf(PermanentParseException.class);
    }
}
