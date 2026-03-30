package org.uet.dse.neo4j.mm.object.common;

public enum AttributeType {
    STRING,
    INTEGER,
    BOOLEAN,
    FLOAT,
    OBJECT_REFERENCE,
    UNKNOWN;

    public static AttributeType from(String value) {
        try {
            return AttributeType.valueOf(value.toUpperCase());
        } catch (Exception e) {
            return UNKNOWN;
        }
    }
}
