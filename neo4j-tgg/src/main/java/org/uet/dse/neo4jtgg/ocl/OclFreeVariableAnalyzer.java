package org.uet.dse.neo4jtgg.ocl;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Computes the free-variable set of a successfully bound OCL expression.
 *
 * <p>The binder already rejects unresolved names, but keeping this small
 * structural pass explicit gives the semantic model a checkable definition of
 * self-dependence: {@code self} is free in the expression unless it is hidden
 * by a lexical iterator or let binding.</p>
 */
public final class OclFreeVariableAnalyzer {
    private OclFreeVariableAnalyzer() {
    }

    public static Set<String> freeVariables(OclSemanticBinder.BoundExpression expression) {
        if (expression == null) throw new IllegalArgumentException("A bound expression is required");
        LinkedHashSet<String> result = new LinkedHashSet<>();
        collect(expression, Set.of(), result);
        return Set.copyOf(result);
    }

    public static boolean isSelfIndependent(OclSemanticBinder.BoundExpression expression) {
        return !freeVariables(expression).contains("self");
    }

    private static void collect(OclSemanticBinder.BoundExpression expression,
                                Set<String> bound, Set<String> result) {
        if (expression instanceof OclSemanticBinder.BoundVariable variable) {
            if (!bound.contains(variable.ast().name)) result.add(variable.ast().name);
            return;
        }
        if (expression instanceof OclSemanticBinder.BoundLiteral) return;
        if (expression instanceof OclSemanticBinder.BoundSetLiteral set) {
            set.elements().forEach(item -> collect(item, bound, result));
            return;
        }
        if (expression instanceof OclSemanticBinder.BoundNot not) {
            collect(not.expression(), bound, result);
            return;
        }
        if (expression instanceof OclSemanticBinder.BoundIf ifExpression) {
            collect(ifExpression.condition(), bound, result);
            collect(ifExpression.thenBranch(), bound, result);
            collect(ifExpression.elseBranch(), bound, result);
            return;
        }
        if (expression instanceof OclSemanticBinder.BoundLet let) {
            collect(let.value(), bound, result);
            LinkedHashSet<String> extended = new LinkedHashSet<>(bound);
            extended.add(let.ast().variableName);
            collect(let.body(), Set.copyOf(extended), result);
            return;
        }
        if (expression instanceof OclSemanticBinder.BoundBinary binary) {
            collect(binary.left(), bound, result);
            collect(binary.right(), bound, result);
            return;
        }
        if (expression instanceof OclSemanticBinder.BoundProperty property) {
            collect(property.source(), bound, result);
            property.qualifiers().forEach(item -> collect(item, bound, result));
            return;
        }
        if (expression instanceof OclSemanticBinder.BoundMethodCall call) {
            collect(call.source(), bound, result);
            call.arguments().forEach(item -> collect(item, bound, result));
            return;
        }
        if (expression instanceof OclSemanticBinder.BoundCollectionOperation operation) {
            collect(operation.source(), bound, result);
            operation.arguments().forEach(item -> collect(item, bound, result));
            return;
        }
        if (expression instanceof OclSemanticBinder.BoundIterator iterator) {
            collect(iterator.source(), bound, result);
            LinkedHashSet<String> extended = new LinkedHashSet<>(bound);
            extended.add(iterator.ast().iteratorName);
            collect(iterator.body(), Set.copyOf(extended), result);
            return;
        }
        throw new IllegalArgumentException("Unknown bound expression: " + expression.getClass().getName());
    }
}
