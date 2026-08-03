package org.uet.dse.neo4jtgg.ocl.ir;

import org.junit.jupiter.api.Test;
import org.uet.dse.neo4jtgg.ocl.OclTypeBinding;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OclCypherQueryModelTest {
    private static final OclTypeBinding BOOLEAN = OclTypeBinding.scalar("Boolean");
    private static final OclTypeBinding STRING = OclTypeBinding.scalar("String");

    @Test
    void invariantFactoryRejectsIncompleteQueryModels() {
        OclCypherPlan.ExpressionPlan predicate = OclCypherQueryModel.literal(Boolean.TRUE, BOOLEAN);

        assertThrows(IllegalArgumentException.class,
                () -> OclCypherQueryModel.invariant("", "ValidName", predicate));
        assertThrows(IllegalArgumentException.class,
                () -> OclCypherQueryModel.invariant("Family", " ", predicate));
        assertThrows(NullPointerException.class,
                () -> OclCypherQueryModel.invariant("Family", "ValidName", null));
        assertThrows(IllegalArgumentException.class,
                () -> OclCypherQueryModel.invariant("Family", "ValidName", literal("notBoolean")));
    }

    @Test
    void expressionFactoriesRejectBlankNamesAndMissingTypes() {
        assertThrows(IllegalArgumentException.class,
                () -> OclCypherQueryModel.variable(" ", BOOLEAN));
        assertThrows(NullPointerException.class,
                () -> OclCypherQueryModel.variable("self", null));
        assertThrows(IllegalArgumentException.class,
                () -> OclCypherQueryModel.binary(" ", literal("a"), literal("b"), BOOLEAN));
        assertThrows(NullPointerException.class,
                () -> OclCypherQueryModel.not(null, BOOLEAN));
        assertThrows(IllegalArgumentException.class,
                () -> OclCypherQueryModel.not(literal("notBoolean"), BOOLEAN));
        assertThrows(IllegalArgumentException.class,
                () -> OclCypherQueryModel.not(OclCypherQueryModel.literal(Boolean.TRUE, BOOLEAN), STRING));
    }

    @Test
    void listBasedFactoriesDefensivelyCopyArguments() {
        OclCypherPlan.ExpressionPlan source = OclCypherQueryModel.variable("self", OclTypeBinding.node("Family"));
        List<OclCypherPlan.ExpressionPlan> args = new ArrayList<>();
        args.add(literal("A"));

        OclCypherPlan.MethodCallPlan methodCall = OclCypherQueryModel.methodCall(source, "concat", args, STRING);
        args.add(literal("B"));

        assertEquals(1, methodCall.arguments().size());
        assertThrows(UnsupportedOperationException.class, () -> methodCall.arguments().add(literal("C")));
    }

    @Test
    void navigationMatchFactoryRejectsInvalidShapeParts() {
        OclCypherPlan.ExpressionPlan owner = OclCypherQueryModel.variable("self", OclTypeBinding.node("Family"));

        assertThrows(IllegalArgumentException.class,
                () -> OclCypherQueryModel.navigationMatch(owner, "", null, null,
                        OclCypherPlan.PredicateMode.NONE, OclTypeBinding.node("Person")));
        assertThrows(NullPointerException.class,
                () -> OclCypherQueryModel.navigationMatch(owner, "nav", null, null,
                        OclCypherPlan.PredicateMode.NONE, OclTypeBinding.node("Person")));
        assertThrows(NullPointerException.class,
                () -> OclCypherQueryModel.navigationMatch(owner, "nav", null, null,
                        null, OclTypeBinding.node("Person")));
        assertThrows(IllegalArgumentException.class,
                () -> OclCypherQueryModel.navigationMatch(owner, "nav", navigationAccess(owner), null,
                        OclCypherPlan.PredicateMode.NONE,
                        OclTypeBinding.nodeCollection("Person", OclTypeBinding.CollectionKind.SET)));
    }

    @Test
    void predicateQueryFactoriesEnforceBooleanResultType() {
        OclCypherPlan.ExpressionPlan owner = OclCypherQueryModel.variable("self", OclTypeBinding.node("Family"));
        OclCypherPlan.NavigationMatchPlan match = OclCypherQueryModel.navigationMatch(
                owner,
                "nav",
                navigationAccess(owner),
                null,
                OclCypherPlan.PredicateMode.NONE,
                OclTypeBinding.node("Person"));

        assertThrows(IllegalArgumentException.class,
                () -> OclCypherQueryModel.existsSubquery(match, STRING));
        assertThrows(IllegalArgumentException.class,
                () -> OclCypherQueryModel.notExistsSubquery(match, STRING));
        assertThrows(IllegalArgumentException.class,
                () -> OclCypherQueryModel.countSubqueryComparison(match, ">", 0, STRING));

        assertEquals(BOOLEAN, OclCypherQueryModel.existsSubquery(match, BOOLEAN).type());
        assertEquals(BOOLEAN, OclCypherQueryModel.notExistsSubquery(match, BOOLEAN).type());
        assertEquals(BOOLEAN, OclCypherQueryModel.countSubqueryComparison(match, ">", 0, BOOLEAN).type());
    }

    @Test
    void validFactoryOutputStillRendersAsInvariantQuery() {
        OclCypherPlan.InvariantPlan plan = OclCypherQueryModel.invariant(
                "Family",
                "AlwaysTrue",
                OclCypherQueryModel.literal(Boolean.TRUE, BOOLEAN));

        OclCypherRenderer.RenderedInvariant rendered = new OclCypherRenderer().renderInvariant(plan);

        assertTrue(rendered.cypher().contains(
                "MATCH (self:Object)-[:ObjectInstanceOf]->(cls {classKey:"));
        assertTrue(rendered.cypher().contains("WHERE NOT coalesce($"));
        assertTrue(rendered.cypher().contains("RETURN DISTINCT self.use_id AS useId"));
        assertTrue(rendered.parameters().containsValue(Boolean.TRUE));
        assertTrue(rendered.parameters().containsValue("Family"));
    }

    private OclCypherPlan.ExpressionPlan literal(String value) {
        return OclCypherQueryModel.literal(value, STRING);
    }

    private OclCypherPlan.NavigationAccessPlan navigationAccess(OclCypherPlan.ExpressionPlan owner) {
        return OclCypherQueryModel.navigationAccess(
                owner,
                new org.uet.dse.neo4jtgg.ocl.OclMetamodelIndex.NavigationInfo(
                        "children",
                        "FamilyChildren",
                        "Family",
                        "Person",
                        OclTypeBinding.nodeCollection("Person", OclTypeBinding.CollectionKind.SET),
                        null,
                        null,
                        org.uet.dse.neo4jtgg.ocl.OclMetamodelIndex.NavigationDirection.OUTGOING,
                        null),
                List.of(),
                OclTypeBinding.nodeCollection("Person", OclTypeBinding.CollectionKind.SET));
    }
}
