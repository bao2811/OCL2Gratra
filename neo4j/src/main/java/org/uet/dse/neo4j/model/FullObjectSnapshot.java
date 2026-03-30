package org.uet.dse.neo4j.model;

import java.util.*;

public class FullObjectSnapshot {
    // ID Object (use_id) -> ObjectState
    public Map<String, ObjectState> objects = new HashMap<>();

    // Identity Link -> LinkState
    public Map<String, LinkState> links = new HashMap<>();

    public boolean isEmpty() {
        return objects.isEmpty() && links.isEmpty();
    }
}