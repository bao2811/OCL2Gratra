package org.uet.dse.neo4jtgg.ocl.ir;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Validates total feature-level refinement, including missing-feature failures. */
public final class MetamodelFeatureRefinementCoverage {
    private static final Pattern CLASS = Pattern.compile(
            "(?s)(?:abstract\\s+)?class\\s+([A-Za-z_]\\w*)(?:\\s+extends\\s+[A-Za-z_]\\w*)?\\s*\\{(.*?)\\}");
    private static final Pattern FEATURE = Pattern.compile(
            "^(?:(id)\\s+)?(attr|val|ref)\\s+([A-Za-z_]\\w*)(?:\\[([^]]+)\\])?\\s+([A-Za-z_]\\w*)");
    private static final Set<String> POLICIES = Set.of("mapped", "derived", "erased");
    private static final Set<String> ERASABLE = Set.of("nodeId", "invariantId", "planId", "symbolId");

    private MetamodelFeatureRefinementCoverage() {
    }

    public static Report validate(Map<String, String> emfByDomain, List<String> csvLines) {
        Map<String, DeclaredFeature> declared = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        emfByDomain.forEach((domain, source) -> parseEmf(domain, source, declared, errors));
        Map<String, Row> rows = new LinkedHashMap<>();
        for (int index = 1; index < csvLines.size(); index++) {
            String line = csvLines.get(index);
            if (line.isBlank()) continue;
            try {
                Row row = Row.parse(line);
                if (rows.put(row.key(), row) != null) errors.add("Duplicate mapping " + row.key());
            } catch (IllegalArgumentException exception) {
                errors.add(exception.getMessage());
            }
        }
        for (DeclaredFeature feature : declared.values()) {
            Row row = rows.get(feature.key());
            if (row == null) {
                errors.add("Unmapped metamodel feature " + feature.key());
                continue;
            }
            if (!feature.kind().equals(row.kind())) errors.add("Wrong kind for " + feature.key());
            if (!feature.multiplicity().equals(row.multiplicity())) errors.add("Wrong cardinality for " + feature.key());
            if (!POLICIES.contains(row.policy())) errors.add("Unknown policy for " + feature.key());
            if (row.witness().isBlank()) errors.add("Missing witness for " + feature.key());
            if (!"CERTIFIED".equals(row.status())) errors.add("Non-certified row " + feature.key());
            if ("erased".equals(row.policy()) && (!ERASABLE.contains(feature.feature())
                    || !"attr".equals(feature.kind()))) {
                errors.add("Illegal erasure of semantic/structural feature " + feature.key());
            }
        }
        for (String key : rows.keySet()) {
            if (!declared.containsKey(key)) errors.add("Mapping names no declared feature " + key);
        }
        return new Report(declared.size(), rows.size(), errors);
    }

    private static void parseEmf(String domain, String source, Map<String, DeclaredFeature> result,
                                 List<String> errors) {
        Matcher classes = CLASS.matcher(source);
        while (classes.find()) {
            String classifier = classes.group(1);
            for (String line : classes.group(2).split("\\R")) {
                Matcher feature = FEATURE.matcher(line.trim());
                if (!feature.find()) continue;
                String multiplicity = feature.group(4) == null ? "0..1" : feature.group(4);
                DeclaredFeature item = new DeclaredFeature(domain, classifier, feature.group(5),
                        feature.group(2), multiplicity);
                if (result.put(item.key(), item) != null) errors.add("Duplicate declaration " + item.key());
            }
        }
    }

    private record DeclaredFeature(String domain, String classifier, String feature,
                                   String kind, String multiplicity) {
        String key() { return domain + "." + classifier + "." + feature; }
    }

    private record Row(String domain, String classifier, String feature, String kind,
                       String multiplicity, String policy, String witness, String status) {
        static Row parse(String line) {
            String[] fields = line.split(";", -1);
            if (fields.length != 8) throw new IllegalArgumentException("Malformed total-refinement row: " + line);
            return new Row(fields[0], fields[1], fields[2], fields[3], fields[4], fields[5], fields[6], fields[7]);
        }

        String key() { return domain + "." + classifier + "." + feature; }
    }

    public record Report(int declaredCount, int mappedCount, List<String> errors) {
        public Report { errors = List.copyOf(errors); }
        public boolean valid() { return errors.isEmpty() && declaredCount == mappedCount; }
    }
}
