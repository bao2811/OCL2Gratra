package org.uet.dse.neo4j.sync.object;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Neo4jObjectSnapshotMapperCodecTest {
    @Test
    void scalarSnapshotDecodesByTheStoredStaticType() {
        assertEquals(9L, Neo4jObjectSnapshotMapper.decodeScalarValue("v1|I|9", "Integer"));
        assertEquals(9L, Neo4jObjectSnapshotMapper.decodeScalarValue("v1|I|9", "Int"));
        assertEquals("A|B", Neo4jObjectSnapshotMapper.decodeScalarValue("v1|S|A%7CB", "String"));
        assertNull(Neo4jObjectSnapshotMapper.decodeScalarValue("v1|V", "Real"));
    }

    @Test
    void scalarSnapshotRejectsMalformedOrUntypedCanonicalText() {
        assertThrows(IllegalArgumentException.class,
                () -> Neo4jObjectSnapshotMapper.decodeScalarValue("v1|I|9", "String"));
        assertThrows(IllegalArgumentException.class,
                () -> Neo4jObjectSnapshotMapper.decodeScalarValue("v1|I|9", null));
    }
}
