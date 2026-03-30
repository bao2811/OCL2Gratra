package org.uet.dse.neo4j.repo.real;

import org.neo4j.driver.*;
import org.neo4j.driver.Record;
import org.uet.dse.neo4j.manager.Neo4jDriverManager;
import org.uet.dse.neo4j.mm.object.node.DomainObject;
import org.uet.dse.neo4j.repo.mapper.DomainObjectMapper;
import org.uet.dse.neo4j.repo.query.DomainObjectQuery;

import java.util.*;

public class Neo4jDomainObjectRepository implements DomainObjectRepository {

    private final Driver driver = Neo4jDriverManager.getInstance().getDriver();
    private final DomainObjectMapper mapper = new DomainObjectMapper();

    @Override
    public Optional<DomainObject> findById(String id) {

        try (Session session = Neo4jDriverManager.getInstance().openSession()) {

            List<Record> records = session.executeRead(tx ->
                    tx.run(
                            DomainObjectQuery.FIND_OBJECT_WITH_ATTRIBUTES,
                            Map.of("id", id)
                    ).list()
            );

//            List<Record> records = session.run(
//                DomainObjectQuery.FIND_OBJECT_WITH_ATTRIBUTEStest,
//                Map.of("id", id)
//            ).list();
            if (records.isEmpty()) {
                return Optional.empty();
            }

            return Optional.of(mapper.map(records));
        }
    }
}
