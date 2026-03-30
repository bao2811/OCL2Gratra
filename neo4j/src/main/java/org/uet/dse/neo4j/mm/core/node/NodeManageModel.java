package org.uet.dse.neo4j.mm.core.node;

import java.util.Map;

public class NodeManageModel extends AbstractMetaNode {
    @Override
    public String getName() { return "NodeManageModel"; }

    @Override
    public String getMetaName() {
        return "";
    }

    @Override
    public String getMetaLabel() {
        return "";
    }

    @Override
    public Map<String, Object> toPropertyMap() {
        return Map.of();
    }
    public String getLabel() { return "ManageModel"; }
}