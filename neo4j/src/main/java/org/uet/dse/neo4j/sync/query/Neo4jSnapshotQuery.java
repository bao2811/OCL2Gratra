package org.uet.dse.neo4j.sync.query;

public class Neo4jSnapshotQuery {
    private Neo4jSnapshotQuery() {}

    public static final String CLASSES_AND_FEATURES =
        "MATCH (m:ManageModel {name: $modelName})-[:DefineMetamodels]->(meta:MetaNode) " +
            "MATCH (inst {modelKey:$modelName})-[:InstanceOf]->(meta)  WHERE meta.name IN " +
                    "  ['NodeConcreteClass','NodeAbstractClass','NodeAssociationClass','NodeEnumeration'] " +
                    "MATCH (inst)-[:InstanceOf]->(meta) " +
                    "WITH inst, meta, labels(inst)[0] AS classType " +

                    // ── Attributes + nested-collection chain ─────────────────────────
                    "OPTIONAL MATCH (inst)-[:HasAttribute]->(a {modelKey:$modelName}) " +
                    "OPTIONAL MATCH (a)-[rRef]->(aRef {modelKey:$modelName}) WHERE type(rRef) = 'ReferenceType' " +
                    "OPTIONAL MATCH p = (a)-[:HasNestedCollection*]->(n:NestedCollection) " +
                    "WITH inst, meta, classType, a, aRef, p ORDER BY length(p) DESC " +
                    "WITH inst, meta, classType, a, aRef, head(collect(p)) AS longestPath " +
                    "WITH inst, meta, classType, a, aRef, " +
                    "     [node IN nodes(longestPath) WHERE 'NestedCollection' IN labels(node) | node.collectionName] AS fullChain " +
                    "WITH inst, meta, classType, collect(DISTINCT { " +
                    "  name: a.attrName, type: a.type, idx: a.index, ref: aRef.name, " +
                    "  sCollection: a.isCollection, isNested: a.isNestedCollection, " +
                    "  collectionType: a.collectionType, chain: fullChain " +
                    "}) AS attrs " +

                    // ── Invariants ───────────────────────────────────────────────────
                    "OPTIONAL MATCH (inst)-[:HasInvariant]->(inv {modelKey:$modelName}) " +
                    "WITH inst, meta, classType, attrs, " +
                    "     collect(DISTINCT {name: inv.invName, expr: inv.expression, exist: inv.isExistential}) AS invariants " +

                    // ── Association-class ends ────────────────────────────────────────
                    "OPTIONAL MATCH (src {modelKey:$modelName})-[s:sourceAssoClass]->(inst) " +
                    "OPTIONAL MATCH (inst)-[t:targetAssoClass]->(tgt {modelKey:$modelName}) " +
                    "WITH inst, meta, classType, attrs, invariants, " +
                    "     {name: src.name, role: s.role, mult: s.multiplicity} AS acSrc, " +
                    "     {name: tgt.name, role: t.role, mult: t.multiplicity} AS acTgt " +

                    // ── Operations + params + pre/post conditions ─────────────────────
                    "OPTIONAL MATCH (inst)-[:HasOperation]->(o {modelKey:$modelName}) " +
                    "OPTIONAL MATCH (o)-[:HasParam]->(p {modelKey:$modelName}) " +
                    "OPTIONAL MATCH (p)-[r2]->(pRef {modelKey:$modelName})   WHERE type(r2)  = 'ReferenceType' " +
                    "OPTIONAL MATCH (o)-[r3]->(retRef {modelKey:$modelName})  WHERE type(r3)  = 'ReferenceReturnType' " +
                    "OPTIONAL MATCH (o)-[:HasPreCondition]->(pre {modelKey:$modelName}) " +
                    "OPTIONAL MATCH (o)-[:HasPostCondition]->(post {modelKey:$modelName}) " +
                    "WITH inst, meta, classType, attrs, invariants, acSrc, acTgt, o, retRef, " +
                    "     collect(DISTINCT {pName: p.pName, pType: p.type, pOrder: p.order, pRef: pRef.name}) AS opParams, " +
                    "     collect(DISTINCT {name: pre.condName,  expr: pre.expression})  AS opPres, " +
                    "     collect(DISTINCT {name: post.condName, expr: post.expression}) AS opPosts " +
                    "WITH inst, meta, classType, attrs, invariants, acSrc, acTgt, " +
                    "     collect(DISTINCT {name: o.opName, body: o.body, isQuery: o.isQuery, " +
                    "       ret: o.returnType, retRef: retRef.name, " +
                    "       params: opParams, pre: opPres, post: opPosts}) AS ops " +

                    "RETURN inst.name AS className, meta.name AS metaName, classType, " +
                    "       inst.value AS enumValues, attrs, invariants, ops, acSrc, acTgt";

    public static final String GENERALIZATIONS =
        "MATCH (m:ManageModel {name: $modelName})-[:DefineMetamodels]->(meta:MetaNode) " +
            "MATCH (c {modelKey:$modelName})-[:InstanceOf]->(meta) " +
            "MATCH (c)-[:Extends]->(p {modelKey:$modelName}) RETURN c.name AS child, p.name AS parent";

    public static final String BINARY_ASSOCIATIONS =
        "MATCH (m:ManageModel {name: $modelName})-[:DefineMetamodels]->(meta:MetaNode) " +
            "MATCH (s {modelKey:$modelName})-[:InstanceOf]->(meta) " +
            "MATCH (s)-[r]->(t {modelKey:$modelName}) " +
                    "WHERE r.modelKey=$modelName AND type(r) IN ['AssociateWith','ComposeOf','Aggregates'] " +
                    "RETURN r.associationName AS name, type(r) AS type, " +
                    "       s.name AS src, r.sourceClassrole AS sRole, r.sourceMultiplicity AS sMult, " +
                    "       t.name AS tgt, r.targerClassrole   AS tRole, r.targetMultiplicity AS tMult";

    public static final String TERNARY_ASSOCIATIONS =
        "MATCH (m:ManageModel {name: $modelName})-[:DefineMetamodels]->(meta:MetaNode {name: 'NodeTernaryAssociation'}) " +
            "MATCH (hub {modelKey:$modelName})-[:InstanceOf]->(meta) " +
            "MATCH (c {modelKey:$modelName})-[r]->(hub) " +
            "WHERE r.modelKey=$modelName AND r.isTernary = true " +
            "RETURN hub.name AS assocName, " +
            "       collect({cls: c.name, role: r.sourceClassrole, " +
            "                mult: r.sourceMultiplicity, idx: r.index, type: type(r)}) AS participants";

    public static final String OBJECTS_AND_ATTRIBUTES =
        "MATCH (o:Object {modelKey:$modelName}) " +
            "MATCH (cls:UmlClass {modelKey:$modelName,classKey:o.runtimeClassKey}) " +
            "OPTIONAL MATCH (o)-[:ObjectHasAttribute]->(val:AttributeValue {modelKey:$modelName})" +
            "-[:InstanceOf]->(attrDef:Attribute {modelKey:$modelName}) " +
            "OPTIONAL MATCH path = (val)-[:HasNestedCollectionValue*0..5]->(leaf) " +
            "OPTIONAL MATCH (leaf)-[r:objectReference|HasReferenceValue]->(target:Object {modelKey:$modelName}) " +
            "RETURN o.use_id AS name, cls.name AS className, " +
            "  collect({ " +
            "  attr:  attrDef.attrName, " +
            "  type:  val.type, " +
            "  value:   val.value, " +
            "  leafVal:   leaf.value, " +
            "  isColl:  val.isCollection, " +
//            "         isObjRef: val.isObjRef, " +
            "  isObjRef:  leaf.isObjRef, " +
            "  nestIsObjRef:  leaf.isObjRef, " +
            "  primVals:  leaf.primitiveValues, " +
            "  ref:   target.use_id, " +
            "  pathIdx:  [rel IN relationships(path) | rel.index], " +
            "  leafCollType: leaf.collectionType, " +
            "  rootCollType: val.collectionType, " +
            "  idx:   r.index " +
            "  }) AS data";

    public static final String BINARY_LINKS =
        "MATCH (o:Object {modelKey:$modelName})" +
            "-[r:LinkAssociateWith|LinkAggregates|LinkComposeOf]->" +
            "(b:Object {modelKey:$modelName}) " +
            "WHERE r.modelKey=$modelName " +
            "  AND NOT coalesce(r.isTernary,        false) " +
            "  AND NOT coalesce(r.isLinkObjectPart, false) " +
            "RETURN r.name AS assocName, type(r) AS label, " +
            "       o.use_id AS src, b.use_id AS tgt, " +
            "       coalesce(r.sourceQualifiers, []) AS sourceQualifiers, " +
            "       coalesce(r.targetQualifiers, []) AS targetQualifiers";

    public  static final String LINK_OBJECTS =
        "MATCH (lo:Object {modelKey:$modelName}) " +
            "MATCH (ac:UmlClass {modelKey:$modelName,classKey:lo.runtimeClassKey})-[:InstanceOf]->"
            + "(:MetaNode {name:'NodeAssociationClass'}) " +
            "MATCH (lo)-[r]->(p:Object {modelKey:$modelName}) " +
            "WHERE r.modelKey=$modelName AND r.isLinkObjectPart = true " +
            "RETURN lo.use_id AS loName, ac.name AS acName, " +
            "       collect({p: p.use_id, idx: r.index}) AS parts";

}
