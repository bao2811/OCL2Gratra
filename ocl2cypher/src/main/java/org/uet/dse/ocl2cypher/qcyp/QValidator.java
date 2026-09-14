package org.uet.dse.ocl2cypher.qcyp;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import org.uet.dse.ocl2cypher.core.CoreDeclaration;
import org.uet.dse.ocl2cypher.core.CoreExpr;
import org.uet.dse.ocl2cypher.runtime.OclType;

/** Structural, typing and lexical-scope validator for the executable Java Q profile. */
public final class QValidator {
    private QValidator() {
    }

    public record Error(String path, String message) {
    }

    public static List<Error> validate(QQuery query) {
        List<Error> errors = new ArrayList<>();
        if (query == null) {
            errors.add(new Error("query", "null"));
            return List.copyOf(errors);
        }
        Context context = new Context(errors);
        validateRoot(query, context);
        if (query.expressionBody() != null) {
            expression(query.expressionBody(), "expression", context);
        }
        if (query.planBody() != null) {
            plan(query.planBody(), "plan", context);
        }
        return List.copyOf(errors);
    }

    private static void validateRoot(QQuery query, Context context) {
        boolean hasExpression = query.expressionBody() != null;
        boolean hasPlan = query.planBody() != null;
        if (hasExpression == hasPlan) {
            context.error("body", "exactly one body is required");
        }

        String classKey = query.contextClassKey();
        CoreDeclaration self = query.selfVariable();
        if ((classKey == null) != (self == null)) {
            context.error("context", "class/self mismatch");
        }
        if (classKey != null && classKey.isBlank()) {
            context.error("context.class", "blank class key");
        }
        if (self != null) {
            declare(self, CoreDeclaration.Kind.SELF, "self", context);
            if (!self.type().isClass()) {
                context.error("self.type", "self must have a class type");
            } else if (classKey != null && !classKey.equals(self.type().className())) {
                context.error("self.type", "self type does not match context class");
            }
            context.active.put(self, Boolean.TRUE);
        }

        if (query.mode() == null) {
            context.error("mode", "missing query mode");
            return;
        }
        if (query.resultShape() == null) {
            context.error("resultShape", "missing result shape");
        }
        if (query.mode() == QQuery.QueryMode.VIOLATIONS) {
            if (!hasExpression || hasPlan) {
                context.error("body", "VIOLATIONS requires expression body");
            }
            if (query.resultShape() != QQuery.QResultShape.IDS || !query.identityProjection()) {
                context.error("resultShape", "VIOLATIONS requires identity IDS projection");
            }
            if (classKey == null || self == null) {
                context.error("context", "VIOLATIONS requires context class and self");
            }
            if (query.resultType() != null) {
                context.error("resultType", "VIOLATIONS has no public value result type");
            }
            if (hasExpression && !OclType.BOOLEAN.equals(query.expressionBody().type)) {
                context.error("expression.type", "VIOLATIONS body must be Boolean");
            }
        } else {
            if (query.identityProjection()) {
                context.error("identityProjection", "VALUE must not project violation identities");
            }
            if (query.resultType() == null) {
                context.error("resultType", "VALUE requires result type");
            } else {
                checkType(query.resultType(), "resultType", context);
                OclType bodyType = hasExpression ? query.expressionBody().type
                        : (hasPlan ? query.planBody().type : null);
                if (!query.resultType().equals(bodyType)) {
                    context.error("resultType", "does not match body type");
                }
                QQuery.QResultShape expected = switch (query.resultType().kind()) {
                    case SET -> QQuery.QResultShape.SET;
                    case BAG -> QQuery.QResultShape.BAG;
                    default -> QQuery.QResultShape.SCALAR;
                };
                if (query.resultShape() != expected) {
                    context.error("resultShape", "does not match result type");
                }
            }
        }
    }

    private static void expression(QNode.QExpr node, String path, Context context) {
        if (node == null) {
            context.error(path, "missing expression");
            return;
        }
        checkType(node.type, path + ".type", context);

        if (node instanceof QNode.QExpr.Variable variable) {
            if (!context.active.containsKey(variable.declaration)) {
                context.error(path + ".declaration", "variable refers outside lexical scope");
            }
            requireType(node.type, variable.declaration.type(), path, context);
        } else if (node instanceof QNode.QExpr.Parameter parameter) {
            requireName(parameter.parameter.name(), path + ".parameter.name", context);
            if (parameter.parameter.name().startsWith("__ocl")) {
                context.error(path + ".parameter.name", "reserved generated namespace");
            }
            requireType(parameter.type, parameter.parameter.expectedType(), path, context);
            QNode.QParameter prior = context.parameters.putIfAbsent(
                    parameter.parameter.name(), parameter.parameter);
            if (prior != null && prior != parameter.parameter) {
                context.error(path + ".parameter",
                        "parameter name is owned by a different declaration");
            }
        } else if (node instanceof QNode.QExpr.Bottom) {
            // Every OCL_val type is a legal bottom carrier.
        } else if (node instanceof QNode.QExpr.Constant constant) {
            validateConstant(constant, path, context);
        } else if (node instanceof QNode.QExpr.Coerce coercion) {
            expression(coercion.source, path + ".source", context);
            if (!coercion.sourceType.equals(coercion.source.type)) {
                context.error(path + ".sourceType", "does not equal source type");
            }
            boolean valid = switch (coercion.kind) {
                case INTEGER_TO_REAL -> coercion.sourceType.equals(OclType.INTEGER)
                        && coercion.type.equals(OclType.REAL);
                case CLASS_UPCAST -> coercion.sourceType.isClass() && coercion.type.isClass();
                case COLLECTION_ELEMENT_COERCION -> coercion.sourceType.isCollection()
                        && coercion.type.isCollection()
                        && coercion.sourceType.kind() == coercion.type.kind()
                        && coercion.sourceType.elementType().isAtomic()
                        && coercion.type.elementType().isAtomic();
            };
            if (!valid || coercion.sourceType.equals(coercion.type)) {
                context.error(path, "invalid coercion signature");
            }
        } else if (node instanceof QNode.QExpr.Let let) {
            expression(let.value, path + ".value", context);
            declare(let.binder, CoreDeclaration.Kind.LET, path + ".binder", context);
            requireType(let.value.type, let.binder.type(), path + ".value", context);
            context.active.put(let.binder, Boolean.TRUE);
            expression(let.body, path + ".body", context);
            context.active.remove(let.binder);
            requireType(let.type, let.body.type, path, context);
        } else if (node instanceof QNode.QExpr.IfExpr conditional) {
            expression(conditional.condition, path + ".condition", context);
            expression(conditional.thenExpr, path + ".then", context);
            expression(conditional.elseExpr, path + ".else", context);
            requireType(conditional.condition.type, OclType.BOOLEAN,
                    path + ".condition", context);
            requireType(conditional.thenExpr.type, conditional.type, path + ".then", context);
            requireType(conditional.elseExpr.type, conditional.type, path + ".else", context);
        } else if (node instanceof QNode.QExpr.ReadAttribute read) {
            expression(read.source, path + ".source", context);
            requireClass(read.source.type, path + ".source", context);
            requireName(read.ownerClassKey, path + ".ownerClassKey", context);
            requireName(read.attributeName, path + ".attributeName", context);
            if (!read.type.isAtomic()) {
                context.error(path + ".type", "attribute result must be atomic");
            }
        } else if (node instanceof QNode.QExpr.NavigateOne navigation) {
            expression(navigation.source, path + ".source", context);
            requireClass(navigation.source.type, path + ".source", context);
            requireClass(navigation.type, path, context);
            validateNavigationMetadata(navigation.associationName, navigation.roleName,
                    navigation.associationClass, navigation.viaAssociationClass, path, context);
            expressions(navigation.qualifiers, path + ".qualifiers", context);
            requireAtomicQualifiers(navigation.qualifiers, path, context);
        } else if (node instanceof QNode.QExpr.TypeTest test) {
            expression(test.source, path + ".source", context);
            requireClass(test.source.type, path + ".source", context);
            requireName(test.targetClassKey, path + ".targetClassKey", context);
            requireType(test.type, OclType.BOOLEAN, path, context);
        } else if (node instanceof QNode.QExpr.TypeCast cast) {
            expression(cast.source, path + ".source", context);
            requireClass(cast.source.type, path + ".source", context);
            requireName(cast.targetClassKey, path + ".targetClassKey", context);
            requireType(cast.type, OclType.clazz(cast.targetClassKey), path, context);
        } else if (node instanceof QNode.QExpr.Unary unary) {
            expression(unary.operand, path + ".operand", context);
            validateUnary(unary.operator, unary.operand.type, unary.type, path, context);
        } else if (node instanceof QNode.QExpr.Binary binary) {
            expression(binary.left, path + ".left", context);
            expression(binary.right, path + ".right", context);
            validateBinary(binary.operator, binary.left.type, binary.right.type,
                    binary.type, path, context);
        } else if (node instanceof QNode.QExpr.Exists3 exists) {
            validateFold(exists.source, exists.iterator, exists.predicate, exists.type,
                    path, context);
        } else if (node instanceof QNode.QExpr.ForAll3 forall) {
            validateFold(forall.source, forall.iterator, forall.predicate, forall.type,
                    path, context);
        } else if (node instanceof QNode.QExpr.CollectionLiteral literal) {
            expressions(literal.elements, path + ".elements", context);
            validateCollectionLiteral(literal, path, context);
        } else if (node instanceof QNode.QExpr.IncludesFamily includes) {
            expression(includes.source, path + ".source", context);
            expression(includes.element, path + ".element", context);
            validateIncludes(includes, path, context);
        } else if (node instanceof QNode.QExpr.CountFamily count) {
            expression(count.source, path + ".source", context);
            if (count.element != null) {
                expression(count.element, path + ".element", context);
            }
            validateCount(count, path, context);
        } else if (node instanceof QNode.QExpr.SetAlgebra algebra) {
            expression(algebra.left, path + ".left", context);
            expression(algebra.right, path + ".right", context);
            boolean operator = algebra.operator == CoreExpr.BinaryOp.SET_UNION
                    || algebra.operator == CoreExpr.BinaryOp.SET_INTERSECTION;
            if (!operator || algebra.left.type.kind() != OclType.Kind.SET
                    || !algebra.right.type.equals(algebra.left.type)
                    || !algebra.type.equals(algebra.left.type)) {
                context.error(path, "invalid set-algebra signature");
            }
        } else if (node instanceof QNode.QExpr.Materialize materialize) {
            plan(materialize.plan, path + ".plan", context);
            if (!materialize.plan.type.isCollection()) {
                context.error(path + ".plan.type", "materialized plan must be collection-valued");
            }
            requireType(materialize.type, materialize.plan.type, path, context);
        } else {
            context.error(path, "uncovered Q expression " + node.getClass().getName());
        }
    }

    private static void plan(QNode.QPlan node, String path, Context context) {
        if (node == null) {
            context.error(path, "missing plan");
            return;
        }
        checkType(node.type, path + ".type", context);
        if (node instanceof QNode.QPlan.FromCollection from) {
            expression(from.collection, path + ".collection", context);
            if (!from.collection.type.isCollection()) {
                context.error(path + ".collection.type", "plan source must be a collection");
            }
            requireType(from.type, from.collection.type, path, context);
        } else if (node instanceof QNode.QPlan.ScanClass scan) {
            requireName(scan.classKey, path + ".classKey", context);
            validateSyntheticScan(scan.variable, scan.classKey, path + ".variable", context);
            requireType(scan.type, OclType.set(OclType.clazz(scan.classKey)), path, context);
        } else if (node instanceof QNode.QPlan.NavigateMany navigation) {
            expression(navigation.source, path + ".source", context);
            requireClass(navigation.source.type, path + ".source", context);
            if (!navigation.type.isCollection() || !navigation.type.elementType().isClass()) {
                context.error(path + ".type", "many-navigation must return collection of objects");
            } else if (!navigation.elementType.equals(navigation.type.elementType())) {
                context.error(path + ".elementType", "does not match collection element type");
            }
            validateNavigationMetadata(navigation.associationName, navigation.roleName,
                    navigation.associationClass, navigation.viaAssociationClass, path, context);
            expressions(navigation.qualifiers, path + ".qualifiers", context);
            requireAtomicQualifiers(navigation.qualifiers, path, context);
        } else if (node instanceof QNode.QPlan.Filter filter) {
            plan(filter.source, path + ".source", context);
            declareIterator(filter.iterator, filter.source.type, path + ".iterator", context);
            context.active.put(filter.iterator, Boolean.TRUE);
            expression(filter.predicate, path + ".predicate", context);
            context.active.remove(filter.iterator);
            requireType(filter.predicate.type, OclType.BOOLEAN, path + ".predicate", context);
            requireType(filter.type, filter.source.type, path, context);
        } else if (node instanceof QNode.QPlan.Collect collect) {
            plan(collect.source, path + ".source", context);
            declareIterator(collect.iterator, collect.source.type, path + ".iterator", context);
            context.active.put(collect.iterator, Boolean.TRUE);
            expression(collect.body, path + ".body", context);
            context.active.remove(collect.iterator);
            if (!collect.body.type.isAtomic()) {
                context.error(path + ".body.type", "collect body must be atomic");
            } else {
                requireType(collect.type, OclType.bag(collect.body.type), path, context);
            }
        } else if (node instanceof QNode.QPlan.Distinct distinct) {
            plan(distinct.source, path + ".source", context);
            if (!distinct.source.type.isCollection()) {
                context.error(path + ".source.type", "distinct source must be a collection");
            } else {
                requireType(distinct.type, OclType.set(distinct.source.type.elementType()),
                        path, context);
            }
        } else if (node instanceof QNode.QPlan.PlanLet let) {
            expression(let.value, path + ".value", context);
            declare(let.binder, CoreDeclaration.Kind.LET, path + ".binder", context);
            requireType(let.value.type, let.binder.type(), path + ".value", context);
            context.active.put(let.binder, Boolean.TRUE);
            plan(let.body, path + ".body", context);
            context.active.remove(let.binder);
            requireType(let.type, let.body.type, path, context);
        } else {
            context.error(path, "uncovered Q plan " + node.getClass().getName());
        }
    }

    private static void validateFold(QNode.QPlan source, CoreDeclaration iterator,
                                     QNode.QExpr predicate, OclType result, String path,
                                     Context context) {
        plan(source, path + ".source", context);
        declareIterator(iterator, source.type, path + ".iterator", context);
        context.active.put(iterator, Boolean.TRUE);
        expression(predicate, path + ".predicate", context);
        context.active.remove(iterator);
        requireType(predicate.type, OclType.BOOLEAN, path + ".predicate", context);
        requireType(result, OclType.BOOLEAN, path, context);
    }

    private static void validateConstant(QNode.QExpr.Constant node, String path,
                                         Context context) {
        boolean valid = node.type.equals(OclType.BOOLEAN) && node.literalValue instanceof Boolean
                || node.type.equals(OclType.INTEGER) && node.literalValue instanceof BigInteger
                || node.type.equals(OclType.REAL) && node.literalValue instanceof BigDecimal
                || node.type.equals(OclType.STRING) && node.literalValue instanceof String
                || node.type.isClass() && node.literalValue instanceof String stableId
                        && !stableId.isEmpty();
        if (!valid) context.error(path, "literal carrier does not match constant type");
    }

    private static void validateUnary(CoreExpr.UnaryOp operator, OclType operand,
                                      OclType result, String path, Context context) {
        boolean valid = switch (operator) {
            case BOOLEAN_NOT -> operand.equals(OclType.BOOLEAN) && result.equals(OclType.BOOLEAN);
            case NUMERIC_NEGATE, NUMERIC_ABS -> operand.isNumeric() && result.equals(operand);
            case REAL_FLOOR, REAL_ROUND -> operand.equals(OclType.REAL)
                    && result.equals(OclType.INTEGER);
            case COLLECTION_SIZE -> operand.isCollection() && result.equals(OclType.INTEGER);
            case COLLECTION_IS_EMPTY, COLLECTION_NOT_EMPTY -> operand.isCollection()
                    && result.equals(OclType.BOOLEAN);
            case COLLECTION_SUM -> operand.isCollection() && operand.elementType().isNumeric()
                    && result.equals(operand.elementType());
        };
        if (!valid) context.error(path, "invalid unary signature for " + operator);
    }

    private static void validateBinary(CoreExpr.BinaryOp operator, OclType left, OclType right,
                                       OclType result, String path, Context context) {
        boolean valid = switch (operator) {
            case NUMERIC_ADD, NUMERIC_SUBTRACT, NUMERIC_MULTIPLY, NUMERIC_MAX, NUMERIC_MIN ->
                    left.isNumeric() && right.equals(left) && result.equals(left);
            case REAL_DIVIDE -> left.equals(OclType.REAL) && right.equals(OclType.REAL)
                    && result.equals(OclType.REAL);
            case INTEGER_DIVIDE, INTEGER_MOD -> left.equals(OclType.INTEGER)
                    && right.equals(OclType.INTEGER) && result.equals(OclType.INTEGER);
            case LESS_THAN, LESS_THAN_OR_EQUAL, GREATER_THAN, GREATER_THAN_OR_EQUAL ->
                    left.isNumeric() && right.equals(left) && result.equals(OclType.BOOLEAN);
            case VALUE_EQUAL, VALUE_NOT_EQUAL -> right.equals(left)
                    && result.equals(OclType.BOOLEAN);
            case BOOLEAN_AND, BOOLEAN_OR, BOOLEAN_XOR, BOOLEAN_IMPLIES ->
                    left.equals(OclType.BOOLEAN) && right.equals(OclType.BOOLEAN)
                            && result.equals(OclType.BOOLEAN);
            case COLLECTION_COUNT -> left.isCollection() && right.equals(left.elementType())
                    && result.equals(OclType.INTEGER);
            case COLLECTION_INCLUDES, COLLECTION_EXCLUDES -> left.isCollection()
                    && right.equals(left.elementType()) && result.equals(OclType.BOOLEAN);
            case COLLECTION_INCLUDES_ALL, COLLECTION_EXCLUDES_ALL -> left.isCollection()
                    && right.equals(left) && result.equals(OclType.BOOLEAN);
            case SET_UNION, SET_INTERSECTION -> left.kind() == OclType.Kind.SET
                    && right.equals(left) && result.equals(left);
        };
        if (!valid) context.error(path, "invalid binary signature for " + operator);
    }

    private static void validateCollectionLiteral(QNode.QExpr.CollectionLiteral node,
                                                  String path, Context context) {
        if (!node.type.isCollection()) {
            context.error(path + ".type", "collection literal must have collection type");
            return;
        }
        OclType.Kind expected = node.collectionKind == CoreExpr.CollectionKind.SET
                ? OclType.Kind.SET : OclType.Kind.BAG;
        if (node.type.kind() != expected) {
            context.error(path + ".kind", "kind/type mismatch");
        }
        for (int index = 0; index < node.elements.size(); index++) {
            requireType(node.elements.get(index).type, node.type.elementType(),
                    path + ".elements[" + index + "]", context);
        }
    }

    private static void validateIncludes(QNode.QExpr.IncludesFamily node,
                                         String path, Context context) {
        if (!node.source.type.isCollection()) {
            context.error(path + ".source.type", "includes source must be a collection");
            return;
        }
        boolean scalar = node.includesKind == QNode.QKind.INCLUDES
                || node.includesKind == QNode.QKind.EXCLUDES;
        boolean collection = node.includesKind == QNode.QKind.INCLUDES_ALL
                || node.includesKind == QNode.QKind.EXCLUDES_ALL;
        OclType expected = scalar ? node.source.type.elementType() : node.source.type;
        if ((!scalar && !collection) || !node.element.type.equals(expected)
                || !node.type.equals(OclType.BOOLEAN)) {
            context.error(path, "invalid includes-family signature");
        }
    }

    private static void validateCount(QNode.QExpr.CountFamily node,
                                      String path, Context context) {
        if (!node.source.type.isCollection()) {
            context.error(path + ".source.type", "count-family source must be a collection");
            return;
        }
        boolean valid = switch (node.countKind) {
            case COUNT -> node.element != null
                    && node.element.type.equals(node.source.type.elementType())
                    && node.type.equals(OclType.INTEGER);
            case SIZE -> node.element == null && node.type.equals(OclType.INTEGER);
            case IS_EMPTY, NOT_EMPTY -> node.element == null && node.type.equals(OclType.BOOLEAN);
            case SUM -> node.element == null && node.source.type.elementType().isNumeric()
                    && node.type.equals(node.source.type.elementType());
            default -> false;
        };
        if (!valid) context.error(path, "invalid count-family signature");
    }

    private static void declareIterator(CoreDeclaration declaration, OclType sourceType,
                                        String path, Context context) {
        declare(declaration, CoreDeclaration.Kind.ITERATOR, path, context);
        if (!sourceType.isCollection()) {
            context.error(path, "iterator source must be collection-valued");
        } else if (!declaration.type().equals(sourceType.elementType())) {
            context.error(path + ".type", "does not match source element type");
        }
    }

    private static void declare(CoreDeclaration declaration, CoreDeclaration.Kind expected,
                                String path, Context context) {
        if (declaration == null) {
            context.error(path, "missing declaration");
            return;
        }
        if (context.owned.put(declaration, Boolean.TRUE) != null) {
            context.error(path, "declaration object is bound more than once");
        }
        if (!context.ids.add(declaration.id())) {
            context.error(path + ".id", "duplicate declaration id");
        }
        if (declaration.id() <= 0) {
            context.error(path + ".id", "declaration id must be positive");
        }
        if (declaration.name().isBlank()) {
            context.error(path + ".name", "blank declaration name");
        }
        if (declaration.kind() != expected) {
            context.error(path + ".kind", "expected " + expected);
        }
        checkType(declaration.type(), path + ".type", context);
    }

    /** Scan variables are generated metadata and are not lexical binders in the Java Q tree. */
    private static void validateSyntheticScan(CoreDeclaration variable, String classKey,
                                              String path, Context context) {
        if (variable == null) {
            context.error(path, "missing generated scan variable");
            return;
        }
        if (variable.kind() != CoreDeclaration.Kind.ITERATOR) {
            context.error(path + ".kind", "scan variable must be ITERATOR");
        }
        requireType(variable.type(), OclType.clazz(classKey), path, context);
    }

    private static void validateNavigationMetadata(String association, String role,
                                                   boolean associationClass,
                                                   boolean viaAssociationClass,
                                                   String path, Context context) {
        requireName(association, path + ".associationName", context);
        requireName(role, path + ".roleName", context);
        if (associationClass && viaAssociationClass) {
            context.error(path,
                    "association-class result and participant-via-class are exclusive");
        }
    }

    private static void expressions(List<QNode.QExpr> nodes, String path, Context context) {
        for (int index = 0; index < nodes.size(); index++) {
            expression(nodes.get(index), path + "[" + index + "]", context);
        }
    }

    private static void requireAtomicQualifiers(List<QNode.QExpr> qualifiers,
                                                String path, Context context) {
        for (int index = 0; index < qualifiers.size(); index++) {
            if (!qualifiers.get(index).type.isAtomic()) {
                context.error(path + ".qualifiers[" + index + "].type",
                        "qualifier must be atomic");
            }
        }
    }

    private static void requireClass(OclType type, String path, Context context) {
        if (!type.isClass()) {
            context.error(path + ".type", "class type required");
        }
    }

    private static void requireName(String name, String path, Context context) {
        if (name == null || name.isBlank()) {
            context.error(path, "missing or blank name");
        }
    }

    private static void requireType(OclType actual, OclType expected,
                                    String path, Context context) {
        if (!expected.equals(actual)) {
            context.error(path + ".type", "expected " + expected + " but found " + actual);
        }
    }

    private static void checkType(OclType type, String path, Context context) {
        if (type == null) {
            context.error(path, "missing type");
            return;
        }
        OclType canonical = switch (type.kind()) {
            case BOOLEAN -> OclType.BOOLEAN;
            case INTEGER -> OclType.INTEGER;
            case REAL -> OclType.REAL;
            case STRING -> OclType.STRING;
            case CLASS -> OclType.clazz(type.className());
            case SET -> OclType.set(type.elementType());
            case BAG -> OclType.bag(type.elementType());
        };
        if (canonical != type) {
            context.error(path, "type is not identity-interned");
        }
    }

    private static final class Context {
        final List<Error> errors;
        final IdentityHashMap<CoreDeclaration, Boolean> active = new IdentityHashMap<>();
        final IdentityHashMap<CoreDeclaration, Boolean> owned = new IdentityHashMap<>();
        final Set<Integer> ids = new HashSet<>();
        final java.util.Map<String, QNode.QParameter> parameters = new java.util.LinkedHashMap<>();

        Context(List<Error> errors) {
            this.errors = errors;
        }

        void error(String path, String message) {
            errors.add(new Error(path, message));
        }
    }
}
