package org.uet.dse.neo4jtgg.model;

import org.uet.dse.neo4j.model.LinkState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ImportLinkSpec {
    private final String associationName;
    private final List<String> endpointNames;
    private final List<List<String>> qualifierValues;

    public ImportLinkSpec(String associationName, List<String> endpointNames) {
        this(associationName, endpointNames, List.of());
    }

    public ImportLinkSpec(String associationName, List<String> endpointNames, List<List<String>> qualifierValues) {
        this.associationName = associationName;
        this.endpointNames = new ArrayList<>(endpointNames);
        this.qualifierValues = new ArrayList<>();
        for (List<String> endQualifiers : qualifierValues != null ? qualifierValues : List.<List<String>>of()) {
            this.qualifierValues.add(List.copyOf(endQualifiers != null ? endQualifiers : List.of()));
        }
    }

    public String getAssociationName() {
        return associationName;
    }

    public List<String> getEndpointNames() {
        return Collections.unmodifiableList(endpointNames);
    }

    public List<List<String>> getQualifierValues() {
        return Collections.unmodifiableList(qualifierValues);
    }

    public String getIdentity() {
        return LinkState.buildIdentity(associationName, endpointNames, qualifierValues, null);
    }
}
