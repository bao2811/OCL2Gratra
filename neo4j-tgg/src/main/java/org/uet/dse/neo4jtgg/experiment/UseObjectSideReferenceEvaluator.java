package org.uet.dse.neo4jtgg.experiment;

import org.tzi.use.parser.ocl.OCLCompiler;
import org.tzi.use.parser.Symtable;
import org.tzi.use.uml.mm.MClass;
import org.tzi.use.uml.ocl.expr.Evaluator;
import org.tzi.use.uml.ocl.expr.Expression;
import org.tzi.use.uml.ocl.value.BooleanValue;
import org.tzi.use.uml.ocl.value.ObjectValue;
import org.tzi.use.uml.ocl.value.Value;
import org.tzi.use.uml.ocl.value.VarBindings;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MSystem;
import org.uet.dse.neo4j.oclite.ast.ASTContext;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Independent oracle backed by USE's native object-model evaluator. It never
 * reads Neo4j and therefore can reveal graph-encoding as well as query errors.
 */
public final class UseObjectSideReferenceEvaluator implements ObjectSideReferenceEvaluator {
    private final OclAstSourcePrinter sourcePrinter = new OclAstSourcePrinter();

    @Override
    public Set<String> violationIds(MSystem system, ASTContext invariant) {
        return violationIds(system, invariant, sourcePrinter.print(invariant.expression));
    }

    @Override
    public Set<String> violationIds(MSystem system, ASTContext invariant, String originalExpressionSource) {
        MClass contextClass = system.model().getClass(invariant.className);
        if (contextClass == null) {
            throw new IllegalArgumentException("Unknown invariant context class: " + invariant.className);
        }
        String source = originalExpressionSource;
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("Original OCL expression source must not be blank.");
        }
        StringWriter errors = new StringWriter();
        Symtable variables = new Symtable();
        try {
            variables.add("self", contextClass, null);
        } catch (org.tzi.use.parser.SemanticException exception) {
            throw new IllegalStateException("Cannot bind USE reference variable `self`.", exception);
        }
        Expression expression = OCLCompiler.compileExpression(
                system.model(), source, invariant.invName + ".ocl",
                new PrintWriter(errors, true), variables, contextClass, false);
        if (expression == null) {
            throw new IllegalArgumentException("USE reference evaluator rejected `" + source + "`: " + errors);
        }

        Evaluator evaluator = new Evaluator();
        Set<String> violations = new LinkedHashSet<>();
        for (MObject object : system.state().objectsOfClassAndSubClasses(contextClass)) {
            VarBindings bindings = new VarBindings();
            bindings.push("self", new ObjectValue(object.cls(), object));
            Value value = evaluator.eval(expression, system.state(), bindings);
            if (!(value instanceof BooleanValue booleanValue) || !booleanValue.isTrue()) {
                violations.add(object.name());
            }
        }
        return Set.copyOf(violations);
    }
}
