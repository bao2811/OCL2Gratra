package org.uet.dse.neo4j.repo;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Values;
import org.tzi.use.uml.mm.MAssociation;
import org.tzi.use.uml.mm.MAssociationClass;
import org.tzi.use.uml.mm.MAssociationEnd;
import org.uet.dse.neo4j.manager.Neo4jDriverManager;
import org.uet.dse.neo4j.mm.core.node.AbstractMetaNode;
import org.uet.dse.neo4j.mm.core.node.NodeEnumeration;
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

    public Neo4jModelRepository() {
        this.driver = Neo4jDriverManager.getInstance().getDriver();
    }

    public void createAssociationEdge(MAssociation assoc) {
        if (assoc.associationEnds().size() < 2) return;

        MAssociationEnd end0 = assoc.associationEnds().get(0);
        MAssociationEnd end1 = assoc.associationEnds().get(1);
        String edgeLabel = resolveBinaryEdgeLabel(end0.aggregationKind(), end1.aggregationKind());

        withSession(session -> {
            session.run(
                    Neo4jModelQuery.DELETE_ASSOCIATION_EDGES,
                    Values.parameters("name", assoc.name())
            );
            session.run(
                    Neo4jModelQuery.upsertBinaryAssociationEdge(edgeLabel),
                    Values.parameters(
                            "assocName", assoc.name(),
                            "sName", end0.cls().name(),
                            "sRole", end0.name(),
                            "sMult", end0.multiplicity().toString(),
                            "sKind", end0.aggregationKind(),
                            "tName", end1.cls().name(),
                            "tRole", end1.name(),
                            "tMult", end1.multiplicity().toString(),
                            "tKind", end1.aggregationKind()
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

    public void initializeMetamodel(String modelName) {
        List<MetaNodeDescriptor> descriptors = MetaNodeRegistry.ALL.stream()
                .map(MetaNodeDescriptor::from)
                .toList();

        withSession(session -> session.run(
            MetaNodeCypherBuilder.buildInitializeScript(modelName, descriptors)
        ));
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
    private void withSession(java.util.function.Consumer<Session> block) {
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
}