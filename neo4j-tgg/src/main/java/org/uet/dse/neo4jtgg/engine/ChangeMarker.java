package org.uet.dse.neo4jtgg.engine;

/**
 * Marker indicating whether a TGG rule variable represents an existing (context) element
 * or a newly created element during transformation.
 */
public enum ChangeMarker {
    /** Element must already exist (matched from the snapshot). */
    CONTEXT,
    /** Element is created by this rule application. */
    CREATE,
    /** Element is deleted by this rule application. */
    DELETE
}
