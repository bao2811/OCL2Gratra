package org.uet.dse.neo4j.oclite;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Neo4jRepositoryCodecTest {
    @Test
    void fallbackDecodesTheCanonicalScalarWireFormatByStaticType() {
        assertEquals(42L, Neo4jRepository.decodeStoredValue("v1|I|42", "Integer", false));
        assertEquals(2.5d, Neo4jRepository.decodeStoredValue("v1|R|2.5", "Real", false));
        assertEquals("Undefined", Neo4jRepository.decodeStoredValue(
                "v1|S|Undefined", "String", false));
        assertNull(Neo4jRepository.decodeStoredValue("v1|V", "String", false));
    }

    @Test
    void fallbackRejectsMissingOrCrossTypedCanonicalPayloads() {
        assertThrows(IllegalArgumentException.class,
                () -> Neo4jRepository.decodeStoredValue("v1|I|42", null, false));
        assertThrows(IllegalArgumentException.class,
                () -> Neo4jRepository.decodeStoredValue("v1|I|42", "String", false));
    }

    @Test
    void collectionPayloadUsesTheCanonicalLeafCodec() {
        assertEquals(Arrays.asList(1L, null, 2L),
                Neo4jRepository.decodeStoredValue(
                        "v1|I|1 | v1|V | v1|I|2", "Integer", true));
        List<String> collection = List.of("v1|I|1", "v1|I|2");
        assertEquals(collection, Neo4jRepository.decodeStoredValue(collection, "Set(Integer)", true));
        assertEquals(7.0d, Neo4jRepository.decodeStoredValue(7L, "Integer", false));
    }
}
