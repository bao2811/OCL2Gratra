package org.uet.dse.neo4j.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FullModelSnapshot {
    public Map<String, ClassState> classes = new HashMap<>();
    public Map<String, AssociationState> associations = new HashMap<>();
    public String modelName;

    public List<Map<String, Object>> ternaryAssociations = new ArrayList<>();

    public boolean isEmpty() {
        return (classes == null || classes.isEmpty())
                && (associations == null || associations.isEmpty())
                && (ternaryAssociations == null || ternaryAssociations.isEmpty());
    }


}
