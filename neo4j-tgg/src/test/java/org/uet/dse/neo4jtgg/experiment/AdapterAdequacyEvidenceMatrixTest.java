package org.uet.dse.neo4jtgg.experiment;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exact Java/Lean/evidence routing guard for PA-COMP. */
class AdapterAdequacyEvidenceMatrixTest {
    private static final List<String> PREMISES = List.of(
            "shared-snapshot", "exact-M2", "PA1", "PA2", "PA3", "PA4",
            "PA5", "PA6", "PA7", "PA8", "PA9");

    @Test
    void everyPaCompPremiseHasLeanJavaObservationAndEvidence() throws Exception {
        Path workspace = workspace();
        List<String> lines = Files.readAllLines(workspace.resolve(
                "verification/coverage/adapter_adequacy_matrix.csv"));
        assertEquals("premise,lean_field,java_source,java_symbol,observation,evidence_test",
                lines.get(0));
        List<Row> rows = lines.subList(1, lines.size()).stream().map(Row::parse).toList();
        assertEquals(PREMISES, rows.stream().map(Row::premise).toList());
        String lean = Files.readString(workspace.resolve("verification/lean/Ocl2CypherProof.lean"));
        for (Row row : rows) {
            assertTrue(lean.contains(row.leanField() + " :"), row.leanField());
            String javaSource = Files.readString(workspace.resolve(row.javaSource()));
            assertTrue(javaSource.contains(row.javaSymbol()), row.premise() + " -> " + row.javaSymbol());
            assertTrue(!row.observation().isBlank(), row.premise());
            assertEquals(getClass().getSimpleName(), row.evidenceTest());
        }
    }

    private static Path workspace() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve(
                    "verification/contract/proof-contract-registry.json"))) return current;
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate workspace root");
    }

    private record Row(String premise, String leanField, String javaSource,
                       String javaSymbol, String observation, String evidenceTest) {
        private static Row parse(String line) {
            List<String> values = new java.util.ArrayList<>();
            StringBuilder field = new StringBuilder();
            boolean quoted = false;
            for (int index = 0; index < line.length(); index++) {
                char character = line.charAt(index);
                if (character == '"') quoted = !quoted;
                else if (character == ',' && !quoted) {
                    values.add(field.toString()); field.setLength(0);
                } else field.append(character);
            }
            values.add(field.toString());
            if (values.size() != 6) throw new IllegalArgumentException(line);
            return new Row(values.get(0), values.get(1), values.get(2),
                    values.get(3), values.get(4), values.get(5));
        }
    }
}
