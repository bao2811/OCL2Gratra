package org.uet.dse.neo4j.oclite;

import org.neo4j.driver.*;
import org.neo4j.driver.Record;
import org.neo4j.driver.types.Node;
import org.uet.dse.neo4j.encoding.CanonicalGraphEncoding;
import org.uet.dse.neo4j.manager.Neo4jDriverManager;
import org.uet.dse.neo4j.oclite.expr.ExpressionNode;
import org.uet.dse.neo4j.oclite.expr.VariableExpression;
import org.uet.dse.neo4j.sync.helper.CanonicalScalarValueCodec;
import org.uet.dse.neo4j.sync.helper.CanonicalCollectionValueCodec;
import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

public class Neo4jRepository {
    private final String modelName;
    private final String modelKey;

    public Neo4jRepository(String modelName) {
        this.modelName = modelName;
        this.modelKey = CanonicalGraphEncoding.modelKey(modelName);
    }


    public Node findNodeById(String name) {
        try (Session session = Neo4jDriverManager.getInstance().openSession()) {
            return session.executeRead(tx -> {
                String cypher = "MATCH (m:ManageModel {name: $modelName})-[:DefineMetamodels]->()"
                    + "<-[:InstanceOf]-(cls:UmlClass {modelKey:$modelKey}) "
                    + "MATCH (obj:Object {modelKey:$modelKey,use_id:$name})-[:ObjectInstanceOf]->(cls) " +
                    "RETURN obj";
                Result res = tx.run(cypher, Values.parameters(
                        "modelName", modelName, "modelKey", modelKey, "name", name));
                return res.hasNext() ? res.next().get("obj").asNode() : null;
            });
        }
    }
    public Object getAttributeValue(Node sourceNode, String attrName) {
        try (Session session = Neo4jDriverManager.getInstance().openSession()) {
            return session.executeRead(tx -> {
                String cypher =
                    "MATCH (o:Object {modelKey:$modelKey}) WHERE elementId(o) = $elementId " +
                        "MATCH (o)-[:ObjectHasAttribute]->(val:AttributeValue {modelKey:$modelKey}) " +
                        "WHERE val.name ENDS WITH $suffix " +
                        "RETURN val.value AS value,val.type AS type,val.isCollection AS isCollection";

                Result res = tx.run(cypher, Values.parameters(
                    "modelKey", modelKey,
                    "elementId", sourceNode.elementId(),
                    "suffix", "_" + attrName
                ));

                if (res.hasNext()) {
                    return decodeStoredValue(res.next());
                }
                return null;
            });
        }
    }

    public boolean isVariableExist(String varName) {
        try (Session session = Neo4jDriverManager.getInstance().openSession()) {
            return session.executeRead(tx -> {
                String cypher =
                    "MATCH (m:ManageModel {name: $modelName})-[:DefineMetamodels]->(meta:MetaNode) " +
                        "WHERE meta.name IN ['NodeConcreteClass', 'NodeAssociationClass'] " +
                        "MATCH (cls:UmlClass {modelKey:$modelKey})-[:InstanceOf]->(meta) " +
                        "MATCH (obj:Object {modelKey:$modelKey,use_id:$varName})-[:ObjectInstanceOf]->(cls) " +
                        "RETURN count(obj) > 0 AS exists";

                Result res = tx.run(cypher, Values.parameters(
                        "modelName", modelName, "modelKey", modelKey, "varName", varName));
                return res.single().get("exists").asBoolean();
            });
        }
    }


    public boolean isRelationship(ExpressionNode sourceNode, String name) {
        String sourceClassName = getClassNameOfExpression(sourceNode);
        if (sourceClassName == null) return false;

        try (Session session = Neo4jDriverManager.getInstance().openSession()) {
            return session.executeRead(tx -> {

                String cypher =
                    "MATCH (m:ManageModel {name:$modelName})-[:DefineMetamodels]->()"
                        + "<-[:InstanceOf]-(cls:UmlClass {modelKey:$modelKey,name:$clsName}) "
                        + "MATCH (cls)-[r]-(tgt:UmlClass {modelKey:$modelKey}) "
                        + "WHERE r.modelKey=$modelKey "
                        + "AND type(r) IN ['AssociateWith', 'ComposeOf', 'Aggregates'] " +
                        "AND ( " +
                        "  (startNode(r) = cls AND (r.targerClassrole = $targetName OR r.associationName = $targetName)) " +
                        "  OR " +
                        "  (endNode(r) = cls AND (r.sourceClassrole = $targetName OR r.associationName = $targetName)) " +
                        ") " +
                        "RETURN count(r) > 0 AS isRel";

                Result res = tx.run(cypher, Values.parameters(
                    "modelName", this.modelName,
                    "modelKey", modelKey,
                    "clsName", sourceClassName,
                    "targetName", name
                ));
                return res.single().get("isRel").asBoolean();
            });
        }
    }

    private String getClassNameOfExpression(ExpressionNode node) {

        if (node instanceof VariableExpression) {
            VariableExpression variable = (VariableExpression) node;
            if (variable.getClassName() != null && !variable.getClassName().isBlank()) {
                return variable.getClassName();
            }
            return findClassNameByObjectId(variable.getVarName());
        }

        return null;
    }

    private String findClassNameByObjectId(String objId) {
        try (Session session = Neo4jDriverManager.getInstance().openSession()) {
            return session.executeRead(tx -> {
                String cypher = "MATCH (o:Object {modelKey:$modelKey,use_id:$id})"
                        + "-[:ObjectInstanceOf]->(cls:UmlClass {modelKey:$modelKey}) RETURN cls.name";
                Result res = tx.run(cypher, Map.of("modelKey", modelKey, "id", objId));
                return res.hasNext() ? res.next().get(0).asString() : null;
            });
        }
    }
    public Object findAttributeValue(Node ownerNode, String attrName) {
        try (Session session = Neo4jDriverManager.getInstance().openSession()) {
            return session.executeRead(tx -> {
                String cypher =
                    "MATCH (o:Object {modelKey:$modelKey}) WHERE elementId(o) = $elementId " +
                        "MATCH (o)-[:ObjectHasAttribute]->(val:AttributeValue {modelKey:$modelKey}) " +
                        "WHERE val.name ENDS WITH $attrName " +
                        "RETURN val.value AS value,val.type AS type,val.isCollection AS isCollection";

                Result res = tx.run(cypher, Values.parameters(
                        "modelKey", modelKey, "elementId", ownerNode.elementId(),
                        "attrName", "_" + attrName));
                if (res.hasNext()) {
                    return decodeStoredValue(res.next());
                }
                return null;
            });
        }
    }

    public List<Node> findRelatedNodes(Node startNode, String relationshipType) {
        try (Session session = Neo4jDriverManager.getInstance().openSession()) {
            return session.executeRead(tx -> {

                String cypher =
                    "MATCH (s:Object {modelKey:$modelKey}) WHERE elementId(s) = $elementId " +
                        "MATCH (s)-[r]-(t:Object {modelKey:$modelKey}) " +
                        "WHERE r.modelKey=$modelKey AND type(r) STARTS WITH 'Link' " +
                        "AND ( " +
                        "  (startNode(r) = s AND (r.targetRole = $relType OR r.name = $relType)) " +
                        "  OR " +
                        "  (endNode(r) = s AND (r.sourceRole = $relType OR r.name = $relType)) " +
                        ") " +
                        "RETURN t";

                Result res = tx.run(cypher, Values.parameters(
                    "elementId", startNode.elementId(),
                    "modelKey", modelKey,
                    "relType", relationshipType
                ));

                List<Node> nodes = new ArrayList<>();
                while (res.hasNext()) {
                    nodes.add(res.next().get("t").asNode());
                }
                return nodes;
            });
        }
    }

    public List<Node> findAllInstancesOfClass(String className) {
        try (Session session = Neo4jDriverManager.getInstance().openSession()) {
            return session.executeRead(tx -> {
                String cypher =
                    "MATCH (cls:UmlClass {modelKey:$modelKey,classKey:$classKey}) " +
                        "MATCH (obj:Object {modelKey:$modelKey})-[:ObjectInstanceOf]->(cls) " +
                        "RETURN DISTINCT obj";

                Result res = tx.run(cypher, Values.parameters(
                    "modelKey", modelKey,
                    "classKey", CanonicalGraphEncoding.classKey(modelName, className)
                ));

                List<Node> instances = new ArrayList<>();
                while (res.hasNext()) {
                    instances.add(res.next().get("obj").asNode());
                }
                return instances;
            });
        }
    }

    public boolean checkClassExistsInDb(String className) {
        try (Session session = Neo4jDriverManager.getInstance().openSession()) {
            return session.executeRead(tx -> {
                String cypher =
                    "MATCH (m:ManageModel {name: $modelName})-[:DefineMetamodels]->(meta:MetaNode) " +
                        "WHERE meta.name IN ['NodeConcreteClass', 'NodeAbstractClass', 'NodeAssociationClass'] " +
                        "MATCH (cls:UmlClass {modelKey:$modelKey,classKey:$classKey})-[:InstanceOf]->(meta) " +
                        "RETURN count(cls) > 0 AS exists";

                Result res = tx.run(cypher, Values.parameters(
                    "modelName", this.modelName,
                    "modelKey", modelKey,
                    "classKey", CanonicalGraphEncoding.classKey(modelName, className)
                ));

                return res.single().get("exists").asBoolean();
            });
        }
    }

    private static Object decodeStoredValue(Record record) {
        Value value = record.get("value");
        if (value.isNull()) return null;
        Object raw = value.asObject();
        boolean collection = record.containsKey("isCollection")
                && !record.get("isCollection").isNull()
                && record.get("isCollection").asBoolean();
        Value type = record.get("type");
        return decodeStoredValue(raw, type == null || type.isNull() ? null : type.asString(), collection);
    }

    static Object decodeStoredValue(Object raw, String typeName, boolean collection) {
        if (raw == null) return null;
        if (collection && raw instanceof String payload
                && !"Undefined".equals(payload) && !"COLLECTION_DATA".equals(payload)
                && !"NESTED_COLLECTION".equals(payload)) {
            return CanonicalCollectionValueCodec.decodeScalarLeaves(payload, typeName);
        }
        if (!collection && raw instanceof String payload && payload.startsWith("v1|")) {
            if (typeName == null || typeName.isBlank()) {
                throw new IllegalArgumentException("A typed canonical scalar payload is missing val.type");
            }
            return CanonicalScalarValueCodec.decode(payload, typeName);
        }
        if (raw instanceof Number number) return number.doubleValue();
        return raw;
    }
}
