package org.uet.dse.neo4jtgg.service.impl;

import java.util.LinkedHashMap;
import java.util.Map;

public final class OclDocumentRuleParameterValuesParser {

    private OclDocumentRuleParameterValuesParser() {
    }

    public static Map<String, Map<String, Object>> parse(String text) {
        Map<String, Map<String, Object>> values = new LinkedHashMap<>();
        if (text == null || text.isBlank()) {
            return values;
        }

        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        for (String line : normalized.split("\n")) {
            String entry = line.trim();
            if (entry.isEmpty()) {
                continue;
            }
            int separator = entry.indexOf("=>");
            if (separator <= 0 || separator == entry.length() - 2) {
                throw new IllegalArgumentException(
                        "Invalid rule parameter entry `" + entry + "`. Use `QualifiedRule => name=value`.");
            }
            String qualifiedRule = entry.substring(0, separator).trim();
            String parameterText = entry.substring(separator + 2).trim();
            if (qualifiedRule.isEmpty()) {
                throw new IllegalArgumentException(
                        "Invalid rule parameter entry `" + entry + "`. Rule target must not be empty.");
            }
            values.put(qualifiedRule, OclParameterValuesParser.parse(parameterText));
        }
        return values;
    }
}
