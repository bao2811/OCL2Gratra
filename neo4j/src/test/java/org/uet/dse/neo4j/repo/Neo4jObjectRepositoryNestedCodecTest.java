package org.uet.dse.neo4j.repo;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class Neo4jObjectRepositoryNestedCodecTest {
  @Test
  void primitiveNestedLeavesRetainBottomMultiplicityAndOrder() {
    assertEquals("v1|I|2 | v1|V | v1|I|2 | v1|I|1",
        Neo4jObjectRepository.encodePrimitiveNestedLeaves(
            Arrays.asList(2L, null, 2L, 1L), false, "Int"));
  }

  @Test
  void primitiveEmptyLeafIsExplicitButEntityLeafNeverCreatesAPayload() {
    assertEquals("COLLECTION_EMPTY",
        Neo4jObjectRepository.encodePrimitiveNestedLeaves(List.of(), false, "String"));
    assertNull(Neo4jObjectRepository.encodePrimitiveNestedLeaves(
        List.of("m1"), true, "Medication"));
    assertNull(Neo4jObjectRepository.encodePrimitiveNestedLeaves(
        List.of(), false, "String", true));
  }
}
