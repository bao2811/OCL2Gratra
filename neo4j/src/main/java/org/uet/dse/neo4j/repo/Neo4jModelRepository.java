package org.uet.dse.neo4j.repo;

import org.neo4j.driver.Driver;
import org.neo4j.driver.QueryRunner;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Transaction;
import org.neo4j.driver.Values;
import org.tzi.use.uml.mm.MAssociation;
import org.tzi.use.uml.mm.MAssociationClass;
import org.tzi.use.uml.mm.MAssociationEnd;
import org.uet.dse.neo4j.manager.Neo4jDriverManager;
import org.uet.dse.neo4j.mm.core.node.AbstractMetaNode;
import org.uet.dse.neo4j.mm.core.node.AbstractClassNode;
import org.uet.dse.neo4j.mm.core.node.NodeAttribute;
import org.uet.dse.neo4j.mm.core.node.NodeEnumeration;
import org.uet.dse.neo4j.encoding.CanonicalGraphEncoding;
import org.uet.dse.neo4j.encoding.CanonicalGraphSchema;
import org.uet.dse.neo4j.query.builder.CypherQueryBuilder;
import org.uet.dse.neo4j.query.builder.MetaNodeCypherBuilder;
import org.uet.dse.neo4j.query.model.MetaNodeDescriptor;
import org.uet.dse.neo4j.query.model.MetaNodeRegistry;
import org.uet.dse.neo4j.repo.query.Neo4jModelQuery;
import org.uet.dse.neo4j.sync.log.LoggingSession;

import java.util.List;
import java.util.Map;

public class Neo4jModelRepository {

    private final Driver driver;
    private final ThreadLocal<QueryRunner> sharedRunner = new ThreadLocal<>();

    public Neo4jModelRepository() {
        this.driver = Neo4jDriverManager.getInstance().getDriver();
    }

    public void createAssociationEdge(MAssociation assoc, String modelName) {
        if (assoc.associationEnds().size() < 2) return;

        MAssociationEnd end0 = assoc.associationEnds().get(0);
        MAssociationEnd end1 = assoc.associationEnds().get(1);
        String edgeLabel = resolveBinaryEdgeLabel(end0.aggregationKind(), end1.aggregationKind());

        withSession(session -> {
            session.run(
                    Neo4jModelQuery.DELETE_ASSOCIATION_EDGES,
                    Values.parameters("name", assoc.name(), "modelName", modelName)
            );
            session.run(
                    Neo4jModelQuery.upsertBinaryAssociationEdge(edgeLabel),
                    Values.parameters(
                            "assocName", assoc.name(),
                            "associationKey", CanonicalGraphEncoding.associationKey(modelName, assoc.name()),
                            "modelName", modelName,
                            "sName", end0.cls().name(),
                            "sourceClassKey", CanonicalGraphEncoding.classKey(modelName, end0.cls().name()),
                            "sRole", end0.name(),
                            "sMult", end0.multiplicity().toString(),
                            "sKind", end0.aggregationKind(),
                            "sOrdered", end0.isOrdered(),
                            "sQualifierNames", qualifierNames(end0),
                            "sQualifierTypes", qualifierTypes(end0),
                            "tName", end1.cls().name(),
                            "targetClassKey", CanonicalGraphEncoding.classKey(modelName, end1.cls().name()),
                            "tRole", end1.name(),
                            "tMult", end1.multiplicity().toString(),
                            "tKind", end1.aggregationKind(),
                            "tOrdered", end1.isOrdered(),
                            "tQualifierNames", qualifierNames(end1),
                            "tQualifierTypes", qualifierTypes(end1)
                    )
            );
        });
    }

    public void createAssociationClassStructure(MAssociationClass ac, String modelName) {
        MAssociationEnd src = ac.associationEnds().get(0);
        MAssociationEnd tgt = ac.associationEnds().get(1);

        withSession(session -> session.run(
                Neo4jModelQuery.UPSERT_ASSOCIATION_CLASS_STRUCTURE,
                Values.parameters(
                    "modelName", modelName,
                    "srcName", src.cls().name(),
                        "tgtName", tgt.cls().name(),
                        "acName", ac.name(),
                        "sRole", src.name(),
                        "sMult", src.multiplicity().toString(),
                        "tRole", tgt.name(),
                        "tMult", tgt.multiplicity().toString()
                )
        ));
    }

    public void upsertTernaryAssociation(MAssociation assoc, String modelName) {
        List<MAssociationEnd> ends = assoc.associationEnds();
        String assocName = assoc.name();

        withSession(session -> {
            session.run(
                    Neo4jModelQuery.createTernaryHub(assocName),
                    Values.parameters("name", assocName)
            );
            for (int i = 0; i < ends.size(); i++) {
                MAssociationEnd end = ends.get(i);
                String edgeLabel = resolveEndEdgeLabel(end.aggregationKind());
                session.run(
                        Neo4jModelQuery.upsertTernarySpoke(assocName, edgeLabel),
                        Values.parameters(
                            "modelName", modelName,
                            "clsName", end.cls().name(),
                                "name", assocName,
                                "idx", i,
                                "role", end.name(),
                                "mult", end.multiplicity().toString()
                        )
                );
            }
        });
    }

    public void createInstanceNode(AbstractMetaNode node, String dynamicLabel, String modelName) {
        Map<String, Object> props = node.toPropertyMap();
        requireNameProperty(props, dynamicLabel);
        props.put("modelKey", CanonicalGraphEncoding.modelKey(modelName));
        props.put("canonicalKey", modelName + "::" + node.getMetaName() + "::" + node.getName());
        if (node instanceof AbstractClassNode) {
            props.put("classKey", CanonicalGraphEncoding.classKey(modelName, node.getName()));
        }
        if (node instanceof NodeAttribute attribute) {
            String id = node.getName();
            String suffix = "_" + attribute.getAttrName();
            String owner = id.endsWith(suffix) ? id.substring(0, id.length() - suffix.length()) : id;
            props.put("attributeKey", CanonicalGraphEncoding.attributeKey(modelName, owner, attribute.getAttrName()));
        }

        withSession(session -> session.run(
                Neo4jModelQuery.upsertInstanceNode(dynamicLabel),
                Values.parameters(
                    "modelName", modelName,
                    "metaName", node.getMetaName(),
                        "id", node.getName(),
                        "props", props
                )
        ));
    }

    public void createStructuralEdge(String fromNodeName, String toNodeName, String edgeLabel) {
        withSession(session -> session.run(
                CypherQueryBuilder.buildStructuralEdgeQuery(edgeLabel),
                Values.parameters("from", fromNodeName, "to", toNodeName)
        ));
    }

    public QueryRunner currentRunner() {
        QueryRunner runner = sharedRunner.get();
        if (runner == null) throw new IllegalStateException("No active model synchronization batch");
        return runner;
    }

    public void upsertClassesBatch(QueryRunner runner, String modelName, List<Map<String, Object>> rows) {
        if (rows.isEmpty()) return;
        runner.run("UNWIND $rows AS row "
                        + "MATCH (m:ManageModel {name:$modelName})-[:DefineMetamodels]->"
                        + "(meta:MetaNode {name:row.metaName}) "
                        + "MERGE (inst:UmlClass {modelKey:$modelName,classKey:row.classKey}) "
                        + "SET inst += row.props "
                        + "MERGE (inst)-[:InstanceOf]->(meta)",
                Map.of("rows", rows, "modelName", modelName));
    }

    public boolean isModelCurrent(String modelName, long modelHash) {
        final boolean[] current = {false};
        withSession(session -> current[0] = session.run(
                "MATCH (m:ManageModel {name:$modelName, encodingVersion:$version, modelHash:$hash}) "
                        + "RETURN m",
                Map.of("modelName", modelName, "version", CanonicalGraphSchema.VERSION,
                        "hash", modelHash)).hasNext());
        return current[0];
    }

    public void upsertInheritanceBatch(QueryRunner runner, String modelName, List<Map<String, Object>> rows) {
        if (rows.isEmpty()) return;
        runner.run("UNWIND $rows AS row "
                        + "MATCH (src:UmlClass {modelKey:$modelName,classKey:row.childKey}), "
                        + "(tgt:UmlClass {modelKey:$modelName,classKey:row.parentKey}) "
                        + "MERGE (src)-[r:Extends]->(tgt) "
                        + "SET r.sourceName=row.childName, r.targetName=row.parentName, r.modelKey=$modelName",
                Map.of("rows", rows, "modelName", modelName));
    }

    public void upsertAttributesBatch(QueryRunner runner, String modelName, List<Map<String, Object>> rows) {
        if (rows.isEmpty()) return;
        runner.run("UNWIND $rows AS row "
                        + "MATCH (m:ManageModel {name:$modelName})-[:DefineMetamodels]->"
                        + "(meta:MetaNode {name:'NodeAttribute'}) "
                        + "MATCH (owner:UmlClass {modelKey:$modelName,classKey:row.ownerKey}) "
                        + "MERGE (a:Attribute {modelKey:$modelName,attributeKey:row.attributeKey}) "
                        + "SET a += row.props "
                        + "MERGE (owner)-[:HasAttribute]->(a) "
                        + "MERGE (a)-[:InstanceOf]->(meta) "
                        + "WITH a OPTIONAL MATCH (a)-[old:ReferenceType]->() DELETE old",
                Map.of("rows", rows, "modelName", modelName));
    }

    public void upsertAttributeReferencesBatch(QueryRunner runner, String modelName,
                                               List<Map<String, Object>> rows) {
        if (rows.isEmpty()) return;
        runner.run("UNWIND $rows AS row "
                        + "MATCH (a:Attribute {modelKey:$modelName,attributeKey:row.attributeKey}), "
                        + "(target:UmlClass {modelKey:$modelName,classKey:row.referenceKey}) "
                        + "MERGE (a)-[:ReferenceType]->(target)",
                Map.of("rows", rows, "modelName", modelName));
    }

    public void removeObsoleteBinaryAssociationTypes(QueryRunner runner, String modelName,
                                                     List<Map<String, Object>> rows) {
        if (rows.isEmpty()) return;
        runner.run("UNWIND $rows AS row "
                        + "MATCH (s:UmlClass {modelKey:$modelName,classKey:row.sourceClassKey})-[r]->"
                        + "(t:UmlClass {modelKey:$modelName,classKey:row.targetClassKey}) "
                        + "WHERE r.modelKey=$modelName AND r.associationKey=row.associationKey "
                        + "AND type(r) <> row.edgeLabel DELETE r",
                Map.of("rows", rows, "modelName", modelName));
    }

    public void upsertBinaryAssociationsBatch(QueryRunner runner, String edgeLabel,
                                               List<Map<String, Object>> rows) {
        if (rows.isEmpty()) return;
        String query = String.format(
                "UNWIND $rows AS row "
                        + "MATCH (s:UmlClass {modelKey:$modelName,classKey:row.sourceClassKey}), "
                        + "(t:UmlClass {modelKey:$modelName,classKey:row.targetClassKey}) "
                        + "MERGE (s)-[r:%s {modelKey:$modelName,associationKey:row.associationKey}]->(t) "
                        + "SET r += row.props, r.modelKey=$modelName, r.isTernary=false",
                edgeLabel);
        runner.run(query, Map.of("rows", rows, "modelName", rows.get(0).get("modelName")));
    }

    public void initializeMetamodel(String modelName) {
        List<MetaNodeDescriptor> descriptors = MetaNodeRegistry.ALL.stream()
                .map(MetaNodeDescriptor::from)
                .toList();

        withSession(session -> {
            boolean initialized = session.run(
                    "MATCH (m:ManageModel {name:$modelName, encodingVersion:$version}) RETURN m",
                    Map.of("modelName", modelName, "version", CanonicalGraphSchema.VERSION)).hasNext();
            if (!initialized) {
                session.run(MetaNodeCypherBuilder.buildInitializeScript(modelName, descriptors));
            }
        });
    }

    /** Runs a complete model synchronization batch in one transaction. */
    public void withSharedSession(java.util.function.Consumer<QueryRunner> block) {
        QueryRunner existing = sharedRunner.get();
        if (existing != null) {
            block.accept(existing);
            return;
        }
        String dbName = Neo4jDriverManager.getInstance().getActiveDatabase();
        try (Session session = driver.session(SessionConfig.forDatabase(dbName))) {
            try (Transaction transaction = session.beginTransaction()) {
                sharedRunner.set(transaction);
                block.accept(transaction);
                transaction.commit();
            }
        } finally {
            sharedRunner.remove();
        }
    }

    public void upsertEnumeration(String enumName, List<String> literals, String modelName) {
        NodeEnumeration nodeEnum = new NodeEnumeration();
        nodeEnum.setName(enumName);
        nodeEnum.setValues(literals);

        withSession(session -> session.run(
                CypherQueryBuilder.buildUpsertEnumerationQuery(nodeEnum.getName()),
                Values.parameters("modelName", modelName, "name", nodeEnum.getName(), "values", nodeEnum.getValues())
        ));
    }

    /**
     * Opens a session against the active database, runs the given block, and
     * closes the session. Centralises the repeated try-with-resources pattern
     * that previously appeared in every public method.
     */
    private void withSession(java.util.function.Consumer<QueryRunner> block) {
        QueryRunner batchRunner = sharedRunner.get();
        if (batchRunner != null) {
            block.accept(batchRunner);
            return;
        }
        String dbName = Neo4jDriverManager.getInstance().getActiveDatabase();
        try (Session session = driver.session(SessionConfig.forDatabase(dbName))) {
            Session loggingSession = new LoggingSession(session);
            block.accept(loggingSession);
        }

    }

    /**
     * Resolves the binary association edge label from both ends' aggregation kinds.
     * Composition takes priority over aggregation.
     */
    private static String resolveBinaryEdgeLabel(int kind0, int kind1) {
        if (kind0 == 2 || kind1 == 2) return "ComposeOf";
        if (kind0 == 1 || kind1 == 1) return "Aggregates";
        return "AssociateWith";
    }

    private static String resolveEndEdgeLabel(int aggregationKind) {
        if (aggregationKind == 2) return "ComposeOf";
        if (aggregationKind == 1) return "Aggregates";
        return "AssociateWith";
    }

    private static void requireNameProperty(Map<String, Object> props, String label) {
        if (props.get("name") == null) {
            throw new RuntimeException("Property 'name' is required for node: " + label);
        }
    }

    private static List<String> qualifierNames(MAssociationEnd end) {
        return end.getQualifiers().stream().map(declaration -> declaration.name()).toList();
    }

    private static List<String> qualifierTypes(MAssociationEnd end) {
        return end.getQualifiers().stream().map(declaration -> declaration.type().toString()).toList();
    }
}
