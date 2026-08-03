package org.uet.dse.neo4j.repo.query;

public class Neo4jModelQuery {
    public static final String DELETE_ASSOCIATION_EDGES =
            "MATCH (s {modelKey: $modelName})-[r {associationName: $name, modelKey: $modelName}]->"
                    + "(t {modelKey: $modelName}) DELETE r";

    public static String upsertBinaryAssociationEdge(String edgeLabel) {
        return String.format(
                "MATCH (s {classKey: $sourceClassKey}), (t {classKey: $targetClassKey}) " +
                        "MERGE (s)-[r:%s {associationKey: $associationKey}]->(t) " +
                        "SET r.associationName    = $assocName, " +
                        "    r.modelKey           = $modelName, " +
                        "    r.sourceClassName    = $sName, " +
                        "    r.sourceClassrole    = $sRole, " +
                        "    r.sourceMultiplicity = $sMult, " +
                        "    r.sourceKind         = $sKind, " +
                        "    r.sourceOrdered      = $sOrdered, " +
                        "    r.sourceQualifierNames = $sQualifierNames, " +
                        "    r.sourceQualifierTypes = $sQualifierTypes, " +
                        "    r.targetClassName    = $tName, " +
                        "    r.targerClassrole    = $tRole, " +
                        "    r.targetMultiplicity = $tMult, " +
                        "    r.targetKind         = $tKind, " +
                        "    r.targetOrdered      = $tOrdered, " +
                        "    r.targetQualifierNames = $tQualifierNames, " +
                        "    r.targetQualifierTypes = $tQualifierTypes, " +
                        "    r.isTernary          = false",
                edgeLabel
        );
    }

    public static final String UPSERT_ASSOCIATION_CLASS_STRUCTURE =
            "MATCH (a {name: $srcName})-[:InstanceOf]->(metaA:MetaNode) " +
                    "MATCH (b {name: $tgtName})-[:InstanceOf]->(metaB:MetaNode) " +
                    "MATCH (ac {name: $acName})-[:InstanceOf]->(metaAC:MetaNode) " +
                    "WHERE metaA.name  IN ['NodeConcreteClass','NodeAbstractClass','NodeAssociationClass'] " +
                    "  AND metaB.name  IN ['NodeConcreteClass','NodeAbstractClass','NodeAssociationClass'] " +
                    "  AND metaAC.name =  'NodeAssociationClass' " +
                    "MERGE (a)-[s:sourceAssoClass]->(ac) " +
                    "  SET s.role = $sRole, s.multiplicity = $sMult, s.index = 0 " +
                    "MERGE (ac)-[t:targetAssoClass]->(b) " +
                    "  SET t.role = $tRole, t.multiplicity = $tMult, t.index = 1 " +
                    "MERGE (a)-[:HasAssociationClass]->(ac) " +
                    "MERGE (b)-[:HasAssociationClass]->(ac)";

    public static String createTernaryHub(String assocName) {
        return String.format(
                "MATCH (meta:MetaNode {name: 'NodeTernaryAssociation'}) " +
                        "MERGE (h:`%s` {name: $name}) " +
                        "MERGE (h)-[:InstanceOf]->(meta)",
                assocName
        );
    }

    public static String upsertTernarySpoke(String assocName, String edgeLabel) {
        return String.format(
                "MATCH (c {name: $clsName}), (hub:`%s` {name: $name}) " +
                        "MERGE (c)-[r:%s {index: $idx}]->(hub) " +
                        "SET r.associationName    = $name, " +
                        "    r.isTernary          = true,  " +
                        "    r.sourceClassrole    = $role, " +
                        "    r.sourceMultiplicity = $mult",
                assocName, edgeLabel
        );
    }

    public static String upsertInstanceNode(String instanceLabel) {
        return String.format(
            "MATCH (m:ManageModel {name: $modelName})-[:DefineMetamodels]->(meta:MetaNode {name: $metaName}) " +
                        "MERGE (inst:%s {name: $id}) " +
                        "SET inst += $props " +
                        "MERGE (inst)-[:InstanceOf]->(meta)",
                instanceLabel
        );
    }

    public static String upsertEnumerationQuery(String enumName) {
        return String.format("MATCH (m:ManageModel {name: $modelName})-[:DefineMetamodels]->(meta:MetaNode {name: 'NodeEnumeration'}) " + "MERGE (e:`%s` {name: $name}) " + "SET e.value = $values " + "MERGE (e)-[:InstanceOf]->(meta)", enumName);
    }
}
