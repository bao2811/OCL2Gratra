package org.uet.dse.neo4jtgg.service.impl;

import java.util.LinkedHashMap;
import java.util.Map;

public final class OclParameterValuesParser {

    private OclParameterValuesParser() {
    }

    public static Map<String, Object> parse(String text) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (text == null || text.isBlank()) {
            return values;
        }

        int start = 0;
        boolean inQuotes = false;
        char quoteChar = 0;
        for (int i = 0; i <= text.length(); i++) {
            char current = i < text.length() ? text.charAt(i) : ',';
            if (inQuotes) {
                if (current == quoteChar) {
                    inQuotes = false;
                }
                continue;
            }
            if (current == '\'' || current == '"') {
                inQuotes = true;
                quoteChar = current;
                continue;
            }
            if (current == ',') {
                String entry = text.substring(start, i).trim();
                if (!entry.isEmpty()) {
                    addEntry(values, entry);
                }
                start = i + 1;
            }
        }
        return values;
    }

    private static void addEntry(Map<String, Object> values, String entry) {
        int separator = entry.indexOf('=');
        if (separator <= 0 || separator == entry.length() - 1) {
            throw new IllegalArgumentException(
                    "Invalid parameter entry `" + entry + "`. Use `name=value` separated by commas.");
        }
        String name = entry.substring(0, separator).trim();
        String rawValue = entry.substring(separator + 1).trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException(
                    "Invalid parameter entry `" + entry + "`. Parameter name must not be empty.");
        }
        values.put(name, parseValue(rawValue));
    }

    private static Object parseValue(String rawValue) {
        if (rawValue.length() >= 2) {
            char first = rawValue.charAt(0);
            char last = rawValue.charAt(rawValue.length() - 1);
            if ((first == '\'' && last == '\'') || (first == '"' && last == '"')) {
                return rawValue.substring(1, rawValue.length() - 1);
            }
        }
        if ("true".equalsIgnoreCase(rawValue)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(rawValue)) {
            return Boolean.FALSE;
        }
        if ("null".equalsIgnoreCase(rawValue)) {
            return null;
        }
        try {
            if (rawValue.contains(".") || rawValue.contains("e") || rawValue.contains("E")) {
                return Double.valueOf(rawValue);
            }
            return Long.valueOf(rawValue);
        } catch (NumberFormatException ignored) {
            return rawValue;
        }
    }
}
