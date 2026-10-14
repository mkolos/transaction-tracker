package com.kolosh.transactiontracker.parsing;

import java.io.IOException;
import java.io.Reader;
import java.util.Arrays;

/**
 * Remembers where every '\n' was, so a record's character offset can be turned into the physical
 * line it started on. Commons CSV only offers record numbers (which drift after blank lines and
 * quoted newlines) and the line where a record <em>ends</em>.
 * Cost: 8 bytes per line of input, fine for bank statements.
 */
final class NewlineIndex {

    private long[] offsets = new long[256];
    private int count;

    /** 1-based physical line containing the character at {@code position}. */
    int lineAt(long position) {
        int idx = Arrays.binarySearch(offsets, 0, count, position);
        int newlinesBefore = idx >= 0 ? idx : -idx - 1;
        return newlinesBefore + 1;
    }

    private void add(long offset) {
        if (count == offsets.length) {
            offsets = Arrays.copyOf(offsets, count * 2);
        }
        offsets[count++] = offset;
    }

    Reader track(Reader delegate) {
        return new Reader() {
            private long position;

            @Override
            public int read(char[] buf, int off, int len) throws IOException {
                int n = delegate.read(buf, off, len);
                for (int i = 0; i < n; i++) {
                    if (buf[off + i] == '\n') {
                        add(position + i);
                    }
                }
                if (n > 0) {
                    position += n;
                }
                return n;
            }

            @Override
            public void close() throws IOException {
                delegate.close();
            }
        };
    }
}
