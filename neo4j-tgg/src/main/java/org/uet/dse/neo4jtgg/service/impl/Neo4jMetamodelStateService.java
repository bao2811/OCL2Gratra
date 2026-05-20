package org.uet.dse.neo4jtgg.service.impl;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Values;
import org.uet.dse.neo4j.manager.Neo4jDriverManager;

import java.util.Set;

public class Neo4jMetamodelStateService {

    public boolean hasImportedMetamodel(String modelName, Set<String> expectedClasses) {
        Driver driver = Neo4jDriverManager.getInstance().getDriver();
        String dbName = Neo4jDriverManager.getInstance().getActiveDatabase();

        try (Session session = driver.session(SessionConfig.forDatabase(dbName))) {
            if (!hasManageModelNode(session, modelName)) {
                return false;
            }
            if (expectedClasses == null || expectedClasses.isEmpty()) {
                return true;
            }
            return hasAllExpectedClasses(session, modelName, expectedClasses);
        }
    }

    private boolean hasManageModelNode(Session session, String modelName) {
        Result result = session.run(
                "MATCH (m:ManageModel {name: $modelName}) RETURN count(m) > 0 AS exists",
                Values.parameters("modelName", modelName)
        );
        return result.single().get("exists").asBoolean();
    }

    private boolean hasAllExpectedClasses(Session session, String modelName, Set<String> expectedClasses) {
        Result result = session.run(
                "MATCH (m:ManageModel {name: $modelName})-[:DefineMetamodels]->()<-[:InstanceOf]-(cls) " +
                        "WHERE cls.name IN $classNames " +
                        "RETURN count(DISTINCT cls.name) AS classCount",
                Values.parameters("modelName", modelName, "classNames", expectedClasses)
        );
        int classCount = result.single().get("classCount").asInt();
        return classCount >= expectedClasses.size();
    }
}
