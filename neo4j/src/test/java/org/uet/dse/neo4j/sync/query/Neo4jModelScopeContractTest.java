package org.uet.dse.neo4j.sync.query;

import org.junit.jupiter.api.Test;
import org.uet.dse.neo4j.repo.query.Neo4jObjectQuery;
import org.uet.dse.neo4j.repo.query.Neo4jModelQuery;

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
        assertTrue(Neo4jObjectQuery.upsertLinkObjectSpoke("AssociationClassParticipant")
                .contains("modelKey:$modelName,objectKey:$linkObjectKey"));
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

    @Test
    void ternaryAssociationWriterDeclaresCanonicalModelScopedKeys() {
        String hub = Neo4jModelQuery.createTernaryHub("Maintenance");
        String spoke = Neo4jModelQuery.upsertTernarySpoke("Maintenance", "AssociateWith");
        assertTrue(hub.contains("modelKey:$modelName,associationKey:$associationKey"));
        assertTrue(hub.contains("h.canonicalKey=$associationKey"));
        assertTrue(spoke.contains("c:UmlClass {modelKey:$modelName,classKey:$classKey}"));
        assertTrue(spoke.contains("modelKey:$modelName,associationKey:$associationKey,index:$idx"));
    }

    @Test
    void ternaryInstanceWriterDeclaresCanonicalModelLinkAndRoleKeys() {
        String hub = Neo4jObjectQuery.createTernaryHub("Maintenance");
        String spoke = Neo4jObjectQuery.upsertTernarySpoke("LinkAssociateWith");
        assertTrue(hub.contains("modelKey:$modelName,linkKey:$linkKey"));
        assertTrue(hub.contains("h.associationKey=$associationKey"));
        assertTrue(spoke.contains("obj:Object {modelKey:$modelName,objectKey:$objectKey}"));
        assertTrue(spoke.contains("modelKey:$modelName,linkKey:$linkKey,index:$idx"));
        assertTrue(spoke.contains("r.role=$role"));
        assertTrue(spoke.contains("r.associationKey=$associationKey"));
    }

    @Test
    void associationClassWriterDeclaresCanonicalModelScopedKeys() {
        String query = Neo4jModelQuery.UPSERT_ASSOCIATION_CLASS_STRUCTURE;
        assertTrue(query.contains("classKey:$associationClassKey"));
        assertTrue(query.contains("associationKey:$associationKey"));
        assertTrue(query.contains("s.associationName=$acName"));
        assertTrue(query.contains("t.associationName=$acName"));
    }
}
