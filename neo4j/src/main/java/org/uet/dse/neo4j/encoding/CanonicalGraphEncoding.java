package org.uet.dse.neo4j.encoding;

import java.util.List;
import java.util.Objects;

/**
 * Stable, model-scoped keys used by the certified OCL validation profile.
 * Legacy {@code name} properties remain available for the USE plugin UI, but
 * validation queries must use these keys whenever a model name is known.
 */
public final class CanonicalGraphEncoding {
    public static final String PROFILE_ID = "canonical-v1";
    private static final String COMPONENT_DELIMITER = "::";

    private CanonicalGraphEncoding() {
    }

    public static String modelKey(String modelName) {
        return component(modelName, "modelName");
    }

    public static String classKey(String modelName, String className) {
        return modelKey(modelName) + "::class::" + component(className, "className");
    }

    public static String attributeKey(String modelName, String ownerClassName, String attributeName) {
        return modelKey(modelName) + "::attribute::" + component(ownerClassName, "ownerClassName")
                + "::" + component(attributeName, "attributeName");
    }

    public static String associationKey(String modelName, String associationName) {
        return modelKey(modelName) + "::association::" + component(associationName, "associationName");
    }

    /**
     * Canonical identity of one binary association-link instance.
     *
     * <p>Every variable component is length-prefixed. Consequently neither
     * delimiters inside object ids nor serialized qualifier payloads can make
     * two distinct semantic tuples share a key.</p>
     */
    public static String binaryLinkKey(String modelName, String associationName,
                                       String sourceId, String targetId,
                                       List<String> sourceQualifiers,
                                       List<String> targetQualifiers) {
        return modelKey(modelName) + "::" + binaryLinkIdentity(associationName, sourceId, targetId,
                sourceQualifiers, targetQualifiers);
    }

    /** Model-independent suffix shared with object/link change detection. */
    public static String binaryLinkIdentity(String associationName,
                                            String sourceId, String targetId,
                                            List<String> sourceQualifiers,
                                            List<String> targetQualifiers) {
        return "link::association="
                + frame(required(associationName, "associationName"))
                + "::source=" + frame(required(sourceId, "sourceId"))
                + "::target=" + frame(required(targetId, "targetId"))
                + "::sourceQualifiers=" + frameList(sourceQualifiers, "sourceQualifiers")
                + "::targetQualifiers=" + frameList(targetQualifiers, "targetQualifiers");
    }

    /** Canonical identity of one object's slot for one declared attribute. */
    public static String attributeSlotKey(String modelName, String objectId,
                                          String ownerClassName, String attributeName) {
        return objectKey(modelName, objectId) + "::slot::"
                + attributeKey(modelName, ownerClassName, attributeName);
    }

    public static String objectKey(String modelName, String objectId) {
        return objectKeyPrefix(modelName) + component(objectId, "objectId");
    }

    public static String objectKeyPrefix(String modelName) {
        return modelKey(modelName) + "::object::";
    }

    private static String required(String value, String label) {
        String normalized = Objects.requireNonNull(value, label).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return normalized;
    }

    private static String frame(String value) {
        return value.length() + ":" + value;
    }

    private static String frameList(List<String> values, String label) {
        Objects.requireNonNull(values, label);
        StringBuilder encoded = new StringBuilder().append(values.size()).append(":");
        for (int index = 0; index < values.size(); index++) {
            encoded.append(frame(Objects.requireNonNull(values.get(index), label + "[" + index + "]")));
        }
        return encoded.toString();
    }

    /**
     * Returns one admitted canonical-v1 key component.
     *
     * <p>The profile uses {@code ::} as an unescaped structural delimiter. Rejecting
     * that delimiter in every semantic component makes tuple decomposition unique,
     * and therefore makes each typed key constructor injective over normalized
     * admitted components. This is an admission check, not an escaping rule, so an
     * invalid name can never silently alias a different model element.</p>
     */
    private static String component(String value, String label) {
        String normalized = required(value, label);
        if (normalized.contains(COMPONENT_DELIMITER)) {
            throw new IllegalArgumentException(label + " must not contain reserved delimiter "
                    + COMPONENT_DELIMITER + " in " + PROFILE_ID);
        }
        return normalized;
    }
}
