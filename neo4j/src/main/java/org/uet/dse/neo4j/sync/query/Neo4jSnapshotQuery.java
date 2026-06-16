package org.uet.dse.neo4j.sync.query;

public class Neo4jSnapshotQuery {
    private Neo4jSnapshotQuery() {}

    public static final String CLASSES_AND_FEATURES =
        "MATCH (m:ManageModel {name: $modelName})-[:DefineMetamodels]->(meta:MetaNode) " +
            "MATCH (inst)-[:InstanceOf]->(meta)  WHERE meta.name IN " +
                    "  ['NodeConcreteClass','NodeAbstractClass','NodeAssociationClass','NodeEnumeration'] " +
                    "MATCH (inst)-[:InstanceOf]->(meta) " +
                    "WITH inst, meta, labels(inst)[0] AS classType " +

                    // ── Attributes + nested-collection chain ─────────────────────────
                    "OPTIONAL MATCH (inst)-[:HasAttribute]->(a) " +
                    "OPTIONAL MATCH (a)-[rRef]->(aRef) WHERE type(rRef) = 'ReferenceType' " +
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
                    "OPTIONAL MATCH (inst)-[:HasInvariant]->(inv) " +
                    "WITH inst, meta, classType, attrs, " +
                    "     collect(DISTINCT {name: inv.invName, expr: inv.expression, exist: inv.isExistential}) AS invariants " +

                    // ── Association-class ends ────────────────────────────────────────
                    "OPTIONAL MATCH (src)-[s:sourceAssoClass]->(inst) " +
                    "OPTIONAL MATCH (inst)-[t:targetAssoClass]->(tgt) " +
                    "WITH inst, meta, classType, attrs, invariants, " +
                    "     {name: src.name, role: s.role, mult: s.multiplicity} AS acSrc, " +
                    "     {name: tgt.name, role: t.role, mult: t.multiplicity} AS acTgt " +

                    // ── Operations + params + pre/post conditions ─────────────────────
                    "OPTIONAL MATCH (inst)-[:HasOperation]->(o) " +
                    "OPTIONAL MATCH (o)-[:HasParam]->(p) " +
                    "OPTIONAL MATCH (p)-[r2]->(pRef)   WHERE type(r2)  = 'ReferenceType' " +
                    "OPTIONAL MATCH (o)-[r3]->(retRef)  WHERE type(r3)  = 'ReferenceReturnType' " +
                    "OPTIONAL MATCH (o)-[:HasPreCondition]->(pre) " +
                    "OPTIONAL MATCH (o)-[:HasPostCondition]->(post) " +
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
            "MATCH (c)-[:InstanceOf]->(meta) " +
            "MATCH (c)-[:Extends]->(p) RETURN c.name AS child, p.name AS parent";

    public static final String BINARY_ASSOCIATIONS =
        "MATCH (m:ManageModel {name: $modelName})-[:DefineMetamodels]->(meta:MetaNode) " +
            "MATCH (s)-[:InstanceOf]->(meta) " +
            "MATCH (s)-[r]->(t) " +
                    "WHERE type(r) IN ['AssociateWith','ComposeOf','Aggregates'] " +
                    "RETURN r.associationName AS name, type(r) AS type, " +
                    "       s.name AS src, r.sourceClassrole AS sRole, r.sourceMultiplicity AS sMult, " +
                    "       t.name AS tgt, r.targerClassrole   AS tRole, r.targetMultiplicity AS tMult";

    public static final String TERNARY_ASSOCIATIONSold =

            "MATCH (c)-[r]->(hub)-[:InstanceOf]->(:MetaNode {name: 'NodeTernaryAssociation'}) " +
                    "WHERE r.isTernary = true " +
                    "RETURN hub.name AS assocName, " +
                    "       collect({cls: c.name, role: r.sourceClassrole, " +
                    "                mult: r.sourceMultiplicity, idx: r.index, type: type(r)}) AS participants";

    public static final String OBJECTS_AND_ATTRIBUTESold =
            "MATCH (o)-[:ObjectInstanceOf]->(cls) " +
                    "OPTIONAL MATCH (o)-[:ObjectHasAttribute]->(val:AttributeValue)-[:InstanceOf]->(attrDef) " +
                    "OPTIONAL MATCH (val)-[r:objectReference]->(target) " +
                    "RETURN o.use_id AS name, cls.name AS className, " +
                    "       collect({ " +
                    "         attr:  attrDef.attrName, " +
                    "         type:  val.type, " +
                    "         value: val.value, " +
                    "         ref:   target.use_id, " +
                    "         idx:   r.index " +
                    "       }) AS data";

    public static final String BINARY_LINKS_old =
            "MATCH (a)-[r]->(b) " +
                    "WHERE type(r) STARTS WITH 'Link' " +
                    "  AND NOT coalesce(r.isTernary,        false) " +
                    "  AND NOT coalesce(r.isLinkObjectPart, false) " +
                    "RETURN r.name AS assocName, type(r) AS label, " +
                    "       a.use_id AS src, b.use_id AS tgt";

    public  static final String LINK_OBJECTSold =
            "MATCH (lo)-[:ObjectInstanceOf]->(ac:AssociationClass) " +
                    "MATCH (lo)-[r]->(p) WHERE r.isLinkObjectPart = true " +
                    "RETURN lo.use_id AS loName, ac.name AS acName, " +
                    "       collect({p: p.use_id, idx: r.index}) AS parts";
    ///


    public static final String TERNARY_ASSOCIATIONS =
        "MATCH (m:ManageModel {name: $modelName})-[:DefineMetamodels]->(meta:MetaNode {name: 'NodeTernaryAssociation'}) " +
            "MATCH (hub)-[:InstanceOf]->(meta) " +
            "MATCH (c)-[r]->(hub) " +
            "WHERE r.isTernary = true " +
            "RETURN hub.name AS assocName, " +
            "       collect({cls: c.name, role: r.sourceClassrole, " +
            "                mult: r.sourceMultiplicity, idx: r.index, type: type(r)}) AS participants";

    public static final String OBJECTS_AND_ATTRIBUTES =
        "MATCH (m:ManageModel {name: $modelName})-[:DefineMetamodels]->(meta:MetaNode) " +
            "WHERE meta.name IN ['NodeConcreteClass', 'NodeAssociationClass'] " +
            "MATCH (cls)-[:InstanceOf]->(meta) " +
            "MATCH (o)-[:ObjectInstanceOf]->(cls) " +
            "OPTIONAL MATCH (o)-[:ObjectHasAttribute]->(val:AttributeValue)-[:InstanceOf]->(attrDef) " +
            "OPTIONAL MATCH path = (val)-[:HasNestedCollectionValue*0..5]->(leaf) " +
            "OPTIONAL MATCH (leaf)-[r:objectReference|HasReferenceValue]->(target) " +
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
        "MATCH (m:ManageModel {name: $modelName})-[:DefineMetamodels]->(meta:MetaNode) " +
            "WHERE meta.name IN ['NodeConcreteClass', 'NodeAssociationClass'] " +
            "MATCH (cls)-[:InstanceOf]->(meta) " +
            "MATCH (o)-[:ObjectInstanceOf]->(cls) " +
            "MATCH (o)-[r]->(b) " +
            "WHERE type(r) STARTS WITH 'Link' " +
            "  AND NOT coalesce(r.isTernary,        false) " +
            "  AND NOT coalesce(r.isLinkObjectPart, false) " +
            "RETURN r.name AS assocName, type(r) AS label, " +
            "       o.use_id AS src, b.use_id AS tgt, " +
            "       coalesce(r.sourceQualifiers, []) AS sourceQualifiers, " +
            "       coalesce(r.targetQualifiers, []) AS targetQualifiers";

    public  static final String LINK_OBJECTS =
        "MATCH (m:ManageModel {name: $modelName})-[:DefineMetamodels]->(meta:MetaNode {name: 'NodeAssociationClass'}) " +
            "MATCH (ac)-[:InstanceOf]->(meta) " +
            "MATCH (lo)-[:ObjectInstanceOf]->(ac) " +
            "MATCH (lo)-[r]->(p) WHERE r.isLinkObjectPart = true " +
            "RETURN lo.use_id AS loName, ac.name AS acName, " +
            "       collect({p: p.use_id, idx: r.index}) AS parts";

}
