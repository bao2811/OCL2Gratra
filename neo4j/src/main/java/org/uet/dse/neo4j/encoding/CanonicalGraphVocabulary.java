package org.uet.dse.neo4j.encoding;

/** Canonical relationship vocabulary and its model-level boundaries. */
public final class CanonicalGraphVocabulary {
    /** M0 object conformance to its runtime UML class and every conforming supertype. */
    public static final String OBJECT_INSTANCE_OF = "ObjectInstanceOf";

    /** Generic schema instantiation, such as M1-to-M2 and AttributeValue-to-Attribute. */
    public static final String SCHEMA_INSTANCE_OF = "InstanceOf";

    private CanonicalGraphVocabulary() {
    }
}
