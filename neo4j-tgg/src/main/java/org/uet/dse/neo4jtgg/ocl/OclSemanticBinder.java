package org.uet.dse.neo4jtgg.ocl;

import org.tzi.use.uml.mm.MAttribute;
import org.tzi.use.uml.mm.MClass;
import org.tzi.use.uml.mm.MOperation;
import org.tzi.use.uml.ocl.expr.VarDecl;
import org.tzi.use.uml.ocl.type.BagType;
import org.tzi.use.uml.ocl.type.CollectionType;
import org.tzi.use.uml.ocl.type.OrderedSetType;
import org.tzi.use.uml.ocl.type.SequenceType;
import org.tzi.use.uml.ocl.type.SetType;
import org.tzi.use.uml.ocl.type.Type;
import org.uet.dse.neo4j.oclite.ast.ASTBinary;
import org.uet.dse.neo4j.oclite.ast.ASTBooleanLiteral;
import org.uet.dse.neo4j.oclite.ast.ASTCollectionOp;
import org.uet.dse.neo4j.oclite.ast.ASTCollectionLiteral;
import org.uet.dse.neo4j.oclite.ast.ASTCollectionRange;
import org.uet.dse.neo4j.oclite.ast.ASTContext;
import org.uet.dse.neo4j.oclite.ast.ASTEnumLiteral;
import org.uet.dse.neo4j.oclite.ast.ASTExpression;
import org.uet.dse.neo4j.oclite.ast.ASTIf;
import org.uet.dse.neo4j.oclite.ast.ASTInvalidLiteral;
import org.uet.dse.neo4j.oclite.ast.ASTIntegerLiteral;
import org.uet.dse.neo4j.oclite.ast.ASTIterator;
import org.uet.dse.neo4j.oclite.ast.ASTIterate;
import org.uet.dse.neo4j.oclite.ast.ASTLet;
import org.uet.dse.neo4j.oclite.ast.ASTMethodCall;
import org.uet.dse.neo4j.oclite.ast.ASTNot;
import org.uet.dse.neo4j.oclite.ast.ASTNullLiteral;
import org.uet.dse.neo4j.oclite.ast.ASTOperationConstraint;
import org.uet.dse.neo4j.oclite.ast.ASTProperty;
import org.uet.dse.neo4j.oclite.ast.ASTRealLiteral;
import org.uet.dse.neo4j.oclite.ast.ASTStringLiteral;
import org.uet.dse.neo4j.oclite.ast.ASTSetLiteral;
import org.uet.dse.neo4j.oclite.ast.ASTVar;
import org.uet.dse.neo4j.oclite.ast.ASTUnary;
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
    private final boolean certifiedFiniteSetSemantics;

    public OclSemanticBinder(OclMetamodelIndex metamodelIndex) {
        this(metamodelIndex, false);
    }

    private OclSemanticBinder(OclMetamodelIndex metamodelIndex, boolean certifiedFiniteSetSemantics) {
        this.metamodelIndex = metamodelIndex;
        this.certifiedFiniteSetSemantics = certifiedFiniteSetSemantics;
    }

    /** Binder view implementing the theorem profile's finite-Set collection policy. */
    public OclSemanticBinder forCertifiedProfile() {
        return certifiedFiniteSetSemantics ? this : new OclSemanticBinder(metamodelIndex, true);
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
        if (expression instanceof ASTInvalidLiteral) {
            throw unsupportedSurfaceConstruct("invalid literal");
        }
        if (expression instanceof ASTUnary unary) {
            throw unsupportedSurfaceConstruct("unary operator `" + unary.operator + "`");
        }
        if (expression instanceof ASTCollectionLiteral collectionLiteral) {
            throw unsupportedSurfaceConstruct("collection literal kind `" + collectionLiteral.kind + "`");
        }
        if (expression instanceof ASTCollectionRange) {
            throw unsupportedSurfaceConstruct("collection literal range");
        }
        if (expression instanceof ASTIterate) {
            throw unsupportedSurfaceConstruct("iterate expression");
        }
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
            // OCL null has no standalone classifier. Keep Void as the
            // binder-only bottom type; contextual positions widen it to the
            // surrounding expected type.
            return new BoundLiteral(expression, OclTypeBinding.scalar("Void"), null);
        }
        if (expression instanceof ASTEnumLiteral literal) {
            var enumType = metamodelIndex.getModel().enumType(literal.enumTypeName);
            if (enumType == null || !enumType.contains(literal.literalName)) {
                throw new OclCodedUnsupportedOperationException(
                        OclDiagnosticCode.GENERIC_FAILURE,
                        "Unknown enumeration literal: " + literal.enumTypeName + "::" + literal.literalName);
            }
            return new BoundLiteral(literal, OclTypeBinding.scalar(literal.enumTypeName), "#" + literal.literalName);
        }
        if (expression instanceof ASTSetLiteral setLiteral) {
            List<BoundExpression> elements = bindArguments(setLiteral.elements, scope);
            return new BoundSetLiteral(setLiteral, elements, inferSetLiteralType(elements));
        }

        if (expression instanceof ASTNot not) {
            BoundExpression inner = bind(not.expression, scope);
            requireBooleanScalar("not", inner.type());
            return new BoundNot(not, inner);
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
            OclTypeBinding variableType = resolveOptionalDeclaredType(
                    letExpression.variableTypeName, value.type(), "let variable `" + letExpression.variableName + "`");
            if (!conformsTo(value.type(), variableType)) {
                throw new OclCodedUnsupportedOperationException(
                        OclDiagnosticCode.LET_TYPE_MISMATCH,
                        "Let type mismatch for `" + letExpression.variableName + "`: initializer has "
                                + describeType(value.type()) + " but declared type is " + describeType(variableType));
            }
            scope.enter(letExpression.variableName, variableType);
            BoundExpression body = bind(letExpression.body, scope);
            scope.exit();
            return new BoundLet(letExpression, value, variableType, body, body.type());
        }

        if (expression instanceof ASTBinary binary) {
            BoundExpression left = bind(binary.left, scope);
            BoundExpression right = bind(binary.right, scope);
            return new BoundBinary(binary, left, right, inferBinaryType(binary.op, left.type(), right.type()));
        }

        if (expression instanceof ASTProperty property) {
            if (property.atPre) {
                throw unsupportedSurfaceConstruct("property call `" + property.name + "@pre`");
            }
            BoundExpression source = bind(property.source, scope);
            if (source.type().isCollection()) {
                if (certifiedFiniteSetSemantics) {
                    throw new OclCodedUnsupportedOperationException(
                            OclDiagnosticCode.OCL_VAL_EXCLUDED_CONSTRUCT,
                            "Implicit collection property projection `collection."
                                    + property.name + "` is outside OCL_val; use an explicit collect iterator.");
                }
                return bindCollectionPropertyProjection(property, source, scope);
            }
            if (!source.type().isNode()) {
                throw new OclCodedUnsupportedOperationException(
                        OclDiagnosticCode.INVALID_PROPERTY_SOURCE,
                        "Property access requires node source: " + property.name);
            }
            return bindNodePropertyAccess(property, source, scope);
        }

        if (expression instanceof ASTMethodCall methodCall) {
            if (methodCall.atPre) {
                throw unsupportedSurfaceConstruct("operation call `" + methodCall.methodName + "@pre`");
            }
            BoundExpression source = bind(methodCall.source, scope);
            List<BoundExpression> arguments = bindArguments(methodCall.args, scope);
            return new BoundMethodCall(methodCall, source, arguments, inferMethodType(methodCall.methodName, source.type(), arguments));
        }

        if (expression instanceof ASTCollectionOp collectionOp) {
            BoundExpression source = bind(collectionOp.source, scope);
            List<BoundExpression> arguments = bindArguments(collectionOp.args, scope);
            OclTypeBinding sourceCollectionType = collectionSourceType(source);
            return new BoundCollectionOperation(collectionOp, source, arguments, sourceCollectionType,
                    inferCollectionOpType(collectionOp.opName, sourceCollectionType, arguments));
        }

        if (expression instanceof ASTIterator iterator) {
            if (iterator.iteratorVariables.size() != 1) {
                throw unsupportedSurfaceConstruct("iterator `" + iterator.operation + "` with "
                        + iterator.iteratorVariables.size() + " variables");
            }
            BoundExpression source = bind(iterator.source, scope);
            OclTypeBinding sourceCollectionType = collectionSourceType(source);
            if (!sourceCollectionType.isCollection()) {
                throw new OclCodedUnsupportedOperationException(
                        OclDiagnosticCode.INVALID_ITERATOR_SOURCE,
                        "Iterator source must be a collection: " + iterator.operation);
            }
            OclTypeBinding iteratorVariableType = resolveOptionalDeclaredType(
                    iterator.iteratorTypeName, sourceCollectionType.elementType(),
                    "iterator variable `" + iterator.iteratorName + "`");
            validateIteratorType(iterator, sourceCollectionType.elementType(), iteratorVariableType);
            scope.enter(iterator.iteratorName, iteratorVariableType);
            BoundExpression body = bind(iterator.body, scope);
            scope.exit();
            return new BoundIterator(iterator, source, sourceCollectionType, iteratorVariableType, body,
                    inferIteratorType(iterator.operation, sourceCollectionType, body.type()));
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
        ASTProperty projectedPropertyAst = new ASTProperty(new ASTVar(iteratorName), property.name, property.qualifiers);
        BoundExpression body = bind(projectedPropertyAst, projectionScope);
        projectionScope.exit();

        ASTIterator collectAst = new ASTIterator(property.source, "collect", iteratorName, projectedPropertyAst);
        BoundIterator collectBound = new BoundIterator(collectAst, source, source.type(), elementType, body,
                inferIteratorType("collect", source.type(), body.type()));
        if (!body.type().isCollection()) {
            return collectBound;
        }

        ASTCollectionOp flattenAst = new ASTCollectionOp(collectAst, "flatten", List.of());
        return new BoundCollectionOperation(flattenAst, collectBound, List.of(), collectBound.type(),
                inferFlattenType(collectBound.type()));
    }

    /**
     * OCL/USE keeps an ordinary navigation with upper multiplicity one scalar.
     * Qualified forms can still have a native collection result, so the USE
     * result binding rather than multiplicity alone decides the property type.
     * When the surface expression applies a collection operator with {@code ->}, the
     * certified profile records the corresponding finite singleton/empty-set
     * view at that operator boundary instead of changing the property's static
     * type inside the bound tree.
     */
    private OclTypeBinding collectionSourceType(BoundExpression source) {
        if (source.type().isCollection()) {
            return source.type();
        }
        if (certifiedFiniteSetSemantics
                && source instanceof BoundProperty property
                && !property.isAttribute()
                && property.navigation() != null
                && property.navigation().targetSingleValued()
                && !property.navigation().resultBinding().isCollection()) {
            return OclTypeBinding.nodeCollection(
                    property.navigation().targetClassName(), OclTypeBinding.CollectionKind.SET);
        }
        return source.type();
    }

    private BoundExpression bindNodePropertyAccess(ASTProperty property, BoundExpression source, Scope scope) {
        MAttribute attribute = metamodelIndex.resolveAttribute(source.type().typeName(), property.name);
        if (attribute != null) {
            if (property.hasQualifiers()) {
                throw new OclCodedUnsupportedOperationException(
                        OclDiagnosticCode.QUALIFIED_ASSOCIATION_UNSUPPORTED,
                        "Qualifier-based navigation/filtering only applies to association ends, not attributes: " + property.name);
            }
            if (attribute.type().isKindOfCollection(Type.VoidHandling.EXCLUDE_VOID)) {
                return bindCollectionValuedAttribute(property, source, attribute);
            }
            return new BoundProperty(property, source, toAttributeBinding(attribute.type()), attribute, null, List.of());
        }

        OclMetamodelIndex.NavigationInfo navigation = metamodelIndex.resolveNavigation(source.type().typeName(), property.name);
        if (navigation != null) {
            List<BoundExpression> qualifierExpressions = List.of();
            if (property.hasQualifiers()) {
                validateQualifiedNavigation(property, navigation, source.type().typeName());
                qualifierExpressions = bindQualifiedNavigationArguments(property, source.type().typeName(), navigation, scope);
            }
            boolean admittedGeneralNavigation = !certifiedFiniteSetSemantics
                    && navigation.supportsCanonicalNAryNavigation();
            if (!navigation.supportsDirectCypherNavigation() && !admittedGeneralNavigation) {
                throw new OclCodedUnsupportedOperationException(navigation.unsupportedCode(), navigation.unsupportedReason());
            }
            // The certified finite-set view may forget ordering on a native
            // collection result, but it must not change a native scalar
            // navigation from Entity to Set(Entity). Such a lift needs an
            // explicit lowering rule and preservation theorem. The native USE
            // binding is authoritative here. In particular,
            // ordinary upper-one navigation is scalar, while some qualified
            // navigation forms can still have a collection result even when
            // the target end itself is upper-one.
            OclTypeBinding resultType = certifiedFiniteSetSemantics && navigation.resultBinding().isCollection()
                    ? OclTypeBinding.nodeCollection(
                            navigation.targetClassName(), OclTypeBinding.CollectionKind.SET)
                    : navigation.resultBinding();
            return new BoundProperty(property, source, resultType, null, navigation, qualifierExpressions);
        }

        throw new OclCodedUnsupportedOperationException(
                OclDiagnosticCode.UNKNOWN_PROPERTY,
                "Unknown property or navigation: " + property.name);
    }

    private BoundExpression bindCollectionValuedAttribute(ASTProperty property, BoundExpression source, MAttribute attribute) {
        OclTypeBinding attributeType = toAttributeBinding(attribute.type());
        return new BoundProperty(property, source, attributeType, attribute, null, List.of());
    }

    private void validateQualifiedNavigation(ASTProperty property,
                                             OclMetamodelIndex.NavigationInfo navigation,
                                             String sourceClassName) {
        if (!navigation.targetHasQualifiers()) {
            throw new OclCodedUnsupportedOperationException(
                    OclDiagnosticCode.QUALIFIED_ASSOCIATION_UNSUPPORTED,
                    "Qualifier arguments can only be used when navigating to a qualified association end: " + property.name);
        }

        List<VarDecl> qualifiers = navigation.targetQualifiers();
        if (qualifiers.size() != property.qualifiers.size()) {
            throw new OclCodedUnsupportedOperationException(
                    OclDiagnosticCode.INVALID_COLLECTION_ARGUMENT,
                    "Qualified navigation `" + sourceClassName + "." + property.name + "` requires "
                            + qualifiers.size() + " qualifier argument(s), but got " + property.qualifiers.size() + ".");
        }
    }

    private List<BoundExpression> bindQualifiedNavigationArguments(ASTProperty property,
                                                                   String sourceClassName,
                                                                   OclMetamodelIndex.NavigationInfo navigation,
                                                                   Scope scope) {
        List<BoundExpression> qualifiers = new ArrayList<>(property.qualifiers.size());
        List<VarDecl> qualifierDefinitions = navigation.targetQualifiers();
        for (int i = 0; i < property.qualifiers.size(); i++) {
            BoundExpression qualifier = bind(property.qualifiers.get(i), scope);
            VarDecl definition = qualifierDefinitions.get(i);
            OclTypeBinding expectedType = metamodelIndex.toBinding(definition.type(), definition.type().shortName());
            if (!isQualifierCompatible(qualifier.type(), expectedType)) {
                throw new OclCodedUnsupportedOperationException(
                        OclDiagnosticCode.INVALID_COLLECTION_ARGUMENT,
                        "Qualified navigation `" + sourceClassName + "." + property.name + "` expects qualifier `"
                                + definition.name() + "` of type `" + expectedType.typeName() + "`.");
            }
            if (qualifier.type().isCollection() || qualifier.type().isNode() || qualifier.type().isClassReference()) {
                throw new OclCodedUnsupportedOperationException(
                        OclDiagnosticCode.QUALIFIED_ASSOCIATION_UNSUPPORTED,
                        "Qualified navigation currently supports only scalar qualifier expressions: " + property.name);
            }
            qualifiers.add(qualifier);
        }
        return List.copyOf(qualifiers);
    }

    private boolean isQualifierCompatible(OclTypeBinding actual, OclTypeBinding expected) {
        if (actual == null || expected == null) {
            return false;
        }
        if ("Void".equals(actual.typeName())) {
            return false;
        }
        return actual.typeName().equals(expected.typeName());
    }

    private OclTypeBinding toAttributeBinding(Type type) {
        if (type.isKindOfCollection(Type.VoidHandling.EXCLUDE_VOID) && type instanceof CollectionType collectionType) {
            return OclTypeBinding.collectionOf(toAttributeBinding(collectionType.elemType()), toCollectionKind(collectionType));
        }
        if (type.isKindOfClass(Type.VoidHandling.EXCLUDE_VOID)) {
            return OclTypeBinding.node(type.shortName());
        }
        return OclTypeBinding.scalar(type.shortName());
    }

    private OclTypeBinding.CollectionKind toCollectionKind(CollectionType collectionType) {
        if (collectionType instanceof SetType) {
            return OclTypeBinding.CollectionKind.SET;
        }
        if (collectionType instanceof BagType) {
            return OclTypeBinding.CollectionKind.BAG;
        }
        if (collectionType instanceof SequenceType) {
            return OclTypeBinding.CollectionKind.SEQUENCE;
        }
        if (collectionType instanceof OrderedSetType) {
            return OclTypeBinding.CollectionKind.ORDERED_SET;
        }
        return OclTypeBinding.CollectionKind.COLLECTION;
    }

    private OclTypeBinding inferBinaryType(String operator, OclTypeBinding left, OclTypeBinding right) {
        return switch (operator) {
            case "and", "or", "xor", "implies" -> {
                requireBooleanScalar(operator, left);
                requireBooleanScalar(operator, right);
                yield OclTypeBinding.scalar("Boolean");
            }
            case "=", "<>" -> {
                requireEqualityCompatible(operator, left, right);
                yield OclTypeBinding.scalar("Boolean");
            }
            case ">", "<", ">=", "<=" -> {
                requireNumericScalar(operator, left);
                requireNumericScalar(operator, right);
                yield OclTypeBinding.scalar("Boolean");
            }
            case "+", "-", "*", "/" -> {
                requireNumericScalar(operator, left);
                requireNumericScalar(operator, right);
                if ("Real".equals(left.typeName()) || "Real".equals(right.typeName()) || "/".equals(operator)) {
                    yield OclTypeBinding.scalar("Real");
                }
                yield OclTypeBinding.scalar("Integer");
            }
            default -> throw new OclCodedUnsupportedOperationException(
                    OclDiagnosticCode.UNSUPPORTED_OPERATOR,
                    "Unsupported operator: " + operator);
        };
    }

    private OclTypeBinding inferSetLiteralType(List<BoundExpression> elements) {
        if (elements.isEmpty()) {
            throw new OclCodedUnsupportedOperationException(
                    OclDiagnosticCode.INVALID_COLLECTION_ARGUMENT,
                    "Set{} requires at least one element because this compiler has no contextual type inference.");
        }

        OclTypeBinding elementType = null;
        for (BoundExpression element : elements) {
            OclTypeBinding candidate = element.type();
            if (candidate.isCollection()) {
                throw new OclCodedUnsupportedOperationException(
                        OclDiagnosticCode.INVALID_COLLECTION_ARGUMENT,
                        "Nested collection elements are outside the certified Set literal fragment.");
            }
            elementType = elementType == null ? candidate : setJoin(elementType, candidate, "Set literal");
        }
        if (elementType == null || isVoidType(elementType)) {
            throw new OclCodedUnsupportedOperationException(
                    OclDiagnosticCode.INVALID_COLLECTION_ARGUMENT,
                    "A Set literal containing only undefined values has no inferable element type.");
        }
        return OclTypeBinding.collectionOf(elementType, OclTypeBinding.CollectionKind.SET);
    }

    private void requireBooleanScalar(String operator, OclTypeBinding type) {
        if (!isBooleanScalarType(type)) {
            throw new OclCodedUnsupportedOperationException(
                    OclDiagnosticCode.UNSUPPORTED_OPERATOR,
                    "Operator `" + operator + "` requires Boolean scalar operand(s), but found " + describeType(type) + ".");
        }
    }

    private void requireNumericScalar(String operator, OclTypeBinding type) {
        if (!isNumericScalarType(type)) {
            throw new OclCodedUnsupportedOperationException(
                    OclDiagnosticCode.UNSUPPORTED_OPERATOR,
                    "Operator `" + operator + "` requires Integer or Real scalar operand(s), but found " + describeType(type) + ".");
        }
    }

    private void requireEqualityCompatible(String operator, OclTypeBinding left, OclTypeBinding right) {
        if (!isEqualityCompatible(left, right)) {
            throw new OclCodedUnsupportedOperationException(
                    OclDiagnosticCode.UNSUPPORTED_OPERATOR,
                    "Operator `" + operator + "` requires compatible operand types, but found "
                            + describeType(left) + " and " + describeType(right) + ".");
        }
    }

    private boolean isEqualityCompatible(OclTypeBinding left, OclTypeBinding right) {
        if (isVoidType(left) || isVoidType(right)) {
            return true;
        }
        if (isNumericScalarType(left) && isNumericScalarType(right)) {
            return true;
        }
        return left.equals(right);
    }

    private OclTypeBinding inferIfType(OclTypeBinding conditionType, OclTypeBinding thenType, OclTypeBinding elseType) {
        if (!isBooleanScalarType(conditionType) && !isVoidType(conditionType)) {
            throw new OclCodedUnsupportedOperationException(
                    OclDiagnosticCode.INVALID_IF_CONDITION,
                    "if condition must be Boolean or bottom.");
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

    private OclTypeBinding inferMethodType(String methodName, OclTypeBinding sourceType, List<BoundExpression> arguments) {
        if ("allInstances".equalsIgnoreCase(methodName)) {
            if (!sourceType.isClassReference()) {
                throw new OclCodedUnsupportedOperationException(
                    OclDiagnosticCode.INVALID_METHOD_RECEIVER,
                    "allInstances() must be called on a class name.");
            }
            if (!arguments.isEmpty()) {
                throw new OclCodedUnsupportedOperationException(
                        OclDiagnosticCode.INVALID_METHOD_ARGUMENT,
                        "allInstances() does not accept arguments.");
            }
            return OclTypeBinding.nodeCollection(sourceType.typeName(), OclTypeBinding.CollectionKind.SET);
        }
        if ("split".equalsIgnoreCase(methodName)) {
            return OclTypeBinding.scalarCollection("String", OclTypeBinding.CollectionKind.SEQUENCE);
        }
        if ("isDefined".equalsIgnoreCase(methodName) || "isUndefined".equalsIgnoreCase(methodName)) {
            return OclTypeBinding.scalar("Boolean");
        }
        // String operations
        if ("concat".equalsIgnoreCase(methodName)) {
            return OclTypeBinding.scalar("String");
        }
        if ("substring".equalsIgnoreCase(methodName)) {
            return OclTypeBinding.scalar("String");
        }
        if ("toLower".equalsIgnoreCase(methodName) || "toUpper".equalsIgnoreCase(methodName)
                || "trim".equalsIgnoreCase(methodName)) {
            return OclTypeBinding.scalar("String");
        }
        if ("toInteger".equalsIgnoreCase(methodName)) {
            return OclTypeBinding.scalar("Integer");
        }
        if ("toReal".equalsIgnoreCase(methodName)) {
            return OclTypeBinding.scalar("Real");
        }
        if ("toString".equalsIgnoreCase(methodName)) {
            return OclTypeBinding.scalar("String");
        }
        if ("oclIsTypeOf".equalsIgnoreCase(methodName)) {
            throw new OclCodedUnsupportedOperationException(
                    OclDiagnosticCode.OCL_IS_TYPE_OF_OUTSIDE_CERTIFIED_FRAGMENT,
                    "oclIsTypeOf() is outside the certified OCL_val fragment until the graph encoding "
                            + "provides a proved direct runtime-class accessor. Use oclIsKindOf() when "
                            + "conformance semantics are intended.");
        }
        if ("oclIsKindOf".equalsIgnoreCase(methodName)) {
            if (!sourceType.isNode() && !isVoidType(sourceType)) {
                throw new OclCodedUnsupportedOperationException(
                        OclDiagnosticCode.INVALID_METHOD_RECEIVER,
                        "oclIsKindOf() requires an object-or-bottom source.");
            }
            requireSingleClassReferenceArgument("oclIsKindOf", arguments);
            return OclTypeBinding.scalar("Boolean");
        }
        if ("oclAsType".equalsIgnoreCase(methodName)) {
            if (!sourceType.isNode() && !isVoidType(sourceType)) {
                throw new OclCodedUnsupportedOperationException(
                        OclDiagnosticCode.INVALID_METHOD_RECEIVER,
                        "oclAsType() requires an object-or-bottom source.");
            }
            requireSingleClassReferenceArgument("oclAsType", arguments);
            return OclTypeBinding.node(arguments.get(0).type().typeName());
        }
        throw new OclCodedUnsupportedOperationException(
                OclDiagnosticCode.UNSUPPORTED_METHOD_CALL,
                "Unsupported method call: " + methodName);
    }

    private OclTypeBinding inferCollectionOpType(String opName, OclTypeBinding sourceType, List<BoundExpression> arguments) {
        return switch (opName) {
            case "size" -> {
                requireNoCollectionArguments("size", arguments);
                yield OclTypeBinding.scalar("Integer");
            }
            case "count" -> {
                requireSingleElementArgument("count", sourceType, arguments);
                yield OclTypeBinding.scalar("Integer");
            }
            case "isEmpty", "notEmpty" -> {
                requireNoCollectionArguments(opName, arguments);
                yield OclTypeBinding.scalar("Boolean");
            }
            case "includes", "excludes" -> {
                requireSingleElementArgument(opName, sourceType, arguments);
                yield OclTypeBinding.scalar("Boolean");
            }
            case "includesAll", "excludesAll" -> {
                OclTypeBinding argumentType = requireSingleCollectionArgument(opName, arguments);
                setJoin(sourceType.elementType(), argumentType.elementType(), opName + "()");
                yield OclTypeBinding.scalar("Boolean");
            }
            case "including" -> inferIncludingType(sourceType, arguments);
            case "excluding" -> inferExcludingType(sourceType, arguments);
            case "append" -> inferOrderedElementMutationType("append", sourceType, arguments);
            case "prepend" -> inferOrderedElementMutationType("prepend", sourceType, arguments);
            case "subSequence" -> inferSubSequenceType(sourceType, arguments);
            case "sum" -> inferSumType(sourceType, arguments);
            case "min", "max" -> inferMinMaxType(opName, sourceType, arguments);
            case "union" -> inferUnionType(sourceType, arguments);
            case "intersection" -> inferIntersectionType(sourceType, arguments);
            case "flatten" -> inferFlattenType(sourceType);
            case "asBag" -> sourceType.withCollectionKind(OclTypeBinding.CollectionKind.BAG);
            case "asSet" -> {
                requireNoCollectionArguments("asSet", arguments);
                yield sourceType.withCollectionKind(OclTypeBinding.CollectionKind.SET);
            }
            case "asOrderedSet" -> sourceType.withCollectionKind(OclTypeBinding.CollectionKind.ORDERED_SET);
            case "at", "first", "last" -> inferPositionalAccessType(sourceType, opName);
            default -> throw new OclCodedUnsupportedOperationException(
                    OclDiagnosticCode.UNSUPPORTED_COLLECTION_OPERATION,
                    "Unsupported collection operation: " + opName);
        };
    }

    private OclTypeBinding inferIteratorType(String operation, OclTypeBinding sourceType, OclTypeBinding bodyType) {
        return switch (operation.toLowerCase()) {
            case "select", "reject" -> {
                requireBooleanScalar(operation, bodyType);
                yield sourceType;
            }
            case "exists", "forall", "one" -> {
                requireBooleanScalar(operation, bodyType);
                yield OclTypeBinding.scalar("Boolean");
            }
            case "any" -> {
                requireBooleanScalar(operation, bodyType);
                yield sourceType.elementType();
            }
            case "isunique" -> {
                if (bodyType.isCollection()) {
                    throw new OclCodedUnsupportedOperationException(
                            OclDiagnosticCode.UNSUPPORTED_ITERATOR,
                            "isUnique() requires a non-collection projection in the certified fragment.");
                }
                yield OclTypeBinding.scalar("Boolean");
            }
            case "collect" -> inferCollectType(sourceType, bodyType);
            case "sortedby" -> inferSortedByType(sourceType, bodyType);
            default -> throw new OclCodedUnsupportedOperationException(
                    OclDiagnosticCode.UNSUPPORTED_ITERATOR,
                    "Unsupported iterator: " + operation);
        };
    }

    private OclTypeBinding inferCollectType(OclTypeBinding sourceType, OclTypeBinding bodyType) {
        if (certifiedFiniteSetSemantics) {
            return OclTypeBinding.collectionOf(bodyType, OclTypeBinding.CollectionKind.SET);
        }
        OclTypeBinding.CollectionKind targetKind = sourceType.isOrderedCollection()
                ? OclTypeBinding.CollectionKind.SEQUENCE
                : OclTypeBinding.CollectionKind.BAG;
        return OclTypeBinding.collectionOf(bodyType, targetKind);
    }

    private OclTypeBinding inferSortedByType(OclTypeBinding sourceType, OclTypeBinding bodyType) {
        if (!sourceType.isCollection()) {
            throw new OclCodedUnsupportedOperationException(
                    OclDiagnosticCode.INVALID_COLLECTION_SOURCE,
                    "sortedBy() requires a collection source.");
        }
        if (bodyType.isCollection() || bodyType.isNode() || bodyType.isClassReference()) {
            throw new OclCodedUnsupportedOperationException(
                    OclDiagnosticCode.UNSUPPORTED_ITERATOR,
                    "sortedBy() currently requires a scalar sort key.");
        }
        OclTypeBinding.CollectionKind targetKind = sourceType.isUniqueCollection()
                ? OclTypeBinding.CollectionKind.ORDERED_SET
                : OclTypeBinding.CollectionKind.SEQUENCE;
        return sourceType.withCollectionKind(targetKind);
    }

    private OclTypeBinding inferSumType(OclTypeBinding sourceType, List<BoundExpression> arguments) {
        requireNoCollectionArguments("sum", arguments);
        OclTypeBinding elementType = requireNumericCollectionElement("sum", sourceType);
        return "Real".equals(elementType.typeName())
                ? OclTypeBinding.scalar("Real")
                : OclTypeBinding.scalar("Integer");
    }

    private OclTypeBinding inferMinMaxType(String operationName, OclTypeBinding sourceType, List<BoundExpression> arguments) {
        requireNoCollectionArguments(operationName, arguments);
        return requireNumericCollectionElement(operationName, sourceType);
    }

    private OclTypeBinding inferUnionType(OclTypeBinding sourceType, List<BoundExpression> arguments) {
        OclTypeBinding argumentType = requireSingleCollectionArgument("union", arguments);
        OclTypeBinding elementType = setJoin(sourceType.elementType(), argumentType.elementType(), "union()");
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
        return OclTypeBinding.collectionOf(elementType, resultKind);
    }

    private OclTypeBinding inferIntersectionType(OclTypeBinding sourceType, List<BoundExpression> arguments) {
        OclTypeBinding argumentType = requireSingleCollectionArgument("intersection", arguments);
        OclTypeBinding elementType = setJoin(sourceType.elementType(), argumentType.elementType(), "intersection()");
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
        return OclTypeBinding.collectionOf(elementType, resultKind);
    }

    /** Functional least common canonical type used by certified finite-set constructors. */
    private OclTypeBinding setJoin(OclTypeBinding left, OclTypeBinding right, String operation) {
        if (left.equals(right)) {
            return left;
        }
        if (isVoidType(left)) {
            return right;
        }
        if (isVoidType(right)) {
            return left;
        }
        if (isNumericScalarType(left) && isNumericScalarType(right)) {
            return OclTypeBinding.scalar("Real");
        }
        if (left.isNode() && right.isNode()) {
            MClass leftClass = metamodelIndex.requireClass(left.typeName());
            MClass rightClass = metamodelIndex.requireClass(right.typeName());
            if (leftClass.allParents().contains(rightClass)) {
                return right;
            }
            if (rightClass.allParents().contains(leftClass)) {
                return left;
            }
            List<MClass> common = new ArrayList<>(leftClass.allParents());
            common.retainAll(rightClass.allParents());
            List<MClass> minimal = common.stream()
                    .filter(candidate -> common.stream().noneMatch(other ->
                            !candidate.equals(other) && other.allParents().contains(candidate)))
                    .toList();
            if (minimal.size() == 1) {
                return OclTypeBinding.node(minimal.get(0).name());
            }
        }
        throw new OclCodedUnsupportedOperationException(
                OclDiagnosticCode.INVALID_COLLECTION_ARGUMENT,
                operation + " operands require one unique canonical element type, but found "
                        + describeType(left) + " and " + describeType(right) + ".");
    }

    private OclCodedUnsupportedOperationException unsupportedSurfaceConstruct(String construct) {
        return new OclCodedUnsupportedOperationException(
                OclDiagnosticCode.UNSUPPORTED_AST_NODE,
                "Parsed OCL construct is not implemented by the semantic binder: " + construct);
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
        if (!sourceType.isCollection()) {
            throw new OclCodedUnsupportedOperationException(
                    OclDiagnosticCode.INVALID_COLLECTION_SOURCE,
                    "flatten() requires a collection source.");
        }
        OclTypeBinding elementType = sourceType.elementType();
        if (!elementType.isCollection()) {
            return sourceType;
        }
        return elementType.withCollectionKind(sourceType.collectionKind());
    }

    private OclTypeBinding inferIncludingType(OclTypeBinding sourceType, List<BoundExpression> arguments) {
        requireSingleElementArgument("including", sourceType, arguments);
        return sourceType;
    }

    private OclTypeBinding inferExcludingType(OclTypeBinding sourceType, List<BoundExpression> arguments) {
        requireSingleElementArgument("excluding", sourceType, arguments);
        return sourceType;
    }

    private OclTypeBinding inferOrderedElementMutationType(String operationName,
                                                           OclTypeBinding sourceType,
                                                           List<BoundExpression> arguments) {
        requireSingleElementArgument(operationName, sourceType, arguments);
        if (!sourceType.isOrderedCollection()) {
            throw new OclCodedUnsupportedOperationException(
                    OclDiagnosticCode.UNORDERED_POSITIONAL_ACCESS,
                    operationName + "() is only supported on ordered collections (Sequence/OrderedSet).");
        }
        return sourceType;
    }

    private OclTypeBinding inferSubSequenceType(OclTypeBinding sourceType, List<BoundExpression> arguments) {
        if (!sourceType.isCollection()) {
            throw new OclCodedUnsupportedOperationException(
                    OclDiagnosticCode.INVALID_COLLECTION_SOURCE,
                    "subSequence() requires a collection source.");
        }
        if (!sourceType.isOrderedCollection()) {
            throw new OclCodedUnsupportedOperationException(
                    OclDiagnosticCode.UNORDERED_POSITIONAL_ACCESS,
                    "subSequence() is only supported on ordered collections (Sequence/OrderedSet).");
        }
        if (arguments.size() != 2) {
            throw new OclCodedUnsupportedOperationException(
                    OclDiagnosticCode.INVALID_COLLECTION_ARGUMENT,
                    "subSequence() requires start and end index arguments.");
        }
        return sourceType;
    }

    private void requireNoCollectionArguments(String operationName, List<BoundExpression> arguments) {
        if (!arguments.isEmpty()) {
            throw new OclCodedUnsupportedOperationException(
                    OclDiagnosticCode.INVALID_COLLECTION_ARGUMENT,
                    operationName + "() does not accept arguments.");
        }
    }

    private void requireSingleElementArgument(String operationName,
                                              OclTypeBinding sourceType,
                                              List<BoundExpression> arguments) {
        if (!sourceType.isCollection()) {
            throw new OclCodedUnsupportedOperationException(
                    OclDiagnosticCode.INVALID_COLLECTION_SOURCE,
                    operationName + "() requires a collection source.");
        }
        if (arguments.size() != 1) {
            throw new OclCodedUnsupportedOperationException(
                    OclDiagnosticCode.INVALID_COLLECTION_ARGUMENT,
                    operationName + "() requires a single argument.");
        }
        setJoin(sourceType.elementType(), arguments.get(0).type(), operationName + "()");
    }

    private void requireSingleClassReferenceArgument(String operationName,
                                                     List<BoundExpression> arguments) {
        if (arguments.size() != 1 || !arguments.get(0).type().isClassReference()) {
            throw new OclCodedUnsupportedOperationException(
                    OclDiagnosticCode.INVALID_METHOD_ARGUMENT,
                    operationName + "() requires exactly one UML class argument.");
        }
    }

    private OclTypeBinding requireNumericCollectionElement(String operationName, OclTypeBinding sourceType) {
        if (!sourceType.isCollection()) {
            throw new OclCodedUnsupportedOperationException(
                    OclDiagnosticCode.INVALID_COLLECTION_SOURCE,
                    operationName + "() requires a collection source.");
        }
        OclTypeBinding elementType = sourceType.elementType();
        if (elementType.isCollection() || !isNumericScalarType(elementType)) {
            throw new OclCodedUnsupportedOperationException(
                    OclDiagnosticCode.INVALID_COLLECTION_SOURCE,
                    operationName + "() requires a collection of Integer or Real values.");
        }
        return elementType;
    }

    private boolean isNumericScalarType(OclTypeBinding type) {
        return type != null
                && !type.isCollection()
                && !type.isNode()
                && !type.isClassReference()
                && ("Integer".equals(type.typeName()) || "Real".equals(type.typeName()));
    }

    private boolean isBooleanScalarType(OclTypeBinding type) {
        return type != null
                && !type.isCollection()
                && !type.isNode()
                && !type.isClassReference()
                && "Boolean".equals(type.typeName());
    }

    private boolean isVoidType(OclTypeBinding type) {
        return type != null && !type.isCollection() && "Void".equals(type.typeName());
    }

    private String describeType(OclTypeBinding type) {
        if (type == null) {
            return "<unknown>";
        }
        if (type.isCollection()) {
            return type.collectionKind() + "(" + describeType(type.elementType()) + ")";
        }
        return type.kind() + "(" + type.typeName() + ")";
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

    private void validateIteratorType(ASTIterator iterator, OclTypeBinding elementType,
                                      OclTypeBinding iteratorVariableType) {
        if (!conformsTo(elementType, iteratorVariableType)) {
            throw new OclCodedUnsupportedOperationException(
                    OclDiagnosticCode.ITERATOR_TYPE_MISMATCH,
                    "Iterator type mismatch for `" + iterator.iteratorName + "`: source elements have "
                            + describeType(elementType) + " but declared type is " + describeType(iteratorVariableType));
        }
    }

    private OclTypeBinding resolveOptionalDeclaredType(String typeName, OclTypeBinding inferredType,
                                                       String declaration) {
        if (typeName == null || typeName.isBlank()) {
            return inferredType;
        }
        if ("Void".equals(typeName.trim())) {
            throw new OclCodedUnsupportedOperationException(
                    OclDiagnosticCode.UNKNOWN_DECLARED_TYPE,
                    "Declared type `Void` is internal to null-bottom inference and cannot be written explicitly for "
                            + declaration);
        }
        OclTypeBinding resolved = resolveDeclaredType(typeName.trim());
        if (resolved == null || resolved.isClassReference()) {
            throw new OclCodedUnsupportedOperationException(
                    OclDiagnosticCode.UNKNOWN_DECLARED_TYPE,
                    "Unknown or unsupported declared type `" + typeName + "` for " + declaration);
        }
        return resolved;
    }

    private OclTypeBinding resolveDeclaredType(String typeName) {
        int open = typeName.indexOf('(');
        if (open > 0 && typeName.endsWith(")")) {
            String kindName = typeName.substring(0, open);
            String elementText = typeName.substring(open + 1, typeName.length() - 1);
            OclTypeBinding elementType = resolveDeclaredType(elementText);
            OclTypeBinding.CollectionKind collectionKind = switch (kindName) {
                case "Collection" -> OclTypeBinding.CollectionKind.COLLECTION;
                case "Set" -> OclTypeBinding.CollectionKind.SET;
                case "Bag" -> OclTypeBinding.CollectionKind.BAG;
                case "Sequence" -> OclTypeBinding.CollectionKind.SEQUENCE;
                case "OrderedSet" -> OclTypeBinding.CollectionKind.ORDERED_SET;
                default -> null;
            };
            return elementType == null || collectionKind == null
                    ? null : OclTypeBinding.collectionOf(elementType, collectionKind);
        }

        return switch (typeName) {
            case "Boolean", "Integer", "Real", "String", "OclAny", "UnlimitedNatural" ->
                    OclTypeBinding.scalar(typeName);
            default -> {
                String localName = typeName.contains("::")
                        ? typeName.substring(typeName.lastIndexOf("::") + 2) : typeName;
                if (metamodelIndex.getClassInfo(typeName) != null) {
                    yield OclTypeBinding.node(typeName);
                }
                if (metamodelIndex.getClassInfo(localName) != null) {
                    yield OclTypeBinding.node(localName);
                }
                if (metamodelIndex.getModel().enumType(typeName) != null) {
                    yield OclTypeBinding.scalar(typeName);
                }
                if (metamodelIndex.getModel().enumType(localName) != null) {
                    yield OclTypeBinding.scalar(localName);
                }
                yield null;
            }
        };
    }

    private boolean conformsTo(OclTypeBinding actual, OclTypeBinding declared) {
        return OclTypeConformance.conformsTo(metamodelIndex, actual, declared);
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
        /** Names free in the bound invariant body; lexical binders are removed. */
        public java.util.Set<String> freeVariables() {
            return OclFreeVariableAnalyzer.freeVariables(expression);
        }

        /** Whether the invariant body does not depend on the context variable. */
        public boolean isSelfIndependent() {
            return OclFreeVariableAnalyzer.isSelfIndependent(expression);
        }
    }

    public interface BoundExpression {
        ASTExpression ast();

        OclTypeBinding type();
    }

    public record BoundVariable(ASTVar ast, OclTypeBinding type) implements BoundExpression {
    }

    public record BoundLiteral(ASTExpression ast, OclTypeBinding type, Object value) implements BoundExpression {
    }

    public record BoundSetLiteral(ASTSetLiteral ast, List<BoundExpression> elements,
                                  OclTypeBinding type) implements BoundExpression {
        public BoundSetLiteral {
            elements = List.copyOf(elements);
        }
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

    public record BoundLet(ASTLet ast, BoundExpression value, OclTypeBinding variableType, BoundExpression body,
                           OclTypeBinding type) implements BoundExpression {
    }

    public record BoundBinary(ASTBinary ast, BoundExpression left, BoundExpression right,
                              OclTypeBinding type) implements BoundExpression {
    }

    public record BoundProperty(ASTProperty ast, BoundExpression source, OclTypeBinding type,
                                MAttribute attribute, OclMetamodelIndex.NavigationInfo navigation,
                                List<BoundExpression> qualifiers) implements BoundExpression {
        public boolean isAttribute() {
            return attribute != null;
        }
    }

    public record BoundMethodCall(ASTMethodCall ast, BoundExpression source, List<BoundExpression> arguments,
                                  OclTypeBinding type) implements BoundExpression {
    }

    public record BoundCollectionOperation(ASTCollectionOp ast, BoundExpression source, List<BoundExpression> arguments,
                                           OclTypeBinding sourceCollectionType,
                                           OclTypeBinding type) implements BoundExpression {
    }

    public record BoundIterator(ASTIterator ast, BoundExpression source, OclTypeBinding sourceCollectionType,
                                OclTypeBinding iteratorVariableType, BoundExpression body,
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
