package org.uet.dse.neo4j.sync.query;

import org.junit.jupiter.api.Test;
import org.uet.dse.neo4j.repo.query.Neo4jObjectQuery;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Neo4jModelScopeContractTest {
    @Test
    void snapshotQueriesConjoinModelScopeWithCanonicalLookups() {
        assertTrue(Neo4jSnapshotQuery.OBJECTS_AND_ATTRIBUTES.contains(
                "cls:UmlClass {modelKey:$modelName,classKey:o.runtimeClassKey}"));
        assertTrue(Neo4jSnapshotQuery.OBJECTS_AND_ATTRIBUTES.contains(
                "val:AttributeValue {modelKey:$modelName}"));
        assertTrue(Neo4jSnapshotQuery.BINARY_LINKS.contains("WHERE r.modelKey=$modelName"));
        assertTrue(Neo4jSnapshotQuery.LINK_OBJECTS.contains(
                "ac:UmlClass {modelKey:$modelName,classKey:lo.runtimeClassKey}"));
        assertTrue(Neo4jObjectQuery.PULL_BINARY_LINKS.contains(
                "a:Object {modelKey:$modelName}"));
        assertTrue(Neo4jObjectQuery.PULL_BINARY_LINKS.contains(
                "WHERE r.modelKey=$modelName"));
        assertTrue(Neo4jObjectQuery.CREATE_ATTRIBUTE_VALUE_IN_MODEL.contains(
                "obj:Object {modelKey:$modelName,use_id:$objName}"));
        assertTrue(Neo4jObjectQuery.PULL_LINK_OBJECTS.contains(
                "ac:UmlClass {modelKey:$modelName}"));
        assertFalse(Neo4jSnapshotQuery.OBJECTS_AND_ATTRIBUTES.contains(
                "cls:UmlClass {classKey:o.runtimeClassKey}"));
    }

    @Test
    void modelWriterScopesEveryClassAndAttributeKeyMatch() throws IOException {
        Path sourcePath = Path.of("src/main/java/org/uet/dse/neo4j/repo/Neo4jModelRepository.java");
        if (!Files.exists(sourcePath)) {
            sourcePath = Path.of("neo4j/src/main/java/org/uet/dse/neo4j/repo/Neo4jModelRepository.java");
        }
        String source = Files.readString(sourcePath);
        assertTrue(source.contains(
                "MERGE (inst:UmlClass {modelKey:$modelName,classKey:row.classKey})"));
        assertTrue(source.contains(
                "MATCH (owner:UmlClass {modelKey:$modelName,classKey:row.ownerKey})"));
        assertTrue(source.contains(
                "MERGE (a:Attribute {modelKey:$modelName,attributeKey:row.attributeKey})"));
        assertTrue(source.contains(
                "MERGE (s)-[r:%s {modelKey:$modelName,associationKey:row.associationKey}]->(t)"));
        assertFalse(source.contains("UmlClass {classKey:row."));
        assertFalse(source.contains("Attribute {attributeKey:row."));
    }
}
