package org.uet.dse.ocl2cypher.qcyp;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import org.uet.dse.ocl2cypher.api.FrontendCompiler;
import org.uet.dse.ocl2cypher.api.ValueQueryRequest;
import org.uet.dse.ocl2cypher.core.CoreDeclaration;
import org.uet.dse.ocl2cypher.core.CoreExpr;
import org.uet.dse.ocl2cypher.core.CoreLowering;
import org.uet.dse.ocl2cypher.core.CoreValidator;
import org.uet.dse.ocl2cypher.diagnostics.SourceSpan;
import org.uet.dse.ocl2cypher.runtime.OclType;
import org.uet.dse.ocl2cypher.source.model.CapabilityMatrix;
import org.uet.dse.ocl2cypher.source.model.SchemaModel;
import org.uet.dse.ocl2cypher.source.model.UmlAssociation;
import org.uet.dse.ocl2cypher.source.model.UmlAttribute;
import org.uet.dse.ocl2cypher.source.model.UmlClass;
import org.uet.dse.ocl2cypher.source.model.UmlQualifier;

/** Dependency-free T-1 corpus over production frontend, Core lowering and Q translation. */
public final class QWellFormednessCorpus {
    private static final SourceSpan S = SourceSpan.UNKNOWN;

    public record Counts(int translatedTrees, int directValidTrees, int rejectedMalformedTrees) {
    }

    private QWellFormednessCorpus() {
    }

    public static Counts run() {
        SchemaModel schema = schema();
        int translated = 0;
        for (var entry : CapabilityMatrix.entries().entrySet()) {
            if (!entry.getValue().admitted()) continue;
            requireTranslatedValue(schema, entry.getValue().surfaceWitness());
            translated++;
        }
        for (String source : List.of(
                "true", "1", "1.25", "'x'", "self.age", "self.items",
                "self.byCode['A']", "self.Appointment",
                "if true then 1 else 2 endif", "let x : Real = 1 in x",
                "Set{1, 2}", "Bag{'a', 'a'}", "Set{1}->exists(x | x = 1)",
                "Set{1}->forAll(x | x = 1)", "Set{1}->select(x | true)",
                "Bag{1}->reject(x | false)", "Set{1}->collect(x | x)")) {
            requireTranslatedValue(schema, source);
            translated++;
        }
        for (String invariant : List.of(
                "context Person inv Literal: true",
                "context Person inv Attribute: self.age > 0",
                "context Person inv Navigation: self.items->forAll(i | true)")) {
            requireTranslatedInvariant(schema, invariant);
            translated++;
        }

        int direct = 0;
        QNode.QExpr set = set(integer(1), integer(2));
        direct += requireValid(value(new QNode.QExpr.IncludesFamily(S, QNode.QKind.INCLUDES,
                set, integer(1), OclType.BOOLEAN)));
        direct += requireValid(value(new QNode.QExpr.CountFamily(S, QNode.QKind.SIZE,
                set, null, OclType.INTEGER)));
        direct += requireValid(value(new QNode.QExpr.SetAlgebra(S,
                CoreExpr.BinaryOp.SET_UNION, set, set, OclType.set(OclType.INTEGER))));
        direct += requireValid(valuePlan(new QNode.QPlan.Distinct(S,
                new QNode.QPlan.FromCollection(S, set))));
        CoreDeclaration binder = declaration(1, "x", CoreDeclaration.Kind.LET, OclType.INTEGER);
        QNode.QPlan planLet = new QNode.QPlan.PlanLet(S, binder, integer(1),
                new QNode.QPlan.FromCollection(S, set(new QNode.QExpr.Variable(S, binder))));
        direct += requireValid(valuePlan(planLet));

        int rejected = 0;
        CoreDeclaration dangling = declaration(1, "x", CoreDeclaration.Kind.LET, OclType.INTEGER);
        rejected += requireMalformed(value(new QNode.QExpr.Variable(S, dangling)));
        rejected += requireMalformed(value(new QNode.QExpr.Constant(S, OclType.INTEGER, "1")));

        CoreDeclaration wrongLet = declaration(1, "x", CoreDeclaration.Kind.LET, OclType.REAL);
        rejected += requireMalformed(value(new QNode.QExpr.Let(S, wrongLet, integer(1),
                new QNode.QExpr.Variable(S, wrongLet))));
        rejected += requireMalformed(value(new QNode.QExpr.IfExpr(S, integer(1), integer(1),
                integer(2), OclType.INTEGER)));
        rejected += requireMalformed(value(new QNode.QExpr.ReadAttribute(S, integer(1),
                "Person", "age", OclType.INTEGER)));
        rejected += requireMalformed(value(new QNode.QExpr.NavigateOne(S, objectVariable(),
                "Works", "items", List.of(), OclType.INTEGER)));
        rejected += requireMalformed(value(new QNode.QExpr.Unary(S,
                CoreExpr.UnaryOp.BOOLEAN_NOT, integer(1), OclType.BOOLEAN)));
        rejected += requireMalformed(value(new QNode.QExpr.Binary(S,
                CoreExpr.BinaryOp.NUMERIC_ADD, integer(1), real("1.0"), OclType.INTEGER)));
        rejected += requireMalformed(value(new QNode.QExpr.CollectionLiteral(S,
                CoreExpr.CollectionKind.SET, List.of(integer(1)), OclType.bag(OclType.INTEGER))));
        rejected += requireMalformed(value(new QNode.QExpr.IncludesFamily(S, QNode.QKind.CONSTANT,
                set, integer(1), OclType.BOOLEAN)));
        rejected += requireMalformed(value(new QNode.QExpr.CountFamily(S, QNode.QKind.COUNT,
                set, null, OclType.INTEGER)));
        rejected += requireMalformed(value(new QNode.QExpr.SetAlgebra(S,
                CoreExpr.BinaryOp.NUMERIC_ADD, set, set, OclType.set(OclType.INTEGER))));
        rejected += requireMalformed(valuePlan(new QNode.QPlan.FromCollection(S, integer(1))));

        QNode.QPlan source = new QNode.QPlan.FromCollection(S, set);
        CoreDeclaration wrongIterator = declaration(1, "x", CoreDeclaration.Kind.ITERATOR,
                OclType.STRING);
        rejected += requireMalformed(valuePlan(new QNode.QPlan.Filter(S, source, wrongIterator,
                integer(1), true)));
        rejected += requireMalformed(valuePlan(new QNode.QPlan.Distinct(S,
                new QNode.QPlan.FromCollection(S, integer(1)))));

        return new Counts(translated, direct, rejected);
    }

    private static void requireTranslatedValue(SchemaModel schema, String source) {
        var frontend = FrontendCompiler.compileValueQuery(
                ValueQueryRequest.contextual(source, "Person"), schema);
        if (frontend.isFailure()) throw new AssertionError(source + " frontend: " + frontend.diagnostics());
        var core = CoreLowering.lowerValueQuery(schema, frontend.value());
        if (core.isFailure()) throw new AssertionError(source + " lowering: " + core.diagnostics());
        List<CoreValidator.Error> coreErrors = CoreValidator.validate(schema, core.value());
        if (!coreErrors.isEmpty()) throw new AssertionError(source + " Core WF: " + coreErrors);
        var query = QCypTranslator.translate(core.value());
        if (query.isFailure()) throw new AssertionError(source + " translation: " + query.diagnostics());
        requireValid(query.value());
    }

    private static void requireTranslatedInvariant(SchemaModel schema, String source) {
        var frontend = FrontendCompiler.compile(source, schema);
        if (frontend.isFailure()) throw new AssertionError(source + " frontend: " + frontend.diagnostics());
        var document = frontend.value().get(0);
        var core = CoreLowering.lower(schema, document, document.constraints.get(0));
        if (core.isFailure()) throw new AssertionError(source + " lowering: " + core.diagnostics());
        List<CoreValidator.Error> coreErrors = CoreValidator.validate(schema, core.value());
        if (!coreErrors.isEmpty()) throw new AssertionError(source + " Core WF: " + coreErrors);
        var query = QCypTranslator.translate(core.value());
        if (query.isFailure()) throw new AssertionError(source + " translation: " + query.diagnostics());
        requireValid(query.value());
    }

    private static int requireValid(QQuery query) {
        List<QValidator.Error> errors = QValidator.validate(query);
        if (!errors.isEmpty()) throw new AssertionError("valid Q tree rejected: " + errors);
        return 1;
    }

    private static int requireMalformed(QQuery query) {
        List<QValidator.Error> errors = QValidator.validate(query);
        if (errors.isEmpty()) throw new AssertionError("malformed Q tree accepted: " + query);
        return 1;
    }

    private static QQuery value(QNode.QExpr expression) {
        QQuery.QResultShape shape = expression.type.kind() == OclType.Kind.SET
                ? QQuery.QResultShape.SET
                : expression.type.kind() == OclType.Kind.BAG
                        ? QQuery.QResultShape.BAG : QQuery.QResultShape.SCALAR;
        return new QQuery(expression, null, shape, QQuery.QueryMode.VALUE,
                expression.type, null, null, false);
    }

    private static QQuery valuePlan(QNode.QPlan plan) {
        QQuery.QResultShape shape = plan.type.kind() == OclType.Kind.SET
                ? QQuery.QResultShape.SET
                : plan.type.kind() == OclType.Kind.BAG
                        ? QQuery.QResultShape.BAG : QQuery.QResultShape.SCALAR;
        return new QQuery(null, plan, shape, QQuery.QueryMode.VALUE,
                plan.type, null, null, false);
    }

    private static QNode.QExpr.Constant integer(long value) {
        return new QNode.QExpr.Constant(S, OclType.INTEGER, BigInteger.valueOf(value));
    }

    private static QNode.QExpr.Constant real(String value) {
        return new QNode.QExpr.Constant(S, OclType.REAL, new BigDecimal(value));
    }

    private static QNode.QExpr.CollectionLiteral set(QNode.QExpr... elements) {
        return new QNode.QExpr.CollectionLiteral(S, CoreExpr.CollectionKind.SET,
                List.of(elements), OclType.set(OclType.INTEGER));
    }

    private static QNode.QExpr.Variable objectVariable() {
        CoreDeclaration self = declaration(1, "self", CoreDeclaration.Kind.SELF,
                OclType.clazz("Person"));
        return new QNode.QExpr.Variable(S, self);
    }

    private static CoreDeclaration declaration(int id, String name,
                                               CoreDeclaration.Kind kind, OclType type) {
        return new CoreDeclaration(id, name, kind, type);
    }

    private static SchemaModel schema() {
        return SchemaModel.builder("t1")
                .clazz(UmlClass.of("Person"))
                .clazz(UmlClass.of("Item"))
                .clazz(UmlClass.of("Unrelated"))
                .clazz(new UmlClass("Appointment", "Appointment", false, true, List.of()))
                .attribute(UmlAttribute.of("Person", "age", OclType.INTEGER))
                .association(UmlAssociation.binary("Works", "Person", "owner", "Item", "items"))
                .association(new UmlAssociation("Qualified", "Qualified",
                        "Person", "qualifiedOwner", 0, 1,
                        "Item", "byCode", 0, -1,
                        List.of(UmlQualifier.typed("code", OclType.STRING)), false, true))
                .association(new UmlAssociation("Appointment", "Appointment",
                        "Person", "participant", 0, -1,
                        "Item", "subject", 0, -1, List.of(), false, true))
                .build();
    }
}
