package org.uet.dse.neo4j.model;

import java.util.Objects;

public class AssociationState {
    public String name;
    public String type; // AssociateWith, ComposeOf, Aggregates
    public String srcName;
    public String srcRole;
    public String srcMult;
    public String tgtName;
    public String tgtRole;
    public String tgtMult;

    public boolean isSameAs(AssociationState other) {
        if (other == null) return false;
        return Objects.equals(name, other.name) &&
               Objects.equals(type, other.type) &&
               Objects.equals(srcName, other.srcName) &&
               Objects.equals(tgtName, other.tgtName) &&
               Objects.equals(srcRole, other.srcRole) &&
               Objects.equals(tgtRole, other.tgtRole) &&
               Objects.equals(srcMult, other.srcMult) &&
               Objects.equals(tgtMult, other.tgtMult);
    }
}