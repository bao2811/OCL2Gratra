package org.uet.dse.neo4jtgg.ocl.ir;

import org.uet.dse.neo4j.encoding.CanonicalGraphEncoding;
import org.uet.dse.neo4j.encoding.CanonicalGraphVocabulary;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Executable conformance check for the checked-in PGMM-CANONICAL-1 instance. */
public final class CanonicalPgmmConformance {
    private CanonicalPgmmConformance() {
    }

    public static Report check(Path instance) throws IOException {
        Map<String, String> values = new LinkedHashMap<>();
        for (String line : Files.readAllLines(instance)) {
            if (line.isBlank() || line.trim().startsWith("#")) continue;
            String[] fields = line.split(";", -1);
            if (fields.length != 3) throw new IllegalArgumentException("Malformed PGMM row: " + line);
            String key = fields[0].trim() + "." + fields[1].trim();
            if (values.put(key, fields[2].trim()) != null) {
                throw new IllegalArgumentException("Duplicate PGMM entry: " + key);
            }
        }
        List<String> errors = new ArrayList<>();
        require(values, "spec.profileId", CanonicalGraphEncoding.PROFILE_ID, errors);
        require(values, "relationshipType.ObjectInstanceOf", CanonicalGraphVocabulary.OBJECT_INSTANCE_OF, errors);
        require(values, "relationshipType.InstanceOf", CanonicalGraphVocabulary.SCHEMA_INSTANCE_OF, errors);
        require(values, "relationshipType.LinkPrefix", "Link", errors);
        require(values, "scope.keyDelimiter", "::", errors);
        require(values, "codec.bottomToken", "v1|V", errors);
        for (String label : List.of("Object", "UmlClass", "AttributeValue", "NestedCollectionValue")) {
            require(values, "nodeType." + label, label, errors);
        }
        for (String property : List.of("modelKey", "use_id", "objectKey", "classKey", "attributeKey",
                "value", "sourceQualifiers", "targetQualifiers")) {
            if (!values.containsKey("property." + property)) errors.add("missing property." + property);
        }
        for (String accessor : List.of("context", "allInstances", "directType", "conformance",
                "attribute", "navigation", "objectIdentity")) {
            if (!values.containsKey("accessor." + accessor)) errors.add("missing accessor." + accessor);
        }
        if (values.getOrDefault("key.modelScoped", "false").equalsIgnoreCase("true") == false) {
            errors.add("key.modelScoped must be true");
        }
        return new Report(errors, values.size());
    }

    private static void require(Map<String, String> values, String key, String expected, List<String> errors) {
        if (!expected.equals(values.get(key))) errors.add(key + " expected " + expected + " but was " + values.get(key));
    }

    public record Report(List<String> errors, int entryCount) {
        public Report {
            errors = List.copyOf(errors);
        }

        public boolean valid() {
            return errors.isEmpty();
        }

        public void requireValid() {
            if (!valid()) throw new IllegalArgumentException("PGMM conformance failed: " + errors);
        }
    }
}
