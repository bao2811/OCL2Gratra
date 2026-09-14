package org.uet.dse.ocl2cypher.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GraphKeyTest {

    @Test
    void lengthPrefixesSeparateFormerDelimiterCollision() {
        String left = GraphKey.slot("m", "a::b", "c");
        String right = GraphKey.slot("m", "a", "b::c");

        assertNotEquals(left, right);
        assertEquals(List.of("a::b", "c"), GraphKey.decode(left).components());
        assertEquals(List.of("a", "b::c"), GraphKey.decode(right).components());
    }

    @Test
    void modelKindAndComponentsAreAllPartOfIdentity() {
        String base = GraphKey.of("m1", GraphKey.Kind.OBJECT, "x");
        assertNotEquals(base, GraphKey.of("m2", GraphKey.Kind.OBJECT, "x"));
        assertNotEquals(base, GraphKey.of("m1", GraphKey.Kind.CLASS, "x"));
        assertNotEquals(base, GraphKey.of("m1", GraphKey.Kind.OBJECT, "x", ""));
    }

    @Test
    void finiteAdversarialCorpusRoundTripsWithoutCollision() {
        List<String> values = List.of("", ":", "::", "a", "a:b", "2:ab", "\u0111ối tượng");
        Set<String> encoded = new HashSet<>();
        int cases = 0;
        for (String model : values) {
            for (GraphKey.Kind kind : GraphKey.Kind.values()) {
                for (String first : values) {
                    for (String second : values) {
                        String key = GraphKey.of(model, kind, first, second);
                        assertTrue(encoded.add(key), "collision at " + model + "/" + kind
                                + "/" + first + "/" + second);
                        GraphKey.Decoded decoded = GraphKey.decode(key);
                        assertEquals(model, decoded.modelKey());
                        assertEquals(kind, decoded.kind());
                        assertEquals(List.of(first, second), decoded.components());
                        cases++;
                    }
                }
            }
        }
        assertEquals(values.size() * GraphKey.Kind.values().length
                * values.size() * values.size(), cases);
    }

    @Test
    void malformedOrUnknownKeysAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> GraphKey.decode("legacy::key"));
        assertThrows(IllegalArgumentException.class, () -> GraphKey.decode("2:x"));
        assertThrows(IllegalArgumentException.class,
                () -> GraphKey.decode("11:GraphKey-v11:m7:UNKNOWN"));
    }
}
