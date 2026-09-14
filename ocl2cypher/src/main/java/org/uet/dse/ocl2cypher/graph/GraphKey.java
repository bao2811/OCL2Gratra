package org.uet.dse.ocl2cypher.graph;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Canonical, model-scoped and injectively decodable keys for graph elements. */
public final class GraphKey {

    public enum Kind {
        MODEL,
        CLASS,
        ATTRIBUTE,
        ASSOCIATION,
        GENERALIZATION,
        CLASS_ATTRIBUTE,
        ASSOCIATION_END,
        OBJECT,
        OBJECT_TYPING,
        SLOT,
        SLOT_OWNERSHIP,
        SLOT_TYPING,
        LINK,
        ASSOCIATION_CLASS_PARTICIPANT
    }

    public record Decoded(String modelKey, Kind kind, List<String> components) {
        public Decoded {
            Objects.requireNonNull(modelKey);
            Objects.requireNonNull(kind);
            components = List.copyOf(components);
        }
    }

    private static final String VERSION = "GraphKey-v1";

    private GraphKey() {
    }

    public static String of(String modelKey, Kind kind, String... components) {
        Objects.requireNonNull(modelKey, "modelKey");
        Objects.requireNonNull(kind, "kind");
        StringBuilder out = new StringBuilder();
        append(out, VERSION);
        append(out, modelKey);
        append(out, kind.name());
        for (String component : components) {
            append(out, Objects.requireNonNull(component, "key component"));
        }
        return out.toString();
    }

    public static Decoded decode(String encoded) {
        Objects.requireNonNull(encoded, "encoded key");
        List<String> parts = new ArrayList<>();
        int cursor = 0;
        while (cursor < encoded.length()) {
            int colon = encoded.indexOf(':', cursor);
            if (colon == -1 || colon == cursor) {
                throw new IllegalArgumentException("malformed length-prefixed graph key");
            }
            String digits = encoded.substring(cursor, colon);
            if (!digits.chars().allMatch(Character::isDigit)) {
                throw new IllegalArgumentException("malformed graph-key length");
            }
            int length;
            try {
                length = Integer.parseInt(digits);
            } catch (NumberFormatException tooLarge) {
                throw new IllegalArgumentException("graph-key component is too large", tooLarge);
            }
            int start = colon + 1;
            int end = start + length;
            if (length < 0 || end < start || end > encoded.length()) {
                throw new IllegalArgumentException("truncated graph-key component");
            }
            parts.add(encoded.substring(start, end));
            cursor = end;
        }
        if (parts.size() < 3 || !VERSION.equals(parts.get(0))) {
            throw new IllegalArgumentException("unsupported graph-key version");
        }
        Kind kind;
        try {
            kind = Kind.valueOf(parts.get(2));
        } catch (IllegalArgumentException unknownKind) {
            throw new IllegalArgumentException("unknown graph-key kind", unknownKind);
        }
        return new Decoded(parts.get(1), kind, parts.subList(3, parts.size()));
    }

    public static boolean isKind(String encoded, Kind kind) {
        try {
            return decode(encoded).kind() == kind;
        } catch (IllegalArgumentException malformed) {
            return false;
        }
    }

    public static String model(String modelKey) {
        return of(modelKey, Kind.MODEL, modelKey);
    }

    public static String clazz(String modelKey, String classKey) {
        return of(modelKey, Kind.CLASS, classKey);
    }

    public static String attribute(String modelKey, String attributeKey) {
        return of(modelKey, Kind.ATTRIBUTE, attributeKey);
    }

    public static String association(String modelKey, String associationKey) {
        return of(modelKey, Kind.ASSOCIATION, associationKey);
    }

    public static String object(String modelKey, String objectId) {
        return of(modelKey, Kind.OBJECT, objectId);
    }

    public static String slot(String modelKey, String objectId, String attributeKey) {
        return of(modelKey, Kind.SLOT, objectId, attributeKey);
    }

    private static void append(StringBuilder out, String value) {
        out.append(value.length()).append(':').append(value);
    }
}
