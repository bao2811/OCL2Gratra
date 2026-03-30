package org.uet.dse.neo4j.mm.core.common;

public enum NCollectionType {
    Set, Bag, None, Other, Sequence, OrderedSet;

    public static NCollectionType fromString(String colStr) {
        try {
            return NCollectionType.valueOf(colStr);
        } catch (Exception e) {
            return NCollectionType.Other;
        }
    }
}
