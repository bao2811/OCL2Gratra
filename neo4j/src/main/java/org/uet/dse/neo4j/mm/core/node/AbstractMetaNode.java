package org.uet.dse.neo4j.mm.core.node;

public abstract class AbstractMetaNode {
    private String metaName;
    private String metaLabel;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    private String instanceId;
    private String name;

    public String getInstanceId() { return instanceId; }
    public void setInstanceId(String instanceId) { this.instanceId = instanceId; }

    public abstract String getMetaName();

    public abstract String getMetaLabel();

    // Thêm method này để phục vụ tham số $props trong Cypher
    public abstract java.util.Map<String, Object> toPropertyMap();

}
