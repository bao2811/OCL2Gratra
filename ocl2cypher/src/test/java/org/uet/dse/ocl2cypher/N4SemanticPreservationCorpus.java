package org.uet.dse.ocl2cypher;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.uet.dse.ocl2cypher.api.FrontendCompiler;
import org.uet.dse.ocl2cypher.api.ValueQueryRequest;
import org.uet.dse.ocl2cypher.core.CoreInterpreter;
import org.uet.dse.ocl2cypher.core.CoreLowering;
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
import org.uet.dse.ocl2cypher.source.omg.ElaboratedSourceInterpreter;
import org.uet.dse.ocl2cypher.source.omg.OmgAs;

/** Dependency-free differential corpus for N-4: elaborated OMG-AS versus Core. */
public final class N4SemanticPreservationCorpus {

    public record Counts(int expressions, int objectEvaluations) {
    }

    private N4SemanticPreservationCorpus() {
    }

    public static void main(String[] args) {
        Counts counts = run();
        System.out.println("PASS: N-4 elaborated-source/Core differential; "
                + counts.expressions() + " expressions and "
                + counts.objectEvaluations() + " object evaluations");
    }

    public static Counts run() {
        SchemaModel schema = schema();
        Snapshot snapshot = snapshot();
        List<String> expressions = expressions();

        int evaluations = 0;
        for (String expression : expressions) {
            var frontend = FrontendCompiler.compileValueQuery(
                    ValueQueryRequest.contextual(expression, "Person"), schema);
            require(frontend.isSuccess(), expression + " frontend: " + frontend.diagnostics());
            OmgAs.ExpressionInOcl source = frontend.value();
            var lowered = CoreLowering.lowerValueQuery(schema, source);
            require(lowered.isSuccess(), expression + " lowering: " + lowered.diagnostics());

            for (String identity : List.of("p1", "p2")) {
                OclValue self = new OclValue.ObjectValue(OclType.clazz("Person"), identity);
                ElaboratedSourceInterpreter.Env sourceEnvironment =
                        new ElaboratedSourceInterpreter.Env();
                sourceEnvironment.bind(source.contextVariable, self);
                CoreInterpreter.Env coreEnvironment = new CoreInterpreter.Env();
                coreEnvironment.bind(lowered.value().selfVariable(), self);

                OclValue sourceValue = ElaboratedSourceInterpreter.eval(schema, snapshot,
                        sourceEnvironment, source.bodyExpression);
                OclValue coreValue = CoreInterpreter.evalUnit(schema, snapshot,
                        lowered.value(), coreEnvironment);
                require(sourceValue.equals(coreValue), expression + " on " + identity
                        + ": source=" + sourceValue + ", core=" + coreValue);
                evaluations++;
            }
        }

        assertBoundaryValues(schema, snapshot);
        return new Counts(expressions.size(), evaluations);
    }

    static List<String> expressions() {
        List<String> expressions = new ArrayList<>();
        CapabilityMatrix.entries().values().stream()
                .filter(CapabilityMatrix.Capability::admitted)
                .map(CapabilityMatrix.Capability::surfaceWitness)
                .forEach(expressions::add);
        expressions.addAll(List.of(
                "self.age",
                "self.active",
                "self.items",
                "self.primaryItem",
                "self.byCode['A']",
                "self.Appointment",
                "if self.active then self.age else 0 endif",
                "let x : Real = self.age in x",
                "let x = 1 in let x = 2 in x",
                "Set{1, 1, 2}",
                "Bag{1, 1, 2}",
                "Set{1, 2}->select(x | x > 1)",
                "Bag{1, 2}->reject(x | x = 1)",
                "Set{1, 2}->exists(x | x = 2)",
                "Set{1, 2}->forAll(x | x > 0)",
                "Set{1, 2}->collect(x | x + 1)",
                // Regression: not(includesAll) is not excludesAll.
                "Set{1, 2}->excludesAll(Set{2, 3})",
                "self.active and false",
                "self.primaryItem.oclIsKindOf(Item)",
                "self.primaryItem.oclAsType(Item) = self.primaryItem"));
        return List.copyOf(expressions);
    }

    private static void assertBoundaryValues(SchemaModel schema, Snapshot snapshot) {
        OclValue overlap = evaluateSource(schema, snapshot,
                "Set{1, 2}->excludesAll(Set{2, 3})", "p1");
        require(overlap.equals(Boolean3.FALSE),
                "overlapping collections must not satisfy excludesAll");
        OclValue missingBoolean = evaluateSource(schema, snapshot,
                "self.active and false", "p2");
        require(missingBoolean.equals(Boolean3.FALSE),
                "bottom and false must be false in Boolean3");
        OclValue missingToOne = evaluateSource(schema, snapshot,
                "self.primaryItem", "p2");
        require(missingToOne.equals(new OclValue.BottomValue(OclType.clazz("Item"))),
                "missing to-one navigation must be typed bottom");
    }

    private static OclValue evaluateSource(SchemaModel schema, Snapshot snapshot,
                                           String expression, String identity) {
        var frontend = FrontendCompiler.compileValueQuery(
                ValueQueryRequest.contextual(expression, "Person"), schema);
        require(frontend.isSuccess(), expression + " frontend: " + frontend.diagnostics());
        ElaboratedSourceInterpreter.Env environment = new ElaboratedSourceInterpreter.Env();
        environment.bind(frontend.value().contextVariable,
                new OclValue.ObjectValue(OclType.clazz("Person"), identity));
        return ElaboratedSourceInterpreter.eval(schema, snapshot, environment,
                frontend.value().bodyExpression);
    }

    static SchemaModel schema() {
        return SchemaModel.builder("n4")
                .clazz(UmlClass.of("Person"))
                .clazz(UmlClass.of("Employee", "Person"))
                .clazz(UmlClass.of("Item"))
                .clazz(new UmlClass("Appointment", "Appointment", false, true, List.of()))
                .attribute(UmlAttribute.of("Person", "age", OclType.INTEGER))
                .attribute(UmlAttribute.of("Person", "active", OclType.BOOLEAN))
                .association(UmlAssociation.binary("Owns", "Person", "owner",
                        "Item", "items"))
                .association(new UmlAssociation("Primary", "Primary",
                        "Person", "primaryOwner", 0, 1,
                        "Item", "primaryItem", 0, 1, List.of(), true, false))
                .association(new UmlAssociation("Qualified", "Qualified",
                        "Person", "qualifiedOwner", 0, 1,
                        "Item", "byCode", 0, -1,
                        List.of(UmlQualifier.typed("code", OclType.STRING)), false, true))
                .association(new UmlAssociation("Appointment", "Appointment",
                        "Person", "participant", 0, -1,
                        "Item", "subject", 0, -1, List.of(), false, true))
                .build();
    }

    static Snapshot snapshot() {
        return Snapshot.builder()
                .object("p1", "Employee")
                .attribute("p1", "age", integer(42))
                .attribute("p1", "active", Boolean3.TRUE)
                .object("p2", "Person")
                .object("i1", "Item")
                .object("i2", "Item")
                .object("a1", "Appointment")
                .link("Owns", "p1", "i1")
                .link("Owns", "p1", "i2")
                .link("Primary", "p1", "i1")
                .link("Qualified", "p1", "i2",
                        List.of(new QualifierValue("code",
                                new OclValue.StringValue("A"))))
                .associationClassLink("a1", "Appointment", "p1", "i1")
                .build();
    }

    private static OclValue integer(long value) {
        return new OclValue.IntegerValue(BigInteger.valueOf(value));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
