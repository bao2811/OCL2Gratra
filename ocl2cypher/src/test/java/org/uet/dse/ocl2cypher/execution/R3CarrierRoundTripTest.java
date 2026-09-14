package org.uet.dse.ocl2cypher.execution;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.uet.dse.ocl2cypher.core.CoreExpr;
import org.uet.dse.ocl2cypher.cypher.CypherArtifacts;
import org.uet.dse.ocl2cypher.cypher.CypherAst;
import org.uet.dse.ocl2cypher.cypher.Realization;
import org.uet.dse.ocl2cypher.diagnostics.SourceSpan;
import org.uet.dse.ocl2cypher.graph.GraphModel;
import org.uet.dse.ocl2cypher.qcyp.QNode;
import org.uet.dse.ocl2cypher.qcyp.QQuery;
import org.uet.dse.ocl2cypher.runtime.OclType;
import org.uet.dse.ocl2cypher.runtime.OclValue;

import static org.junit.jupiter.api.Assertions.*;

/** Executable obligations for the finite tagged-carrier isomorphism in R-3. */
class R3CarrierRoundTripTest {

    @Test
    void canonicalTagsCoverEveryAdmittedCarrierFamily() {
        assertEquals("Boolean3", CypherArtifacts.typeTag(OclType.BOOLEAN));
        assertEquals("Integer", CypherArtifacts.typeTag(OclType.INTEGER));
        assertEquals("Real", CypherArtifacts.typeTag(OclType.REAL));
        assertEquals("String", CypherArtifacts.typeTag(OclType.STRING));
        assertEquals("Class:Person", CypherArtifacts.typeTag(OclType.clazz("Person")));
        assertEquals("Set<Integer>", CypherArtifacts.typeTag(OclType.set(OclType.INTEGER)));
        assertEquals("Bag<Class:Person>",
                CypherArtifacts.typeTag(OclType.bag(OclType.clazz("Person"))));

        OclType set = OclType.set(OclType.INTEGER);
        assertEquals("Integer", CypherArtifacts.carrierTypeTag(set, false));
        assertEquals("Set<Integer>", CypherArtifacts.carrierTypeTag(set, true));
    }

    @Test
    void scalarConstructorsRoundTripOnTheCertifiedExecutionDomain() {
        List<OclValue> values = List.of(
                new OclValue.BooleanValue(OclType.BOOLEAN,
                        OclValue.BooleanValue.Bool3.TRUE),
                new OclValue.BooleanValue(OclType.BOOLEAN,
                        OclValue.BooleanValue.Bool3.FALSE),
                new OclValue.IntegerValue(BigInteger.valueOf(Long.MIN_VALUE)),
                new OclValue.IntegerValue(BigInteger.valueOf(Long.MAX_VALUE)),
                new OclValue.RealValue(new BigDecimal("0.5")),
                new OclValue.StringValue("not-a-bottom-token"),
                new OclValue.ObjectValue(OclType.clazz("Person"), "person-1"),
                new OclValue.BottomValue(OclType.BOOLEAN),
                new OclValue.BottomValue(OclType.INTEGER),
                new OclValue.BottomValue(OclType.REAL),
                new OclValue.BottomValue(OclType.STRING),
                new OclValue.BottomValue(OclType.clazz("Person")));

        for (OclValue value : values) {
            assertEquals(value, decode(encode(value), value.type()), value.toString());
        }
    }

    @Test
    void collectionConstructorsPreserveEmptyElementBottomWholeBottomAndMultiplicity() {
        OclType setType = OclType.set(OclType.INTEGER);
        OclType bagType = OclType.bag(OclType.INTEGER);
        OclValue.IntegerValue one = new OclValue.IntegerValue(BigInteger.ONE);
        OclValue.BottomValue elementBottom = new OclValue.BottomValue(OclType.INTEGER);

        OclValue.SetValue empty = new OclValue.SetValue(setType, List.of());
        OclValue.SetValue withBottom = new OclValue.SetValue(setType, List.of(elementBottom));
        OclValue.BottomValue wholeBottom = new OclValue.BottomValue(setType);
        OclValue.BagValue bag = new OclValue.BagValue(bagType, List.of(one, one, elementBottom));

        assertEquals(empty, decode(encode(empty), setType));
        assertEquals(withBottom, decode(encode(withBottom), setType));
        assertEquals(wholeBottom, decode(encode(wholeBottom), setType));
        assertEquals(bag, decode(encode(bag), bagType));

        assertNotEquals(empty, withBottom);
        assertNotEquals(empty, wholeBottom);
        assertNotEquals(withBottom, wholeBottom);
        assertEquals(3, ((OclValue.BagValue) decode(encode(bag), bagType)).size());
    }

    @Test
    void setRoundTripIsExtensionalAndBagRoundTripKeepsOccurrences() {
        OclType setType = OclType.set(OclType.STRING);
        OclType bagType = OclType.bag(OclType.STRING);
        OclValue.StringValue a = new OclValue.StringValue("a");
        OclValue.StringValue b = new OclValue.StringValue("b");

        OclValue.SetValue left = new OclValue.SetValue(setType, List.of(a, b, a));
        OclValue.SetValue right = new OclValue.SetValue(setType, List.of(b, a));
        assertEquals(left, right);
        assertEquals(right, decode(encode(left), setType));

        OclValue.BagValue bag = new OclValue.BagValue(bagType, List.of(a, b, a));
        OclValue.BagValue decoded = (OclValue.BagValue) decode(encode(bag), bagType);
        assertEquals(bag, decoded);
        assertEquals(3, decoded.occurrences().size());
    }

    @Test
    void realizationPublishesTheSameCanonicalTagAsTheCarrier() {
        var bool = new QNode.QExpr.Constant(SourceSpan.UNKNOWN, OclType.BOOLEAN, true);
        var boolQuery = new QQuery(bool, null, QQuery.QResultShape.SCALAR,
                QQuery.QueryMode.VALUE, OclType.BOOLEAN, null, null, false);
        var boolArtifact = Realization.realize(boolQuery, new GraphModel("r3"),
                CypherAst.Dialect.CYPHER_5);
        assertTrue(boolArtifact.isSuccess(), () -> boolArtifact.diagnostics().toString());
        assertEquals("Boolean3", boolArtifact.value().contract().elementTypeTag());

        var one = new QNode.QExpr.Constant(SourceSpan.UNKNOWN, OclType.INTEGER,
                BigInteger.ONE);
        OclType setType = OclType.set(OclType.INTEGER);
        var set = new QNode.QExpr.CollectionLiteral(SourceSpan.UNKNOWN,
                CoreExpr.CollectionKind.SET, List.of(one), setType);
        var setQuery = new QQuery(set, null, QQuery.QResultShape.SET,
                QQuery.QueryMode.VALUE, setType, null, null, false);
        var setArtifact = Realization.realize(setQuery, new GraphModel("r3"),
                CypherAst.Dialect.CYPHER_5);
        assertTrue(setArtifact.isSuccess(), () -> setArtifact.diagnostics().toString());
        assertEquals("Set<Integer>", setArtifact.value().contract().elementTypeTag());
        assertEquals(CypherArtifacts.OCL_BOTTOM,
                setArtifact.value().contract().wholeBottomTag());
    }

    private static OclValue decode(CypherAst.CypherExpr encoded, OclType type) {
        return Neo4jExecutionAdapter.decodeTagged(eval(encoded), type);
    }

    private static CypherAst.CypherExpr encode(OclValue value) {
        if (value instanceof OclValue.BottomValue) {
            return CypherArtifacts.bottomLiteral(value.type());
        }
        if (value instanceof OclValue.BooleanValue bool) {
            return CypherArtifacts.taggedScalar(value.type(),
                    new CypherAst.BooleanLiteral(
                            bool.bool() == OclValue.BooleanValue.Bool3.TRUE));
        }
        if (value instanceof OclValue.IntegerValue integer) {
            return CypherArtifacts.taggedScalar(value.type(),
                    new CypherAst.IntegerLiteral(integer.value()));
        }
        if (value instanceof OclValue.RealValue real) {
            return CypherArtifacts.taggedScalar(value.type(),
                    new CypherAst.FloatLiteral(real.value()));
        }
        if (value instanceof OclValue.StringValue string) {
            return CypherArtifacts.taggedScalar(value.type(),
                    new CypherAst.StringLiteral(string.value()));
        }
        if (value instanceof OclValue.ObjectValue object) {
            return CypherArtifacts.taggedScalar(value.type(),
                    new CypherAst.StringLiteral(object.stableId()));
        }
        List<OclValue> values;
        if (value instanceof OclValue.SetValue set) {
            values = set.members();
        } else if (value instanceof OclValue.BagValue bag) {
            values = bag.occurrences();
        } else {
            throw new IllegalArgumentException("unsupported carrier value: " + value);
        }
        return CypherArtifacts.collectionTag(value.type(),
                new CypherAst.ListExpr(values.stream().map(R3CarrierRoundTripTest::encode).toList()));
    }

    /** Evaluate only the closed constructor fragment emitted by CypherArtifacts. */
    private static Object eval(CypherAst.CypherExpr expression) {
        if (expression instanceof CypherAst.LocatedExpr located) {
            return eval(located.expression());
        }
        if (expression instanceof CypherAst.NullLiteral) {
            return null;
        }
        if (expression instanceof CypherAst.BooleanLiteral literal) {
            return literal.value();
        }
        if (expression instanceof CypherAst.IntegerLiteral literal) {
            return literal.value();
        }
        if (expression instanceof CypherAst.FloatLiteral literal) {
            return literal.value();
        }
        if (expression instanceof CypherAst.StringLiteral literal) {
            return literal.value();
        }
        if (expression instanceof CypherAst.ListExpr list) {
            return list.items().stream().map(R3CarrierRoundTripTest::eval).toList();
        }
        if (expression instanceof CypherAst.MapExpr map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (CypherAst.MapEntry entry : map.entries()) {
                result.put(entry.key(), eval(entry.value()));
            }
            return result;
        }
        if (expression instanceof CypherAst.CaseExpr conditional) {
            for (CypherAst.WhenThen branch : conditional.branches()) {
                if (Boolean.TRUE.equals(eval(branch.when()))) {
                    return eval(branch.then());
                }
            }
            return eval(conditional.elseExpr());
        }
        throw new IllegalArgumentException("not a closed carrier constructor: " + expression);
    }
}
