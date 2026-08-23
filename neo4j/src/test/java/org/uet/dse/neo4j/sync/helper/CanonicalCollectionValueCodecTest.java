package org.uet.dse.neo4j.sync.helper;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CanonicalCollectionValueCodecTest {
    @Test
    void roundTripsScalarLeavesIncludingBottomAndStructuralDelimiter() {
        List<Object> values = Arrays.asList("left | right", null, "100% exact");

        String encoded = CanonicalCollectionValueCodec.encodeScalarLeaves(values, "String");

        assertEquals("v1|S|left %7C right | v1|V | v1|S|100%25 exact", encoded);
        assertEquals(values, CanonicalCollectionValueCodec.decodeScalarLeaves(encoded, "String"));
    }

    @Test
    void usesOneExplicitPayloadForTheEmptyCollection() {
        assertEquals(CanonicalCollectionValueCodec.EMPTY,
                CanonicalCollectionValueCodec.encodeScalarLeaves(List.of(), "Integer"));
        assertEquals(List.of(), CanonicalCollectionValueCodec.decodeScalarLeaves(
                CanonicalCollectionValueCodec.EMPTY, "Integer"));
    }

    @Test
    void preservesOrderMultiplicityAndTypedLeafTags() {
        List<Object> values = List.of(2L, 1L, 2L);
        String encoded = CanonicalCollectionValueCodec.encodeScalarLeaves(values, "Integer");

        assertEquals("v1|I|2 | v1|I|1 | v1|I|2", encoded);
        assertEquals(values, CanonicalCollectionValueCodec.decodeScalarLeaves(encoded, "Integer"));
        assertNotEquals(encoded,
                CanonicalCollectionValueCodec.encodeScalarLeaves(List.of(1L, 2L), "Integer"));
    }

    @Test
    void rejectsLegacyMalformedAndCrossTypedLeaves() {
        assertThrows(IllegalArgumentException.class,
                () -> CanonicalCollectionValueCodec.decodeScalarLeaves("1 | 2", "Integer"));
        assertThrows(IllegalArgumentException.class,
                () -> CanonicalCollectionValueCodec.decodeScalarLeaves("v1|S|1", "Integer"));
        assertThrows(IllegalArgumentException.class,
                () -> CanonicalCollectionValueCodec.decodeScalarLeaves("v1|I|1 | ", "Integer"));
        assertThrows(IllegalArgumentException.class,
                () -> CanonicalCollectionValueCodec.encodeScalarLeaves(null, "Integer"));
    }

    @Test
    void refinementMatrixCoversEveryCodecBoundary() throws Exception {
        Path matrix = resolveWorkspaceFile(
                "verification/coverage/canonical_collection_codec_refinement.csv");
        List<String[]> rows = Files.readAllLines(matrix).stream()
                .skip(1)
                .filter(line -> !line.isBlank())
                .map(line -> line.split(",", -1))
                .toList();
        Set<String> expected = new LinkedHashSet<>(Arrays.asList(
                "CC-EMPTY", "CC-TYPED-LEAF", "CC-BOTTOM", "CC-DELIMITER",
                "CC-ORDER-MULTIPLICITY", "CC-NESTED-LEAF", "CC-READBACK",
                "CC-LEAN-SCALAR-FRAMING", "CC-LEAN-NESTED-SPECIALIZATION"));
        assertEquals(expected, rows.stream().map(row -> row[0]).collect(
                java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
        assertEquals(9, rows.size());
        assertEquals(7, rows.stream().filter(row -> row.length == 6
                && "EXECUTABLE_WITNESS".equals(row[5])).count());
        assertEquals(2, rows.stream().filter(row -> row.length == 6
                && "MECHANIZED".equals(row[5])).count());
    }

    private static Path resolveWorkspaceFile(String relative) {
        Path working = Path.of("").toAbsolutePath().normalize();
        for (Path root : List.of(working, working.resolve(".."))) {
            Path candidate = root.resolve(relative).normalize();
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Cannot locate workspace file: " + relative);
    }
}
