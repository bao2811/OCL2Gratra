package org.uet.dse.ocl2cypher.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import org.uet.dse.ocl2cypher.runtime.OclType;
import org.uet.dse.ocl2cypher.source.model.SchemaModel;
import org.uet.dse.ocl2cypher.source.model.UmlAssociation;
import org.uet.dse.ocl2cypher.source.model.UmlAttribute;
import org.uet.dse.ocl2cypher.source.model.UmlQualifier;

/** Structural, typing, lexical-scope and optional schema validator for Java Core IR. */
public final class CoreValidator {
    private CoreValidator() {
    }

    public record Error(String path, String message) {
    }

    /** Validate every condition that does not require access to the source schema. */
    public static List<Error> validate(CoreUnit unit) {
        return validate(null, unit);
    }

    /** Validate Core well-formedness, including UML resolution against {@code schema}. */
    public static List<Error> validate(SchemaModel schema, CoreUnit unit) {
        List<Error> errors = new ArrayList<>();
        if (unit == null) {
            errors.add(new Error("unit", "null"));
            return List.copyOf(errors);
        }

        Context context = new Context(schema, errors);
        validateRoot(unit, context);
        if (unit.body() != null) {
            visit(unit.body(), "body", context);
        }
        return List.copyOf(errors);
    }

    private static void validateRoot(CoreUnit unit, Context context) {
        CoreDeclaration self = unit.selfVariable();
        String classKey = unit.contextClassKey();
        if ((classKey == null) != (self == null)) {
            context.error("context", "class/self mismatch");
        }
        if (classKey != null) {
            if (classKey.isBlank()) {
                context.error("context.class", "blank class key");
            }
            if (context.schema != null && !context.schema.hasClass(classKey)) {
                context.error("context.class", "class is absent from schema: " + classKey);
            }
        }
        if (self != null) {
            declare(self, CoreDeclaration.Kind.SELF, "self", context);
            if (!self.type().isClass()) {
                context.error("self.type", "self must have a class type");
            } else if (classKey != null && !self.type().className().equals(classKey)) {
                context.error("self.type", "self type does not match context class");
            }
            context.active.put(self, Boolean.TRUE);
        }
        if (unit.isInvariant()) {
            if (classKey == null || self == null) {
                context.error("context", "invariant requires context class and self");
            }
            if (!OclType.BOOLEAN.equals(unit.body().type())) {
                context.error("body.type", "invariant body must be Boolean");
            }
        }
    }

    private static void visit(CoreExpr expression, String path, Context context) {
        if (expression == null) {
            context.error(path, "missing expression");
            return;
        }
        checkType(expression.type(), path + ".type", context);

        if (expression instanceof CoreExpr.LiteralBoolean) {
            requireType(expression, OclType.BOOLEAN, path, context);
        } else if (expression instanceof CoreExpr.LiteralInteger) {
            requireType(expression, OclType.INTEGER, path, context);
        } else if (expression instanceof CoreExpr.LiteralReal) {
            requireType(expression, OclType.REAL, path, context);
        } else if (expression instanceof CoreExpr.LiteralString) {
            requireType(expression, OclType.STRING, path, context);
        } else if (expression instanceof CoreExpr.Bottom) {
            // Every OCL_val type is a legal bottom carrier.
        } else if (expression instanceof CoreExpr.Variable variable) {
            if (!context.active.containsKey(variable.declaration)) {
                context.error(path + ".declaration", "variable refers outside lexical scope");
            }
            requireType(expression, variable.declaration.type(), path, context);
        } else if (expression instanceof CoreExpr.Let let) {
            visit(let.value, path + ".value", context);
            declare(let.binder, CoreDeclaration.Kind.LET, path + ".binder", context);
            requireType(let.value, let.binder.type(), path + ".value", context);
            context.active.put(let.binder, Boolean.TRUE);
            visit(let.inExpr, path + ".in", context);
            context.active.remove(let.binder);
            requireType(expression, let.inExpr.type(), path, context);
        } else if (expression instanceof CoreExpr.IfExpr conditional) {
            visit(conditional.condition, path + ".condition", context);
            visit(conditional.thenExpr, path + ".then", context);
            visit(conditional.elseExpr, path + ".else", context);
            requireType(conditional.condition, OclType.BOOLEAN, path + ".condition", context);
            requireType(conditional.thenExpr, expression.type(), path + ".then", context);
            requireType(conditional.elseExpr, expression.type(), path + ".else", context);
        } else if (expression instanceof CoreExpr.Coerce coercion) {
            visit(coercion.source, path + ".source", context);
            if (!coercion.sourceType.equals(coercion.source.type())) {
                context.error(path + ".sourceType", "does not equal source.type");
            }
            if (coercion.sourceType.equals(coercion.type())) {
                context.error(path, "identity coercion must not create a CoreCoerce node");
            }
            validateCoercion(coercion, path, context);
        } else if (expression instanceof CoreExpr.AttributeRead read) {
            visit(read.source, path + ".source", context);
            requireClass(read.source.type(), path + ".source.type", context);
            validateAttribute(read, path, context);
        } else if (expression instanceof CoreExpr.Navigation navigation) {
            visit(navigation.source, path + ".source", context);
            requireClass(navigation.source.type(), path + ".source.type", context);
            visitAll(navigation.qualifiers, path + ".qualifiers", context);
            validateNavigation(navigation, path, context);
        } else if (expression instanceof CoreExpr.AssociationClassNavigation navigation) {
            visit(navigation.source, path + ".source", context);
            requireClass(navigation.source.type(), path + ".source.type", context);
            visitAll(navigation.qualifiers, path + ".qualifiers", context);
            validateAssociationClassNavigation(navigation, path, context);
        } else if (expression instanceof CoreExpr.AllInstances all) {
            OclType expected = OclType.set(OclType.clazz(all.classKey));
            requireType(expression, expected, path, context);
            requireSchemaClass(all.classKey, path + ".classKey", context);
        } else if (expression instanceof CoreExpr.TypeTest test) {
            visit(test.source, path + ".source", context);
            requireClass(test.source.type(), path + ".source.type", context);
            requireType(expression, OclType.BOOLEAN, path, context);
            validateRelatedClasses(test.source.type(), test.targetClassKey, path, context);
        } else if (expression instanceof CoreExpr.TypeCast cast) {
            visit(cast.source, path + ".source", context);
            requireClass(cast.source.type(), path + ".source.type", context);
            requireType(expression, OclType.clazz(cast.targetClassKey), path, context);
            validateRelatedClasses(cast.source.type(), cast.targetClassKey, path, context);
        } else if (expression instanceof CoreExpr.Unary unary) {
            visit(unary.operand, path + ".operand", context);
            validateUnary(unary, path, context);
        } else if (expression instanceof CoreExpr.Binary binary) {
            visit(binary.left, path + ".left", context);
            visit(binary.right, path + ".right", context);
            validateBinary(binary, path, context);
        } else if (expression instanceof CoreExpr.CollectionLiteral literal) {
            visitAll(literal.elements, path + ".elements", context);
            validateCollectionLiteral(literal, path, context);
        } else if (expression instanceof CoreExpr.Iterator iterator) {
            visit(iterator.source, path + ".source", context);
            declare(iterator.iterator, CoreDeclaration.Kind.ITERATOR,
                    path + ".iterator", context);
            validateIteratorSource(iterator, path, context);
            context.active.put(iterator.iterator, Boolean.TRUE);
            visit(iterator.body, path + ".body", context);
            context.active.remove(iterator.iterator);
            validateIteratorResult(iterator, path, context);
        } else {
            context.error(path, "uncovered Core constructor " + expression.getClass().getName());
        }
    }

    private static void validateCoercion(CoreExpr.Coerce coercion, String path,
                                         Context context) {
        OclType source = coercion.sourceType;
        OclType target = coercion.type();
        boolean valid = switch (coercion.kind) {
            case INTEGER_TO_REAL -> source.equals(OclType.INTEGER)
                    && target.equals(OclType.REAL);
            case CLASS_UPCAST -> source.isClass() && target.isClass()
                    && admitsAtomic(source, target, context.schema);
            case COLLECTION_ELEMENT_COERCION -> source.isCollection()
                    && target.isCollection() && source.kind() == target.kind()
                    && admitsAtomic(source.elementType(), target.elementType(), context.schema);
        };
        if (!valid) {
            context.error(path, "invalid " + coercion.kind + " coercion: "
                    + source + " -> " + target);
        }
    }

    private static boolean admitsAtomic(OclType source, OclType target, SchemaModel schema) {
        if (!source.isAtomic() || !target.isAtomic()) return false;
        if (source.equals(target)) return true;
        if (source.equals(OclType.INTEGER) && target.equals(OclType.REAL)) return true;
        return source.isClass() && target.isClass()
                && (schema == null || schema.conforms(source.className(), target.className()));
    }

    private static void validateAttribute(CoreExpr.AttributeRead read, String path,
                                          Context context) {
        if (context.schema == null || !read.source.type().isClass()) return;
        UmlAttribute attribute = context.schema.attribute(read.ownerClassKey, read.attributeName);
        if (attribute == null) {
            context.error(path, "attribute is absent from schema: " + read.ownerClassKey
                    + "::" + read.attributeName);
            return;
        }
        if (!context.schema.conforms(read.source.type().className(), read.ownerClassKey)) {
            context.error(path + ".source.type", "receiver does not conform to attribute owner");
        }
        requireType(read, attribute.declaredType(), path, context);
    }

    private static void validateNavigation(CoreExpr.Navigation node, String path,
                                           Context context) {
        if (!node.source.type().isClass()) return;
        if (node.kind == CoreExpr.NavKind.TO_ONE && node.type().isCollection()) {
            context.error(path + ".type", "TO_ONE navigation must be atomic");
        }
        if (node.kind == CoreExpr.NavKind.TO_MANY && !node.type().isCollection()) {
            context.error(path + ".type", "TO_MANY navigation must be a collection");
        }
        if (context.schema == null) return;
        SchemaModel.Navigation resolved;
        try {
            resolved = context.schema.navigation(node.source.type().className(), node.roleName);
        } catch (IllegalArgumentException ambiguous) {
            context.error(path, "ambiguous schema navigation: " + ambiguous.getMessage());
            return;
        }
        if (resolved == null || !resolved.association().name().equals(node.associationName)) {
            context.error(path, "navigation does not resolve to the recorded association");
            return;
        }
        if (resolved.reverse() != node.reverse) {
            context.error(path + ".reverse", "navigation direction differs from schema");
        }
        CoreExpr.NavKind expectedKind = resolved.toMany()
                ? CoreExpr.NavKind.TO_MANY : CoreExpr.NavKind.TO_ONE;
        if (node.kind != expectedKind) {
            context.error(path + ".kind", "navigation kind differs from multiplicity");
        }
        OclType target = OclType.clazz(resolved.targetClassKey());
        OclType expectedType = resolved.toMany()
                ? (resolved.unique() ? OclType.set(target) : OclType.bag(target)) : target;
        requireType(node, expectedType, path, context);
        validateQualifiers(node.qualifiers, resolved.association(), path, context);
    }

    private static void validateAssociationClassNavigation(
            CoreExpr.AssociationClassNavigation node, String path, Context context) {
        if (!node.source.type().isClass()) return;
        OclType associationClass = OclType.clazz(node.associationClassKey);
        OclType expectedByKind = node.kind == CoreExpr.NavKind.TO_MANY
                ? (node.type().kind() == OclType.Kind.BAG
                        ? OclType.bag(associationClass) : OclType.set(associationClass))
                : associationClass;
        requireType(node, expectedByKind, path, context);
        if (context.schema == null) return;
        SchemaModel.AssociationClassNavigation resolved;
        try {
            resolved = context.schema.associationClassNavigation(
                    node.source.type().className(), node.associationClassKey);
        } catch (IllegalArgumentException ambiguous) {
            context.error(path, "ambiguous association-class navigation: "
                    + ambiguous.getMessage());
            return;
        }
        if (resolved == null) {
            context.error(path, "association-class navigation is absent from schema");
            return;
        }
        if (!resolved.participantRole().equals(node.navigationSource)) {
            context.error(path + ".navigationSource", "participant role differs from schema");
        }
        if (resolved.receiverIsTarget() != node.receiverIsTarget) {
            context.error(path + ".receiverIsTarget", "participant direction differs from schema");
        }
        CoreExpr.NavKind expectedKind = resolved.toMany()
                ? CoreExpr.NavKind.TO_MANY : CoreExpr.NavKind.TO_ONE;
        if (node.kind != expectedKind) {
            context.error(path + ".kind", "association-class multiplicity differs from schema");
        }
        OclType expectedType = resolved.toMany()
                ? (resolved.unique() ? OclType.set(associationClass)
                        : OclType.bag(associationClass)) : associationClass;
        requireType(node, expectedType, path, context);
        validateQualifiers(node.qualifiers, resolved.association(), path, context);
    }

    private static void validateQualifiers(List<CoreExpr> actual, UmlAssociation association,
                                           String path, Context context) {
        List<UmlQualifier> expected = association.qualifiers();
        if (actual.size() != expected.size()) {
            context.error(path + ".qualifiers", "qualifier arity differs from schema");
            return;
        }
        for (int index = 0; index < actual.size(); index++) {
            requireType(actual.get(index), expected.get(index).declaredType(),
                    path + ".qualifiers[" + index + "]", context);
        }
    }

    private static void validateRelatedClasses(OclType source, String target, String path,
                                               Context context) {
        requireSchemaClass(target, path + ".targetClass", context);
        if (context.schema == null || !source.isClass()) return;
        String sourceClass = source.className();
        if (!(context.schema.conforms(sourceClass, target)
                || context.schema.conforms(target, sourceClass))) {
            context.error(path, "source and target classes are unrelated");
        }
    }

    private static void validateUnary(CoreExpr.Unary node, String path, Context context) {
        OclType operand = node.operand.type();
        boolean valid = switch (node.operator) {
            case BOOLEAN_NOT -> operand.equals(OclType.BOOLEAN)
                    && node.type().equals(OclType.BOOLEAN);
            case NUMERIC_NEGATE, NUMERIC_ABS -> operand.isNumeric()
                    && node.type().equals(operand);
            case REAL_FLOOR, REAL_ROUND -> operand.equals(OclType.REAL)
                    && node.type().equals(OclType.INTEGER);
            case COLLECTION_SIZE -> operand.isCollection()
                    && node.type().equals(OclType.INTEGER);
            case COLLECTION_IS_EMPTY, COLLECTION_NOT_EMPTY -> operand.isCollection()
                    && node.type().equals(OclType.BOOLEAN);
            case COLLECTION_SUM -> operand.isCollection()
                    && operand.elementType().isNumeric()
                    && node.type().equals(operand.elementType());
        };
        if (!valid) context.error(path, "invalid unary signature for " + node.operator);
    }

    private static void validateBinary(CoreExpr.Binary node, String path, Context context) {
        OclType left = node.left.type();
        OclType right = node.right.type();
        OclType result = node.type();
        boolean valid = switch (node.operator) {
            case NUMERIC_ADD, NUMERIC_SUBTRACT, NUMERIC_MULTIPLY,
                    NUMERIC_MAX, NUMERIC_MIN -> left.isNumeric() && right.equals(left)
                    && result.equals(left);
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
            case COLLECTION_COUNT -> left.isCollection()
                    && right.equals(left.elementType()) && result.equals(OclType.INTEGER);
            case COLLECTION_INCLUDES, COLLECTION_EXCLUDES -> left.isCollection()
                    && right.equals(left.elementType()) && result.equals(OclType.BOOLEAN);
            case COLLECTION_INCLUDES_ALL, COLLECTION_EXCLUDES_ALL -> left.isCollection()
                    && right.equals(left) && result.equals(OclType.BOOLEAN);
            case SET_UNION, SET_INTERSECTION -> left.kind() == OclType.Kind.SET
                    && right.equals(left) && result.equals(left);
        };
        if (!valid) context.error(path, "invalid binary signature for " + node.operator);
    }

    private static void validateCollectionLiteral(CoreExpr.CollectionLiteral node, String path,
                                                  Context context) {
        if (!node.type().isCollection()) {
            context.error(path + ".type", "collection literal must have collection type");
            return;
        }
        OclType.Kind expectedKind = node.kind == CoreExpr.CollectionKind.SET
                ? OclType.Kind.SET : OclType.Kind.BAG;
        if (node.type().kind() != expectedKind) {
            context.error(path + ".kind", "literal kind differs from result type");
        }
        for (int index = 0; index < node.elements.size(); index++) {
            requireType(node.elements.get(index), node.type().elementType(),
                    path + ".elements[" + index + "]", context);
        }
    }

    private static void validateIteratorSource(CoreExpr.Iterator node, String path,
                                               Context context) {
        if (!node.source.type().isCollection()) {
            context.error(path + ".source.type", "iterator source must be a collection");
            return;
        }
        CoreExpr.CollectionKind expectedKind = node.source.type().kind() == OclType.Kind.SET
                ? CoreExpr.CollectionKind.SET : CoreExpr.CollectionKind.BAG;
        if (node.sourceKind != expectedKind) {
            context.error(path + ".sourceKind", "does not match source collection kind");
        }
        if (!node.iterator.type().equals(node.source.type().elementType())) {
            context.error(path + ".iterator.type", "does not match source element type");
        }
    }

    private static void validateIteratorResult(CoreExpr.Iterator node, String path,
                                               Context context) {
        boolean valid = switch (node.iteratorKind) {
            case EXISTS, FORALL -> node.body.type().equals(OclType.BOOLEAN)
                    && node.type().equals(OclType.BOOLEAN);
            case SELECT, REJECT -> node.body.type().equals(OclType.BOOLEAN)
                    && node.type().equals(node.source.type());
            case COLLECT -> node.body.type().isAtomic()
                    && node.type().equals(OclType.bag(node.body.type()));
        };
        if (!valid) context.error(path, "invalid iterator signature for " + node.iteratorKind);
    }

    private static void visitAll(List<CoreExpr> expressions, String path, Context context) {
        for (int index = 0; index < expressions.size(); index++) {
            visit(expressions.get(index), path + "[" + index + "]", context);
        }
    }

    private static void declare(CoreDeclaration declaration, CoreDeclaration.Kind expectedKind,
                                String path, Context context) {
        if (context.owned.put(declaration, Boolean.TRUE) != null) {
            context.error(path, "declaration object is bound more than once");
        }
        if (!context.ids.add(declaration.id())) {
            context.error(path + ".id", "duplicate declaration id " + declaration.id());
        }
        if (declaration.id() <= 0) {
            context.error(path + ".id", "declaration id must be positive");
        }
        if (declaration.name().isBlank()) {
            context.error(path + ".name", "blank declaration name");
        }
        if (declaration.kind() != expectedKind) {
            context.error(path + ".kind", "expected " + expectedKind
                    + " but found " + declaration.kind());
        }
        checkType(declaration.type(), path + ".type", context);
    }

    private static void requireType(CoreExpr expression, OclType expected, String path,
                                    Context context) {
        if (!expression.type().equals(expected)) {
            context.error(path + ".type", "expected " + expected + " but found "
                    + expression.type());
        }
    }

    private static void requireClass(OclType type, String path, Context context) {
        if (!type.isClass()) context.error(path, "expected a class type, found " + type);
    }

    private static void checkType(OclType type, String path, Context context) {
        if (type == null) {
            context.error(path, "missing type");
            return;
        }
        if (type.isClass()) {
            if (type.className().isBlank()) context.error(path, "blank class name");
            requireSchemaClass(type.className(), path, context);
        } else if (type.isCollection()) {
            if (!type.elementType().isAtomic()) {
                context.error(path, "nested collection type is outside OCL_val");
            }
            checkType(type.elementType(), path + ".element", context);
        }
        if (canonical(type) != type) {
            context.error(path, "type is not the canonical interned instance");
        }
    }

    private static OclType canonical(OclType type) {
        return switch (type.kind()) {
            case BOOLEAN -> OclType.BOOLEAN;
            case INTEGER -> OclType.INTEGER;
            case REAL -> OclType.REAL;
            case STRING -> OclType.STRING;
            case CLASS -> OclType.clazz(type.className());
            case SET -> OclType.set(type.elementType());
            case BAG -> OclType.bag(type.elementType());
        };
    }

    private static void requireSchemaClass(String classKey, String path, Context context) {
        if (context.schema != null && !context.schema.hasClass(classKey)) {
            context.error(path, "class is absent from schema: " + classKey);
        }
    }

    private static final class Context {
        private final SchemaModel schema;
        private final List<Error> errors;
        private final IdentityHashMap<CoreDeclaration, Boolean> active = new IdentityHashMap<>();
        private final IdentityHashMap<CoreDeclaration, Boolean> owned = new IdentityHashMap<>();
        private final Set<Integer> ids = new HashSet<>();

        private Context(SchemaModel schema, List<Error> errors) {
            this.schema = schema;
            this.errors = errors;
        }

        private void error(String path, String message) {
            errors.add(new Error(path, message));
        }
    }
}
