package com.kolosh.transactiontracker.parsing;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackInputStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.csv.DuplicateHeaderMode;

/**
 * Streams a CSV file as {@link ParsedRecord}s: good rows as {@link NormalizedRow}, bad rows as
 * {@link RowError}. Memory use does not grow with file size (apart from the newline index).
 * <p>
 * Structural problems (no header, missing columns, malformed quoting) throw
 * {@link PermanentParseException}. The header checks happen eagerly, before the stream is returned.
 * The caller must close the returned stream.
 */
public class CsvParser {

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    public Stream<ParsedRecord> parse(InputStream in, CsvDialect dialect, BankFormat format) {
        NewlineIndex newlines = new NewlineIndex();
        CSVParser csv = null;
        try {
            Reader reader = newlines.track(new InputStreamReader(skipUtf8Bom(in), dialect.charset()));
            csv = open(reader, dialect);
            requireColumns(csv.getHeaderNames(), format);
        } catch (IOException | UncheckedIOException | IllegalStateException e) {
            closeQuietly(csv, in);
            throw new PermanentParseException("Malformed CSV: " + e.getMessage());
        } catch (PermanentParseException e) {
            closeQuietly(csv, in);
            throw e;
        }

        CSVParser parser = csv;
        Iterator<ParsedRecord> records = new RecordIterator(parser.iterator(), format, dialect, newlines);
        return StreamSupport
                .stream(Spliterators.spliteratorUnknownSize(records, Spliterator.ORDERED | Spliterator.NONNULL), false)
                .onClose(() -> closeQuietly(parser, in));
    }

    private static CSVParser open(Reader reader, CsvDialect dialect) throws IOException {
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setDelimiter(dialect.delimiter())
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(false)
                .setDuplicateHeaderMode(DuplicateHeaderMode.ALLOW_ALL)
                .setAllowMissingColumnNames(true)
                .get();
        return format.parse(reader);
    }

    private static void requireColumns(List<String> headers, BankFormat format) {
        if (headers.stream().allMatch(String::isBlank)) {
            throw new PermanentParseException("File has no header row");
        }
        List<String> missing = format.requiredColumns().stream().filter(c -> !headers.contains(c)).toList();
        if (!missing.isEmpty()) {
            throw new PermanentParseException(
                    "Missing required columns " + missing + " for format '" + format.name() + "'. Found: " + headers,
                    missing);
        }
    }

    private static InputStream skipUtf8Bom(InputStream in) throws IOException {
        PushbackInputStream pushback = new PushbackInputStream(in, UTF8_BOM.length);
        byte[] head = pushback.readNBytes(UTF8_BOM.length);
        if (!java.util.Arrays.equals(head, UTF8_BOM)) {
            pushback.unread(head);
        }
        return pushback;
    }

    private static void closeQuietly(AutoCloseable... closeables) {
        for (AutoCloseable c : closeables) {
            if (c != null) {
                try {
                    c.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static final class RecordIterator implements Iterator<ParsedRecord> {

        private final Iterator<CSVRecord> delegate;
        private final BankFormat format;
        private final CsvDialect dialect;
        private final NewlineIndex newlines;

        RecordIterator(Iterator<CSVRecord> delegate, BankFormat format, CsvDialect dialect, NewlineIndex newlines) {
            this.delegate = delegate;
            this.format = format;
            this.dialect = dialect;
            this.newlines = newlines;
        }

        private ParsedRecord pending;

        @Override
        public boolean hasNext() {
            if (pending == null) {
                pending = advance();
            }
            return pending != null;
        }

        @Override
        public ParsedRecord next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            ParsedRecord result = pending;
            pending = null;
            return result;
        }

        private ParsedRecord advance() {
            while (true) {
                CSVRecord record;
                try {
                    if (!delegate.hasNext()) {
                        return null;
                    }
                    record = delegate.next();
                } catch (UncheckedIOException | IllegalStateException e) {
                    throw new PermanentParseException("Malformed CSV: " + e.getMessage());
                }
                if (record.stream().allMatch(String::isBlank)) {
                    continue;
                }
                return convert(record);
            }
        }

        private ParsedRecord convert(CSVRecord record) {
            long line = newlines.lineAt(record.getCharacterPosition());
            String raw = String.join(String.valueOf(dialect.delimiter()), record.toList());
            if (!record.isConsistent()) {
                return new RowError(line, raw,
                        "Expected " + record.getParser().getHeaderNames().size() + " columns but found " + record.size());
            }
            try {
                return format.parse(record.toMap());
            } catch (InvalidRowException e) {
                return new RowError(line, raw, e.getMessage());
            }
        }
    }
}
