package org.uet.dse.neo4jtgg.ocl;

import java.lang.reflect.Array;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The sole concrete representation of OCL bottom inside generated Cypher.
 *
 * <p>The token is a tagged map, while admitted UML scalar values and canonical
 * graph keys are Neo4j scalar values.  Keeping construction here prevents an
 * ordinary value from silently acquiring bottom semantics.</p>
 */
public final class OclBottomToken {
    public static final String MARKER = "__oclBottom";
    private static final Map<String, Object> VALUE = Map.of(MARKER, Boolean.TRUE);

    private OclBottomToken() {
    }

    public static Map<String, Object> value() {
        return VALUE;
    }

    public static boolean isToken(Object value) {
        return value instanceof Map<?, ?> map
                && map.size() == 1
                && Boolean.TRUE.equals(map.get(MARKER));
    }

    /** Reject a renderer token smuggled through operation parameters. */
    public static void requireNoExternalToken(Map<String, Object> parameters) {
        if (parameters == null) {
            return;
        }
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        rejectNestedToken(parameters, "runtime parameters", visited, false);
    }

    /** Check that a token occurs only as a complete top-level generated parameter. */
    public static void requireWellFormedGeneratedParameters(Map<String, Object> parameters) {
        if (parameters == null) {
            return;
        }
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            if (isToken(entry.getValue())) {
                continue;
            }
            Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
            rejectNestedToken(entry.getValue(), "generated parameter $" + entry.getKey(), visited, true);
        }
    }

    private static void rejectNestedToken(Object value, String location, Set<Object> visited,
                                          boolean generated) {
        if (value == null || value instanceof String || value instanceof Number
                || value instanceof Boolean || value instanceof Character || value.getClass().isEnum()) {
            return;
        }
        if (isToken(value)) {
            throw new IllegalArgumentException((generated ? "Nested" : "External")
                    + " OCL bottom token is forbidden in " + location);
        }
        if (!visited.add(value)) {
            return;
        }
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                rejectNestedToken(entry.getKey(), location, visited, generated);
                rejectNestedToken(entry.getValue(), location, visited, generated);
            }
        } else if (value instanceof Iterable<?> iterable) {
            for (Object element : iterable) {
                rejectNestedToken(element, location, visited, generated);
            }
        } else if (value.getClass().isArray()) {
            for (int index = 0; index < Array.getLength(value); index++) {
                rejectNestedToken(Array.get(value, index), location, visited, generated);
            }
        }
    }
}
