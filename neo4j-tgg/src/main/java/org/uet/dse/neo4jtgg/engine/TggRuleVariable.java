package org.uet.dse.neo4jtgg.engine;

/**
 * A typed variable in a TGG rule with a change marker.
 */
public record TggRuleVariable(String name, String className, ChangeMarker marker) {

    public boolean isContext() {
        return marker == ChangeMarker.CONTEXT;
    }

    public boolean isCreate() {
        return marker == ChangeMarker.CREATE;
    }

    public boolean isDelete() {
        return marker == ChangeMarker.DELETE;
    }
}
