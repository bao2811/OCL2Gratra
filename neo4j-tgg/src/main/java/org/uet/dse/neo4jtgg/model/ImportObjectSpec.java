package org.uet.dse.neo4jtgg.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class ImportObjectSpec {
    private final String objectName;
    private final String className;
    private final Map<String, String> attributes = new LinkedHashMap<>();

    public ImportObjectSpec(String objectName, String className) {
        this.objectName = objectName;
        this.className = className;
    }

    public String getObjectName() {
        return objectName;
    }

    public String getClassName() {
        return className;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }
}
