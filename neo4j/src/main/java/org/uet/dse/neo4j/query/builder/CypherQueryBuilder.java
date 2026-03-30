package org.uet.dse.neo4j.query.builder;

public class CypherQueryBuilder {
    public static String buildUpsertEnumerationQuery(String dynamicLabel) {
        return String.format(
                "MATCH (meta:MetaNode {name: 'NodeEnumeration'}) " +
                        "MERGE (e:`%s` {name: $name}) " +
                        "SET e.value = $values " +
                        "MERGE (e)-[:InstanceOf]->(meta)",
                dynamicLabel
        );
    }

    public static String buildInstanceCreationQuery(String dynamicLabel) {
        return String.format(
                "MATCH (meta:MetaNode {name: $metaName}) " +
                        "MERGE (inst:`%s` {name: $id}) " +
                        "SET inst += $props " +
                        "MERGE (inst)-[:InstanceOf]->(meta)",
                dynamicLabel
        );
    }


    public static String buildStructuralEdgeQuery(String edgeLabel) {
        return String.format(
                "MATCH (a {name: $from}), (b {name: $to}) " +
                        "MERGE (a)-[:`%s`]->(b)",
                edgeLabel
        );
    }

    public static String buildExtendsEdgeQuery() {
        return "MATCH (src {name: $srcName}), (tgt {name: $tgtName}) " +
                "MERGE (src)-[r:Extends]->(tgt) " +
                "SET r.sourceName = $srcName, r.targetName = $tgtName";
    }
}
