package org.uet.dse.neo4j.sync.helper;

import org.tzi.use.uml.ocl.type.Type;

import java.util.ArrayList;
import java.util.List;

/**
 * Canonical wire codec for one scalar-leaf collection cell.
 *
 * <p>Nested collections are represented structurally by
 * {@code HasNestedCollectionValue}; every deepest scalar list is stored in one
 * {@code value} property. Each leaf uses {@link CanonicalScalarValueCodec}, so
 * semantic bottom and strings containing the structural delimiter remain
 * disjoint. The separator contains a raw pipe, while scalar payload content
 * escapes every pipe, making splitting unambiguous.</p>
 */
public final class CanonicalCollectionValueCodec {
    public static final String EMPTY = "COLLECTION_EMPTY";
    public static final String SEPARATOR = " | ";

    private CanonicalCollectionValueCodec() {
    }

    public static String encodeScalarLeaves(List<?> values, Type scalarType) {
        if (scalarType == null || scalarType.isKindOfCollection(Type.VoidHandling.EXCLUDE_VOID)
                || scalarType.isKindOfClass(Type.VoidHandling.EXCLUDE_VOID)) {
            throw new IllegalArgumentException("A non-collection scalar leaf type is required");
        }
        return encodeScalarLeaves(values, scalarType.shortName());
    }

    public static String encodeScalarLeaves(List<?> values, String scalarTypeName) {
        if (values == null) {
            throw new IllegalArgumentException("A scalar leaf list is required");
        }
        if (values.isEmpty()) {
            return EMPTY;
        }
        return values.stream()
                .map(value -> CanonicalScalarValueCodec.encode(value, scalarTypeName))
                .collect(java.util.stream.Collectors.joining(SEPARATOR));
    }

    public static List<Object> decodeScalarLeaves(String payload, Type scalarType) {
        if (scalarType == null || scalarType.isKindOfCollection(Type.VoidHandling.EXCLUDE_VOID)
                || scalarType.isKindOfClass(Type.VoidHandling.EXCLUDE_VOID)) {
            throw new IllegalArgumentException("A non-collection scalar leaf type is required");
        }
        return decodeScalarLeaves(payload, scalarType.shortName());
    }

    public static List<Object> decodeScalarLeaves(String payload, String scalarTypeName) {
        if (payload == null) {
            throw new IllegalArgumentException("A canonical collection payload must not be null");
        }
        if (EMPTY.equals(payload)) {
            return List.of();
        }
        if (payload.isEmpty()) {
            throw new IllegalArgumentException("An empty string is not a canonical collection payload");
        }
        String[] encodedLeaves = payload.split(java.util.regex.Pattern.quote(SEPARATOR), -1);
        List<Object> decoded = new ArrayList<>(encodedLeaves.length);
        for (String encodedLeaf : encodedLeaves) {
            if (encodedLeaf.isEmpty()) {
                throw new IllegalArgumentException("Malformed empty scalar leaf in collection payload");
            }
            decoded.add(CanonicalScalarValueCodec.decode(encodedLeaf, scalarTypeName));
        }
        return java.util.Collections.unmodifiableList(decoded);
    }
}
