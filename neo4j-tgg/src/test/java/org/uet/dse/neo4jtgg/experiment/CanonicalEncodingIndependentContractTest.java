package org.uet.dse.neo4jtgg.experiment;

import org.junit.jupiter.api.Test;
import org.uet.dse.neo4j.encoding.CanonicalGraphEncoding;
import org.uet.dse.neo4j.encoding.CanonicalGraphSchema;
import org.uet.dse.neo4j.encoding.CanonicalGraphVocabulary;
import org.uet.dse.neo4j.model.LinkState;
import org.uet.dse.neo4j.repo.query.Neo4jObjectQuery;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Expected keys are literals from the canonical-v1 contract, not production helper output. */
class CanonicalEncodingIndependentContractTest {
    @Test
    void productionKeysConformToIndependentlySpecifiedCanonicalV1Examples() {
        assertEquals("Sales", CanonicalGraphEncoding.modelKey(" Sales "));
        assertEquals("Sales::class::Person", CanonicalGraphEncoding.classKey("Sales", "Person"));
        assertEquals("Sales::attribute::Person::age",
                CanonicalGraphEncoding.attributeKey("Sales", "Person", "age"));
        assertEquals("Sales::association::Employment",
                CanonicalGraphEncoding.associationKey("Sales", "Employment"));
        assertEquals("Sales::object::p1", CanonicalGraphEncoding.objectKey("Sales", "p1"));
        assertEquals("Sales::object::p1::slot::Sales::attribute::Person::age",
                CanonicalGraphEncoding.attributeSlotKey("Sales", "p1", "Person", "age"));
        assertEquals("Sales::link::association=10:Employment::source=2:p1::target=2:c1"
                        + "::sourceQualifiers=1:5:'HCM'::targetQualifiers=1:4:'A1'",
                CanonicalGraphEncoding.binaryLinkKey("Sales", "Employment", "p1", "c1",
                        List.of("'HCM'"), List.of("'A1'")));
    }

    @Test
    void typedKeyConstructorsArePairwiseDistinctForDistinctAdmittedTuples() {
        List<String> keys = List.of(
                CanonicalGraphEncoding.classKey("Sales", "Person"),
                CanonicalGraphEncoding.classKey("Sales", "Company"),
                CanonicalGraphEncoding.classKey("Archive", "Person"),
                CanonicalGraphEncoding.attributeKey("Sales", "Person", "name"),
                CanonicalGraphEncoding.attributeKey("Sales", "Person", "age"),
                CanonicalGraphEncoding.attributeKey("Sales", "Company", "name"),
                CanonicalGraphEncoding.associationKey("Sales", "Employment"),
                CanonicalGraphEncoding.associationKey("Sales", "Management"));

        assertEquals(keys.size(), new HashSet<>(keys).size());
    }

    @Test
    void reservedDelimiterIsRejectedInsteadOfCreatingAnAmbiguousTupleEncoding() {
        assertThrows(IllegalArgumentException.class,
                () -> CanonicalGraphEncoding.modelKey("Sales::attribute::Person"));
        assertThrows(IllegalArgumentException.class,
                () -> CanonicalGraphEncoding.classKey("Sales", "Person::Archived"));
        assertThrows(IllegalArgumentException.class,
                () -> CanonicalGraphEncoding.attributeKey("Sales", "Person::Address", "city"));
        assertThrows(IllegalArgumentException.class,
                () -> CanonicalGraphEncoding.attributeKey("Sales", "Person", "address::city"));
        assertThrows(IllegalArgumentException.class,
                () -> CanonicalGraphEncoding.associationKey("Sales", "Employment::Historic"));
    }

    @Test
    void objectWriterMergesByCanonicalIdentityAndAlwaysRefreshesUseId() {
        String single = Neo4jObjectQuery.upsertObjectNodeInModel("Person");
        String batch = Neo4jObjectQuery.upsertObjectNodesBatchInModel("Person");

        assertTrue(single.contains("MERGE (obj:Object:`Person` {objectKey: $objectKey})"));
        assertTrue(single.contains("SET obj.use_id = $objName"));
        assertTrue(batch.contains("MERGE (obj:Object:`Person` {objectKey:row.objectKey})"));
        assertTrue(batch.contains("SET obj.use_id=row.objName"));
    }

    @Test
    void schemaEnforcesAtMostOneObjectNodePerCanonicalObjectKey() {
        assertTrue(CanonicalGraphSchema.statements().stream().anyMatch(statement ->
                statement.equals("CREATE CONSTRAINT canonical_object_key IF NOT EXISTS "
                        + "FOR (n:Object) REQUIRE n.objectKey IS UNIQUE")));
    }

    @Test
    void batchObjectWriterReplacesStaleTypesAndMaterializesEveryConformingClassKey() {
        String batch = Neo4jObjectQuery.upsertObjectNodesBatchInModel("Employee");

        assertTrue(batch.contains("WHERE NOT oldCls.classKey IN row.classKeys"));
        assertTrue(batch.contains("FOREACH (membership IN staleMemberships | DELETE membership)"));
        assertTrue(batch.contains("WHERE cls.classKey IN row.classKeys"));
        assertTrue(batch.contains("MERGE (obj)-[:ObjectInstanceOf]->(cls)"));
    }

    @Test
    void relationshipVocabularySeparatesObjectConformanceFromSchemaInstantiation() {
        assertEquals("ObjectInstanceOf", CanonicalGraphVocabulary.OBJECT_INSTANCE_OF);
        assertEquals("InstanceOf", CanonicalGraphVocabulary.SCHEMA_INSTANCE_OF);

        String query = Neo4jObjectQuery.upsertObjectNodeInModel("Person");
        assertTrue(query.contains("(obj)-[:ObjectInstanceOf]->(cls)"));
        assertTrue(query.contains("(cls)-[:InstanceOf]->(meta)"));
        assertFalse(query.contains("(obj)-[:InstanceOf]->(cls)"));
    }

    @Test
    void attributeWriterAndSchemaUseCanonicalPerObjectSlotIdentity() {
        String batch = Neo4jObjectQuery.UPSERT_SCALAR_ATTRIBUTE_VALUES_BATCH;

        assertTrue(batch.contains("MERGE (val:AttributeValue {slotKey:row.slotKey})"));
        assertTrue(batch.contains("SET val.name=row.valId, val.attributeKey=row.attributeKey"));
        assertTrue(batch.contains("MERGE (obj)-[:ObjectHasAttribute]->(val)"));
        assertTrue(CanonicalGraphSchema.statements().stream().anyMatch(statement ->
                statement.equals("CREATE CONSTRAINT canonical_attribute_slot_key IF NOT EXISTS "
                        + "FOR (n:AttributeValue) REQUIRE n.slotKey IS UNIQUE")));
    }

    @Test
    void binaryLinkKeyIsInjectiveAcrossEndpointsQualifierArityOrderAndPayloadBoundaries() {
        List<String> keys = List.of(
                CanonicalGraphEncoding.binaryLinkKey("Sales", "A", "ab", "c", List.of("x", "yz"), List.of()),
                CanonicalGraphEncoding.binaryLinkKey("Sales", "A", "a", "bc", List.of("x", "yz"), List.of()),
                CanonicalGraphEncoding.binaryLinkKey("Sales", "A", "ab", "c", List.of("xy", "z"), List.of()),
                CanonicalGraphEncoding.binaryLinkKey("Sales", "A", "ab", "c", List.of("yz", "x"), List.of()),
                CanonicalGraphEncoding.binaryLinkKey("Sales", "A", "ab", "c", List.of("x", "yz"), List.of("")));

        assertEquals(keys.size(), new HashSet<>(keys).size());
        assertThrows(NullPointerException.class, () -> CanonicalGraphEncoding.binaryLinkKey(
                "Sales", "A", "ab", "c", List.of(), null));
        assertEquals(CanonicalGraphEncoding.binaryLinkKey(
                        "Sales", "A", "ab", "c", List.of("x", "yz"), List.of()),
                "Sales::" + LinkState.buildIdentity("A", List.of("ab", "c"),
                        List.of(List.of("x", "yz"), List.of()), null));
    }

    @Test
    void binaryLinkWriterMergesByCanonicalLinkIdentityForEverySupportedLabel() {
        for (String label : List.of("LinkAssociateWith", "LinkAggregates", "LinkComposeOf")) {
            String batch = Neo4jObjectQuery.upsertBinaryLinksBatchInModel(label);
            assertTrue(batch.contains("MERGE (a)-[r:" + label + " {linkKey:row.linkKey}]->(b)"));
            assertTrue(batch.contains("r.associationKey=row.associationKey"));
        }
        assertTrue(CanonicalGraphSchema.statements().stream().anyMatch(statement ->
                statement.contains("[r:LinkAggregates]") && statement.contains("r.linkKey")));
        assertTrue(CanonicalGraphSchema.statements().stream().anyMatch(statement ->
                statement.contains("[r:LinkComposeOf]") && statement.contains("r.linkKey")));
    }
}
