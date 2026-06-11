package org.uet.dse.neo4jtgg.service.impl;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Values;
import org.uet.dse.neo4j.manager.Neo4jDriverManager;
import org.uet.dse.neo4jtgg.model.TggRuleInfo;
import org.uet.dse.neo4jtgg.model.TggWorkspaceDefinition;
import org.uet.dse.neo4jtgg.model.WorkspaceSide;

import java.util.LinkedHashSet;
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

    public void connectTggMetamodelLayers(TggWorkspaceDefinition definition) {
        if (definition == null) {
            return;
        }

        Driver driver = Neo4jDriverManager.getInstance().getDriver();
        String dbName = Neo4jDriverManager.getInstance().getActiveDatabase();

        try (Session session = driver.session(SessionConfig.forDatabase(dbName))) {
            Set<String> connectedAssociations = new LinkedHashSet<>();
            for (TggRuleInfo rule : definition.getRules()) {
                for (TggRuleInfo.CorrPattern pattern : rule.getRequiredCorrPatterns()) {
                    connectPattern(session, connectedAssociations, rule, pattern);
                }
                for (TggRuleInfo.CorrPattern pattern : rule.getOutputCorrPatterns()) {
                    connectPattern(session, connectedAssociations, rule, pattern);
                }
            }
        }
    }

    private void connectPattern(Session session,
                                Set<String> connectedAssociations,
                                TggRuleInfo rule,
                                TggRuleInfo.CorrPattern pattern) {
        if (pattern == null) {
            return;
        }

        String corrClassName = pattern.corrClassName();
        String sourceClassName = rule.getTypedVariables(WorkspaceSide.SOURCE).get(pattern.sourceVarName());
        String targetClassName = rule.getTypedVariables(WorkspaceSide.TARGET).get(pattern.targetVarName());

        if (corrClassName != null && sourceClassName != null) {
            upsertCrossModelAssociation(session, connectedAssociations, corrClassName, sourceClassName);
        }
        if (corrClassName != null && targetClassName != null) {
            upsertCrossModelAssociation(session, connectedAssociations, corrClassName, targetClassName);
        }
    }

    private void upsertCrossModelAssociation(Session session,
                                             Set<String> connectedAssociations,
                                             String corrClassName,
                                             String domainClassName) {
        String associationName = corrClassName + "_" + domainClassName;
        if (!connectedAssociations.add(associationName)) {
            return;
        }

        session.run(
                "MATCH (corr {name: $corrClassName})-[:InstanceOf]->(corrMeta:MetaNode) " +
                        "MATCH (domain {name: $domainClassName})-[:InstanceOf]->(domainMeta:MetaNode) " +
                        "WHERE corrMeta.name IN ['NodeConcreteClass','NodeAbstractClass','NodeAssociationClass'] " +
                        "  AND domainMeta.name IN ['NodeConcreteClass','NodeAbstractClass','NodeAssociationClass'] " +
                        "MERGE (corr)-[r:AssociateWith {associationName: $associationName}]->(domain) " +
                        "SET r.sourceClassName = $corrClassName, " +
                        "    r.sourceClassrole = $corrRole, " +
                        "    r.sourceMultiplicity = '0..*', " +
                        "    r.sourceKind = 0, " +
                        "    r.targetClassName = $domainClassName, " +
                        "    r.targerClassrole = $domainRole, " +
                        "    r.targetMultiplicity = '0..*', " +
                        "    r.targetKind = 0, " +
                        "    r.isTernary = false",
                Values.parameters(
                        "corrClassName", corrClassName,
                        "domainClassName", domainClassName,
                        "associationName", associationName,
                        "corrRole", lowerFirst(corrClassName),
                        "domainRole", lowerFirst(domainClassName)
                )
        );
    }

    private String lowerFirst(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return Character.toLowerCase(value.charAt(0)) + value.substring(1);
    }
}
