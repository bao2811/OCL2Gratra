package org.uet.dse.neo4jtgg.ocl.ir;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks field/reference/cardinality witnesses rather than subclass names only. */
class OclFieldRefinementContractTest {
    private static final Pattern CLASS = Pattern.compile(
            "(?s)\\bclass\\s+([A-Za-z_][A-Za-z0-9_]*)[^\\{]*\\{(.*?)\\n\\}");

    @Test
    void everyFieldWitnessExistsInEmfAndJavaAndUsesExplicitErasurePolicy() throws Exception {
        Path root = workspaceRoot();
        List<Row> rows = Files.readAllLines(root.resolve("verification/coverage/ova_cqm_field_refinement.csv"))
                .stream().skip(1).filter(line -> !line.isBlank()).map(Row::parse).toList();
        assertTrue(rows.size() >= 20, "Field refinement corpus is unexpectedly small");
        Set<String> domains = new LinkedHashSet<>();
        for (Row row : rows) {
            domains.add(row.domain());
            assertEquals("CERTIFIED", row.status(), row.metamodelClassifier() + "." + row.metamodelField());
            assertTrue(Set.of("preserved", "derived", "erased").contains(row.erasurePolicy()),
                    "Unknown erasure policy: " + row.erasurePolicy());
            assertTrue(Set.of("1", "*").contains(row.cardinality()));
            String emf = Files.readString(root.resolve(row.domain().equals("OVA")
                    ? "md/research/model/OCL-Validation-Algebra.emf"
                    : "md/research/model/Cypher-Query-Model.emf"));
            assertTrue(emfFieldExists(emf, row.metamodelClassifier(), row.metamodelField()),
                    "Missing EMF field " + row.metamodelClassifier() + "." + row.metamodelField());
            Class<?> type = Class.forName("org.uet.dse.neo4jtgg.ocl.ir." + row.javaClass().replace('$', '$'));
            assertTrue(type.isRecord(), "Refinement target is not a record: " + type);
            Set<String> components = new LinkedHashSet<>();
            for (RecordComponent component : type.getRecordComponents()) components.add(component.getName());
            assertTrue(components.contains(row.javaComponent()),
                    "Missing Java component " + row.javaClass() + "." + row.javaComponent());
        }
        assertEquals(Set.of("OVA", "CQM"), domains);
    }

    private boolean emfFieldExists(String source, String classifier, String field) {
        Matcher matcher = CLASS.matcher(source);
        while (matcher.find()) {
            if (!matcher.group(1).equals(classifier)) continue;
            return Pattern.compile("(?m)^\\s*(?:attr|val|ref|id\\s+attr)\\s+[^;]*\\b"
                    + Pattern.quote(field) + "(?:\\[[^]]+\\])?\\s*(?:=\\s*[^;]+)?;").matcher(matcher.group(2)).find();
        }
        return false;
    }

    private Path workspaceRoot() {
        Path working = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        if (Files.isDirectory(working.resolve("md/research/model"))) return working;
        if (working.getParent() != null && Files.isDirectory(working.getParent().resolve("md/research/model"))) {
            return working.getParent();
        }
        throw new IllegalStateException("Cannot locate workspace root from " + working);
    }

    private record Row(String domain, String metamodelClassifier, String javaClass,
                       String metamodelField, String javaComponent, String cardinality,
                       String erasurePolicy, String status) {
        static Row parse(String line) {
            String[] fields = line.split(";", -1);
            if (fields.length != 8) throw new IllegalArgumentException("Malformed field refinement row: " + line);
            return new Row(fields[0], fields[1], fields[2], fields[3], fields[4], fields[5], fields[6], fields[7]);
        }
    }
}
