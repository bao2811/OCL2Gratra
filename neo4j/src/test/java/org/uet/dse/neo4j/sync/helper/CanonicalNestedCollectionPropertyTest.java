package org.uet.dse.neo4j.sync.helper;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanonicalNestedCollectionPropertyTest {
  private static final long SEED = 20260823L;
  private static final int CASES = 256;

  @Test
  void seededLeavesRoundTripEmptySingletonDuplicateOrderAndBottom() {
    Random random = new Random(SEED);
    boolean sawEmpty = false;
    boolean sawSingleton = false;
    boolean sawDuplicate = false;
    boolean sawOrderWitness = false;
    boolean sawBottom = false;

    for (int caseIndex = 0; caseIndex < CASES; caseIndex++) {
      int size = random.nextInt(9);
      List<Object> values = new ArrayList<>();
      for (int index = 0; index < size; index++) {
        values.add(random.nextInt(8) == 0 ? null : (long) random.nextInt(5));
      }
      String encoded = CanonicalCollectionValueCodec.encodeScalarLeaves(values, "Integer");
      assertEquals(values, CanonicalCollectionValueCodec.decodeScalarLeaves(encoded, "Integer"),
          "seed=" + SEED + " case=" + caseIndex);

      sawEmpty |= values.isEmpty();
      sawSingleton |= values.size() == 1;
      sawBottom |= values.contains(null);
      sawDuplicate |= values.stream().anyMatch(value -> Collections.frequency(values, value) > 1);
      if (values.size() > 1) {
        List<Object> reversed = new ArrayList<>(values);
        Collections.reverse(reversed);
        if (!values.equals(reversed)) {
          sawOrderWitness = true;
          assertNotEquals(encoded,
              CanonicalCollectionValueCodec.encodeScalarLeaves(reversed, "Integer"));
        }
      }
    }

    assertTrue(sawEmpty && sawSingleton && sawDuplicate && sawOrderWitness && sawBottom,
        () -> "Generator coverage failed for seed " + SEED);
  }
}
