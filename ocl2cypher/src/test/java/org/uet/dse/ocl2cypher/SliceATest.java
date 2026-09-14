package org.uet.dse.ocl2cypher;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.uet.dse.ocl2cypher.api.CoreOracle;
import org.uet.dse.ocl2cypher.runtime.OclType;
import org.uet.dse.ocl2cypher.source.model.SchemaModel;
import org.uet.dse.ocl2cypher.source.model.Snapshot;
import org.uet.dse.ocl2cypher.source.model.UmlAttribute;
import org.uet.dse.ocl2cypher.source.model.UmlClass;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Slice A — scalar attribute invariant ({@code self.age >= 18}) end-to-end.
 *
 * <p>The test does not materialize any graph or Cypher: it shows that the
 * source denotation (the Core oracle) already distinguishes the three carrier
 * states the specification worries about — an object whose age is defined and
 * satisfies the bound, an object whose age violates it, and an object whose
 * age is missing so the interpreter answers the declared typed bottom. The
 * last case therefore belongs to the violation set (F or bottom, not only F),
 * and the violation oracle does not collapse its answer to an empty set. Calls
 * that expect a violated-object set therefore use the correct two-valued test,
 * not a "bottom → false" filter.
 */
class SliceATest {

    private static SchemaModel schema() {
        return SchemaModel.builder("model")
                .clazz(UmlClass.of("Person"))
                .attribute(UmlAttribute.of("Person", "age", OclType.INTEGER))
                .build();
    }

    @Test
    void adultInvariantViolationSetEqualsCoreOracle() {
        String ocl = "context Person inv Adult: self.age >= 18";
        SchemaModel sm = schema();
        Snapshot sn = Snapshot.builder()
                .object("alice", "Person")
                .attribute("alice", "age", new org.uet.dse.ocl2cypher.runtime.OclValue.IntegerValue(java.math.BigInteger.valueOf(20)))
                .object("bob", "Person")
                .attribute("bob", "age", new org.uet.dse.ocl2cypher.runtime.OclValue.IntegerValue(java.math.BigInteger.valueOf(15)))
                .build();

        List<String> violations = CoreOracle.violationsOclEq(ocl, sm, sn);

        assertEquals(Set.of("bob"), Set.copyOf(violations),
                "adult violations are exactly the objects whose age is 15, not 20");
        assertFalse(violations.contains("alice"),
                "defined carriers that satisfy the bound are not violations");
    }

    @Test
    void missingAttributeBodyGoesBottomAndViolates() {
        String ocl = "context Person inv Adult: self.age >= 18";
        SchemaModel sm = schema();
        Snapshot sn = Snapshot.builder()
                .object("charlie", "Person")
                // no age — interpreter will answer Bottom(Integer), not 0 or native null
                .build();

        List<String> violations = CoreOracle.violationsOclEq(ocl, sm, sn);

        assertEquals(Set.of("charlie"), Set.copyOf(violations),
                "a missing attribute does not become empty — "
                        + "⊥Integer in (⊥Integer >= 18) is ⊥Boolean, and ⊥Boolean is a violation");
    }

    @Test
    void allSatisfyYieldsEmptyViolations() {
        String ocl = "context Person inv Adult: self.age >= 18";
        SchemaModel sm = schema();
        Snapshot sn = Snapshot.builder()
                .object("carol", "Person")
                .attribute("carol", "age", new org.uet.dse.ocl2cypher.runtime.OclValue.IntegerValue(java.math.BigInteger.valueOf(18)))
                .build();

        assertTrue(CoreOracle.violationsOclEq(ocl, sm, sn).isEmpty());
    }
}
