package org.uet.dse.neo4j.repo.query;

public class Neo4jObjectQuery {
    public static String upsertObjectNode(String className) {
        return String.format(
                "MATCH (cls {name: $clsName}) " +
                        "MERGE (obj:`%s` {use_id: $objName}) " +
                        "MERGE (obj)-[:ObjectInstanceOf]->(cls)",
                className
        );
    }

    public static final String DELETE_ATTRIBUTE_VALUE =
            "MATCH (v:AttributeValue {name: $vId}) DETACH DELETE v";

    public static final String CREATE_ATTRIBUTE_VALUE =
            "MATCH (obj {use_id: $objName}), (attrDef:Attribute {name: $attrDefId}) " +
                    "CREATE (val:AttributeValue {name: $valId}) " +
                    "SET val.type              = $type, " +
                    "    val.value             = $val, " +
                    "    val.isCollection      = $isColl, " +
                    "    val.collectionType    = $collType, " +
                    "    val.isNestedCollection = $isNested " +
                    "CREATE (obj)-[:ObjectHasAttribute]->(val) " +
                    "CREATE (val)-[:InstanceOf]->(attrDef)";
    public static final String CREATE_OBJECT_REFERENCE =
            "MATCH (v {name: $vId}), (target {use_id: $tId}) " +
                    "CREATE (v)-[:objectReference {index: $idx}]->(target)";

    public static final String CHECK_CLASS_EXISTS =
            "MATCH (meta:MetaNode) " +
                    "WHERE meta.name IN ['NodeConcreteClass','NodeAbstractClass','NodeAssociationClass'] " +
                    "MATCH (cls {name: $name})-[:InstanceOf]->(meta) " +
                    "RETURN cls LIMIT 1";
    public static String upsertBinaryLink(String label) {
        return String.format(
                "MATCH (a {use_id: $id1}), (b {use_id: $id2}) " +
                        "MERGE (a)-[r:%s {name: $name}]->(b) " +
                        "SET r.sourceRole = $sRole, r.targetRole = $tRole, r.isTernary = false",
                label
        );
    }

    public static String createTernaryHub(String assocName) {
        return String.format(
                "MERGE (h:LinkHub:`%s` {use_id: $id, name: $name})",
                assocName
        );
    }

    public static String upsertTernarySpoke(String label) {
        return String.format(
                "MATCH (obj {use_id: $objId}), (h:LinkHub {use_id: $hubId}) " +
                        "MERGE (obj)-[r:%s]->(h) " +
                        "SET r.role = $role, r.isTernary = true, r.index = $idx",
                label
        );
    }

    public static String upsertLinkObjectSpoke(String label) {
        return String.format(
                "MATCH (lo {use_id: $loId}), (p {use_id: $pId}) " +
                        "MERGE (lo)-[r:%s]->(p) " +
                        "SET r.role = $role, r.isLinkObjectPart = true",
                label
        );
    }
    public static final String DELETE_OBJECT_DEEPLY =
            "MATCH (o {use_id: $name}) " +
                    "OPTIONAL MATCH (o)-[:ObjectHasAttribute]->(val:AttributeValue) " +
                    "DETACH DELETE o, val";

    public static final String GET_OBJECT_TIMESTAMP =
            "MATCH (v:ModelVersion {id: 'CURRENT'}) RETURN v.objectTimestamp AS ts";

    public static final String GET_MODEL_HASH =
            "MATCH (v:ModelVersion {id: 'CURRENT'}) RETURN v.modelHash AS hash";

    public static final String PULL_BINARY_LINKS =
            "MATCH (a)-[r]->(b) " +
                    "WHERE type(r) STARTS WITH 'Link' " +
                    "  AND NOT coalesce(r.isTernary,        false) " +
                    "  AND NOT coalesce(r.isLinkObjectPart, false) " +
                    "RETURN r.name AS assocName, a.use_id AS src, b.use_id AS tgt";

    public static final String PULL_TERNARY_LINKS =
            "MATCH (p)-[r]->(hub:LinkHub) WHERE r.isTernary = true " +
                    "RETURN hub.name AS assocName, " +
                    "       collect({obj: p.use_id, idx: r.index}) AS participants";

    public static final String PULL_LINK_OBJECTS =
            "MATCH (lo)-[:ObjectInstanceOf]->(ac:AssociationClass) " +
                    "MATCH (lo)-[r]->(p) WHERE r.isLinkObjectPart = true " +
                    "RETURN lo.use_id AS loName, ac.name AS acName, " +
                    "       collect(p.use_id) AS participants";

    public static final String CREATE_NESTED_NODE =
            "MATCH (parent {name: $parentId}) " +
                    "CREATE (n:NestedCollection {name: $nId}) " +
                    "SET n.collectionName = $cName, n.isNestedCollection = $isNested " +
                    "CREATE (parent)-[:HasNestedCollection {index: $idx}]->(n)";

    public static final String CREATE_OBJECT_REFERENCE_FROM_ANY =
            "MATCH (src {name: $srcId}), (target {use_id: $tId}) " +
                    "CREATE (src)-[:objectReference {index: $idx}]->(target)";

    public static final String SET_PRIMITIVE_VALUE_ON_NODE =
            "MATCH (n {name: $nId}) SET n.value = $val";

    public static final String GET_OBJECTS_AND_NESTED_ATTRIBUTES =
            "MATCH (o)-[:ObjectInstanceOf]->(cls) " +
                    "OPTIONAL MATCH (o)-[:ObjectHasAttribute]->(val:AttributeValue)-[:InstanceOf]->(attrDef) " +
                    "OPTIONAL MATCH p = (val)-[:HasNestedCollection*0..5]->(leaf) " +
                    "WHERE NOT (leaf)-[:HasNestedCollection]->() " +
                    "RETURN o.use_id as objId, cls.name as className, " +
                    "collect({ " +
                    "  attr: attrDef.attrName, " +
                    "  type: val.type, " +
                    "  isColl: val.isCollection, " +
                    "  isNested: val.isNestedCollection, " +
                    "  rootCollType: val.collectionType, " +
                    "  chain: [n IN nodes(p) WHERE 'NestedCollection' IN labels(n) | {name: n.collectionName, id: n.name}], " +
                    "  leafVal: leaf.value, " +
                    "  refs: [(leaf)-[r:objectReference]->(t) | {id: t.use_id, idx: r.index}] " +
                    "}) as attrs";

    //TODO: not yet cover manage node
    public static final String CREATE_REFERENCE_VALUE_EDGE =
        "MATCH (src {name: $valNodeId}), (target {use_id: $targetId}) " +
            "MERGE (src)-[r:HasReferenceValue]->(target) " +
            "SET r.index = $idx";


    // Trong Neo4jObjectQuery.java
    public static final String CREATE_NESTED_COLLECTION_NODE =
        "CREATE (n:NestedCollectionValue {name: $nId, collectionType: $collType, isObjRef: $isObjRef})"; // THÊM isObjRef

    public static final String CONNECT_NESTED_NODE =
        "MATCH (p {name: $parentId}), (c {name: $childId}) " +
            "CREATE (p)-[r:HasNestedCollectionValue {index: $idx}]->(c)";

    public static final String CONNECT_NESTED_TO_OBJECT =
        "MATCH (n {name: $nId}), (t {use_id: $targetId}) " +
            "CREATE (n)-[r:HasReferenceValue {index: $idx}]->(t)";

    public static final String DELETE_NESTED_TREE =
        "MATCH (o {use_id: $objName})-[:ObjectHasAttribute]->(val:AttributeValue {name: $valId}) " +
            "OPTIONAL MATCH (val)-[:HasNestedCollectionValue*1..10]->(nested) " +
            "DETACH DELETE nested";
}
