package org.uet.dse.neo4jtgg.experiment;

import org.uet.dse.neo4j.oclite.ast.*;

import java.util.stream.Collectors;

/** Reconstructs an admitted OCL expression for the independent USE evaluator. */
public final class OclAstSourcePrinter {
    public String print(ASTExpression expression) {
        if (expression instanceof ASTVar value) return value.name;
        if (expression instanceof ASTStringLiteral value) return "'" + value.value.replace("'", "''") + "'";
        if (expression instanceof ASTIntegerLiteral value) return Long.toString(value.value);
        if (expression instanceof ASTRealLiteral value) return Double.toString(value.value);
        if (expression instanceof ASTBooleanLiteral value) return Boolean.toString(value.value);
        if (expression instanceof ASTNullLiteral) return "null";
        if (expression instanceof ASTSetLiteral value) return "Set{" + join(value.elements) + "}";
        if (expression instanceof ASTEnumLiteral value) return value.enumTypeName + "::" + value.literalName;
        if (expression instanceof ASTNot value) return "not (" + print(value.expression) + ")";
        if (expression instanceof ASTBinary value) {
            return "(" + print(value.left) + " " + value.op + " " + print(value.right) + ")";
        }
        if (expression instanceof ASTIf value) {
            return "if " + print(value.condition) + " then " + print(value.thenBranch)
                    + " else " + print(value.elseBranch) + " endif";
        }
        if (expression instanceof ASTLet value) {
            return "let " + value.variableName + " = " + print(value.value) + " in " + print(value.body);
        }
        if (expression instanceof ASTProperty value) {
            String qualifiers = value.qualifiers.isEmpty() ? "" : "[" + join(value.qualifiers) + "]";
            return print(value.source) + "." + value.name + qualifiers;
        }
        if (expression instanceof ASTMethodCall value) {
            return print(value.source) + "." + value.methodName + "(" + join(value.args) + ")";
        }
        if (expression instanceof ASTCollectionOp value) {
            return print(value.source) + "->" + value.opName + "(" + join(value.args) + ")";
        }
        if (expression instanceof ASTIterator value) {
            String type = value.iteratorTypeName == null || value.iteratorTypeName.isBlank()
                    ? "" : " : " + value.iteratorTypeName;
            String iterator = print(value.source) + "->" + value.operation + "(" + value.iteratorName + type
                    + " | " + print(value.body) + ")";
            // OCL_val defines collect as a finite-set image. USE's native collect
            // may retain duplicate projections, so close the reference expression
            // to Set explicitly before evaluating it as the object-side oracle.
            return "collect".equals(value.operation) ? iterator + "->asSet()" : iterator;
        }
        throw new IllegalArgumentException("Unsupported AST expression for reference evaluation: "
                + expression.getClass().getSimpleName());
    }

    private String join(java.util.List<ASTExpression> expressions) {
        return expressions.stream().map(this::print).collect(Collectors.joining(", "));
    }
}
