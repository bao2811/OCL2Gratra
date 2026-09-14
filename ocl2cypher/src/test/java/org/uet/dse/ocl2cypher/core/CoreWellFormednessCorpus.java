package org.uet.dse.ocl2cypher.core;

import java.math.BigInteger;
import java.util.List;
import org.uet.dse.ocl2cypher.api.FrontendCompiler;
import org.uet.dse.ocl2cypher.api.ValueQueryRequest;
import org.uet.dse.ocl2cypher.diagnostics.SourceSpan;
import org.uet.dse.ocl2cypher.runtime.OclType;
import org.uet.dse.ocl2cypher.source.model.CapabilityMatrix;
import org.uet.dse.ocl2cypher.source.model.SchemaModel;
import org.uet.dse.ocl2cypher.source.model.UmlAssociation;
import org.uet.dse.ocl2cypher.source.model.UmlAttribute;
import org.uet.dse.ocl2cypher.source.model.UmlClass;
import org.uet.dse.ocl2cypher.source.model.UmlQualifier;

/** Dependency-free N-3 corpus over production frontend, lowering and CoreValidator. */
public final class CoreWellFormednessCorpus {
    private static final SourceSpan S = SourceSpan.UNKNOWN;

    public record Counts(int acceptedProductionTrees, int rejectedMalformedTrees) {
    }

    private CoreWellFormednessCorpus() {
    }

    public static void main(String[] args) {
        Counts counts = run();
        System.out.println("PASS: N-3 Core WF corpus; " + counts.acceptedProductionTrees()
                + " production trees accepted and " + counts.rejectedMalformedTrees()
                + " malformed trees rejected");
    }

    public static Counts run() {
        SchemaModel schema = schema();
        int accepted = 0;
        for (var entry : CapabilityMatrix.entries().entrySet()) {
            if (!entry.getValue().admitted()) continue;
            requireWellFormed(schema, entry.getValue().surfaceWitness());
            accepted++;
        }
        for (String source : List.of(
                "true",
                "1",
                "1.25",
                "'x'",
                "self.age",
                "self.items",
                "self.byCode['A']",
                "self.Appointment",
                "if true then 1 else 2 endif",
                "let x : Real = 1 in x",
                "Set{1, 2}",
                "Bag{'a', 'a'}",
                "Set{1}->exists(x | x = 1)",
                "Set{1}->forAll(x | x = 1)",
                "Set{1}->select(x | true)",
                "Bag{1}->reject(x | false)",
                "Set{1}->collect(x | x)")) {
            requireWellFormed(schema, source);
            accepted++;
        }

        int rejected = 0;
        CoreDeclaration dangling = declaration(1, "x", CoreDeclaration.Kind.LET,
                OclType.INTEGER);
        rejected += requireMalformed(schema, query(new CoreExpr.Variable(S, dangling)));

        CoreDeclaration wrongLetKind = declaration(1, "x", CoreDeclaration.Kind.ITERATOR,
                OclType.INTEGER);
        rejected += requireMalformed(schema, query(new CoreExpr.Let(S, wrongLetKind,
                integer(1), new CoreExpr.Variable(S, wrongLetKind))));

        CoreDeclaration realLet = declaration(1, "x", CoreDeclaration.Kind.LET, OclType.REAL);
        rejected += requireMalformed(schema, query(new CoreExpr.Let(S, realLet,
                integer(1), new CoreExpr.Variable(S, realLet))));

        CoreDeclaration first = declaration(1, "x", CoreDeclaration.Kind.LET, OclType.INTEGER);
        CoreDeclaration second = declaration(1, "y", CoreDeclaration.Kind.LET, OclType.INTEGER);
        CoreExpr.Let firstLet = new CoreExpr.Let(S, first, integer(1),
                new CoreExpr.Variable(S, first));
        CoreExpr.Let secondLet = new CoreExpr.Let(S, second, integer(2),
                new CoreExpr.Variable(S, second));
        rejected += requireMalformed(schema, query(new CoreExpr.CollectionLiteral(S,
                CoreExpr.CollectionKind.BAG, List.of(firstLet, secondLet),
                OclType.bag(OclType.INTEGER))));

        rejected += requireMalformed(schema, query(new CoreExpr.IfExpr(S,
                integer(1), integer(1), integer(2), OclType.INTEGER)));
        rejected += requireMalformed(schema, query(new CoreExpr.Coerce(S,
                CoreExpr.CoercionKind.INTEGER_TO_REAL, OclType.STRING,
                new CoreExpr.LiteralString(S, "x"), OclType.REAL)));

        CoreDeclaration self = declaration(1, "self", CoreDeclaration.Kind.SELF,
                OclType.clazz("Person"));
        CoreExpr.Variable selfReference = new CoreExpr.Variable(S, self);
        rejected += requireMalformed(schema, contextual(self,
                new CoreExpr.AttributeRead(S, selfReference, "Person", "missing",
                        OclType.INTEGER)));
        rejected += requireMalformed(schema, contextual(self,
                new CoreExpr.Navigation(S, CoreExpr.NavKind.TO_ONE, selfReference,
                        "Works", "items", List.of(), OclType.clazz("Item"))));
        rejected += requireMalformed(schema, query(new CoreExpr.AllInstances(S, "Missing")));
        rejected += requireMalformed(schema, contextual(self,
                new CoreExpr.TypeTest(S, CoreExpr.TypeTestKind.CONFORMS_TO,
                        selfReference, "Unrelated")));

        rejected += requireMalformed(schema, query(new CoreExpr.Unary(S,
                CoreExpr.UnaryOp.BOOLEAN_NOT, integer(1), OclType.BOOLEAN)));
        rejected += requireMalformed(schema, query(new CoreExpr.Binary(S,
                CoreExpr.BinaryOp.NUMERIC_ADD, integer(1),
                new CoreExpr.LiteralReal(S, java.math.BigDecimal.ONE), OclType.REAL)));
        rejected += requireMalformed(schema, query(new CoreExpr.CollectionLiteral(S,
                CoreExpr.CollectionKind.SET, List.of(integer(1)),
                OclType.bag(OclType.INTEGER))));

        CoreDeclaration iterator = declaration(1, "x", CoreDeclaration.Kind.ITERATOR,
                OclType.STRING);
        rejected += requireMalformed(schema, query(new CoreExpr.Iterator(S,
                CoreExpr.IteratorKind.SELECT, CoreExpr.CollectionKind.SET,
                new CoreExpr.CollectionLiteral(S, CoreExpr.CollectionKind.SET,
                        List.of(integer(1)), OclType.set(OclType.INTEGER)),
                iterator, new CoreExpr.LiteralBoolean(S, true),
                OclType.set(OclType.INTEGER))));

        return new Counts(accepted, rejected);
    }

    private static SchemaModel schema() {
        return SchemaModel.builder("n3")
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

    private static void requireWellFormed(SchemaModel schema, String source) {
        var frontend = FrontendCompiler.compileValueQuery(
                ValueQueryRequest.contextual(source, "Person"), schema);
        if (frontend.isFailure()) {
            throw new AssertionError(source + " frontend: " + frontend.diagnostics());
        }
        var lowering = CoreLowering.lowerValueQuery(schema, frontend.value());
        if (lowering.isFailure()) {
            throw new AssertionError(source + " lowering: " + lowering.diagnostics());
        }
        List<CoreValidator.Error> errors = CoreValidator.validate(schema, lowering.value());
        if (!errors.isEmpty()) throw new AssertionError(source + " Core WF: " + errors);
    }

    private static int requireMalformed(SchemaModel schema, CoreQuery query) {
        List<CoreValidator.Error> errors = CoreValidator.validate(schema, query);
        if (errors.isEmpty()) throw new AssertionError("malformed Core tree was accepted: " + query);
        return 1;
    }

    private static CoreExpr.LiteralInteger integer(long value) {
        return new CoreExpr.LiteralInteger(S, BigInteger.valueOf(value));
    }

    private static CoreDeclaration declaration(int id, String name,
                                               CoreDeclaration.Kind kind, OclType type) {
        return new CoreDeclaration(id, name, kind, type);
    }

    private static CoreQuery query(CoreExpr body) {
        return new CoreQuery(null, null, body);
    }

    private static CoreQuery contextual(CoreDeclaration self, CoreExpr body) {
        return new CoreQuery("Person", self, body);
    }
}
