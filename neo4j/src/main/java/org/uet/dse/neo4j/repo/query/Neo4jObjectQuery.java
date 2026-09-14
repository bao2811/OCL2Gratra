package org.uet.dse.neo4j.repo.query;

import static org.uet.dse.neo4j.encoding.CanonicalGraphVocabulary.OBJECT_INSTANCE_OF;
import static org.uet.dse.neo4j.encoding.CanonicalGraphVocabulary.SCHEMA_INSTANCE_OF;

public class Neo4jObjectQuery {
    public static String upsertObjectNode(String className) {
        return String.format(
                "MATCH (cls {name: $clsName}) " +
                        "MERGE (obj:`%s` {use_id: $objName}) " +
                        "MERGE (obj)-[:" + OBJECT_INSTANCE_OF + "]->(cls)",
                className
        );
    }

    public static String upsertObjectNodeInModel(String className) {
        return String.format(
                "MATCH (m:ManageModel {name: $modelName})-[:DefineMetamodels]->(meta:MetaNode) " +
                        "MATCH (runtimeCls {modelKey:$modelName,classKey: $runtimeClassKey})-[:" + SCHEMA_INSTANCE_OF + "]->(meta) " +
                        "MATCH (cls {modelKey:$modelName})-[:" + SCHEMA_INSTANCE_OF + "]->(meta) WHERE cls.classKey IN $classKeys " +
                        "MERGE (obj:Object:`%s` {objectKey: $objectKey}) " +
                        "SET obj.use_id = $objName, obj.modelKey = $modelName, " +
                        "    obj.runtimeClassKey = $runtimeClassKey " +
                        "MERGE (obj)-[:" + OBJECT_INSTANCE_OF + "]->(cls)",
                className
        );
    }

    public static String upsertObjectNodesBatchInModel(String className) {
        return String.format(
                "UNWIND $rows AS row "
                        + "MATCH (runtimeCls:UmlClass {modelKey:$modelName,classKey:row.runtimeClassKey}) "
                        + "MERGE (obj:Object:`%s` {objectKey:row.objectKey}) "
                        + "SET obj.use_id=row.objName, obj.modelKey=$modelName, "
                        + "obj.runtimeClassKey=row.runtimeClassKey "
                        + "WITH row,obj OPTIONAL MATCH (obj)-[old:" + OBJECT_INSTANCE_OF + "]->(oldCls:UmlClass) "
                        + "WHERE NOT oldCls.classKey IN row.classKeys "
                        + "WITH row,obj,collect(old) AS staleMemberships "
                        + "FOREACH (membership IN staleMemberships | DELETE membership) "
                        + "WITH row,obj MATCH (cls:UmlClass {modelKey:$modelName}) WHERE cls.classKey IN row.classKeys "
                        + "MERGE (obj)-[:" + OBJECT_INSTANCE_OF + "]->(cls)",
                className);
    }

    public static final String UPSERT_SCALAR_ATTRIBUTE_VALUES_BATCH =
            "UNWIND $rows AS row "
                    + "MATCH (obj:Object {modelKey:$modelName,objectKey:row.objectKey}) "
                    + "MATCH (attrDef:Attribute {modelKey:$modelName,attributeKey:row.attributeKey}) "
                    + "MERGE (val:AttributeValue {slotKey:row.slotKey}) "
                    + "SET val.name=row.valId, val.attributeKey=row.attributeKey, val.modelKey=$modelName, "
                    + "val.type=row.type, val.value=row.value, val.isCollection=row.isCollection, "
                    + "val.collectionType=row.collectionType, val.isNestedCollection=false "
                    + "MERGE (obj)-[:ObjectHasAttribute]->(val) "
                    + "MERGE (val)-[:" + SCHEMA_INSTANCE_OF + "]->(attrDef)";

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
                    "CREATE (val)-[:" + SCHEMA_INSTANCE_OF + "]->(attrDef)";

    public static final String CREATE_ATTRIBUTE_VALUE_IN_MODEL =
            "MATCH (m:ManageModel {name: $modelName})-[:DefineMetamodels]->(meta:MetaNode) " +
                    "MATCH (obj:Object {modelKey:$modelName,use_id:$objName})-[:" + OBJECT_INSTANCE_OF + "]->"
                    + "(cls:UmlClass {modelKey:$modelName})-[:"
                    + SCHEMA_INSTANCE_OF + "]->(meta) " +
                    "MATCH (cls)-[:HasAttribute]->"
                    + "(attrDef:Attribute {modelKey:$modelName,name:$attrDefId}) " +
                    "MERGE (val:AttributeValue {slotKey: $slotKey}) " +
                    "SET val.attributeKey       = $attributeKey, " +
                    "    val.name              = $valId, " +
                    "    val.modelKey          = $modelName, " +
                    "    val.type              = $type, " +
                    "    val.value             = $val, " +
                    "    val.isCollection      = $isColl, " +
                    "    val.collectionType    = $collType, " +
                    "    val.isNestedCollection = $isNested " +
                    "MERGE (obj)-[:ObjectHasAttribute]->(val) " +
                    "MERGE (val)-[:" + SCHEMA_INSTANCE_OF + "]->(attrDef)";
    public static final String CREATE_OBJECT_REFERENCE =
            "MATCH (v {name: $vId}), (target {use_id: $tId}) " +
                    "CREATE (v)-[:objectReference {index: $idx}]->(target)";

    public static final String CHECK_CLASS_EXISTS =
            "MATCH (meta:MetaNode) " +
                    "WHERE meta.name IN ['NodeConcreteClass','NodeAbstractClass','NodeAssociationClass'] " +
                    "MATCH (cls {name: $name})-[:" + SCHEMA_INSTANCE_OF + "]->(meta) " +
                    "RETURN cls LIMIT 1";
    public static String upsertBinaryLink(String label) {
        return String.format(
                "MATCH (a {use_id: $id1}), (b {use_id: $id2}) " +
                        "MERGE (a)-[r:%s {name: $name}]->(b) " +
                        "SET r.sourceRole = $sRole, r.targetRole = $tRole, r.isTernary = false, " +
                        "    r.sourceQualifiers = $sQualifiers, r.targetQualifiers = $tQualifiers",
                label
        );
    }

    public static String upsertBinaryLinkInModel(String label) {
        return String.format(
                "MATCH (a {modelKey:$modelName,objectKey: $objectKey1}), "
                        + "(b {modelKey:$modelName,objectKey: $objectKey2}) " +
                        "MERGE (a)-[r:%s {linkKey: $linkKey}]->(b) " +
                        "SET r.name = $name, r.modelKey = $modelName, r.associationKey = $associationKey, " +
                        "    r.sourceRole = $sRole, r.targetRole = $tRole, r.isTernary = false, " +
                        "    r.sourceQualifiers = $sQualifiers, r.targetQualifiers = $tQualifiers",
                label
        );
    }

    public static String upsertBinaryLinksBatchInModel(String label) {
        return String.format(
                        "UNWIND $rows AS row "
                        + "MATCH (a:Object {modelKey:$modelName,objectKey:row.sourceKey}), "
                        + "(b:Object {modelKey:$modelName,objectKey:row.targetKey}) "
                        + "MERGE (a)-[r:%s {linkKey:row.linkKey}]->(b) "
                        + "SET r.name=row.name, r.modelKey=$modelName, r.associationKey=row.associationKey, "
                        + "r.sourceRole=row.sourceRole, r.targetRole=row.targetRole, r.isTernary=false, "
                        + "r.sourceQualifiers=row.sourceQualifiers, r.targetQualifiers=row.targetQualifiers",
                label);
    }

    public static String createTernaryHub(String assocName) {
        return String.format(
                "MERGE (h:LinkHub:`%s` {modelKey:$modelName,linkKey:$linkKey}) " +
                        "SET h.use_id=$linkKey, h.name=$name, h.associationKey=$associationKey, " +
                        "h.canonicalKey=$linkKey",
                assocName
        );
    }

    public static String upsertTernarySpoke(String label) {
        return String.format(
                "MATCH (obj:Object {modelKey:$modelName,objectKey:$objectKey}), " +
                        "(h:LinkHub {modelKey:$modelName,linkKey:$linkKey}) " +
                        "MERGE (obj)-[r:%s {modelKey:$modelName,linkKey:$linkKey,index:$idx}]->(h) " +
                        "SET r.role=$role, r.associationKey=$associationKey, r.isTernary=true",
                label
        );
    }

    public static String upsertLinkObjectSpoke(String label) {
        return String.format(
                "MATCH (lo:Object {modelKey:$modelName,objectKey:$linkObjectKey}), " +
                        "(p:Object {modelKey:$modelName,objectKey:$participantKey}) " +
                        "MERGE (lo)-[r:%s {modelKey:$modelName,index:$idx}]->(p) " +
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
            "MATCH (a:Object {modelKey:$modelName})-[r]->(b:Object {modelKey:$modelName}) " +
                    "WHERE r.modelKey=$modelName AND type(r) STARTS WITH 'Link' " +
                    "  AND NOT coalesce(r.isTernary,        false) " +
                    "  AND NOT coalesce(r.isLinkObjectPart, false) " +
                    "RETURN r.name AS assocName, a.use_id AS src, b.use_id AS tgt, " +
                    "       coalesce(r.sourceQualifiers, []) AS sourceQualifiers, " +
                    "       coalesce(r.targetQualifiers, []) AS targetQualifiers";

    public static final String PULL_TERNARY_LINKS =
            "MATCH (p:Object {modelKey:$modelName})-[r]->"
                    + "(hub:LinkHub {modelKey:$modelName}) "
                    + "WHERE r.modelKey=$modelName AND r.isTernary = true " +
                    "RETURN hub.name AS assocName, " +
                    "       collect({obj: p.use_id, idx: r.index}) AS participants";

    public static final String PULL_LINK_OBJECTS =
            "MATCH (lo:Object {modelKey:$modelName})-[:" + OBJECT_INSTANCE_OF + "]->"
                    + "(ac:UmlClass {modelKey:$modelName})-[:" + SCHEMA_INSTANCE_OF + "]->"
                    + "(:MetaNode {name:'NodeAssociationClass'}) "
                    + "MATCH (lo)-[r]->(p:Object {modelKey:$modelName}) "
                    + "WHERE r.modelKey=$modelName AND r.isLinkObjectPart = true " +
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
            "MATCH (o)-[:" + OBJECT_INSTANCE_OF + "]->(cls) " +
                    "OPTIONAL MATCH (o)-[:ObjectHasAttribute]->(val:AttributeValue)-[:"
                    + SCHEMA_INSTANCE_OF + "]->(attrDef) " +
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
