package org.uet.dse.neo4j.sync.helper;

import org.tzi.use.uml.ocl.type.Type;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Injective, typed representation of one scalar value in the canonical graph.
 *
 * <p>The tag separates semantic bottom from every ordinary string. Percent and
 * the structural delimiter are escaped without trimming or quote removal, so
 * the codec preserves the exact Unicode string supplied by USE. Real64 uses
 * one canonical zero payload because OCL numeric equality identifies IEEE
 * {@code -0.0} and {@code +0.0}.</p>
 */
public final class CanonicalScalarValueCodec {
    public static final String BOTTOM = "v1|V";
    public static final String STRING_PREFIX = "v1|S|";
    public static final String INTEGER_PREFIX = "v1|I|";
    public static final String REAL_PREFIX = "v1|R|";
    public static final String BOOLEAN_PREFIX = "v1|B|";
    public static final String ENUM_PREFIX = "v1|E|";

    private CanonicalScalarValueCodec() {
    }

    public static String encode(Object value, Type type) {
        if (type == null) {
            throw new IllegalArgumentException("A static UML type is required for scalar encoding");
        }
        if (type.isKindOfCollection(Type.VoidHandling.EXCLUDE_VOID)
                || type.isKindOfClass(Type.VoidHandling.EXCLUDE_VOID)) {
            throw new IllegalArgumentException("Only scalar values can use the canonical scalar codec: " + type);
        }
        return encode(value, type.shortName());
    }

    public static String encode(Object value, String typeName) {
        if (value == null) {
            return BOTTOM;
        }
        return switch (requiredTypeName(typeName)) {
            case "String" -> STRING_PREFIX + escape(requireInstance(value, String.class, typeName));
            case "Integer" -> INTEGER_PREFIX + canonicalInteger(value);
            case "Real" -> REAL_PREFIX + canonicalReal(value);
            case "Boolean" -> BOOLEAN_PREFIX + requireInstance(value, Boolean.class, typeName);
            default -> ENUM_PREFIX + escape(value.toString());
        };
    }

    /** Decodes a payload and rejects a tag that disagrees with the static type. */
    public static Object decode(String payload, Type type) {
        if (type == null) {
            throw new IllegalArgumentException("A static UML type is required for scalar decoding");
        }
        return decode(payload, type.shortName());
    }

    /** Decodes a payload and rejects malformed, legacy, or cross-typed text. */
    public static Object decode(String payload, String typeName) {
        if (payload == null) {
            throw new IllegalArgumentException("A canonical scalar payload must not be null");
        }
        if (BOTTOM.equals(payload)) {
            return null;
        }
        String normalizedType = requiredTypeName(typeName);
        try {
            return switch (normalizedType) {
                case "String" -> unescape(requirePrefix(payload, STRING_PREFIX, normalizedType));
                case "Integer" -> Long.parseLong(requirePrefix(payload, INTEGER_PREFIX, normalizedType));
                case "Real" -> decodeReal(requirePrefix(payload, REAL_PREFIX, normalizedType));
                case "Boolean" -> decodeBoolean(requirePrefix(payload, BOOLEAN_PREFIX, normalizedType));
                default -> unescape(requirePrefix(payload, ENUM_PREFIX, normalizedType));
            };
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "Malformed canonical " + normalizedType + " payload: " + payload, exception);
        }
    }

    public static String escape(String value) {
        if (value == null) {
            throw new IllegalArgumentException("A scalar payload component must not be null");
        }
        return value.replace("%", "%25").replace("|", "%7C");
    }

    public static String unescape(String value) {
        if (value == null) {
            throw new IllegalArgumentException("A scalar payload component must not be null");
        }
        validateEscapes(value);
        return value.replace("%7C", "|").replace("%25", "%");
    }

    private static String canonicalInteger(Object value) {
        BigInteger integer;
        if (value instanceof BigInteger bigInteger) {
            integer = bigInteger;
        } else if (value instanceof Byte || value instanceof Short
                || value instanceof Integer || value instanceof Long) {
            integer = BigInteger.valueOf(((Number) value).longValue());
        } else if (value instanceof BigDecimal decimal) {
            integer = decimal.toBigIntegerExact();
        } else {
            throw new IllegalArgumentException("Integer payload requires an exact integral value: " + value);
        }
        if (integer.compareTo(BigInteger.valueOf(Long.MIN_VALUE)) < 0
                || integer.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
            throw new IllegalArgumentException("Integer payload is outside the certified Int64 domain: " + value);
        }
        return integer.toString();
    }

    private static String canonicalReal(Object value) {
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("Real payload requires a numeric value: " + value);
        }
        double real = number.doubleValue();
        if (!Double.isFinite(real)) {
            throw new IllegalArgumentException("Real payload must be finite: " + value);
        }
        if (real == 0.0d) {
            real = 0.0d;
        }
        return Double.toString(real);
    }

    private static double decodeReal(String raw) {
        double value = Double.parseDouble(raw);
        if (!Double.isFinite(value) || !canonicalReal(value).equals(raw)) {
            throw new IllegalArgumentException("Real payload is not finite canonical Real64 text: " + raw);
        }
        return value;
    }

    private static boolean decodeBoolean(String raw) {
        if ("true".equals(raw)) return true;
        if ("false".equals(raw)) return false;
        throw new IllegalArgumentException("Malformed canonical Boolean payload: " + raw);
    }

    private static String requirePrefix(String payload, String prefix, String typeName) {
        if (!payload.startsWith(prefix)) {
            throw new IllegalArgumentException(
                    "Canonical payload tag does not match static type " + typeName + ": " + payload);
        }
        return payload.substring(prefix.length());
    }

    private static String requiredTypeName(String typeName) {
        if (typeName == null || typeName.isBlank()) {
            throw new IllegalArgumentException("A non-blank static UML type name is required");
        }
        return switch (typeName.trim()) {
            // UmlTypeTranslator persists compact database aliases, while USE
            // type objects expose the corresponding OCL names.
            case "Int" -> "Integer";
            case "Double" -> "Real";
            default -> typeName.trim();
        };
    }

    private static <T> T requireInstance(Object value, Class<T> expected, String typeName) {
        if (!expected.isInstance(value)) {
            throw new IllegalArgumentException(
                    typeName + " payload requires " + expected.getSimpleName() + ", found "
                            + value.getClass().getSimpleName());
        }
        return expected.cast(value);
    }

    private static void validateEscapes(String value) {
        for (int index = value.indexOf('%'); index >= 0; index = value.indexOf('%', index + 1)) {
            if (!(value.startsWith("%25", index) || value.startsWith("%7C", index))) {
                throw new IllegalArgumentException("Malformed canonical scalar escape at index " + index);
            }
            index += 2;
        }
    }
}
