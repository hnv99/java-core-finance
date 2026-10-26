package com.fincore.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Minimal RFC 4180 style CSV encoding: fields containing a comma or quote are quoted,
 * and quotes inside them are doubled.
 * <p>
 * Utility class: {@code final} with a private constructor, so it can be neither
 * extended nor instantiated.
 */
final class CsvCodec {

    private CsvCodec() {
    }

    static String join(List<String> fields) {
        return fields.stream()
                .map(CsvCodec::escape)
                .collect(Collectors.joining(","));
    }

    static List<String> split(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    boolean escapedQuote = i + 1 < line.length() && line.charAt(i + 1) == '"';
                    if (escapedQuote) {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields;
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }
}
