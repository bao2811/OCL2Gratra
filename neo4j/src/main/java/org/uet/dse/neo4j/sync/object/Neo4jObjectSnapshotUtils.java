package org.uet.dse.neo4j.sync.object;

import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.uet.dse.neo4j.manager.Neo4jDriverManager;
import org.uet.dse.neo4j.model.FullObjectSnapshot;
import org.uet.dse.neo4j.model.LinkState;
import org.uet.dse.neo4j.model.ObjectState;
import org.uet.dse.neo4j.sync.query.Neo4jSnapshotQuery;

import java.util.Map;

public class Neo4jObjectSnapshotUtils {
    public FullObjectSnapshot getNeo4jObjectSnapshot(String modelName) {
        FullObjectSnapshot snapshot = new FullObjectSnapshot();
        String dbName = Neo4jDriverManager.getInstance().getActiveDatabase();

        try (Session session = Neo4jDriverManager.getInstance().getDriver()
                .session(SessionConfig.forDatabase(dbName))) {

            collectObjects(session, snapshot, modelName);
            collectBinaryLinks(session, snapshot, modelName);
            collectLinkObjects(session, snapshot, modelName);
        }

        return snapshot;
    }

    private void collectObjects(Session session, FullObjectSnapshot snapshot, String modelName) {
        session.run(Neo4jSnapshotQuery.OBJECTS_AND_ATTRIBUTES, Map.of("modelName", modelName)).forEachRemaining(row -> {
            ObjectState os = Neo4jObjectSnapshotMapper.toObjectState(row);
            snapshot.objects.put(os.name, os);
        });
    }

    private void collectBinaryLinks(Session session, FullObjectSnapshot snapshot, String modelName) {
        session.run(Neo4jSnapshotQuery.BINARY_LINKS, Map.of("modelName", modelName)).forEachRemaining(rec -> {
            LinkState ls = Neo4jObjectSnapshotMapper.toBinaryLink(rec);
            snapshot.links.put(ls.getIdentity(), ls);
        });
    }

    private void collectLinkObjects(Session session, FullObjectSnapshot snapshot, String modelName) {
        session.run(Neo4jSnapshotQuery.LINK_OBJECTS, Map.of("modelName", modelName)).forEachRemaining(rec -> {
            LinkState ls = Neo4jObjectSnapshotMapper.toLinkObject(rec);
            snapshot.links.put(ls.getIdentity(), ls);
        });
    }
}
