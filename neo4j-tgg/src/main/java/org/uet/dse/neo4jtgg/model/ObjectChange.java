package org.uet.dse.neo4jtgg.model;

import java.util.Map;

/**
 * Represents a single object-level change detected between two snapshots.
 */
public record ObjectChange(
        String objectId,
        String className,
        Map<String, Object> previousAttributes,
        Map<String, Object> currentAttributes) {

    public ObjectChange {
        previousAttributes = previousAttributes != null ? Map.copyOf(previousAttributes) : Map.of();
        currentAttributes = currentAttributes != null ? Map.copyOf(currentAttributes) : Map.of();
    }

    public static ObjectChange added(String objectId, String className, Map<String, Object> attributes) {
        return new ObjectChange(objectId, className, Map.of(), attributes);
    }

    public static ObjectChange deleted(String objectId, String className, Map<String, Object> attributes) {
        return new ObjectChange(objectId, className, attributes, Map.of());
    }

    public static ObjectChange modified(String objectId, String className,
                                         Map<String, Object> previousAttributes,
                                         Map<String, Object> currentAttributes) {
        return new ObjectChange(objectId, className, previousAttributes, currentAttributes);
    }

    public boolean hasAttributeChanges() {
        return !previousAttributes.equals(currentAttributes);
    }

    public String toDisplayText() {
        return objectId + " : " + className;
    }
}
