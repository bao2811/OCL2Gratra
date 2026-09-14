package org.uet.dse.ocl2cypher;

import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.uet.dse.ocl2cypher.api.FrontendCompiler;
import org.uet.dse.ocl2cypher.api.ValueQueryRequest;
import org.uet.dse.ocl2cypher.core.CoreDeclaration;
import org.uet.dse.ocl2cypher.core.CoreExpr;
import org.uet.dse.ocl2cypher.core.CoreInterpreter;
import org.uet.dse.ocl2cypher.core.CoreLowering;
import org.uet.dse.ocl2cypher.cypher.CypherAst;
import org.uet.dse.ocl2cypher.cypher.Realization;
import org.uet.dse.ocl2cypher.cypher.Serializer;
import org.uet.dse.ocl2cypher.diagnostics.SourceSpan;
import org.uet.dse.ocl2cypher.graph.GraphBuilder;
import org.uet.dse.ocl2cypher.graph.GraphKey;
import org.uet.dse.ocl2cypher.graph.GraphModel;
import org.uet.dse.ocl2cypher.graph.GraphObservation;
import org.uet.dse.ocl2cypher.qcyp.NormQ;
import org.uet.dse.ocl2cypher.qcyp.QCypTranslator;
import org.uet.dse.ocl2cypher.qcyp.QInterpreter;
import org.uet.dse.ocl2cypher.qcyp.QNode;
import org.uet.dse.ocl2cypher.qcyp.QQuery;
import org.uet.dse.ocl2cypher.runtime.Boolean3;
import org.uet.dse.ocl2cypher.runtime.OclType;
import org.uet.dse.ocl2cypher.runtime.OclValue;
import org.uet.dse.ocl2cypher.source.model.CapabilityMatrix;
import org.uet.dse.ocl2cypher.source.model.QualifierValue;
import org.uet.dse.ocl2cypher.source.model.SchemaModel;
import org.uet.dse.ocl2cypher.source.model.Snapshot;
import org.uet.dse.ocl2cypher.source.model.UmlAssociation;
import org.uet.dse.ocl2cypher.source.model.UmlAttribute;
import org.uet.dse.ocl2cypher.source.model.UmlClass;
import org.uet.dse.ocl2cypher.source.model.UmlQualifier;

/**
 * Regression checks for the high-risk semantic and representation boundaries.
 */
class SemanticBoundaryRegressionTest {

    @Test
    void distinctBagHasSetTypeAndRemovesDuplicatesWithoutRealization() {
        var span = SourceSpan.UNKNOWN;
        var one = new QNode.QExpr.Constant(span, OclType.INTEGER, BigInteger.ONE);
        var two = new QNode.QExpr.Constant(span, OclType.INTEGER, BigInteger.TWO);
        var bag = new QNode.QExpr.CollectionLiteral(span, CoreExpr.CollectionKind.BAG,
                List.of(one, one, two), OclType.bag(OclType.INTEGER));
        var distinct = new QNode.QPlan.Distinct(span, new QNode.QPlan.FromCollection(span, bag));
        assertEquals(OclType.set(OclType.INTEGER), distinct.type);
        var sm = SchemaModel.builder("distinct").build();
        var graph = new GraphModel("distinct");
        var env = new CoreInterpreter.Env();
        var items = QInterpreter.evalPlan(sm, graph, env, distinct);
        assertEquals(List.of(new OclValue.IntegerValue(BigInteger.ONE),
                new OclValue.IntegerValue(BigInteger.TWO)), items);
        var materialized = QInterpreter.evalExpr(sm, graph, env,
                new QNode.QExpr.Materialize(span, distinct));
        assertEquals(OclType.set(OclType.INTEGER), materialized.type());
        assertEquals(items, QInterpreter.evalPlan(sm, graph, env,
                new QNode.QPlan.Distinct(span, distinct)));

        var empty = new QNode.QExpr.CollectionLiteral(span, CoreExpr.CollectionKind.BAG,
                List.of(), OclType.bag(OclType.INTEGER));
        var emptyDistinct = new QNode.QPlan.Distinct(span,
                new QNode.QPlan.FromCollection(span, empty));
        assertEquals(List.of(), QInterpreter.evalPlan(sm, graph, env, emptyDistinct));

        var elementBottom = new QNode.QExpr.Bottom(span, OclType.INTEGER);
        var bottomBag = new QNode.QExpr.CollectionLiteral(span, CoreExpr.CollectionKind.BAG,
                List.of(elementBottom), OclType.bag(OclType.INTEGER));
        var bottomDistinct = new QNode.QPlan.Distinct(span,
                new QNode.QPlan.FromCollection(span, bottomBag));
        assertEquals(List.of(elementBottomValue()),
                QInterpreter.evalPlan(sm, graph, env, bottomDistinct));

        var wholeBottom = new QNode.QPlan.Distinct(span,
                new QNode.QPlan.FromCollection(span,
                        new QNode.QExpr.Bottom(span, OclType.bag(OclType.INTEGER))));
        assertNull(QInterpreter.evalPlan(sm, graph, env, wholeBottom));
    }

    private static OclValue.BottomValue elementBottomValue() {
        return new OclValue.BottomValue(OclType.INTEGER);
    }

    @Test
    void collectionSizeHasCardinalityCertificateAndRealizes() {
        var sm = SchemaModel.builder("m").clazz(UmlClass.of("Person")).build();
        var sn = Snapshot.builder().object("p", "Person").build();
        String text = realizeViolationText(
                "context Person inv BagSize: Bag{1, 1}->size() = 2", sm, sn);
        assertTrue(text.contains("size("), text);
    }

    @Test
    void storedIntegerAttributeIsCertifiedButArithmeticOverflowRejects() {
        SchemaModel sm = SchemaModel.builder("m").clazz(UmlClass.of("Person"))
                .attribute(org.uet.dse.ocl2cypher.source.model.UmlAttribute.of(
                        "Person", "age", OclType.INTEGER)).build();
        Snapshot sn = Snapshot.builder().object("p", "Person").build();
        // Simple attribute comparison: certified [MIN,MAX] >= Literal [18,18] → success
        assertNotNull(realizeViolationText("context Person inv AgePositive: self.age > 0", sm, sn));
        // Arithmetic: [MIN,MAX] + [1,1] = [MIN+1,MAX+1] exceeds INT64 → correctly rejected
        expectNumericRejection("context Person inv AgeSum: self.age + 1 > 0", sm, sn);
    }

    @Test
    void numericVariableRealizationIsCertifiedAndSucceeds() {
        SchemaModel sm = SchemaModel.builder("m").clazz(UmlClass.of("Person")).build();
        Snapshot sn = Snapshot.builder().object("p", "Person").build();
        // Literal evidence is not yet transported through declaration bindings.
        assertNotNull(realizeViolationText("context Person inv Local: let x: Integer = 1 in x > 0", sm, sn));
    }

    @Test
    void uncertifiedRealCoercionIsRejectedExplicitly() {
        SchemaModel sm = SchemaModel.builder("m").clazz(UmlClass.of("Person")).build();
        Snapshot sn = Snapshot.builder().object("p", "Person").build();
        expectRealRejection("context Person inv RealCast: 1.0 + 2.0 > 0.0", sm, sn);
        expectRealRejection(
                "context Person inv RealLet: let x: Real = 0.5 in x > 0.0", sm, sn);
    }

    @Test
    void storedRealComparisonRequiresExactBinary64LeafCertificate() {
        SchemaModel sm = SchemaModel.builder("real-leaf")
                .clazz(UmlClass.of("Person"))
                .attribute(UmlAttribute.of("Person", "rate", OclType.REAL))
                .build();
        Snapshot exact = Snapshot.builder().object("p", "Person")
                .attribute("p", "rate", new OclValue.RealValue(
                        new java.math.BigDecimal("0.5"))).build();
        Snapshot rounded = Snapshot.builder().object("p", "Person")
                .attribute("p", "rate", new OclValue.RealValue(
                        new java.math.BigDecimal("0.1"))).build();
        Snapshot collapsed = Snapshot.builder().object("p", "Person")
                .attribute("p", "rate", new OclValue.RealValue(
                        new java.math.BigDecimal("9007199254740993"))).build();

        assertNotNull(realizeViolationText(
                "context Person inv Exact: self.rate >= 0.5", sm, exact));
        expectRealRejection(
                "context Person inv Rounded: self.rate >= 0.1", sm, rounded);
        expectRealRejection(
                "context Person inv Collapse: self.rate = 9007199254740992.0", sm,
                collapsed);
    }

    private static String realizeViolationText(String source, SchemaModel sm, Snapshot sn) {
        var parsed = FrontendCompiler.compile(source, sm);
        assertTrue(parsed.isSuccess(), () -> parsed.diagnostics().toString());
        var doc = parsed.value().get(0);
        var lowered = CoreLowering.lower(sm, doc, doc.constraints.get(0));
        assertTrue(lowered.isSuccess(), () -> lowered.diagnostics().toString());
        var translated = QCypTranslator.translate(lowered.value());
        assertTrue(translated.isSuccess(), () -> translated.diagnostics().toString());
        var graph = GraphBuilder.build(sm, sn);
        assertTrue(graph.isSuccess(), () -> graph.diagnostics().toString());
        var realized = Realization.realize(translated.value(), graph.value().graph(),
                CypherAst.Dialect.CYPHER_5);
        assertTrue(realized.isSuccess(), () -> realized.diagnostics().toString());
        return Serializer.cypherText(realized.value());
    }

    /**
     * For inputs intentionally outside the certified numeric realization
     * domain.
     */
    private static void expectNumericRejection(String source, SchemaModel sm, Snapshot sn) {
        var parsed = FrontendCompiler.compile(source, sm);
        assertTrue(parsed.isSuccess(), () -> parsed.diagnostics().toString());
        var doc = parsed.value().get(0);
        var lowered = CoreLowering.lower(sm, doc, doc.constraints.get(0));
        assertTrue(lowered.isSuccess(), () -> lowered.diagnostics().toString());
        var translated = QCypTranslator.translate(lowered.value());
        assertTrue(translated.isSuccess(), () -> translated.diagnostics().toString());
        var graph = GraphBuilder.build(sm, sn);
        assertTrue(graph.isSuccess(), () -> graph.diagnostics().toString());
        assertEquals(new java.util.TreeSet<>(org.uet.dse.ocl2cypher.api.CoreOracle.violationsOclEq(source, sm, sn)),
                new java.util.TreeSet<>(QInterpreter.violations(sm, graph.value().graph(), lowered.value(), translated.value())), source);
        var realized = Realization.realize(translated.value(), graph.value().graph(),
                CypherAst.Dialect.CYPHER_5);
        assertFalse(realized.isSuccess(), () -> "expected certified numeric rejection: "
                + realized.diagnostics());
        assertEquals("R_NUMERIC_CAPABILITY", realized.primaryDiagnostic().code(),
                () -> realized.diagnostics().toString());
        assertEquals(org.uet.dse.ocl2cypher.diagnostics.Stage.R, realized.primaryDiagnostic().stage());
    }

    private static void expectRealRejection(String source, SchemaModel sm, Snapshot sn) {
        var parsed = FrontendCompiler.compile(source, sm);
        assertTrue(parsed.isSuccess(), () -> parsed.diagnostics().toString());
        var doc = parsed.value().get(0);
        var lowered = CoreLowering.lower(sm, doc, doc.constraints.get(0));
        assertTrue(lowered.isSuccess(), () -> lowered.diagnostics().toString());
        var translated = QCypTranslator.translate(lowered.value());
        assertTrue(translated.isSuccess(), () -> translated.diagnostics().toString());
        var graph = GraphBuilder.build(sm, sn);
        assertTrue(graph.isSuccess(), () -> graph.diagnostics().toString());
        assertEquals(new java.util.TreeSet<>(org.uet.dse.ocl2cypher.api.CoreOracle.violationsOclEq(source, sm, sn)),
                new java.util.TreeSet<>(QInterpreter.violations(sm, graph.value().graph(), lowered.value(), translated.value())), source);
        var realized = Realization.realize(translated.value(), graph.value().graph(), CypherAst.Dialect.CYPHER_5);
        assertFalse(realized.isSuccess(), () -> realized.diagnostics().toString());
        assertEquals("R-REAL-EXACT-UNSUPPORTED", realized.primaryDiagnostic().code());
        assertEquals(org.uet.dse.ocl2cypher.diagnostics.Stage.R, realized.primaryDiagnostic().stage());
    }

    private static SchemaModel qualifiedSchema(boolean unique) {
        return SchemaModel.builder("m")
                .clazz(UmlClass.of("Company"))
                .clazz(UmlClass.of("Employee"))
                .association(new UmlAssociation("employment-key", "employment",
                        "Company", "employer", 0, -1,
                        "Employee", "employees", 0, -1,
                        List.of(new UmlQualifier("slot", OclType.INTEGER, List.of(
                                new OclValue.IntegerValue(BigInteger.ONE),
                                new OclValue.IntegerValue(BigInteger.TWO)))), false, unique))
                .build();
    }

    private static SchemaModel qualifiedSchemaWithBounds(boolean unique,
            int sourceLower,
            int targetLower,
            UmlQualifier qualifier) {
        return SchemaModel.builder("m")
                .clazz(UmlClass.of("Company"))
                .clazz(UmlClass.of("Employee"))
                .association(new UmlAssociation("employment-key", "employment",
                        "Company", "employer", sourceLower, -1,
                        "Employee", "employees", targetLower, -1,
                        List.of(qualifier), false, unique))
                .build();
    }

    private static UmlQualifier slotDomain() {
        return new UmlQualifier("slot", OclType.INTEGER, List.of(
                new OclValue.IntegerValue(BigInteger.ONE),
                new OclValue.IntegerValue(BigInteger.TWO)));
    }

    private static Snapshot qualifiedSnapshot() {
        return Snapshot.builder()
                .object("c", "Company")
                .object("e1", "Employee")
                .object("e2", "Employee")
                .link("employment", "c", "e1",
                        List.of(new QualifierValue("slot", new OclValue.IntegerValue(BigInteger.ONE))))
                .link("employment", "c", "e1",
                        List.of(new QualifierValue("slot", new OclValue.IntegerValue(BigInteger.ONE))))
                .link("employment", "c", "e2",
                        List.of(new QualifierValue("slot", new OclValue.IntegerValue(BigInteger.TWO))))
                .build();
    }

    private static Snapshot uniqueQualifiedSnapshot() {
        return Snapshot.builder()
                .object("c", "Company")
                .object("e1", "Employee")
                .object("e2", "Employee")
                .link("employment", "c", "e1",
                        List.of(new QualifierValue("slot", new OclValue.IntegerValue(BigInteger.ONE))))
                .link("employment", "c", "e2",
                        List.of(new QualifierValue("slot", new OclValue.IntegerValue(BigInteger.TWO))))
                .build();
    }

    @Test
    void booleanThreeValuedTableIsTotalAndUsesKleeneDominance() {
        assertTrue(Boolean3.tableCompleteAndSound());
        assertTrue(Boolean3.BOTTOM.isBottom());
        assertTrue(Boolean3.BOTTOM instanceof OclValue.BottomValue);
        assertSame(Boolean3.FALSE, Boolean3.and(Boolean3.FALSE, Boolean3.BOTTOM));
        assertSame(Boolean3.TRUE, Boolean3.implies(Boolean3.FALSE, Boolean3.BOTTOM));
        assertSame(Boolean3.BOTTOM, Boolean3.implies(Boolean3.TRUE, Boolean3.BOTTOM));
    }

    @Test
    void booleanBottomRemainsBottomWhenUsedAsAnIfCondition() {
        SchemaModel sm = SchemaModel.builder("m")
                .clazz(UmlClass.of("Person"))
                .attribute(new org.uet.dse.ocl2cypher.source.model.UmlAttribute(
                        "Person::flag", "flag", "Person", OclType.BOOLEAN))
                .build();
        Snapshot sn = Snapshot.builder().object("p", "Person").build();
        String source = "context Person inv BottomCondition: "
                + "if self.flag then false else true endif";

        assertEquals(List.of("p"), org.uet.dse.ocl2cypher.api.CoreOracle
                .violationsOclEq(source, sm, sn));
        var parsed = FrontendCompiler.compile(source, sm);
        assertTrue(parsed.isSuccess(), () -> parsed.diagnostics().toString());
        var doc = parsed.value().get(0);
        var lowered = CoreLowering.lower(sm, doc, doc.constraints.get(0));
        assertTrue(lowered.isSuccess(), () -> lowered.diagnostics().toString());
        var translated = QCypTranslator.translate(lowered.value());
        assertTrue(translated.isSuccess(), () -> translated.diagnostics().toString());
        GraphModel graph = GraphBuilder.build(sm, sn).value().graph();
        CoreInterpreter.Env env = new CoreInterpreter.Env();
        env.bind(lowered.value().selfVariable(),
                new OclValue.ObjectValue(OclType.clazz("Person"), "p"));
        assertTrue(org.uet.dse.ocl2cypher.qcyp.QInterpreter.evalExpr(
                sm, graph, env, translated.value().expressionBody()).isBottom());
    }

    @Test
    void reverseAndQualifiedNavigationUseTheDeclaredAssociation() {
        SchemaModel sm = qualifiedSchema(false);
        Snapshot sn = qualifiedSnapshot();
        GraphModel g = GraphBuilder.build(sm, sn).value().graph();
        OclValue.IntegerValue one = new OclValue.IntegerValue(BigInteger.ONE);

        assertEquals(List.of("e1", "e1"),
                GraphObservation.linkTargets(g, sm, "c", "employees", false, List.of(one)));
        assertEquals(List.of("c", "c"),
                GraphObservation.linkTargets(g, sm, "e1", "employer", true, List.of()));
    }

    @Test
    void bagNavigationPreservesDuplicateOccurrencesAndSetNavigationDeduplicates() {
        Snapshot sn = qualifiedSnapshot();
        GraphModel bagGraph = GraphBuilder.build(qualifiedSchema(false), sn).value().graph();
        Snapshot uniqueSn = uniqueQualifiedSnapshot();
        GraphModel setGraph = GraphBuilder.build(qualifiedSchema(true), uniqueSn).value().graph();
        assertEquals(3, GraphObservation.linkTargets(bagGraph, qualifiedSchema(false),
                "c", "employees").size());
        assertEquals(2, GraphObservation.linkTargets(setGraph, qualifiedSchema(true),
                "c", "employees").size());
    }

    @Test
    void validRepChecksQualifiedLowerBoundsForEveryFiniteKey() {
        SchemaModel sm = qualifiedSchemaWithBounds(false, 0, 1, slotDomain());
        Snapshot sn = Snapshot.builder()
                .object("c", "Company")
                .object("e1", "Employee")
                .link("employment", "c", "e1",
                        List.of(new QualifierValue("slot",
                                new OclValue.IntegerValue(BigInteger.ONE))))
                .link("employment", "c", "e1",
                        List.of(new QualifierValue("slot",
                                new OclValue.IntegerValue(BigInteger.ONE))))
                .build();

        var result = GraphBuilder.build(sm, sn);
        assertTrue(result.isFailure(), () -> result.diagnostics().toString());
        assertEquals("VALIDREP_MULTIPLICITY", result.primaryDiagnostic().code());
        assertTrue(result.primaryDiagnostic().message().contains("qualifier"));
    }

    @Test
    void validRepRequiresCompleteQualifierDomainsForPositiveLowerBounds() {
        SchemaModel sm = qualifiedSchemaWithBounds(false, 0, 1,
                UmlQualifier.typed("slot", OclType.INTEGER));
        Snapshot sn = Snapshot.builder()
                .object("c", "Company")
                .object("e1", "Employee")
                .link("employment", "c", "e1",
                        List.of(new QualifierValue("slot",
                                new OclValue.IntegerValue(BigInteger.ONE))))
                .build();

        var result = GraphBuilder.build(sm, sn);
        assertTrue(result.isFailure(), () -> result.diagnostics().toString());
        assertEquals("VALIDREP_QUALIFIER_DOMAIN_REQUIRED", result.primaryDiagnostic().code());
    }

    @Test
    void linkStableKeysAreDeterministicAndInheritanceCyclesAreRejected() {
        SchemaModel sm = qualifiedSchema(false);
        Snapshot sn = qualifiedSnapshot();
        var first = GraphBuilder.build(sm, sn).value().graph().relationships().stream()
                .map(GraphModel.Relationship::stableKey).toList();
        var second = GraphBuilder.build(sm, sn).value().graph().relationships().stream()
                .map(GraphModel.Relationship::stableKey).toList();
        assertEquals(first, second);

        Snapshot reordered = Snapshot.builder()
                .object("c", "Company")
                .object("e1", "Employee")
                .object("e2", "Employee")
                .link("employment", "c", "e2",
                        List.of(new QualifierValue("slot",
                                new OclValue.IntegerValue(BigInteger.TWO))))
                .link("employment", "c", "e1",
                        List.of(new QualifierValue("slot",
                                new OclValue.IntegerValue(BigInteger.ONE))))
                .link("employment", "c", "e1",
                        List.of(new QualifierValue("slot",
                                new OclValue.IntegerValue(BigInteger.ONE))))
                .build();
        var reorderedKeys = GraphBuilder.build(sm, reordered).value().graph().relationships()
                .stream().map(GraphModel.Relationship::stableKey).toList();
        assertEquals(first.stream().filter(k -> GraphKey.isKind(k, GraphKey.Kind.LINK))
                        .sorted().toList(),
                reorderedKeys.stream().filter(k -> GraphKey.isKind(k, GraphKey.Kind.LINK))
                        .sorted().toList());

        SchemaModel cyclic = SchemaModel.builder("cycle")
                .clazz(new UmlClass("A", "A", false, false, List.of("B")))
                .clazz(new UmlClass("B", "B", false, false, List.of("A")))
                .build();
        assertTrue(cyclic.hasInheritanceCycle());
        assertTrue(GraphBuilder.build(cyclic, Snapshot.builder().build()).isFailure());
    }

    @Test
    void iteratorMayUseTheImplicitItBinder() {
        SchemaModel sm = SchemaModel.builder("m")
                .clazz(UmlClass.of("Company"))
                .clazz(UmlClass.of("Employee"))
                .attribute(new org.uet.dse.ocl2cypher.source.model.UmlAttribute(
                        "Employee::age", "age", "Employee", OclType.INTEGER))
                .association(new UmlAssociation("employment-key", "employment",
                        "Company", "employer", 0, 1,
                        "Employee", "employees", 0, -1,
                        List.of(), false, true))
                .build();
        var parsed = FrontendCompiler.compile(
                "context Company inv Adults: self.employees->forAll(it.age >= 18)", sm);
        assertTrue(parsed.isSuccess(), () -> parsed.diagnostics().toString());
    }

    @Test
    void loweringFindsAttributesOnEveryDirectSuperclassBranch() {
        SchemaModel sm = SchemaModel.builder("m")
                .clazz(UmlClass.of("Left"))
                .clazz(UmlClass.of("Right"))
                .clazz(UmlClass.of("Child", "Left", "Right"))
                .attribute(new org.uet.dse.ocl2cypher.source.model.UmlAttribute(
                        "Right::age", "age", "Right", OclType.INTEGER))
                .build();
        var parsed = FrontendCompiler.compile(
                "context Child inv Adult: self.age >= 18", sm);
        assertTrue(parsed.isSuccess(), () -> parsed.diagnostics().toString());
        var doc = parsed.value().get(0);
        var comparison = (org.uet.dse.ocl2cypher.source.omg.OmgAs.OperationCallExp) doc.constraints.get(0).specification.bodyExpression;
        var property = (org.uet.dse.ocl2cypher.source.omg.OmgAs.PropertyCallExp) comparison.source;
        assertEquals("Right::age", property.referredProperty);
        var lowered = CoreLowering.lower(sm, doc, doc.constraints.get(0));
        assertTrue(lowered.isSuccess(), () -> lowered.diagnostics().toString());
        var coreComparison = (CoreExpr.Binary) lowered.value().body();
        var coreProperty = (CoreExpr.AttributeRead) coreComparison.left;
        assertEquals("Right", coreProperty.ownerClassKey);
    }

    @Test
    void frontendRejectsAmbiguousInheritedAttributesAndAcceptsAnOverride() {
        SchemaModel ambiguous = SchemaModel.builder("m")
                .clazz(UmlClass.of("Left"))
                .clazz(UmlClass.of("Right"))
                .clazz(UmlClass.of("Child", "Left", "Right"))
                .attribute(new org.uet.dse.ocl2cypher.source.model.UmlAttribute(
                        "Left::age", "age", "Left", OclType.INTEGER))
                .attribute(new org.uet.dse.ocl2cypher.source.model.UmlAttribute(
                        "Right::age", "age", "Right", OclType.INTEGER))
                .build();
        var rejected = FrontendCompiler.compile(
                "context Child inv Adult: self.age >= 18", ambiguous);
        assertTrue(rejected.isFailure());
        assertEquals("E_AMBIGUOUS_PROPERTY", rejected.primaryDiagnostic().code());

        SchemaModel overridden = SchemaModel.builder("m")
                .clazz(UmlClass.of("Left"))
                .clazz(UmlClass.of("Right"))
                .clazz(UmlClass.of("Child", "Left", "Right"))
                .attribute(new org.uet.dse.ocl2cypher.source.model.UmlAttribute(
                        "Left::age", "age", "Left", OclType.INTEGER))
                .attribute(new org.uet.dse.ocl2cypher.source.model.UmlAttribute(
                        "Right::age", "age", "Right", OclType.INTEGER))
                .attribute(new org.uet.dse.ocl2cypher.source.model.UmlAttribute(
                        "Child::age", "age", "Child", OclType.INTEGER))
                .build();
        var accepted = FrontendCompiler.compile(
                "context Child inv Adult: self.age >= 18", overridden);
        assertTrue(accepted.isSuccess(), () -> accepted.diagnostics().toString());
    }

    @Test
    void setAlgebraNowHasACompleteSourceToRealizationPath() {
        SchemaModel sm = SchemaModel.builder("m")
                .clazz(UmlClass.of("Person"))
                .attribute(new org.uet.dse.ocl2cypher.source.model.UmlAttribute(
                        "Person::age", "age", "Person", OclType.INTEGER))
                .build();
        var parsed = FrontendCompiler.compile(
                "context Person inv Bad: Set{1}->union(Set{2})->includes(1)", sm);
        assertTrue(parsed.isSuccess(), () -> parsed.diagnostics().toString());
        var doc = parsed.value().get(0);
        var lowered = CoreLowering.lower(sm, doc, doc.constraints.get(0));
        assertTrue(lowered.isSuccess(), () -> lowered.diagnostics().toString());
        expectNumericRejection("context Person inv Union: Set{1}->union(Set{2})->includes(1)",
                sm, Snapshot.builder().object("p", "Person").build());
        String text = realizeViolationText(
                "context Person inv Union: Set{'a'}->union(Set{'b'})->includes('a')",
                sm, Snapshot.builder().object("p", "Person").build());
        assertTrue(text.contains("reduce(`distinctAcc"), text);
    }

    @Test
    void operationCapabilityMatrixIsTotalAndFailSafe() {
        assertEquals(org.uet.dse.ocl2cypher.source.omg.OclOperation.values().length,
                CapabilityMatrix.entries().size());
        assertTrue(CapabilityMatrix.isAdmitted(
                org.uet.dse.ocl2cypher.source.omg.OclOperation.NUMERIC_ABS));
        assertTrue(CapabilityMatrix.isAdmitted(
                org.uet.dse.ocl2cypher.source.omg.OclOperation.SET_UNION));
        assertTrue(CapabilityMatrix.isAdmitted(
                org.uet.dse.ocl2cypher.source.omg.OclOperation.COLLECTION_INCLUDES));
    }

    @Test
    void includesAllExcludesAllAndIntersectionUseTypedQuantification() {
        SchemaModel sm = SchemaModel.builder("m").clazz(UmlClass.of("Person")).build();
        Snapshot sn = Snapshot.builder().object("p", "Person").build();

        String includesAll = realizeViolationText(
                "context Person inv AllPresent: Set{1, 2}->includesAll(Set{2})", sm, sn);
        String excludesAll = realizeViolationText(
                "context Person inv AllAbsent: Bag{1, 1}->excludesAll(Bag{2, 2})", sm, sn);
        expectNumericRejection(
                "context Person inv Intersect: Set{1, 2}->intersection(Set{2})->size() = 1",
                sm, sn);
        String intersection = realizeViolationText(
                "context Person inv Intersect: Set{'a', 'b'}->intersection(Set{'b'})->notEmpty()", sm, sn);

        assertTrue(includesAll.contains("all(`membershipItem"), includesAll);
        assertTrue(excludesAll.contains("all(`membershipItem"), excludesAll);
        assertTrue(intersection.contains("intersectionItem"), intersection);
        assertTrue(intersection.contains("reduce(`distinctAcc"), intersection);
    }

    @Test
    void certifiedAbsSucceedsWhileFloorAndRoundStillRequireCertificates() {
        SchemaModel sm = SchemaModel.builder("m").clazz(UmlClass.of("Person")).build();
        Snapshot sn = Snapshot.builder().object("p", "Person").build();

        String abs = realizeViolationText(
                "context Person inv Abs: (-2).abs() = 2", sm, sn);
        assertTrue(abs.contains("abs("), abs);
        expectNumericRejection(
                "context Person inv Floor: (-1.2).floor() = -2", sm, sn);
        expectNumericRejection(
                "context Person inv Round: (-1.5).round() = -1", sm, sn);
        assertEquals(List.of(), org.uet.dse.ocl2cypher.api.CoreOracle.violationsOclEq(
                "context Person inv Round: (-1.5).round() = -1", sm, sn));
    }

    @Test
    void certifiedLiteralArithmeticPreservesExpectedCoreAndQResults() {
        SchemaModel sm = SchemaModel.builder("certified-literals").clazz(UmlClass.of("Person")).build();
        Snapshot sn = Snapshot.builder().object("p", "Person").build();
        for (String body : List.of("(-7) div 3 = -2", "(-7) mod 3 = -1",
                "Bag{2, 2, -1}->sum() = 3", "Set{2, 2, -1}->sum() = 1",
                "(-2).abs() = 2", "2.min(3) = 2", "2.max(3) = 3")) {
            String source = "context Person inv Certified: " + body;
            assertEquals(List.of(), org.uet.dse.ocl2cypher.api.CoreOracle.violationsOclEq(source, sm, sn));
            var fe = FrontendCompiler.compile(source, sm);
            assertTrue(fe.isSuccess(), () -> fe.diagnostics().toString());
            var doc = fe.value().get(0);
            var core = CoreLowering.lower(sm, doc, doc.constraints.get(0));
            assertTrue(core.isSuccess(), () -> core.diagnostics().toString());
            var q = QCypTranslator.translate(core.value());
            assertTrue(q.isSuccess(), () -> q.diagnostics().toString());
            var graph = GraphBuilder.build(sm, sn);
            assertTrue(graph.isSuccess(), () -> graph.diagnostics().toString());
            assertEquals(List.of(), org.uet.dse.ocl2cypher.qcyp.QInterpreter.violations(
                    sm, graph.value().graph(), core.value(), q.value()), source);
            org.uet.dse.ocl2cypher.cypher.Neo4jCypherParserGate.assertParses(realizeViolationText(source, sm, sn));
        }
    }

    @Test
    void frontendRejectsIllTypedLogicalAndCollectionOperations() {
        SchemaModel sm = SchemaModel.builder("m")
                .clazz(UmlClass.of("Person"))
                .build();

        var badAnd = FrontendCompiler.compile(
                "context Person inv BadAnd: 1 and true", sm);
        assertTrue(badAnd.isFailure());
        assertEquals("E_TYPE", badAnd.primaryDiagnostic().code());

        var badMembership = FrontendCompiler.compile(
                "context Person inv BadMember: Set{1}->includes('x')", sm);
        assertTrue(badMembership.isFailure());
        assertEquals("E_TYPE", badMembership.primaryDiagnostic().code());

        var dotCollectionCall = FrontendCompiler.compile(
                "context Person inv BadShape: Set{1}.includes(1)", sm);
        assertTrue(dotCollectionCall.isFailure());
        assertEquals("E_INVALID_CALL_SHAPE", dotCollectionCall.primaryDiagnostic().code());

        var badSum = FrontendCompiler.compile(
                "context Person inv BadSum: Set{'x'}->sum() = 'x'", sm);
        assertTrue(badSum.isFailure());
        assertEquals("E_TYPE", badSum.primaryDiagnostic().code());

        var badCast = FrontendCompiler.compile(
                "context Person inv BadCast: Set{1}.oclAsType(Person) = self", sm);
        assertTrue(badCast.isFailure());
        assertEquals("E_TYPE", badCast.primaryDiagnostic().code());
    }

    @Test
    void frontendEnforcesQualifierArityEvenWhenNoQualifierWasWritten() {
        SchemaModel sm = qualifiedSchema(false);

        var missing = FrontendCompiler.compile(
                "context Company inv MissingQualifier: self.employees->isEmpty()", sm);
        assertTrue(missing.isFailure());
        assertEquals("E_ARITY", missing.primaryDiagnostic().code());

        var supplied = FrontendCompiler.compile(
                "context Company inv Qualified: self.employees[1]->notEmpty()", sm);
        assertTrue(supplied.isSuccess(), () -> supplied.diagnostics().toString());
    }

    @Test
    void collectionMembershipCoercesNumericElementTypesConsistently() {
        SchemaModel sm = SchemaModel.builder("m")
                .clazz(UmlClass.of("Person"))
                .build();
        var parsed = FrontendCompiler.compile(
                "context Person inv NumericMember: Set{1}->includes(1.0)", sm);
        assertTrue(parsed.isSuccess(), () -> parsed.diagnostics().toString());
        var doc = parsed.value().get(0);
        var lowered = CoreLowering.lower(sm, doc, doc.constraints.get(0));
        assertTrue(lowered.isSuccess(), () -> lowered.diagnostics().toString());
    }

    @Test
    void emptyCollectionLiteralUsesExpectedTypesButRejectsUninferredUse() {
        SchemaModel sm = SchemaModel.builder("m").clazz(UmlClass.of("Person")).build();
        Snapshot sn = Snapshot.builder().object("p", "Person").build();

        expectNumericRejection(
                "context Person inv EmptyLet: "
                + "let xs : Set(Integer) = Set{} in xs->isEmpty()",
                sm, sn);
        String letText = realizeViolationText("context Person inv EmptyLet: "
                + "let xs : Set(String) = Set{} in xs->isEmpty()", sm, sn);
        expectNumericRejection(
                "context Person inv EmptyEq: Set{} = Set{1}->reject(x | true)", sm, sn);
        // Empty literal has no uncertified elements; member 1 has an I64 certificate.
        String numericMembership = realizeViolationText(
                "context Person inv EmptyMember: Set{}->excludes(1)", sm, sn);
        assertTrue(numericMembership.contains(" IN []"), numericMembership);
        String equalityText = realizeViolationText(
                "context Person inv EmptyEq: Set{} = Set{'a'}->reject(x | true)", sm, sn);
        String membershipText = realizeViolationText(
                "context Person inv EmptyMember: Set{}->excludes('a')", sm, sn);

        assertTrue(letText.contains(" IN []"), letText);
        assertTrue(equalityText.contains(" IN []"), equalityText);
        assertTrue(membershipText.contains(" IN []"), membershipText);
        assertEquals(List.of(), org.uet.dse.ocl2cypher.api.CoreOracle.violationsOclEq(
                "context Person inv EmptyLet: "
                + "let xs : Set(Integer) = Set{} in xs->isEmpty()", sm, sn));

        var uninferred = FrontendCompiler.compile(
                "context Person inv UnknownEmpty: Set{}->isEmpty()", sm);
        assertTrue(uninferred.isFailure());
        assertEquals("E_UNINFERRED_EMPTY_COLLECTION_TYPE",
                uninferred.primaryDiagnostic().code());
    }

    @Test
    void collectionLiteralCoercesEveryElementToItsJoinType() {
        SchemaModel sm = SchemaModel.builder("m").clazz(UmlClass.of("Person")).build();
        expectRealRejection(
                "context Person inv NumericJoin: Set{1, 2.0}->includes(1.0)", sm,
                Snapshot.builder().object("p", "Person").build());
    }

    @Test
    void collectPlanCanBeMaterializedBeforeACollectionOperation() {
        SchemaModel sm = SchemaModel.builder("m")
                .clazz(UmlClass.of("Company"))
                .clazz(UmlClass.of("Employee"))
                .attribute(new org.uet.dse.ocl2cypher.source.model.UmlAttribute(
                        "Employee::age", "age", "Employee", OclType.INTEGER))
                .association(new UmlAssociation("employment-key", "employment",
                        "Company", "employer", 0, 1,
                        "Employee", "employees", 0, -1,
                        List.of(), false, true))
                .build();
        var parsed = FrontendCompiler.compile(
                "context Company inv HasAges: self.employees->collect(e | e.age)->size() > 0", sm);
        assertTrue(parsed.isSuccess(), () -> parsed.diagnostics().toString());
        var doc = parsed.value().get(0);
        var lowered = CoreLowering.lower(sm, doc, doc.constraints.get(0));
        assertTrue(lowered.isSuccess(), () -> lowered.diagnostics().toString());
        var translated = QCypTranslator.translate(lowered.value());
        assertTrue(translated.isSuccess(), () -> translated.diagnostics().toString());
        var graph = GraphBuilder.build(sm, Snapshot.builder().object("c", "Company").build());
        assertTrue(graph.isSuccess(), () -> graph.diagnostics().toString());
        var realized = Realization.realize(translated.value(), graph.value().graph(),
                CypherAst.Dialect.CYPHER_5);
        assertTrue(realized.isFailure());
        assertEquals("R_NUMERIC_CAPABILITY", realized.primaryDiagnostic().code());
        expectNumericRejection("context Company inv HasAges: self.employees->collect(e | e.age)->size() > 0",
                sm, Snapshot.builder().object("c", "Company").build());
        String text = realizeViolationText("context Company inv Collected: self.employees->collect(e | true)->notEmpty()",
                sm, Snapshot.builder().object("c", "Company").build());
        assertTrue(text.contains("__oclItems"), text);
        assertTrue(text.contains("COLLECT"), text);
    }

    @Test
    void includesAndExcludesReachRealizationThroughTheCanonicalBinaryPath() {
        SchemaModel sm = SchemaModel.builder("m")
                .clazz(UmlClass.of("Person"))
                .build();
        Snapshot sn = Snapshot.builder().object("p", "Person").build();

        String includes = realizeViolationText(
                "context Person inv Member: Set{1, 2}->includes(1)", sm, sn);
        String excludes = realizeViolationText(
                "context Person inv Absent: Bag{1, 2}->excludes(3)", sm, sn);

        assertTrue(includes.contains("any(`x"), includes);
        assertTrue(excludes.contains("any(`x"), excludes);
        assertFalse(includes.contains("any(["), includes);
        assertFalse(excludes.contains("any(["), excludes);
    }

    @Test
    void setLiteralDeduplicatesButBagLiteralKeepsOccurrences() {
        SchemaModel sm = SchemaModel.builder("m")
                .clazz(UmlClass.of("Person"))
                .build();
        Snapshot sn = Snapshot.builder().object("p", "Person").build();

        String setSizeText = realizeViolationText(
                "context Person inv SetSize: Set{1, 1}->size() = 1", sm, sn);
        String bagSizeText = realizeViolationText(
                "context Person inv BagSize: Bag{1, 1}->size() = 2", sm, sn);
        assertTrue(setSizeText.contains("reduce(`distinctAcc"), setSizeText);
        assertFalse(bagSizeText.contains("distinctAcc"), bagSizeText);
        String setText = realizeViolationText("context Person inv SetMembers: Set{1,1}->notEmpty()", sm, sn);
        String bagText = realizeViolationText("context Person inv BagMembers: Bag{1,1}->notEmpty()", sm, sn);

        assertTrue(setText.contains("reduce(`distinctAcc"), setText);
        assertTrue(setText.contains("any(`existingItem"), setText);
        assertFalse(bagText.contains("distinctAcc"), bagText);
    }

    @Test
    void collectionEqualityUsesSetExtensionalityAndBagMultiplicity() {
        SchemaModel sm = SchemaModel.builder("m")
                .clazz(UmlClass.of("Person"))
                .build();
        Snapshot sn = Snapshot.builder().object("p", "Person").build();

        String setText = realizeViolationText(
                "context Person inv SetEq: Set{1, 2} = Set{2, 1}", sm, sn);
        String bagText = realizeViolationText(
                "context Person inv BagEq: Bag{1, 1} <> Bag{1}", sm, sn);

        assertTrue(setText.contains("all(`leftMember"), setText);
        assertTrue(setText.contains("all(`rightMember"), setText);
        assertTrue(bagText.contains("all(`bagMember"), bagText);
        assertTrue(bagText.contains("size("), bagText);
    }

    @Test
    void selfIsBoundAsATaggedObjectRatherThanReadAsARawNodeValue() {
        SchemaModel sm = SchemaModel.builder("m")
                .clazz(UmlClass.of("Person"))
                .attribute(new org.uet.dse.ocl2cypher.source.model.UmlAttribute(
                        "Person::age", "age", "Person", OclType.INTEGER))
                .attribute(org.uet.dse.ocl2cypher.source.model.UmlAttribute.of("Person", "active", OclType.BOOLEAN))
                .build();
        // Integer attribute realization now succeeds; verify self-binding shape.
        String ageText = realizeViolationText("context Person inv Adult: self.age >= 18", sm,
                Snapshot.builder().object("p", "Person").build());
        assertTrue(ageText.contains("`self`.`use_id`"), ageText);
        String text = realizeViolationText(
                "context Person inv Active: self.active", sm,
                Snapshot.builder().object("p", "Person").build());

        assertTrue(text.contains("`self`.`use_id`"), text);
        assertFalse(text.contains("`self`.`__oclValue`"), text);
    }

    @Test
    void exactClassCastAcceptsAnObjectAlreadyOfTheTargetClass() {
        SchemaModel sm = SchemaModel.builder("m")
                .clazz(UmlClass.of("Person"))
                .build();
        Snapshot sn = Snapshot.builder().object("p", "Person").build();

        String text = realizeViolationText(
                "context Person inv ReflexiveCast: self.oclAsType(Person) = self", sm, sn);

        assertTrue(text.contains("ObjectInstanceOf"), text);
        assertTrue(text.contains("`Extends`*1.."), text);
        assertTrue(text.contains(" OR EXISTS"), text);
    }

    @Test
    void directQBottomCoerceDistinctAndPlanLetHaveRealizationRules() {
        SourceSpan span = SourceSpan.UNKNOWN;
        CoreDeclaration self = new CoreDeclaration(1, "self", CoreDeclaration.Kind.SELF,
                OclType.clazz("Person"));
        CoreDeclaration local = new CoreDeclaration(2, "x", CoreDeclaration.Kind.LET,
                OclType.INTEGER);
        QNode.QExpr one = new QNode.QExpr.Constant(span, OclType.INTEGER, BigInteger.ONE);
        QNode.QExpr duplicateBag = new QNode.QExpr.CollectionLiteral(span,
                CoreExpr.CollectionKind.BAG, List.of(one, one), OclType.bag(OclType.INTEGER));
        QNode.QPlan distinct = new QNode.QPlan.Distinct(span,
                new QNode.QPlan.FromCollection(span, duplicateBag));
        QNode.QExpr coerced = new QNode.QExpr.Coerce(span,
                CoreExpr.CoercionKind.INTEGER_TO_REAL, OclType.INTEGER, one, OclType.REAL);
        QNode.QPlan letPlan = new QNode.QPlan.PlanLet(span, local, one, distinct);
        QNode.QExpr materialized = new QNode.QExpr.Materialize(span, letPlan);
        QNode.QExpr size = new QNode.QExpr.Unary(span, CoreExpr.UnaryOp.COLLECTION_SIZE,
                materialized, OclType.INTEGER);
        QNode.QExpr body = new QNode.QExpr.Binary(span, CoreExpr.BinaryOp.VALUE_EQUAL,
                size, one, OclType.BOOLEAN);
        QQuery query = new QQuery(body, null, QQuery.QResultShape.IDS,
                QQuery.QueryMode.VIOLATIONS, null, "Person", self, true);
        GraphModel graph = GraphBuilder.build(
                SchemaModel.builder("m").clazz(UmlClass.of("Person")).build(),
                Snapshot.builder().object("p", "Person").build()).value().graph();

        var realized = Realization.realize(query, graph, CypherAst.Dialect.CYPHER_5);
        assertTrue(realized.isFailure());
        assertEquals("R_NUMERIC_CAPABILITY", realized.primaryDiagnostic().code());
        assertEquals(new OclValue.IntegerValue(BigInteger.ONE), QInterpreter.evalExpr(
                SchemaModel.builder("m").clazz(UmlClass.of("Person")).build(), graph,
                new CoreInterpreter.Env(), size));
        assertEquals(OclType.set(OclType.INTEGER), materialized.type);

        QQuery bottomQuery = new QQuery(new QNode.QExpr.Bottom(span, OclType.BOOLEAN), null,
                QQuery.QResultShape.IDS, QQuery.QueryMode.VIOLATIONS, null,
                "Person", self, true);
        var bottomRealized = Realization.realize(bottomQuery, graph,
                CypherAst.Dialect.CYPHER_5);
        assertTrue(bottomRealized.isSuccess(), () -> bottomRealized.diagnostics().toString());
        assertTrue(Serializer.cypherText(bottomRealized.value())
                .contains("`__oclBottom`: true"));

        QNode.QExpr coerceEquality = new QNode.QExpr.Binary(span,
                CoreExpr.BinaryOp.VALUE_EQUAL, coerced,
                new QNode.QExpr.Constant(span, OclType.REAL, java.math.BigDecimal.ONE),
                OclType.BOOLEAN);
        QQuery coerceQuery = new QQuery(coerceEquality, null, QQuery.QResultShape.IDS,
                QQuery.QueryMode.VIOLATIONS, null, "Person", self, true);
        var coerceRealized = Realization.realize(coerceQuery, graph,
                CypherAst.Dialect.CYPHER_5);
        assertTrue(coerceRealized.isFailure());
        assertEquals("R-REAL-EXACT-UNSUPPORTED", coerceRealized.primaryDiagnostic().code());
        assertEquals(Boolean3.TRUE, QInterpreter.evalExpr(
                SchemaModel.builder("m").clazz(UmlClass.of("Person")).build(), graph,
                new CoreInterpreter.Env(), coerceEquality));
    }

    @Test
    void stringPlanLetDistinctMaterializesWithSupportAndRealizes() {
        var span = SourceSpan.UNKNOWN;
        var sm = SchemaModel.builder("m").build();
        var graph = new GraphModel("m");
        var local = new CoreDeclaration(2, "x", CoreDeclaration.Kind.LET, OclType.STRING);
        var item = new QNode.QExpr.Constant(span, OclType.STRING, "a");
        var variable = new QNode.QExpr.Variable(span, local);
        var bag = new QNode.QExpr.CollectionLiteral(span, CoreExpr.CollectionKind.BAG,
                List.of(variable, variable), OclType.bag(OclType.STRING));
        var plan = new QNode.QPlan.PlanLet(span, local, item,
                new QNode.QPlan.Distinct(span, new QNode.QPlan.FromCollection(span, bag)));
        var materialized = new QNode.QExpr.Materialize(span, plan);
        assertEquals(new OclValue.SetValue(OclType.set(OclType.STRING),
                List.of(new OclValue.StringValue("a"))),
                QInterpreter.evalExpr(sm, graph, new CoreInterpreter.Env(), materialized));
        var query = new QQuery(null, plan, QQuery.QResultShape.SET,
                QQuery.QueryMode.VALUE, plan.type, null, null, false);
        var realized = Realization.realize(query, graph, CypherAst.Dialect.CYPHER_5);
        assertTrue(realized.isSuccess(), () -> realized.diagnostics().toString());
        assertTrue(Serializer.cypherText(realized.value()).contains("reduce(`distinctAcc"));
        assertEquals(CypherAst.ResultShape.SET, realized.value().contract().shape());
    }

    @Test
    void directLegacyCollectionConstructorsHaveRealizationRules() {
        SourceSpan span = SourceSpan.UNKNOWN;
        CoreDeclaration self = new CoreDeclaration(1, "self", CoreDeclaration.Kind.SELF,
                OclType.clazz("Person"));
        QNode.QExpr one = new QNode.QExpr.Constant(span, OclType.INTEGER, BigInteger.ONE);
        QNode.QExpr set = new QNode.QExpr.CollectionLiteral(span,
                CoreExpr.CollectionKind.SET, List.of(one), OclType.set(OclType.INTEGER));
        QNode.QExpr all = new QNode.QExpr.IncludesFamily(span, QNode.QKind.INCLUDES_ALL,
                set, set, OclType.BOOLEAN);
        QNode.QExpr union = new QNode.QExpr.SetAlgebra(span,
                CoreExpr.BinaryOp.SET_UNION, set, set, OclType.set(OclType.INTEGER));
        QNode.QExpr sum = new QNode.QExpr.CountFamily(span, QNode.QKind.SUM, set,
                null, OclType.INTEGER);
        GraphModel graph = GraphBuilder.build(
                SchemaModel.builder("m").clazz(UmlClass.of("Person")).build(),
                Snapshot.builder().object("p", "Person").build()).value().graph();

        QNode.QExpr unionCheck = new QNode.QExpr.Binary(span,
                CoreExpr.BinaryOp.VALUE_EQUAL, union, set, OclType.BOOLEAN);
        QNode.QExpr sumCheck = new QNode.QExpr.Binary(span,
                CoreExpr.BinaryOp.VALUE_EQUAL, sum, one, OclType.BOOLEAN);
        for (QNode.QExpr expression : List.of(all, unionCheck, sumCheck)) {
            QQuery query = new QQuery(expression, null, QQuery.QResultShape.IDS,
                    QQuery.QueryMode.VIOLATIONS, null, "Person", self, true);
            var realized = Realization.realize(query, graph, CypherAst.Dialect.CYPHER_5);
            assertEquals(Boolean3.TRUE, QInterpreter.evalExpr(
                    SchemaModel.builder("m").clazz(UmlClass.of("Person")).build(), graph,
                    new CoreInterpreter.Env(), expression));
            if (expression == all || expression == sumCheck) {
                assertTrue(realized.isSuccess(), () -> realized.diagnostics().toString());
                assertTrue(Serializer.cypherText(realized.value()).contains(
                        expression == all ? "all(" : "reduce("));
            } else {
                assertTrue(realized.isFailure());
                assertEquals("R_NUMERIC_CAPABILITY", realized.primaryDiagnostic().code());
                assertEquals(org.uet.dse.ocl2cypher.diagnostics.Stage.R, realized.primaryDiagnostic().stage());
            }
        }
    }

    @Test
    void sourceBuildersRejectSilentOverwritesAndUnknownLinkEndpoints() {
        assertThrows(IllegalArgumentException.class, () -> SchemaModel.builder("m")
                .clazz(UmlClass.of("Person"))
                .clazz(UmlClass.of("Person")));
        assertThrows(IllegalArgumentException.class, () -> SchemaModel.builder("m")
                .clazz(UmlClass.of("Person"))
                .attribute(new org.uet.dse.ocl2cypher.source.model.UmlAttribute(
                        "Person::age", "age", "Person", OclType.INTEGER))
                .attribute(new org.uet.dse.ocl2cypher.source.model.UmlAttribute(
                        "Person::age2", "age", "Person", OclType.INTEGER)));
        assertThrows(IllegalArgumentException.class, () -> SchemaModel.builder("m")
                .clazz(UmlClass.of("Person")).clazz(UmlClass.of("Employee"))
                .association(UmlAssociation.binary("a", "Person", "owner",
                        "Employee", "employees"))
                .association(UmlAssociation.binary("b", "Person", "owner",
                        "Employee", "workers")));
        assertThrows(IllegalArgumentException.class, () -> Snapshot.builder()
                .object("p", "Person").object("p", "Person"));
        assertThrows(IllegalArgumentException.class, () -> Snapshot.builder()
                .object("p", "Person")
                .attribute("p", "age", new OclValue.IntegerValue(BigInteger.ONE))
                .attribute("p", "age", new OclValue.IntegerValue(BigInteger.TWO)));
        assertThrows(IllegalArgumentException.class, () -> Snapshot.builder()
                .link("owns", "missing", "also-missing"));

        SchemaModel orphanAttribute = SchemaModel.builder("m")
                .clazz(UmlClass.of("Person"))
                .attribute(new org.uet.dse.ocl2cypher.source.model.UmlAttribute(
                        "Ghost::age", "age", "Ghost", OclType.INTEGER))
                .build();
        assertTrue(GraphBuilder.build(orphanAttribute, Snapshot.builder().build()).isFailure());

        SchemaModel invalidMultiplicity = SchemaModel.builder("m")
                .clazz(UmlClass.of("Person"))
                .association(new UmlAssociation("bad-key", "bad",
                        "Person", "owner", 2, 1,
                        "Person", "items", 0, -1,
                        List.of(), false, true))
                .build();
        var invalidBuild = GraphBuilder.build(invalidMultiplicity, Snapshot.builder().build());
        assertTrue(invalidBuild.isFailure());
        assertEquals("MM_INVALID_MULTIPLICITY", invalidBuild.primaryDiagnostic().code());
    }

    @Test
    void normalizationIsMandatoryAtRAndIdempotentByStructuralKey() {
        SourceSpan span = SourceSpan.UNKNOWN;
        QNode.QExpr one = new QNode.QExpr.Constant(span, OclType.INTEGER, BigInteger.ONE);
        QNode.QExpr set = new QNode.QExpr.CollectionLiteral(span,
                CoreExpr.CollectionKind.SET, List.of(one), OclType.set(OclType.INTEGER));
        QNode.QExpr derived = new QNode.QExpr.IncludesFamily(span, QNode.QKind.INCLUDES,
                set, one, OclType.BOOLEAN);
        QNode.QExpr once = NormQ.normalizeExpr(derived);
        QNode.QExpr twice = NormQ.normalizeExpr(once);

        assertInstanceOfBinary(once);
        assertEquals(NormQ.structuralKey(once), NormQ.structuralKey(twice));

        CoreDeclaration self = new CoreDeclaration(1, "self", CoreDeclaration.Kind.SELF,
                OclType.clazz("Person"));
        QQuery query = new QQuery(derived, null, QQuery.QResultShape.IDS,
                QQuery.QueryMode.VIOLATIONS, null, "Person", self, true);
        GraphModel graph = GraphBuilder.build(
                SchemaModel.builder("m").clazz(UmlClass.of("Person")).build(),
                Snapshot.builder().object("p", "Person").build()).value().graph();
        var realized = Realization.realize(query, graph, CypherAst.Dialect.CYPHER_5);
        assertTrue(realized.isSuccess(), () -> realized.diagnostics().toString());
        assertTrue(Serializer.cypherText(realized.value()).contains("any(`x"));
    }

    private static void assertInstanceOfBinary(QNode.QExpr expression) {
        assertTrue(expression instanceof QNode.QExpr.Binary,
                () -> "expected canonical Binary, found " + expression.getClass().getSimpleName());
    }

    @Test
    void valuePlanWholeBottomCarriesTheCollectionTypeTag() {
        SourceSpan span = SourceSpan.UNKNOWN;
        OclType setOfInteger = OclType.set(OclType.INTEGER);
        QNode.QPlan plan = new QNode.QPlan.FromCollection(span,
                new QNode.QExpr.Bottom(span, setOfInteger));
        QQuery query = new QQuery(null, plan, QQuery.QResultShape.SET,
                QQuery.QueryMode.VALUE, setOfInteger, null, null, false);
        GraphModel graph = GraphBuilder.build(
                SchemaModel.builder("m").clazz(UmlClass.of("Person")).build(),
                Snapshot.builder().build()).value().graph();

        var realized = Realization.realize(query, graph, CypherAst.Dialect.CYPHER_5);
        assertTrue(realized.isSuccess(), () -> realized.diagnostics().toString());
        String text = Serializer.cypherText(realized.value());
        assertTrue(text.contains("THEN 'Set<Integer>' ELSE 'Integer'"), text);
    }

    @Test
    void realizationScopeUsesDeclarationIdentityUnderShadowing() {
        // Integer variable now certified; verify the let-binding realization succeeds.
        assertNotNull(realizeViolationText("context Person inv Local: let x : Integer = 1 in x = 1",
                SchemaModel.builder("m").clazz(UmlClass.of("Person")).build(),
                Snapshot.builder().object("p", "Person").build()));
        SourceSpan span = SourceSpan.UNKNOWN;
        CoreDeclaration self = new CoreDeclaration(1, "self", CoreDeclaration.Kind.SELF,
                OclType.clazz("Person"));
        CoreDeclaration outer = new CoreDeclaration(2, "x", CoreDeclaration.Kind.LET,
                OclType.STRING);
        CoreDeclaration inner = new CoreDeclaration(3, "x", CoreDeclaration.Kind.LET,
                OclType.STRING);
        QNode.QExpr one = new QNode.QExpr.Constant(span, OclType.STRING, "outer");
        QNode.QExpr two = new QNode.QExpr.Constant(span, OclType.STRING, "inner");
        QNode.QExpr refersToOuter = new QNode.QExpr.Binary(span,
                CoreExpr.BinaryOp.VALUE_EQUAL,
                new QNode.QExpr.Variable(span, outer), one, OclType.BOOLEAN);
        QNode.QExpr nested = new QNode.QExpr.Let(span, inner, two, refersToOuter);
        QNode.QExpr body = new QNode.QExpr.Let(span, outer, one, nested);
        QQuery query = new QQuery(body, null, QQuery.QResultShape.IDS,
                QQuery.QueryMode.VIOLATIONS, null, "Person", self, true);
        GraphModel graph = GraphBuilder.build(
                SchemaModel.builder("m").clazz(UmlClass.of("Person")).build(),
                Snapshot.builder().object("p", "Person").build()).value().graph();

        var realized = Realization.realize(query, graph, CypherAst.Dialect.CYPHER_5);
        assertTrue(realized.isSuccess(), () -> realized.diagnostics().toString());
        String text = Serializer.cypherText(realized.value());
        assertFalse(text.contains("`__oclValue`: 'inner'"),
                "the shadowing binder is unused; an identity-correct scope must not capture it: "
                + text);
    }

    @Test
    void valueModeProducesAStableTaggedResultContract() {
        SchemaModel sm = SchemaModel.builder("m")
                .clazz(UmlClass.of("Person"))
                .attribute(new org.uet.dse.ocl2cypher.source.model.UmlAttribute(
                        "Person::age", "age", "Person", OclType.INTEGER))
                .attribute(org.uet.dse.ocl2cypher.source.model.UmlAttribute.of("Person", "active", OclType.BOOLEAN))
                .build();
        for (String attribute : List.of("age", "active")) {
            var parsed = FrontendCompiler.compileValueQuery(
                    org.uet.dse.ocl2cypher.api.ValueQueryRequest.contextual(
                            "self." + attribute, "Person"), sm);
            assertTrue(parsed.isSuccess(), () -> parsed.diagnostics().toString());
            var lowered = CoreLowering.lowerValueQuery(sm, parsed.value());
            assertTrue(lowered.isSuccess(), () -> lowered.diagnostics().toString());
            var translated = QCypTranslator.translate(lowered.value());
            assertTrue(translated.isSuccess(), () -> translated.diagnostics().toString());
            GraphModel graph = GraphBuilder.build(sm,
                    Snapshot.builder().object("p", "Person").build()).value().graph();
            var realized = Realization.realize(translated.value(), graph, CypherAst.Dialect.CYPHER_5);
            assertTrue(realized.isSuccess(), () -> attribute + ": " + realized.diagnostics().toString());
            assertEquals(CypherAst.ResultShape.SCALAR, realized.value().contract().shape());
        }
    }

    @Test
    void valueModeRejectsInvariantUnitsAndSupportsContextlessQueries() {
        SchemaModel sm = SchemaModel.builder("m").clazz(UmlClass.of("Person")).build();
        var parsed = FrontendCompiler.compile("context Person inv Always: true", sm);
        assertTrue(parsed.isSuccess(), () -> parsed.diagnostics().toString());
        var doc = parsed.value().get(0);
        var invariant = CoreLowering.lower(sm, doc, doc.constraints.get(0));
        assertTrue(invariant.isSuccess(), () -> invariant.diagnostics().toString());
        assertTrue(invariant.value() instanceof org.uet.dse.ocl2cypher.core.CoreInvariant);

        QNode.QExpr constant = new QNode.QExpr.Constant(SourceSpan.UNKNOWN,
                OclType.INTEGER, BigInteger.ONE);
        QQuery contextless = new QQuery(constant, null, QQuery.QResultShape.SCALAR,
                QQuery.QueryMode.VALUE, OclType.INTEGER, null, null, false);
        GraphModel graph = GraphBuilder.build(sm, Snapshot.builder().build()).value().graph();
        var realized = Realization.realize(contextless, graph, CypherAst.Dialect.CYPHER_5);
        assertTrue(realized.isSuccess(), () -> realized.diagnostics().toString());
        String text = Serializer.cypherText(realized.value());
        assertFalse(text.contains("MATCH"), text);
        assertTrue(text.contains("RETURN"), text);
        assertTrue(realized.value().parameters().isEmpty());
    }

    @Test
    void typedBottomTypeTestsFollowProfileTypeConformance() {
        SchemaModel sm = SchemaModel.builder("m")
                .clazz(UmlClass.of("Person"))
                .clazz(UmlClass.of("Employee", "Person"))
                .build();
        Snapshot sn = Snapshot.builder().build();
        SourceSpan span = SourceSpan.UNKNOWN;
        CoreExpr bottomEmployee = new CoreExpr.Bottom(span, OclType.clazz("Employee"));
        CoreExpr exactEmployee = new CoreExpr.TypeTest(span, CoreExpr.TypeTestKind.EXACT_TYPE,
                bottomEmployee, "Employee");
        CoreExpr kindOfPerson = new CoreExpr.TypeTest(span, CoreExpr.TypeTestKind.CONFORMS_TO,
                bottomEmployee, "Person");
        CoreExpr exactPerson = new CoreExpr.TypeTest(span, CoreExpr.TypeTestKind.EXACT_TYPE,
                bottomEmployee, "Person");
        CoreInterpreter.Env env = new CoreInterpreter.Env();

        assertEquals(Boolean3.TRUE, CoreInterpreter.eval(sm, sn, env, exactEmployee));
        assertEquals(Boolean3.TRUE, CoreInterpreter.eval(sm, sn, env, kindOfPerson));
        assertEquals(Boolean3.FALSE, CoreInterpreter.eval(sm, sn, env, exactPerson));

        GraphModel graph = GraphBuilder.build(sm, sn).value().graph();
        QNode.QExpr qBottom = new QNode.QExpr.Bottom(span, OclType.clazz("Employee"));
        assertEquals(Boolean3.TRUE, QInterpreter.evalExpr(sm, graph, env,
                new QNode.QExpr.TypeTest(span, CoreExpr.TypeTestKind.EXACT_TYPE,
                        qBottom, "Employee")));
        assertEquals(Boolean3.TRUE, QInterpreter.evalExpr(sm, graph, env,
                new QNode.QExpr.TypeTest(span, CoreExpr.TypeTestKind.CONFORMS_TO,
                        qBottom, "Person")));
        assertEquals(Boolean3.FALSE, QInterpreter.evalExpr(sm, graph, env,
                new QNode.QExpr.TypeTest(span, CoreExpr.TypeTestKind.EXACT_TYPE,
                        qBottom, "Person")));
        CoreExpr cast = new CoreExpr.TypeCast(span,
                new CoreExpr.Bottom(span, OclType.clazz("Person")), "Employee");
        assertEquals(new OclValue.BottomValue(OclType.clazz("Employee")),
                CoreInterpreter.eval(sm, sn, env, cast));
        assertEquals(Boolean3.TRUE, CoreInterpreter.eval(sm, sn, env,
                new CoreExpr.TypeTest(span, CoreExpr.TypeTestKind.EXACT_TYPE, cast, "Employee")));
        assertEquals(Boolean3.FALSE, CoreInterpreter.eval(sm, sn, env,
                new CoreExpr.TypeTest(span, CoreExpr.TypeTestKind.CONFORMS_TO,
                        new CoreExpr.Bottom(span, OclType.clazz("Person")), "Employee")));

        for (CoreExpr.TypeTestKind kind : CoreExpr.TypeTestKind.values()) {
            QNode.QExpr test = new QNode.QExpr.TypeTest(span, kind, qBottom, "Person");
            QQuery query = new QQuery(test, null, QQuery.QResultShape.SCALAR,
                    QQuery.QueryMode.VALUE, OclType.BOOLEAN, null, null, false);
            var artifact = Realization.realize(query, graph, CypherAst.Dialect.CYPHER_5);
            assertTrue(artifact.isSuccess(), () -> artifact.diagnostics().toString());
            var clause = (CypherAst.ReturnClause) artifact.value().query().clauses().get(0);
            assertNotNull(clause);
            String cypher = Serializer.cypherText(artifact.value());
            assertTrue(cypher.contains("Class:Person"), cypher);
            assertTrue(cypher.contains("__oclType"), cypher);
        }
    }

    @Test
    void typeOperationsRejectUnknownUnrelatedAndNonClassOperands() {
        var schema = SchemaModel.builder("types")
                .clazz(UmlClass.of("Person"))
                .clazz(UmlClass.of("Employee", "Person"))
                .clazz(UmlClass.of("Vehicle")).build();
        for (String operation : List.of("oclIsTypeOf", "oclIsKindOf", "oclAsType")) {
            for (String expression : List.of("self." + operation + "(Vehicle)",
                    "self." + operation + "(UnknownClass)", "1." + operation + "(Person)")) {
                var rejected = FrontendCompiler.compileValueQuery(
                        org.uet.dse.ocl2cypher.api.ValueQueryRequest.contextual(expression, "Person"), schema);
                assertFalse(rejected.isSuccess(), expression);
                assertFalse(rejected.diagnostics().isEmpty(), expression);
            }
            for (String target : List.of("Person", "Employee")) {
                var accepted = FrontendCompiler.compileValueQuery(
                        org.uet.dse.ocl2cypher.api.ValueQueryRequest.contextual(
                                "self." + operation + "(" + target + ")", "Person"), schema);
                assertTrue(accepted.isSuccess(), () -> accepted.diagnostics().toString());
                var lowered = CoreLowering.lowerValueQuery(schema, accepted.value());
                assertTrue(lowered.isSuccess(), () -> lowered.diagnostics().toString());
            }
        }
    }

    @Test
    void storedTypedBottomIsNotRetaggedAsADefinedNullPayload() {
        SchemaModel sm = SchemaModel.builder("m")
                .clazz(UmlClass.of("Person"))
                .attribute(new org.uet.dse.ocl2cypher.source.model.UmlAttribute(
                        "Person::age", "age", "Person", OclType.INTEGER))
                .attribute(org.uet.dse.ocl2cypher.source.model.UmlAttribute.of("Person", "active", OclType.BOOLEAN))
                .build();
        Snapshot sn = Snapshot.builder()
                .object("p", "Person")
                .attribute("p", "active", Boolean3.TRUE).build();
        String text = realizeViolationText(
                "context Person inv Adult: self.age >= 18", sm, sn);
        assertTrue(text.contains("__oclBottom"), text);
    }

    @Test
    void storedIntegerAttributeIsCertifiedAndRealizes() {
        var schema = SchemaModel.builder("m")
                .clazz(UmlClass.of("Person"))
                .attribute(UmlAttribute.of("Person", "age", OclType.INTEGER)).build();
        var sn = Snapshot.builder()
                .object("p", "Person")
                .attribute("p", "age", new OclValue.IntegerValue(BigInteger.valueOf(42))).build();
        var graph = GraphBuilder.build(schema, sn);
        assertTrue(graph.isSuccess(), () -> graph.diagnostics().toString());

        var fe = FrontendCompiler.compileValueQuery(
                ValueQueryRequest.contextual("self.age", "Person"), schema);
        assertTrue(fe.isSuccess(), () -> fe.diagnostics().toString());
        var lowered = CoreLowering.lowerValueQuery(schema, fe.value());
        assertTrue(lowered.isSuccess(), () -> lowered.diagnostics().toString());
        var q = QCypTranslator.translate(lowered.value());
        assertTrue(q.isSuccess(), () -> q.diagnostics().toString());
        var r = Realization.realize(q.value(), graph.value().graph(), CypherAst.Dialect.CYPHER_5);
        assertTrue(r.isSuccess(), () -> r.diagnostics().toString());
        assertEquals(CypherAst.ResultShape.SCALAR, r.value().contract().shape());
    }

    @Test
    void storedIntegerAttributeRequiresConcreteInt64PayloadCertificate() {
        var schema = SchemaModel.builder("numeric-domain")
                .clazz(UmlClass.of("Person"))
                .attribute(UmlAttribute.of("Person", "age", OclType.INTEGER)).build();
        for (BigInteger value : List.of(BigInteger.ZERO, BigInteger.valueOf(Long.MAX_VALUE),
                BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE),
                BigInteger.valueOf(Long.MIN_VALUE).subtract(BigInteger.ONE))) {
            var snapshot = Snapshot.builder().object("p", "Person")
                    .attribute("p", "age", new OclValue.IntegerValue(value)).build();
            if (value.compareTo(BigInteger.valueOf(Long.MIN_VALUE)) >= 0
                    && value.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) <= 0) {
                assertNotNull(realizeViolationText(
                        "context Person inv Positive: self.age > 0", schema, snapshot));
            } else {
                expectNumericRejection(
                        "context Person inv Positive: self.age > 0", schema, snapshot);
            }
        }
    }
}
