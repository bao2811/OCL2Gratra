package org.uet.dse.neo4jtgg.ocl;

import org.uet.dse.neo4j.oclite.ast.ASTBinary;
import org.uet.dse.neo4j.oclite.ast.ASTCollectionLiteral;
import org.uet.dse.neo4j.oclite.ast.ASTCollectionOp;
import org.uet.dse.neo4j.oclite.ast.ASTCollectionRange;
import org.uet.dse.neo4j.oclite.ast.ASTContext;
import org.uet.dse.neo4j.oclite.ast.ASTExpression;
import org.uet.dse.neo4j.oclite.ast.ASTIf;
import org.uet.dse.neo4j.oclite.ast.ASTIntegerLiteral;
import org.uet.dse.neo4j.oclite.ast.ASTIterate;
import org.uet.dse.neo4j.oclite.ast.ASTIterator;
import org.uet.dse.neo4j.oclite.ast.ASTLet;
import org.uet.dse.neo4j.oclite.ast.ASTMethodCall;
import org.uet.dse.neo4j.oclite.ast.ASTNode;
import org.uet.dse.neo4j.oclite.ast.ASTNot;
import org.uet.dse.neo4j.oclite.ast.ASTProperty;
import org.uet.dse.neo4j.oclite.ast.ASTSetLiteral;
import org.uet.dse.neo4j.oclite.ast.ASTUnary;

import java.util.List;

/**
 * Capture-preserving source rewrites that reduce approved surface extensions
 * to the frozen OCL_val constructor algebra before certified admission.
 */
public final class OclCertifiedSurfaceNormalizer {
    private OclCertifiedSurfaceNormalizer() {
    }

    /**
     * First vertical slice:
     * {@code source->one(x | predicate)} becomes
     * {@code source->select(x | predicate)->size() = 1}.
     *
     * <p>The rewrite reuses the original iterator declaration, so it neither
     * introduces a binder nor changes capture. Generated nodes inherit the
     * source span of the surface iterator for stable diagnostics.</p>
     */
    public static ASTContext normalize(ASTContext context) {
        if (context == null) throw new IllegalArgumentException("Invariant is required.");
        ASTContext normalized = new ASTContext(
                context.className, context.invName, normalizeExpression(context.expression));
        return at(normalized, context);
    }

    static ASTExpression normalizeExpression(ASTExpression expression) {
        if (expression == null) return null;

        if (expression instanceof ASTIterator iterator) {
            ASTExpression source = normalizeExpression(iterator.source);
            ASTExpression body = normalizeExpression(iterator.body);
            if ("one".equalsIgnoreCase(iterator.operation)) {
                ASTIterator selected = at(new ASTIterator(
                        source, "select", iterator.iteratorVariables, body), iterator);
                ASTCollectionOp cardinality = at(
                        new ASTCollectionOp(selected, "size", List.of()), iterator);
                ASTIntegerLiteral one = at(new ASTIntegerLiteral(1), iterator);
                return at(new ASTBinary(cardinality, "=", one), iterator);
            }
            return at(new ASTIterator(
                    source, iterator.operation, iterator.iteratorVariables, body), iterator);
        }
        if (expression instanceof ASTBinary binary) {
            return at(new ASTBinary(normalizeExpression(binary.left), binary.op,
                    normalizeExpression(binary.right)), binary);
        }
        if (expression instanceof ASTNot not) {
            return at(new ASTNot(normalizeExpression(not.expression)), not);
        }
        if (expression instanceof ASTUnary unary) {
            return at(new ASTUnary(unary.operator, normalizeExpression(unary.expression)), unary);
        }
        if (expression instanceof ASTIf conditional) {
            return at(new ASTIf(normalizeExpression(conditional.condition),
                    normalizeExpression(conditional.thenBranch),
                    normalizeExpression(conditional.elseBranch)), conditional);
        }
        if (expression instanceof ASTLet let) {
            return at(new ASTLet(let.variableName, let.variableTypeName,
                    normalizeExpression(let.value), normalizeExpression(let.body)), let);
        }
        if (expression instanceof ASTProperty property) {
            return at(new ASTProperty(normalizeExpression(property.source), property.name,
                    normalizeAll(property.qualifiers), property.atPre), property);
        }
        if (expression instanceof ASTCollectionOp operation) {
            return at(new ASTCollectionOp(normalizeExpression(operation.source),
                    operation.opName, normalizeAll(operation.args)), operation);
        }
        if (expression instanceof ASTMethodCall method) {
            return at(new ASTMethodCall(normalizeExpression(method.source), method.methodName,
                    normalizeAll(method.args), method.atPre), method);
        }
        if (expression instanceof ASTSetLiteral set) {
            return at(new ASTSetLiteral(normalizeAll(set.elements)), set);
        }
        if (expression instanceof ASTCollectionLiteral collection) {
            return at(new ASTCollectionLiteral(collection.kind,
                    normalizeAll(collection.elements)), collection);
        }
        if (expression instanceof ASTCollectionRange range) {
            return at(new ASTCollectionRange(normalizeExpression(range.first),
                    normalizeExpression(range.last)), range);
        }
        if (expression instanceof ASTIterate iterate) {
            return at(new ASTIterate(normalizeExpression(iterate.source), iterate.operation,
                    iterate.iteratorVariables, iterate.accumulator,
                    normalizeExpression(iterate.initialValue), normalizeExpression(iterate.body)), iterate);
        }

        // Literals and variables are leaves. Unknown forms remain visible to
        // the closed-world admission policy instead of being silently erased.
        return expression;
    }

    private static List<ASTExpression> normalizeAll(List<ASTExpression> expressions) {
        return expressions.stream().map(OclCertifiedSurfaceNormalizer::normalizeExpression).toList();
    }

    private static <T extends ASTNode> T at(T target, ASTNode source) {
        target.setSourceSpan(source.sourceSpan());
        return target;
    }
}
