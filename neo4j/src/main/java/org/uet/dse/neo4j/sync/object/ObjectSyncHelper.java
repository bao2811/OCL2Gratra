package org.uet.dse.neo4j.sync.object;

import org.uet.dse.neo4j.manager.Neo4jDriverManager;

import java.util.Comparator;

public class ObjectSyncHelper {
    public static long calculateModelHash(org.tzi.use.uml.mm.MModel model) {
        StringBuilder sb = new StringBuilder();
        model.classes().stream()
                .sorted(Comparator.comparing(org.tzi.use.uml.mm.MModelElement::name))
                .forEach(cls -> {
                    sb.append("Class:").append(cls.name()).append(cls.isAbstract());
                    cls.allAttributes().forEach(a -> sb.append("Attr:").append(a.name()).append(a.type().toString()));
                    cls.allOperations().forEach(o -> sb.append("Op:").append(o.name()));
                });
        return (long) sb.toString().hashCode();
    }

    private String getLinkLabel(int kindT, int kindS) {
        int maxKind = Math.max(kindT, kindS);
        if (maxKind == 2) return "LinkComposeOf";
        if (maxKind == 1) return "LinkAggregates";
        return "LinkAssociateWith";
    }

    public static long getRemoteModelHash() {
        String dbName = Neo4jDriverManager.getInstance().getActiveDatabase();
        try (org.neo4j.driver.Session session = Neo4jDriverManager.getInstance().getDriver().session(org.neo4j.driver.SessionConfig.forDatabase(dbName))) {
            org.neo4j.driver.Result res = session.run("MATCH (v:ModelVersion {id: 'CURRENT'}) RETURN v.modelHash AS hash");
            if (res.hasNext()) return res.single().get("hash").asLong();
        }
        return -1;
    }
}
