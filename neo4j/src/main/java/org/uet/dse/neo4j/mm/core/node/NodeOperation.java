package org.uet.dse.neo4j.mm.core.node;

import org.uet.dse.neo4j.aop.MetaNode;
import org.uet.dse.neo4j.mm.core.common.NType;

import java.util.HashMap;
import java.util.Map;

@MetaNode(type = MetaNodeData.NODE_OPERATION)
public class NodeOperation extends AbstractMetaNode {
    private String body;
    private boolean isAbstract;
    private boolean isQuery;

    private String opName;

    private NType returnType;

    private boolean isReturnCollection;

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public boolean isAbstract() {
        return isAbstract;
    }

    public void setAbstract(boolean anAbstract) {
        isAbstract = anAbstract;
    }

    public boolean isQuery() {
        return isQuery;
    }

    public void setQuery(boolean query) {
        isQuery = query;
    }

    public String getOpName() {
        return opName;
    }

    public void setOpName(String opName) {
        this.opName = opName;
    }

    public NType getReturnType() {
        return returnType;
    }

    public void setReturnType(NType returnType) {
        this.returnType = returnType;
    }

    public boolean isReturnCollection() {
        return isReturnCollection;
    }

    public void setReturnCollection(boolean returnCollection) {
        isReturnCollection = returnCollection;
    }

    @Override
    public String getMetaName() {
        return MetaNodeData.NODE_OPERATION.getName();
    }

    @Override
    public String getMetaLabel() {
        return MetaNodeData.NODE_OPERATION.getLabel();
    }

    @Override
    public Map<String, Object> toPropertyMap() {
        Map<String, Object> props = new HashMap<>();
        props.put("name", this.getName());
        props.put("opName", this.opName);
        props.put("isReturnCollection", this.isReturnCollection);
        props.put("isAbstract", this.isAbstract);
        props.put("body", this.body);
        props.put("isQuery", this.isQuery);
        props.put("returnType", returnType != null ? returnType.name() : NType.None.name());
        return props;
    }
}
