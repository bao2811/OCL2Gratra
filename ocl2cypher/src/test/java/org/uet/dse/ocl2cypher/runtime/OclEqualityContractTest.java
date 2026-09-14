package org.uet.dse.ocl2cypher.runtime;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.uet.dse.ocl2cypher.runtime.OclEquality.BoolKind.FALSE;
import static org.uet.dse.ocl2cypher.runtime.OclEquality.BoolKind.TRUE;

/** Executable refinement checks for F-3a--d on well-typed runtime values. */
class OclEqualityContractTest {

    @Test
    void equalityIsTotalAndAnEquivalenceOnEveryAtomicCarrier() {
        List<List<OclValue>> carriers = List.of(
                List.of(bottom(OclType.BOOLEAN), bool(true), bool(false)),
                List.of(bottom(OclType.INTEGER), integer(0), integer(1)),
                List.of(bottom(OclType.REAL), real("0.5"), real("1.0")),
                List.of(bottom(OclType.STRING), string("a"), string("b")),
                List.of(bottom(OclType.clazz("Person")), object("p1"), object("p2"))
        );
        for (List<OclValue> carrier : carriers) {
            assertEquivalence(carrier);
        }
    }

    @Test
    void exactRealEqualityUsesNormalizedNumericValue() {
        OclValue halfDecimal = real("0.5");
        OclValue halfRational = new OclValue.RealValue(
                new ExactReal(BigInteger.valueOf(2), BigInteger.valueOf(4)));
        assertEquals(TRUE, OclEquality.equal(halfDecimal, halfRational));
        assertEquals(halfDecimal, halfRational);
    }

    @Test
    void setEqualityIsExtensionalAndSeparatesThreeBottomStates() {
        OclType type = OclType.set(OclType.INTEGER);
        OclValue zero = integer(0);
        OclValue one = integer(1);
        OclValue elementBottom = bottom(OclType.INTEGER);
        OclValue empty = new OclValue.SetValue(type, List.of());
        OclValue reordered = new OclValue.SetValue(type, List.of(one, zero));
        OclValue canonical = new OclValue.SetValue(type, List.of(zero, one, zero));
        OclValue withElementBottom = new OclValue.SetValue(type, List.of(elementBottom));
        OclValue wholeBottom = bottom(type);

        assertEquals(TRUE, OclEquality.equal(reordered, canonical));
        assertEquals(2, ((OclValue.SetValue) canonical).size());
        assertEquals(FALSE, OclEquality.equal(empty, withElementBottom));
        assertEquals(FALSE, OclEquality.equal(empty, wholeBottom));
        assertEquals(FALSE, OclEquality.equal(withElementBottom, wholeBottom));
        assertEquivalence(List.of(empty, reordered, canonical, withElementBottom, wholeBottom));
    }

    @Test
    void bagEqualityUsesMultiplicityButNotOccurrenceOrder() {
        OclType type = OclType.bag(OclType.INTEGER);
        OclValue zero = integer(0);
        OclValue one = integer(1);
        OclValue elementBottom = bottom(OclType.INTEGER);
        OclValue a = new OclValue.BagValue(type, List.of(zero, one, zero));
        OclValue permutation = new OclValue.BagValue(type, List.of(zero, zero, one));
        OclValue fewer = new OclValue.BagValue(type, List.of(zero, one));
        OclValue twoBottoms = new OclValue.BagValue(type,
                List.of(elementBottom, bottom(OclType.INTEGER)));
        OclValue oneBottom = new OclValue.BagValue(type, List.of(elementBottom));
        OclValue wholeBottom = bottom(type);

        assertEquals(TRUE, OclEquality.equal(a, permutation));
        assertEquals(FALSE, OclEquality.equal(a, fewer));
        assertEquals(FALSE, OclEquality.equal(twoBottoms, oneBottom));
        assertEquals(FALSE, OclEquality.equal(oneBottom, wholeBottom));
        assertEquivalence(List.of(a, permutation, fewer, twoBottoms, oneBottom, wholeBottom));
    }

    private static void assertEquivalence(List<OclValue> values) {
        for (OclValue a : values) {
            assertEquals(TRUE, OclEquality.equal(a, a), "reflexivity: " + a);
            for (OclValue b : values) {
                OclEquality.BoolKind ab = OclEquality.equal(a, b);
                assertNotEquals(OclEquality.BoolKind.BOTTOM, ab, "totality");
                assertEquals(ab, OclEquality.equal(b, a), "symmetry: " + a + ", " + b);
                for (OclValue c : values) {
                    if (ab == TRUE && OclEquality.equal(b, c) == TRUE) {
                        assertEquals(TRUE, OclEquality.equal(a, c),
                                "transitivity: " + a + ", " + b + ", " + c);
                    }
                }
            }
        }
    }

    private static OclValue bottom(OclType type) {
        return new OclValue.BottomValue(type);
    }

    private static OclValue bool(boolean value) {
        return new OclValue.BooleanValue(OclType.BOOLEAN,
                value ? OclValue.BooleanValue.Bool3.TRUE : OclValue.BooleanValue.Bool3.FALSE);
    }

    private static OclValue integer(long value) {
        return new OclValue.IntegerValue(BigInteger.valueOf(value));
    }

    private static OclValue real(String value) {
        return new OclValue.RealValue(new BigDecimal(value));
    }

    private static OclValue string(String value) {
        return new OclValue.StringValue(value);
    }

    private static OclValue object(String id) {
        return new OclValue.ObjectValue(OclType.clazz("Person"), id);
    }
}
