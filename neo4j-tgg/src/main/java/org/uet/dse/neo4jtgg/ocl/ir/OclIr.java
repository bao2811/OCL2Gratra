package org.uet.dse.neo4jtgg.ocl.ir;

import org.tzi.use.uml.mm.MAttribute;
import org.tzi.use.uml.ocl.type.Type;
import org.uet.dse.neo4jtgg.ocl.OclMetamodelIndex;
import org.uet.dse.neo4jtgg.ocl.OclTypeBinding;

import java.util.List;

/**
 * Backend-independent semantic IR for the supported OCL validation subset.
 *
 * <p>This class is the current Java implementation of the proposed OCL
 * Semantic IR metamodel. The records below should be read as metamodel
 * elements rather than as Cypher templates. They describe typed OCL semantics
 * after semantic binding has resolved classes, attributes, navigation roles,
 * collection kinds, and iterator scopes.</p>
 *
 * <p>Implementation note: this file currently contains both pure semantic IR
 * nodes such as {@link CollectionOperation} and optimized graph-oriented nodes
 * such as {@link NavigationPredicateCheck}. At the research/metamodel level
 * these are treated as two different models: {@code M_SemanticIR} and
 * {@code M_OptimizedIR}. Keeping them in one Java class is a compatibility
 * choice, not a change to the formal pipeline.</p>
 *
 * <p>The scientific role of this layer is to separate OCL meaning from both
 * parser syntax and backend-specific query text. The intended preservation
 * statement is:</p>
 *
 * <pre>
 * [[ e ]]_OCL(M, rho) = [[ T_IR(e) ]]_IR(M, rho)
 * </pre>
 *
 * <p>where {@code T_IR} is implemented by {@link OclIrBuilder}.</p>
 */
public final class OclIr {
    private OclIr() {
    }

    /**
     * IR-level representation of a context invariant. The predicate evaluates
     * to a Boolean for each instance of {@code contextClassName}.
     */
    public record InvariantQuery(String contextClassName, String invariantName, Expression predicate) {
    }

    /**
     * Common supertype for both OCL Semantic IR and Optimized IR expressions.
     * The two marker subtypes below make the metamodel boundary visible in code
     * while keeping the existing API compatible during the transition.
     */
    public sealed interface Expression permits SemanticExpression, OptimizedExpression {
        OclTypeBinding type();
    }

    /**
     * Marker for expressions that still directly represent the bound OCL
     * semantic model before graph-oriented optimization.
     */
    public sealed interface SemanticExpression extends Expression permits Variable, Literal, SetLiteral, Not, Binary,
            AttributeAccess, NavigationAccess, MethodCall, CollectionOperation, IteratorOperation, If, Let {
    }

    /**
     * Marker for expressions accepted by the Cypher planning layer after
     * optimization. It intentionally includes unchanged semantic nodes because
     * not every OCL expression needs a graph-specific rewrite.
     */
    public sealed interface OptimizedExpression extends Expression permits Variable, Literal, SetLiteral, Not, Binary,
            AttributeAccess, NavigationAccess, MethodCall, CollectionOperation, IteratorOperation, If, Let,
            NavigationPredicateCheck, NavigationCountComparison, NavigationAggregation, NavigationUniquenessCheck {
    }

    public record Variable(String name, OclTypeBinding type) implements SemanticExpression, OptimizedExpression {
    }

    public record Literal(Object value, OclTypeBinding type) implements SemanticExpression, OptimizedExpression {
    }

    public record SetLiteral(List<Expression> elements, OclTypeBinding type)
            implements SemanticExpression, OptimizedExpression {
        public SetLiteral {
            elements = List.copyOf(elements);
        }
    }

    public record Not(Expression expression, OclTypeBinding type) implements SemanticExpression, OptimizedExpression {
    }

    public record If(Expression condition, Expression thenBranch, Expression elseBranch,
                     OclTypeBinding type) implements SemanticExpression, OptimizedExpression {
    }

    public record Let(String variableName, Expression value, Expression body,
                      OclTypeBinding type) implements SemanticExpression, OptimizedExpression {
    }

    public record Binary(String operator, Expression left, Expression right,
                         OclTypeBinding type) implements SemanticExpression, OptimizedExpression {
    }

    /**
     * Attribute access over a typed source object. This is distinct from
     * {@link NavigationAccess}; the semantic binder decides which one a source
     * OCL property call denotes.
     */
    public record AttributeAccess(Expression source, String attributeName, Type attributeType,
                                  OclTypeBinding type, MAttribute attribute)
            implements SemanticExpression, OptimizedExpression {
    }

    /**
     * Association navigation with resolved metamodel metadata. The navigation
     * info is what allows the Cypher backend to render Link relationships with
     * association name, source role, target role, direction, and qualifier data.
     */
    public record NavigationAccess(Expression source, OclMetamodelIndex.NavigationInfo navigation,
                                   List<Expression> qualifiers,
                                   OclTypeBinding type) implements SemanticExpression, OptimizedExpression {
    }

    public record MethodCall(Expression source, String methodName, List<Expression> arguments,
                             OclTypeBinding type) implements SemanticExpression, OptimizedExpression {
    }

    public record CollectionOperation(Expression source, OclTypeBinding sourceCollectionType,
                                      String operationName, List<Expression> arguments,
                                      OclTypeBinding type) implements SemanticExpression, OptimizedExpression {
    }

    public record IteratorOperation(Expression source, OclTypeBinding sourceCollectionType,
                                    String operationName, String iteratorName,
                                    Expression body, OclTypeBinding type) implements SemanticExpression, OptimizedExpression {
    }

    /**
     * Graph-oriented semantic form for navigation predicates such as
     * {@code nav->notEmpty()}, {@code nav->exists(x | p)}, and
     * {@code nav->forAll(x | p)} after IR optimization.
     */
    public record NavigationPredicateCheck(NavigationAccess navigation, String iteratorName, Expression predicate,
                                           NavigationPredicateKind kind,
                                           OclTypeBinding type) implements OptimizedExpression {
    }

    /**
     * Graph-oriented semantic form for navigation count comparisons, e.g.
     * {@code self.children->size() > 2}.
     */
    public record NavigationCountComparison(NavigationAccess navigation, String iteratorName, Expression predicate,
                                            String operator, long literal,
                                            OclTypeBinding type) implements OptimizedExpression {
    }

    /**
     * Semantic form for aggregate operations over a navigation result.
     */
    public record NavigationAggregation(NavigationAccess navigation, String iteratorName, Expression predicate,
                                        Expression projection, String operationName,
                                        OclTypeBinding type) implements OptimizedExpression {
    }

    /**
     * Semantic form for uniqueness checks over a navigation result.
     */
    public record NavigationUniquenessCheck(NavigationAccess navigation, String iteratorName, Expression predicate,
                                            Expression projection, OclTypeBinding type) implements OptimizedExpression {
    }

    public enum NavigationPredicateKind {
        EXISTS,
        NOT_EXISTS,
        FORALL
    }
}
