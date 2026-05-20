package org.uet.dse.neo4jtgg.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ImportLinkSpec {
    private final String associationName;
    private final List<String> endpointNames;

    public ImportLinkSpec(String associationName, List<String> endpointNames) {
        this.associationName = associationName;
        this.endpointNames = new ArrayList<>(endpointNames);
    }

    public String getAssociationName() {
        return associationName;
    }

    public List<String> getEndpointNames() {
        return Collections.unmodifiableList(endpointNames);
    }
}
