package org.uet.dse.neo4jtgg.ocl;

import org.tzi.use.uml.mm.MAttribute;
import org.tzi.use.uml.mm.MOperation;
import org.tzi.use.uml.ocl.type.Type;
import org.uet.dse.neo4j.oclite.ast.ASTBinary;
import org.uet.dse.neo4j.oclite.ast.ASTBooleanLiteral;
import org.uet.dse.neo4j.oclite.ast.ASTCollectionOp;
import org.uet.dse.neo4j.oclite.ast.ASTContext;
import org.uet.dse.neo4j.oclite.ast.ASTExpression;
import org.uet.dse.neo4j.oclite.ast.ASTIf;
import org.uet.dse.neo4j.oclite.ast.ASTIntegerLiteral;
import org.uet.dse.neo4j.oclite.ast.ASTIterator;
import org.uet.dse.neo4j.oclite.ast.ASTLet;
import org.uet.dse.neo4j.oclite.ast.ASTMethodCall;
import org.uet.dse.neo4j.oclite.ast.ASTNot;
import org.uet.dse.neo4j.oclite.ast.ASTNullLiteral;
import org.uet.dse.neo4j.oclite.ast.ASTOperationConstraint;
import org.uet.dse.neo4j.oclite.ast.ASTProperty;
import org.uet.dse.neo4j.oclite.ast.ASTRealLiteral;
import org.uet.dse.neo4j.oclite.ast.ASTStringLiteral;
import org.uet.dse.neo4j.oclite.ast.ASTVar;
import org.uet.dse.neo4jtgg.ocl.diagnostic.OclCodedUnsupportedOperationException;
import org.uet.dse.neo4jtgg.ocl.diagnostic.OclDiagnosticCode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

public class OclSemanticBinder {
    private final OclMetamodelIndex metamodelIndex;

    public OclSemanticBinder(OclMetamodelIndex metamodelIndex) {
        this.metamodelIndex = metamodelIndex;
    }

    public BoundContextInvariant bindContext(ASTContext context) {
        return bindContext(context, Map.of());
    }

    public BoundContextInvariant bindContext(ASTContext context, Map<String, OclTypeBinding> additionalVariables) {
        metamodelIndex.requireClass(context.className);
        Scope scope = new Scope();
        scope.enter("self", OclTypeBinding.node(context.className));
        for (Map.Entry<String, OclTypeBinding> entry : additionalVariables.entrySet()) {
            scope.enter(entry.getKey(), entry.getValue());
        }
        BoundExpression expression = bind(context.expression, scope);
        return new BoundContextInvariant(context, expression);
    }

    public BoundContextInvariant bindOperationConstraint(ASTOperationConstraint constraint) {
        if (!"pre".equalsIgnoreCase(constraint.constraintKind)
                && !"post".equalsIgnoreCase(constraint.constraintKind)) {
            throw new OclCodedUnsupportedOperationException(
                    OclDiagnosticCode.UNSUPPORTED_RULE_KIND,
                    "Only operation preconditions and the current subset of postconditions are bindable on the current operation path.");
        }
        ASTContext syntheticContext = new ASTContext(constraint.className, constraint.ruleName, constraint.expression);
        Map<String, OclTypeBinding> additionalVariables = resolveOperationParameters(constraint);
        if ("post".equalsIgnoreCase(constraint.constraintKind)) {
            OclTypeBinding resultBinding = resolveOperationResultBinding(constraint);
            if (resultBinding != null) {
                additionalVariables.put("result", resultBinding);
            }
        }
        return bindContext(syntheticContext, additionalVariables);
    }

    public BoundExpression bind(ASTExpression expression, Scope scope) {
        if (expression instanceof ASTVar var) {
            OclTypeBinding variableType = scope.lookup(var.name);
            if (variableType != null) {
                return new BoundVariable(var, variableType);
            }
            if (metamodelIndex.getClassInfo(var.name) != null) {
                return new BoundVariable(var, OclTypeBinding.classReference(var.name));
            }
            throw new OclCodedUnsupportedOperationException(
                    OclDiagnosticCode.UNSUPPORTED_FREE_VARIABLE,
                    "Unsupported free variable: " + var.name);
        }

        if (expression instanceof ASTStringLiteral literal) {
            return new BoundLiteral(literal, OclTypeBinding.scalar("String"), literal.value);
        }
        if (expression instanceof ASTIntegerLiteral literal) {
            return new BoundLiteral(literal, OclTypeBinding.scalar("Integer"), literal.value);
        }
        if (expression instanceof ASTRealLiteral literal) {
            return new BoundLiteral(literal, OclTypeBinding.scalar("Real"), literal.value);
        }
        if (expression instanceof ASTBooleanLiteral literal) {
            return new BoundLiteral(literal, OclTypeBinding.scalar("Boolean"), literal.value);
        }
        if (expression instanceof ASTNullLiteral) {
            return new BoundLiteral(expression, OclTypeBinding.scalar("Void"), null);
        }

        if (expression instanceof ASTNot not) {
            return new BoundNot(not, bind(not.expression, scope));
        }

        if (expression instanceof ASTIf ifExpression) {
            BoundExpression condition = bind(ifExpression.condition, scope);
            BoundExpression thenBranch = bind(ifExpression.thenBranch, scope);
            BoundExpression elseBranch = bind(ifExpression.elseBranch, scope);
            return new BoundIf(ifExpression, condition, thenBranch, elseBranch,
                    inferIfType(condition.type(), thenBranch.type(), elseBranch.type()));
        }

        if (expression instanceof ASTLet letExpression) {
            BoundExpression value = bind(letExpression.value, scope);
            scope.enter(letExpression.variableName, value.type());
            BoundExpression body = bind(letExpression.body, scope);
            scope.exit();
            return new BoundLet(letExpression, value, body, body.type());
        }

        if (expression instanceof ASTBinary binary) {
            BoundExpression left = bind(binary.left, scope);
            BoundExpression right = bind(binary.right, scope);
            return new BoundBinary(binary, left, right, inferBinaryType(binary.op, left.type(), right.type()));
        }

        if (expression instanceof ASTProperty property) {
            BoundExpression source = bind(property.source, scope);
            if (source.type().isCollection()) {
                return bindCollectionPropertyProjection(property, source, scope);
            }
            if (!source.type().isNode()) {
                throw new OclCodedUnsupportedOperationException(
                        OclDiagnosticCode.INVALID_PROPERTY_SOURCE,
                        "Property access requires node source: " + property.name);
            }
            return bindNodePropertyAccess(property, source);
        }

        if (expression instanceof ASTMethodCall methodCall) {
            BoundExpression source = bind(methodCall.source, scope);
            List<BoundExpression> arguments = bindArguments(methodCall.args, scope);
            return new BoundMethodCall(methodCall, source, arguments, inferMethodType(methodCall.methodName, source.type()));
        }

        if (expression instanceof ASTCollectionOp collectionOp) {
            BoundExpression source = bind(collectionOp.source, scope);
            List<BoundExpression> arguments = bindArguments(collectionOp.args, scope);
            return new BoundCollectionOperation(collectionOp, source, arguments,
                    inferCollectionOpType(collectionOp.opName, source.type(), arguments));
        }

        if (expression instanceof ASTIterator iterator) {
            BoundExpression source = bind(iterator.source, scope);
            if (!source.type().isCollection()) {
                throw new OclCodedUnsupportedOperationException(
                        OclDiagnosticCode.INVALID_ITERATOR_SOURCE,
                        "Iterator source must be a collection: " + iterator.operation);
            }
            validateIteratorType(iterator, source.type().elementType());
            scope.enter(iterator.iteratorName, source.type().elementType());
            BoundExpression body = bind(iterator.body, scope);
            scope.exit();
            return new BoundIterator(iterator, source, body, inferIteratorType(iterator.operation, source.type(), body.type()));
        }

        throw new OclCodedUnsupportedOperationException(
                OclDiagnosticCode.UNSUPPORTED_AST_NODE,
                "Unsupported AST node: " + expression.getClass().getSimpleName());
    }

    private List<BoundExpression> bindArguments(List<ASTExpression> arguments, Scope scope) {
        List<BoundExpression> result = new ArrayList<>(arguments.size());
        for (ASTExpression argument : arguments) {
            result.add(bind(argument, scope));
        }
        return List.copyOf(result);
    }

    private BoundExpression bindCollectionPropertyProjection(ASTProperty property, BoundExpression source, Scope scope) {
        OclTypeBinding elementType = source.type().elementType();
        if (!elementType.isNode()) {
            throw new OclCodedUnsupportedOperationException(
                    OclDiagnosticCode.INVALID_PROPERTY_SOURCE,
                    "Property access requires node source: " + property.name);
        }

        String iteratorName = "__proj_" + property.name;
        Scope projectionScope = new Scope(scope);
        projectionScope.enter(iteratorName, elementType);
        ASTProperty projectedPropertyAst = new ASTProperty(new ASTVar(iteratorName), property.name);
        BoundExpression body = bind(projectedPropertyAst, projectionScope);
        projectionScope.exit();

        if (body.type().isCollection()) {
            throw new OclCodedUnsupportedOperationException(
                    OclDiagnosticCode.INVALID_PROPERTY_SOURCE,
                    "Property projection over collection-valued results is not supported yet: " + property.name);
        }

        ASTIterator collectAst = new ASTIterator(property.source, "collect", iteratorName, projectedPropertyAst);
        return new BoundIterator(collectAst, source, body, inferIteratorType("collect", source.type(), body.type()));
    }

    private BoundExpression bindNodePropertyAccess(ASTProperty property, BoundExpression source) {
        MAttribute attribute = metamodelIndex.resolveAttribute(source.type().typeName(), property.name);
        if (attribute != null) {
            if (attribute.type().isKindOfCollection(Type.VoidHandling.EXCLUDE_VOID)) {
                throw new OclCodedUnsupportedOperationException(
                        OclDiagnosticCode.COLLECTION_VALUED_ATTRIBUTE_UNSUPPORTED,
                        "Collection-valued attributes are not supported yet: " + property.name);
            }
            return new BoundProperty(property, source, OclTypeBinding.scalar(attribute.type().shortName()), attribute, null);
        }

        OclMetamodelIndex.NavigationInfo navigation = metamodelIndex.resolveNavigation(source.type().typeName(), property.name);
        if (navigation != null) {
            if (!navigation.supportsDirectCypherNavigation()) {
                throw new OclCodedUnsupportedOperationException(navigation.unsupportedCode(), navigation.unsupportedReason());
            }
            return new BoundProperty(property, source, navigation.resultBinding(), null, navigation);
        }

        throw new OclCodedUnsupportedOperationException(
                OclDiagnosticCode.UNKNOWN_PROPERTY,
                "Unknown property or navigation: " + property.name);
    }

    private OclTypeBinding inferBinaryType(String operator, OclTypeBinding left, OclTypeBinding right) {
        return switch (operator) {
            case "=", "<>", "and", "or", "implies", ">", "<", ">=", "<=" -> OclTypeBinding.scalar("Boolean");
            case "+", "-", "*", "/" -> {
                if ("Real".equals(left.typeName()) || "Real".equals(right.typeName()) || "/".equals(operator)) {
                    yield OclTypeBinding.scalar("Real");
                }
                yield left;
            }
            default -> throw new OclCodedUnsupportedOperationException(
                    OclDiagnosticCode.UNSUPPORTED_OPERATOR,
                    "Unsupported operator: " + operator);
        };
    }

    private OclTypeBinding inferIfType(OclTypeBinding conditionType, OclTypeBinding thenType, OclTypeBinding elseType) {
        if (!"Boolean".equals(conditionType.typeName()) || conditionType.isCollection()) {
            throw new OclCodedUnsupportedOperationException(
                    OclDiagnosticCode.INVALID_IF_CONDITION,
                    "if condition must be Boolean.");
        }
        if ("Void".equals(thenType.typeName())) {
            return elseType;
        }
        if ("Void".equals(elseType.typeName())) {
            return thenType;
        }
        if (thenType.equals(elseType)) {
            return thenType;
        }
        throw new OclCodedUnsupportedOperationException(
                OclDiagnosticCode.INCOMPATIBLE_IF_BRANCH_TYPES,
                "if branches must have compatible types: " + thenType.typeName() + " vs " + elseType.typeName());
    }

    private OclTypeBinding inferMethodType(String methodName, OclTypeBinding sourceType) {
        if ("allInstances".equalsIgnoreCase(methodName)) {
            if (!sourceType.isClassReference()) {
                throw new OclCodedUnsupportedOperationException(
                        OclDiagnosticCode.INVALID_METHOD_RECEIVER,
                        "allInstances() must be called on a class name.");
            }
            return OclTypeBinding.nodeCollection(sourceType.typeName(), OclTypeBinding.CollectionKind.SET);
        }
        if ("split".equalsIgnoreCase(methodName)) {
            return OclTypeBinding.scalarCollection("String", OclTypeBinding.CollectionKind.SEQUENCE);
        }
        if ("isDefined".equalsIgnoreCase(methodName) || "isUndefined".equalsIgnoreCase(methodName)) {
            return OclTypeBinding.scalar("Boolean");
        }
        throw new OclCodedUnsupportedOperationException(
                OclDiagnosticCode.UNSUPPORTED_METHOD_CALL,
                "Unsupported method call: " + methodName);
    }

    private OclTypeBinding inferCollectionOpType(String opName, OclTypeBinding sourceType, List<BoundExpression> arguments) {
        return switch (opName) {
            case "size", "count" -> OclTypeBinding.scalar("Integer");
            case "isEmpty", "notEmpty", "includes", "excludes", "includesAll", "excludesAll" -> OclTypeBinding.scalar("Boolean");
            case "union" -> inferUnionType(sourceType, arguments);
            case "intersection" -> inferIntersectionType(sourceType, arguments);
            case "flatten" -> inferFlattenType(sourceType);
            case "asSet" -> sourceType.withCollectionKind(OclTypeBinding.CollectionKind.SET);
            case "asOrderedSet" -> sourceType.withCollectionKind(OclTypeBinding.CollectionKind.ORDERED_SET);
            case "at", "first", "last" -> inferPositionalAccessType(sourceType, opName);
            default -> throw new OclCodedUnsupportedOperationException(
                    OclDiagnosticCode.UNSUPPORTED_COLLECTION_OPERATION,
                    "Unsupported collection operation: " + opName);
        };
    }

    private OclTypeBinding inferIteratorType(String operation, OclTypeBinding sourceType, OclTypeBinding bodyType) {
        return switch (operation.toLowerCase()) {
            case "select" -> sourceType;
            case "exists", "forall", "one" -> OclTypeBinding.scalar("Boolean");
            case "any" -> sourceType.elementType();
            case "collect" -> inferCollectType(sourceType, bodyType);
            default -> throw new OclCodedUnsupportedOperationException(
                    OclDiagnosticCode.UNSUPPORTED_ITERATOR,
                    "Unsupported iterator: " + operation);
        };
    }

    private OclTypeBinding inferCollectType(OclTypeBinding sourceType, OclTypeBinding bodyType) {
        OclTypeBinding.CollectionKind targetKind = sourceType.isOrderedCollection()
                ? OclTypeBinding.CollectionKind.SEQUENCE
                : OclTypeBinding.CollectionKind.BAG;
        return bodyType.isNode()
                ? OclTypeBinding.nodeCollection(bodyType.typeName(), targetKind)
                : OclTypeBinding.scalarCollection(bodyType.typeName(), targetKind);
    }

    private OclTypeBinding inferUnionType(OclTypeBinding sourceType, List<BoundExpression> arguments) {
        OclTypeBinding argumentType = requireSingleCollectionArgument("union", arguments);
        OclTypeBinding.CollectionKind resultKind = switch (sourceType.collectionKind()) {
            case SET -> argumentType.collectionKind() == OclTypeBinding.CollectionKind.BAG
                    ? OclTypeBinding.CollectionKind.BAG
                    : OclTypeBinding.CollectionKind.SET;
            case BAG -> OclTypeBinding.CollectionKind.BAG;
            case SEQUENCE -> OclTypeBinding.CollectionKind.SEQUENCE;
            case ORDERED_SET -> argumentType.collectionKind() == OclTypeBinding.CollectionKind.ORDERED_SET
                    ? OclTypeBinding.CollectionKind.ORDERED_SET
                    : OclTypeBinding.CollectionKind.SET;
            case COLLECTION, NONE -> sourceType.collectionKind();
        };
        return sourceType.withCollectionKind(resultKind);
    }

    private OclTypeBinding inferIntersectionType(OclTypeBinding sourceType, List<BoundExpression> arguments) {
        OclTypeBinding argumentType = requireSingleCollectionArgument("intersection", arguments);
        OclTypeBinding.CollectionKind resultKind = switch (sourceType.collectionKind()) {
            case SET -> OclTypeBinding.CollectionKind.SET;
            case BAG -> argumentType.collectionKind() == OclTypeBinding.CollectionKind.SET
                    ? OclTypeBinding.CollectionKind.SET
                    : OclTypeBinding.CollectionKind.BAG;
            case SEQUENCE -> OclTypeBinding.CollectionKind.BAG;
            case ORDERED_SET -> argumentType.collectionKind() == OclTypeBinding.CollectionKind.ORDERED_SET
                    ? OclTypeBinding.CollectionKind.ORDERED_SET
                    : OclTypeBinding.CollectionKind.SET;
            case COLLECTION, NONE -> sourceType.collectionKind();
        };
        return sourceType.withCollectionKind(resultKind);
    }

    private OclTypeBinding requireSingleCollectionArgument(String operationName, List<BoundExpression> arguments) {
        if (arguments.size() != 1 || !arguments.get(0).type().isCollection()) {
            throw new OclCodedUnsupportedOperationException(
                    OclDiagnosticCode.INVALID_COLLECTION_ARGUMENT,
                    operationName + "() requires a single collection argument.");
        }
        return arguments.get(0).type();
    }

    private OclTypeBinding inferFlattenType(OclTypeBinding sourceType) {
        return switch (sourceType.collectionKind()) {
            case SET, BAG, SEQUENCE, ORDERED_SET, COLLECTION -> sourceType;
            case NONE -> throw new OclCodedUnsupportedOperationException(
                    OclDiagnosticCode.INVALID_COLLECTION_SOURCE,
                    "flatten() requires a collection source.");
        };
    }

    private OclTypeBinding inferPositionalAccessType(OclTypeBinding sourceType, String operationName) {
        if (!sourceType.isCollection()) {
            throw new OclCodedUnsupportedOperationException(
                    OclDiagnosticCode.INVALID_COLLECTION_SOURCE,
                    operationName + "() requires a collection source.");
        }
        return switch (sourceType.collectionKind()) {
            case SEQUENCE, ORDERED_SET -> sourceType.elementType();
            case SET, BAG, COLLECTION -> throw new OclCodedUnsupportedOperationException(
                    OclDiagnosticCode.UNORDERED_POSITIONAL_ACCESS,
                    operationName + "() is only supported on ordered collections (Sequence/OrderedSet).");
            case NONE -> throw new OclCodedUnsupportedOperationException(
                    OclDiagnosticCode.INVALID_COLLECTION_SOURCE,
                    operationName + "() requires a collection source.");
        };
    }

    private void validateIteratorType(ASTIterator iterator, OclTypeBinding elementType) {
        if (iterator.iteratorTypeName == null || iterator.iteratorTypeName.isBlank()) {
            return;
        }
        if (!iterator.iteratorTypeName.equals(elementType.typeName())) {
            throw new OclCodedUnsupportedOperationException(
                    OclDiagnosticCode.ITERATOR_TYPE_MISMATCH,
                    "Iterator type mismatch: expected " + elementType.typeName() + " but found " + iterator.iteratorTypeName);
        }
    }

    private Map<String, OclTypeBinding> resolveOperationParameters(ASTOperationConstraint constraint) {
        Map<String, OclTypeBinding> bindings = new LinkedHashMap<>();
        MOperation operation = metamodelIndex.resolveOperation(constraint.className, constraint.operationName,
                constraint.parameterNames.size());
        for (int index = 0; index < constraint.parameterNames.size(); index++) {
            String parameterName = constraint.parameterNames.get(index);
            if (operation != null && operation.paramList().size() > index) {
                Type parameterType = operation.paramList().varDecl(index).type();
                bindings.put(parameterName, metamodelIndex.toBinding(parameterType, parameterType != null ? parameterType.shortName() : "OclAny"));
            } else {
                bindings.put(parameterName, OclTypeBinding.scalar("OclAny"));
            }
        }
        return bindings;
    }

    private OclTypeBinding resolveOperationResultBinding(ASTOperationConstraint constraint) {
        MOperation operation = metamodelIndex.resolveOperation(constraint.className, constraint.operationName,
                constraint.parameterNames.size());
        if (operation == null || operation.resultType() == null) {
            return null;
        }
        return metamodelIndex.toBinding(operation.resultType(), operation.resultType().shortName());
    }

    public record BoundContextInvariant(ASTContext ast, BoundExpression expression) {
    }

    public interface BoundExpression {
        ASTExpression ast();

        OclTypeBinding type();
    }

    public record BoundVariable(ASTVar ast, OclTypeBinding type) implements BoundExpression {
    }

    public record BoundLiteral(ASTExpression ast, OclTypeBinding type, Object value) implements BoundExpression {
    }

    public record BoundNot(ASTNot ast, BoundExpression expression) implements BoundExpression {
        @Override
        public OclTypeBinding type() {
            return OclTypeBinding.scalar("Boolean");
        }
    }

    public record BoundIf(ASTIf ast, BoundExpression condition, BoundExpression thenBranch,
                          BoundExpression elseBranch, OclTypeBinding type) implements BoundExpression {
    }

    public record BoundLet(ASTLet ast, BoundExpression value, BoundExpression body,
                           OclTypeBinding type) implements BoundExpression {
    }

    public record BoundBinary(ASTBinary ast, BoundExpression left, BoundExpression right,
                              OclTypeBinding type) implements BoundExpression {
    }

    public record BoundProperty(ASTProperty ast, BoundExpression source, OclTypeBinding type,
                                MAttribute attribute, OclMetamodelIndex.NavigationInfo navigation) implements BoundExpression {
        public boolean isAttribute() {
            return attribute != null;
        }
    }

    public record BoundMethodCall(ASTMethodCall ast, BoundExpression source, List<BoundExpression> arguments,
                                  OclTypeBinding type) implements BoundExpression {
    }

    public record BoundCollectionOperation(ASTCollectionOp ast, BoundExpression source, List<BoundExpression> arguments,
                                           OclTypeBinding type) implements BoundExpression {
    }

    public record BoundIterator(ASTIterator ast, BoundExpression source, BoundExpression body,
                                OclTypeBinding type) implements BoundExpression {
    }

    public static final class Scope {
        private final Deque<Map<String, OclTypeBinding>> scopes = new ArrayDeque<>();

        public Scope() {
            scopes.push(new LinkedHashMap<>());
        }

        public Scope(Scope other) {
            this();
            scopes.clear();
            java.util.Iterator<Map<String, OclTypeBinding>> iterator = other.scopes.descendingIterator();
            while (iterator.hasNext()) {
                scopes.push(new LinkedHashMap<>(iterator.next()));
            }
        }

        public void enter(String name, OclTypeBinding typeBinding) {
            Map<String, OclTypeBinding> next = new LinkedHashMap<>(scopes.peek());
            next.put(name, typeBinding);
            scopes.push(next);
        }

        public void exit() {
            scopes.pop();
        }

        public OclTypeBinding lookup(String name) {
            return scopes.peek().get(name);
        }
    }
}
