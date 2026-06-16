package org.uet.dse.neo4jtgg.ocl.ir;

import org.tzi.use.uml.mm.MAttribute;
import org.tzi.use.uml.ocl.type.Type;
import org.uet.dse.neo4jtgg.ocl.OclMetamodelIndex;
import org.uet.dse.neo4jtgg.ocl.OclTypeBinding;

import java.util.List;

public final class OclIr {
    private OclIr() {
    }

    public record InvariantQuery(String contextClassName, String invariantName, Expression predicate) {
    }

    public sealed interface Expression permits Variable, Literal, Not, Binary, AttributeAccess,
            NavigationAccess, MethodCall, CollectionOperation, IteratorOperation, If, Let,
            NavigationPredicateCheck, NavigationCountComparison, NavigationAggregation, NavigationUniquenessCheck {
        OclTypeBinding type();
    }

    public record Variable(String name, OclTypeBinding type) implements Expression {
    }

    public record Literal(Object value, OclTypeBinding type) implements Expression {
    }

    public record Not(Expression expression, OclTypeBinding type) implements Expression {
    }

    public record If(Expression condition, Expression thenBranch, Expression elseBranch,
                     OclTypeBinding type) implements Expression {
    }

    public record Let(String variableName, Expression value, Expression body,
                      OclTypeBinding type) implements Expression {
    }

    public record Binary(String operator, Expression left, Expression right, OclTypeBinding type) implements Expression {
    }

    public record AttributeAccess(Expression source, String attributeName, Type attributeType,
                                  OclTypeBinding type, MAttribute attribute) implements Expression {
    }

    public record NavigationAccess(Expression source, OclMetamodelIndex.NavigationInfo navigation,
                                   List<Expression> qualifiers,
                                   OclTypeBinding type) implements Expression {
    }

    public record MethodCall(Expression source, String methodName, List<Expression> arguments,
                             OclTypeBinding type) implements Expression {
    }

    public record CollectionOperation(Expression source, String operationName, List<Expression> arguments,
                                      OclTypeBinding type) implements Expression {
    }

    public record IteratorOperation(Expression source, String operationName, String iteratorName,
                                    Expression body, OclTypeBinding type) implements Expression {
    }

    public record NavigationPredicateCheck(NavigationAccess navigation, String iteratorName, Expression predicate,
                                           NavigationPredicateKind kind, OclTypeBinding type) implements Expression {
    }

    public record NavigationCountComparison(NavigationAccess navigation, String iteratorName, Expression predicate,
                                            String operator, long literal, OclTypeBinding type) implements Expression {
    }

    public record NavigationAggregation(NavigationAccess navigation, String iteratorName, Expression predicate,
                                        Expression projection, String operationName,
                                        OclTypeBinding type) implements Expression {
    }

    public record NavigationUniquenessCheck(NavigationAccess navigation, String iteratorName, Expression predicate,
                                            Expression projection, OclTypeBinding type) implements Expression {
    }

    public enum NavigationPredicateKind {
        EXISTS,
        NOT_EXISTS,
        FORALL
    }
}
