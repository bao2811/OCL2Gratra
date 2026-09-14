package org.uet.dse.ocl2cypher.core;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import org.uet.dse.ocl2cypher.diagnostics.*;
import org.uet.dse.ocl2cypher.runtime.OclType;
import org.uet.dse.ocl2cypher.source.model.SchemaModel;
import org.uet.dse.ocl2cypher.source.model.Admission;
import org.uet.dse.ocl2cypher.source.omg.OmgAs;
import org.uet.dse.ocl2cypher.source.omg.OclOperation;
import org.uet.dse.ocl2cypher.trace.Trace;
import org.uet.dse.ocl2cypher.trace.TraceCollector;

/**
 * {@code N_SM}: resolved, typed OMG OCL AS to {@link CoreExpr}.
 *
 * <p>Every source constructor recognized by the frontend is dispatched here
 * with a single unambiguous rule family (see Rule 02). Unadmitted OMG-AS nodes
 * (surface null/invalid, standalone type expressions, collection ranges,
 * nested collections, Sequence/OrderedSet) never produce a Core node and
 * never produce a typed bottom.
 */
public final class CoreLowering {

    private CoreLowering() {
    }

    public static Result<CoreInvariant> lower(SchemaModel sm,
                                              OmgAs.OmgDocument document,
                                              OmgAs.Constraint invariant) {
        if (!document.constraints.contains(invariant)) {
            return Result.failure(Diagnostic.error(Stage.N_SM, "N_FOREIGN_CONSTRAINT",
                    "constraint is not owned by the supplied OMG document"));
        }
        if (!document.contextClassKey.equals(invariant.constrainedElement)) {
            return Result.failure(Diagnostic.error(Stage.N_SM, "N_CONTEXT_MISMATCH",
                    "document and constraint context classifiers differ"));
        }
        Optional<Diagnostic> admissionFailure = Admission.check(invariant);
        if (admissionFailure.isPresent()) {
            return Result.failure(admissionFailure.get());
        }
        if (!OclType.BOOLEAN.equals(invariant.specification.bodyExpression.type)) {
            return Result.failure(Diagnostic.error(Stage.N_SM, "N_INVARIANT_TYPE",
                    "invariant body must be Boolean, found "
                            + invariant.specification.bodyExpression.type));
        }
        try {
            OmgContext context = new OmgContext(sm);
            OmgAs.Variable contextVariable = invariant.specification.contextVariable;
            if (contextVariable == null || contextVariable.type == null
                    || !contextVariable.type.isClass()
                    || !document.contextClassKey.equals(contextVariable.type.className())) {
                return Result.failure(Diagnostic.error(Stage.N_SM, "N_INVARIANT_CONTEXT",
                        "invariant requires one class-typed context variable"));
            }
            CoreDeclaration self = context.declare(contextVariable,
                    CoreDeclaration.Kind.SELF);
            CoreExpr body = lowerOmg(context, invariant.specification.bodyExpression);
            return Result.success(new CoreInvariant(invariant.name,
                    document.contextClassKey, self, body));
        } catch (CoreLowerError error) {
            return Result.failure(error.diagnostic());
        }
    }

    public static Result<CoreInvariant> lower(SchemaModel sm,
                                              OmgAs.OmgDocument document,
                                              OmgAs.Constraint invariant,
                                              TraceCollector traces) {
        Result<CoreInvariant> result = lower(sm, document, invariant);
        if (result.isSuccess()) {
            String name = invariant.name == null ? "<anonymous>" : invariant.name;
            traces.record(Trace.Stage.N_SM,
                    "omg-document:" + document.contextClassKey + ":constraint:" + name,
                    "core-invariant:" + document.contextClassKey + "::" + name,
                    RuleId.N_INVARIANT);
        }
        return result;
    }

    /** Lower a public OMG {@code ExpressionInOcl} value query directly by declaration identity. */
    public static Result<CoreQuery> lowerValueQuery(SchemaModel sm,
                                                    OmgAs.ExpressionInOcl query) {
        Optional<Diagnostic> admissionFailure =
                Admission.checkQueryExpression(query.bodyExpression);
        if (admissionFailure.isPresent()) {
            return Result.failure(admissionFailure.get());
        }
        try {
            OmgContext context = new OmgContext(sm);
            CoreDeclaration self = null;
            String contextClassKey = null;
            if (query.contextVariable != null) {
                if (query.contextVariable.type == null || !query.contextVariable.type.isClass()) {
                    return Result.failure(Diagnostic.error(Stage.N_SM, "N_QUERY_CONTEXT_TYPE",
                            "value-query context variable must have a class type"));
                }
                contextClassKey = query.contextVariable.type.className();
                self = context.declare(query.contextVariable, CoreDeclaration.Kind.SELF);
            }
            CoreExpr body = lowerOmg(context, query.bodyExpression);
            return Result.success(new CoreQuery(contextClassKey, self, body));
        } catch (CoreLowerError error) {
            return Result.failure(error.diagnostic());
        }
    }

    public static Result<CoreQuery> lowerValueQuery(SchemaModel sm,
                                                    OmgAs.ExpressionInOcl query,
                                                    TraceCollector traces) {
        Result<CoreQuery> result = lowerValueQuery(sm, query);
        if (result.isSuccess()) {
            traces.record(Trace.Stage.N_SM, "omg-value-query", "core-value-query",
                    RuleId.N_INVARIANT);
        }
        return result;
    }

    private static CoreExpr lowerOmg(OmgContext context, OmgAs.OclExpression node) {
        if (node instanceof OmgAs.BooleanLiteralExp value) {
            return new CoreExpr.LiteralBoolean(node.span, value.booleanSymbol);
        }
        if (node instanceof OmgAs.IntegerLiteralExp value) {
            return new CoreExpr.LiteralInteger(node.span, value.integerSymbol);
        }
        if (node instanceof OmgAs.RealLiteralExp value) {
            return new CoreExpr.LiteralReal(node.span, value.realSymbol);
        }
        if (node instanceof OmgAs.StringLiteralExp value) {
            return new CoreExpr.LiteralString(node.span, value.stringSymbol);
        }
        if (node instanceof OmgAs.VariableExp variable) {
            CoreDeclaration declaration = context.lookup(variable.referredVariable);
            if (declaration == null) {
                throw lowerError("N_UNBOUND_VARIABLE",
                        "unbound OMG declaration " + variable.referredVariable.name);
            }
            return new CoreExpr.Variable(node.span, declaration);
        }
        if (node instanceof OmgAs.AssociationClassCallExp associationClass) {
            CoreExpr source = lowerOmg(context, associationClass.source);
            if (!source.type().isClass()) {
                throw lowerError("N_ASSOCIATION_CLASS_SOURCE",
                        "association-class navigation requires a class receiver");
            }
            SchemaModel.AssociationClassNavigation navigation;
            try {
                navigation = context.schema.associationClassNavigation(
                        source.type().className(), associationClass.referredAssociationClass);
            } catch (IllegalArgumentException ambiguous) {
                throw lowerError("N_ASSOCIATION_CLASS_RESOLUTION", ambiguous.getMessage());
            }
            if (navigation == null
                    || !navigation.participantRole().equals(associationClass.navigationSource)) {
                throw lowerError("N_ASSOCIATION_CLASS_RESOLUTION",
                        "resolved association-class navigation is missing for "
                                + associationClass.referredAssociationClass);
            }
            List<CoreExpr> qualifiers = new ArrayList<>();
            for (OmgAs.OclExpression qualifier : associationClass.qualifier) {
                qualifiers.add(lowerOmg(context, qualifier));
            }
            return new CoreExpr.AssociationClassNavigation(node.span,
                    navigation.toMany() ? CoreExpr.NavKind.TO_MANY : CoreExpr.NavKind.TO_ONE,
                    source, navigation.associationClass().key(), navigation.participantRole(),
                    qualifiers, node.type, navigation.receiverIsTarget());
        }
        if (node instanceof OmgAs.PropertyCallExp property) {
            CoreExpr source = lowerOmg(context, property.source);
            var attribute = context.schema.attributeByKey(property.referredProperty);
            if (attribute != null) {
                return new CoreExpr.AttributeRead(node.span, source, attribute.ownerClassKey(),
                        attribute.name(), attribute.declaredType());
            }
            var navigation = context.schema.navigation(property.source.type.className(),
                    property.navigationSource);
            if (navigation == null) {
                throw lowerError("N_UNRESOLVED_PROPERTY",
                        "resolved graph navigation is missing for " + property.referredProperty);
            }
            List<CoreExpr> qualifiers = new ArrayList<>();
            for (OmgAs.OclExpression qualifier : property.qualifier) {
                qualifiers.add(lowerOmg(context, qualifier));
            }
            OclType target = OclType.clazz(navigation.targetClassKey());
            OclType result = navigation.toMany()
                    ? (navigation.unique() ? OclType.set(target) : OclType.bag(target)) : target;
            return new CoreExpr.Navigation(node.span,
                    navigation.toMany() ? CoreExpr.NavKind.TO_MANY : CoreExpr.NavKind.TO_ONE,
                    source, navigation.association().name(), navigation.roleName(), qualifiers,
                    result, navigation.reverse(),
                    context.schema.clazz(navigation.association().key()) != null
                            && context.schema.clazz(navigation.association().key())
                                    .isAssociationClass());
        }
        if (node instanceof OmgAs.OperationCallExp operation) {
            OclOperation kind = OclOperation.resolved(operation.referredOperation);
            if (kind == OclOperation.ALL_INSTANCES) {
                return new CoreExpr.AllInstances(node.span,
                        ((OmgAs.TypeExp) operation.source).referredType);
            }
            CoreExpr source = lowerOmg(context, operation.source);
            if (kind == OclOperation.OCL_IS_TYPE_OF
                    || kind == OclOperation.OCL_IS_KIND_OF
                    || kind == OclOperation.OCL_AS_TYPE) {
                requireOmgArity(operation, 1);
                if (!(operation.argument.get(0) instanceof OmgAs.TypeExp target)
                        || !source.type().isClass()
                        || !context.schema.hasClass(source.type().className())
                        || !context.schema.hasClass(target.referredType)
                        || !(context.schema.conforms(source.type().className(), target.referredType)
                            || context.schema.conforms(target.referredType, source.type().className()))) {
                    throw lowerError("N_TYPE_OPERATION", "type operations require resolved related class types");
                }
            }
            if (kind == OclOperation.OCL_IS_TYPE_OF
                    || kind == OclOperation.OCL_IS_KIND_OF) {
                requireOmgArity(operation, 1);
                OmgAs.TypeExp type = (OmgAs.TypeExp) operation.argument.get(0);
                return new CoreExpr.TypeTest(node.span,
                        kind == OclOperation.OCL_IS_TYPE_OF
                                ? CoreExpr.TypeTestKind.EXACT_TYPE
                                : CoreExpr.TypeTestKind.CONFORMS_TO,
                        source, type.referredType);
            }
            if (kind == OclOperation.OCL_AS_TYPE) {
                requireOmgArity(operation, 1);
                return new CoreExpr.TypeCast(node.span, source,
                        ((OmgAs.TypeExp) operation.argument.get(0)).referredType);
            }
            if (operation.argument.isEmpty()) {
                return new CoreExpr.Unary(node.span, mapUnary(kind), source, node.type);
            }
            requireOmgArity(operation, 1);
            return new CoreExpr.Binary(node.span, mapBinary(kind), source,
                    lowerOmg(context, operation.argument.get(0)), node.type);
        }
        if (node instanceof OmgAs.IteratorExp iterator) {
            CoreExpr source = lowerOmg(context, iterator.source);
            OmgAs.Variable variable = iterator.iterator.get(0);
            CoreDeclaration declaration = context.declare(variable,
                    CoreDeclaration.Kind.ITERATOR);
            CoreExpr body;
            try {
                body = lowerOmg(context, iterator.body);
            } finally {
                context.leave(variable, declaration);
            }
            CoreExpr.IteratorKind kind = switch (iterator.name) {
                case "select" -> CoreExpr.IteratorKind.SELECT;
                case "reject" -> CoreExpr.IteratorKind.REJECT;
                case "exists" -> CoreExpr.IteratorKind.EXISTS;
                case "forAll" -> CoreExpr.IteratorKind.FORALL;
                case "collect" -> CoreExpr.IteratorKind.COLLECT;
                default -> throw lowerError("N_UNSUPPORTED_ITERATOR",
                        "unknown resolved iterator " + iterator.name);
            };
            CoreExpr.CollectionKind sourceKind = source.type().kind() == OclType.Kind.SET
                    ? CoreExpr.CollectionKind.SET : CoreExpr.CollectionKind.BAG;
            return new CoreExpr.Iterator(node.span, kind, sourceKind, source, declaration,
                    body, node.type);
        }
        if (node instanceof OmgAs.CollectionLiteralExp collection) {
            List<CoreExpr> elements = new ArrayList<>();
            for (OmgAs.CollectionLiteralPart part : collection.part) {
                elements.add(lowerOmg(context, ((OmgAs.CollectionItem) part).item));
            }
            return new CoreExpr.CollectionLiteral(node.span,
                    collection.kind == OmgAs.OmgCollectionKind.SET
                            ? CoreExpr.CollectionKind.SET : CoreExpr.CollectionKind.BAG,
                    elements, node.type);
        }
        if (node instanceof OmgAs.IfExp conditional) {
            return new CoreExpr.IfExpr(node.span,
                    lowerOmg(context, conditional.condition),
                    lowerOmg(context, conditional.thenExpression),
                    lowerOmg(context, conditional.elseExpression), node.type);
        }
        if (node instanceof OmgAs.LetExp let) {
            CoreExpr value = lowerOmg(context, let.variable.initExpression);
            value = coerce(context, value, let.variable.type);
            CoreDeclaration declaration = context.declare(let.variable,
                    CoreDeclaration.Kind.LET);
            CoreExpr body;
            try {
                body = lowerOmg(context, let.in);
            } finally {
                context.leave(let.variable, declaration);
            }
            return new CoreExpr.Let(node.span, declaration, value, body);
        }
        if (node instanceof OmgAs.CoerceExp coercion) {
            return new CoreExpr.Coerce(node.span, coercion.kind, coercion.sourceType,
                    lowerOmg(context, coercion.sourceExpression), node.type);
        }
        throw lowerError("N_UNCOVERED_CONSTRUCTOR",
                "no Core lowering rule for " + node.getClass().getSimpleName());
    }

    private static void requireOmgArity(OmgAs.OperationCallExp operation, int expected) {
        if (operation.argument.size() != expected) {
            throw lowerError("N_ARITY", operation.referredOperation + " requires "
                    + expected + " argument(s)");
        }
    }

    /** N-COERCE: resolved AS may still require explicit boundary coercion. */
    private static CoreExpr coerce(OmgContext context, CoreExpr value, OclType target) {
        OclType source = value.type();
        if (target == null || !org.uet.dse.ocl2cypher.runtime.TypeConformance.conforms(
                source, target, context.schema::conforms)) {
            throw lowerError("N_TYPE", "coercion is outside OCL_val: " + source + " -> " + target);
        }
        if (source.equals(target)) return value;
        CoreExpr.CoercionKind kind = source.isCollection()
                ? CoreExpr.CoercionKind.COLLECTION_ELEMENT_COERCION
                : source.isClass() ? CoreExpr.CoercionKind.CLASS_UPCAST
                : CoreExpr.CoercionKind.INTEGER_TO_REAL;
        return new CoreExpr.Coerce(value.span, kind, source, value, target);
    }

    private static CoreLowerError lowerError(String code, String message) {
        return new CoreLowerError(Diagnostic.error(Stage.N_SM, code, message));
    }

    private static final class OmgContext {
        private final SchemaModel schema;
        /** Only declarations whose lexical scope contains the current node. */
        private final IdentityHashMap<OmgAs.Variable, CoreDeclaration> declarations =
                new IdentityHashMap<>();
        /** Reject reusing one OMG declaration object as two binder occurrences. */
        private final IdentityHashMap<OmgAs.Variable, Boolean> declaredOnce =
                new IdentityHashMap<>();
        private int nextId = 1;

        private OmgContext(SchemaModel schema) {
            this.schema = schema;
        }

        private CoreDeclaration declare(OmgAs.Variable variable, CoreDeclaration.Kind kind) {
            CoreDeclaration declaration = new CoreDeclaration(nextId++, variable.name, kind,
                    variable.type);
            if (declaredOnce.put(variable, Boolean.TRUE) != null
                    || declarations.put(variable, declaration) != null) {
                throw lowerError("N_DUPLICATE_DECLARATION",
                        "OMG declaration was bound twice: " + variable.name);
            }
            return declaration;
        }

        private void leave(OmgAs.Variable variable, CoreDeclaration expected) {
            CoreDeclaration removed = declarations.remove(variable);
            if (removed != expected) {
                throw lowerError("N_BINDING_SCOPE",
                        "internal declaration scope mismatch: " + variable.name);
            }
        }

        private CoreDeclaration lookup(OmgAs.Variable variable) {
            return declarations.get(variable);
        }
    }

    private static CoreExpr.UnaryOp mapUnary(OclOperation op) {
        return switch (op) {
            case BOOLEAN_NOT -> CoreExpr.UnaryOp.BOOLEAN_NOT;
            case NUMERIC_NEGATE -> CoreExpr.UnaryOp.NUMERIC_NEGATE;
            case NUMERIC_ABS -> CoreExpr.UnaryOp.NUMERIC_ABS;
            case REAL_FLOOR -> CoreExpr.UnaryOp.REAL_FLOOR;
            case REAL_ROUND -> CoreExpr.UnaryOp.REAL_ROUND;
            case COLLECTION_SIZE -> CoreExpr.UnaryOp.COLLECTION_SIZE;
            case COLLECTION_IS_EMPTY -> CoreExpr.UnaryOp.COLLECTION_IS_EMPTY;
            case COLLECTION_NOT_EMPTY -> CoreExpr.UnaryOp.COLLECTION_NOT_EMPTY;
            case COLLECTION_SUM -> CoreExpr.UnaryOp.COLLECTION_SUM;
            default -> throw new IllegalArgumentException("not a unary op: " + op);
        };
    }

    private static CoreExpr.BinaryOp mapBinary(OclOperation op) {
        return switch (op) {
            case NUMERIC_ADD -> CoreExpr.BinaryOp.NUMERIC_ADD;
            case NUMERIC_SUBTRACT -> CoreExpr.BinaryOp.NUMERIC_SUBTRACT;
            case NUMERIC_MULTIPLY -> CoreExpr.BinaryOp.NUMERIC_MULTIPLY;
            case REAL_DIVIDE -> CoreExpr.BinaryOp.REAL_DIVIDE;
            case INTEGER_DIVIDE -> CoreExpr.BinaryOp.INTEGER_DIVIDE;
            case INTEGER_MOD -> CoreExpr.BinaryOp.INTEGER_MOD;
            case NUMERIC_MAX -> CoreExpr.BinaryOp.NUMERIC_MAX;
            case NUMERIC_MIN -> CoreExpr.BinaryOp.NUMERIC_MIN;
            case LESS_THAN -> CoreExpr.BinaryOp.LESS_THAN;
            case LESS_THAN_OR_EQUAL -> CoreExpr.BinaryOp.LESS_THAN_OR_EQUAL;
            case GREATER_THAN -> CoreExpr.BinaryOp.GREATER_THAN;
            case GREATER_THAN_OR_EQUAL -> CoreExpr.BinaryOp.GREATER_THAN_OR_EQUAL;
            case VALUE_EQUAL -> CoreExpr.BinaryOp.VALUE_EQUAL;
            case VALUE_NOT_EQUAL -> CoreExpr.BinaryOp.VALUE_NOT_EQUAL;
            case BOOLEAN_AND -> CoreExpr.BinaryOp.BOOLEAN_AND;
            case BOOLEAN_OR -> CoreExpr.BinaryOp.BOOLEAN_OR;
            case BOOLEAN_XOR -> CoreExpr.BinaryOp.BOOLEAN_XOR;
            case BOOLEAN_IMPLIES -> CoreExpr.BinaryOp.BOOLEAN_IMPLIES;
            case COLLECTION_COUNT -> CoreExpr.BinaryOp.COLLECTION_COUNT;
            case COLLECTION_INCLUDES -> CoreExpr.BinaryOp.COLLECTION_INCLUDES;
            case COLLECTION_EXCLUDES -> CoreExpr.BinaryOp.COLLECTION_EXCLUDES;
            case COLLECTION_INCLUDES_ALL -> CoreExpr.BinaryOp.COLLECTION_INCLUDES_ALL;
            case COLLECTION_EXCLUDES_ALL -> CoreExpr.BinaryOp.COLLECTION_EXCLUDES_ALL;
            case SET_UNION -> CoreExpr.BinaryOp.SET_UNION;
            case SET_INTERSECTION -> CoreExpr.BinaryOp.SET_INTERSECTION;
            default -> throw new IllegalArgumentException("not a binary op: " + op);
        };
    }

    static final class CoreLowerError extends RuntimeException {
        private final Diagnostic diagnostic;

        CoreLowerError(Diagnostic diagnostic) {
            super(diagnostic.toString(), null, false, false);
            this.diagnostic = diagnostic;
        }

        Diagnostic diagnostic() {
            return diagnostic;
        }
    }
}
