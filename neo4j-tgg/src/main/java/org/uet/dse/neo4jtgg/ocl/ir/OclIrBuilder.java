package org.uet.dse.neo4jtgg.ocl.ir;

import org.uet.dse.neo4jtgg.ocl.OclSemanticBinder;
import org.uet.dse.neo4jtgg.ocl.diagnostic.OclCodedUnsupportedOperationException;
import org.uet.dse.neo4jtgg.ocl.diagnostic.OclDiagnosticCode;

import java.util.ArrayList;
import java.util.List;

public class OclIrBuilder {
    public OclIr.InvariantQuery buildInvariant(OclSemanticBinder.BoundContextInvariant invariant) {
        return new OclIr.InvariantQuery(
                invariant.ast().className,
                invariant.ast().invName,
                buildExpression(invariant.expression()));
    }

    public OclIr.Expression buildExpression(OclSemanticBinder.BoundExpression expression) {
        if (expression instanceof OclSemanticBinder.BoundVariable variable) {
            return new OclIr.Variable(variable.ast().name, variable.type());
        }
        if (expression instanceof OclSemanticBinder.BoundLiteral literal) {
            return new OclIr.Literal(literal.value(), literal.type());
        }
        if (expression instanceof OclSemanticBinder.BoundNot not) {
            return new OclIr.Not(buildExpression(not.expression()), not.type());
        }
        if (expression instanceof OclSemanticBinder.BoundIf ifExpression) {
            return new OclIr.If(
                    buildExpression(ifExpression.condition()),
                    buildExpression(ifExpression.thenBranch()),
                    buildExpression(ifExpression.elseBranch()),
                    ifExpression.type());
        }
        if (expression instanceof OclSemanticBinder.BoundLet letExpression) {
            return new OclIr.Let(
                    letExpression.ast().variableName,
                    buildExpression(letExpression.value()),
                    buildExpression(letExpression.body()),
                    letExpression.type());
        }
        if (expression instanceof OclSemanticBinder.BoundBinary binary) {
            return new OclIr.Binary(binary.ast().op, buildExpression(binary.left()), buildExpression(binary.right()), binary.type());
        }
        if (expression instanceof OclSemanticBinder.BoundProperty property) {
            if (property.isAttribute()) {
                return new OclIr.AttributeAccess(
                        buildExpression(property.source()),
                        property.ast().name,
                        property.attribute().type(),
                        property.type(),
                        property.attribute());
            }
            return new OclIr.NavigationAccess(
                    buildExpression(property.source()),
                    property.navigation(),
                    buildArguments(property.qualifiers()),
                    property.type());
        }
        if (expression instanceof OclSemanticBinder.BoundMethodCall methodCall) {
            return new OclIr.MethodCall(
                    buildExpression(methodCall.source()),
                    methodCall.ast().methodName,
                    buildArguments(methodCall.arguments()),
                    methodCall.type());
        }
        if (expression instanceof OclSemanticBinder.BoundCollectionOperation collectionOperation) {
            return new OclIr.CollectionOperation(
                    buildExpression(collectionOperation.source()),
                    collectionOperation.ast().opName,
                    buildArguments(collectionOperation.arguments()),
                    collectionOperation.type());
        }
        if (expression instanceof OclSemanticBinder.BoundIterator iterator) {
            return new OclIr.IteratorOperation(
                    buildExpression(iterator.source()),
                    iterator.ast().operation,
                    iterator.ast().iteratorName,
                    buildExpression(iterator.body()),
                    iterator.type());
        }
        throw new OclCodedUnsupportedOperationException(
                OclDiagnosticCode.UNSUPPORTED_BOUND_EXPRESSION,
                "Unsupported bound expression: " + expression.getClass().getSimpleName());
    }

    private List<OclIr.Expression> buildArguments(List<OclSemanticBinder.BoundExpression> arguments) {
        List<OclIr.Expression> result = new ArrayList<>(arguments.size());
        for (OclSemanticBinder.BoundExpression argument : arguments) {
            result.add(buildExpression(argument));
        }
        return List.copyOf(result);
    }
}
