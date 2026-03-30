package org.uet.dse.neo4j.mm.graphdb;

public class Edge {
    private String type;
    private String fromVertexId;
    private String toVertexId;

    public Edge(String type, String fromVertexId, String toVertexId) {
        this.type = type;
        this.fromVertexId = fromVertexId;
        this.toVertexId = toVertexId;
    }
    public String getType() { return type; }
    public String getFromVertexId() { return fromVertexId; }
    public String getToVertexId() { return toVertexId; }
}