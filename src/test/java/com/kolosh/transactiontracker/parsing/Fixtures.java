package com.kolosh.transactiontracker.parsing;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

final class Fixtures {

    private Fixtures() {
    }

    static InputStream open(String name) {
        InputStream in = Fixtures.class.getResourceAsStream("/csv/" + name);
        if (in == null) {
            throw new IllegalArgumentException("Missing fixture /csv/" + name);
        }
        return in;
    }

    static InputStream inline(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
