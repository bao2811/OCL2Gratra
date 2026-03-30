package org.uet.dse.neo4j.mm.graphdb;

public class Vertex {
    private String id;
    private String name;

    public Vertex(String id, String name) {
        this.id = id;
        this.name = name;
    }
    public String getId() { return id; }
    public String getName() { return name; }
}