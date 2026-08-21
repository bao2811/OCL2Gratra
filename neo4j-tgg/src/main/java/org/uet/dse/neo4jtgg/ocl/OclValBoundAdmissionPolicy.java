package org.uet.dse.neo4jtgg.ocl;

import org.tzi.use.uml.ocl.type.Type;
import org.uet.dse.neo4jtgg.ocl.diagnostic.OclCodedUnsupportedOperationException;
import org.uet.dse.neo4jtgg.ocl.diagnostic.OclDiagnosticCode;

import java.util.Set;

/** Semantic/type admission checks that can only run after metamodel binding. */
public final class OclValBoundAdmissionPolicy {
    private static final Set<String> CERTIFIED_SCALAR_TYPES = Set.of(
            "Boolean", "Integer", "Real", "String", "Void");
    private static final Set<String> NATIVE_SCALAR_COLLECTION_OPERATIONS = Set.of(
            "asSet", "size", "isEmpty", "notEmpty");

    private OclValBoundAdmissionPolicy() {
    }

    public static void verify(OclSemanticBinder.BoundContextInvariant invariant) {
        if (invariant == null || invariant.expression() == null) reject("missing bound invariant");
        OclTypeBinding rootType = invariant.expression().type();
        if (rootType.isCollection() || !"Boolean".equals(rootType.typeName())) {
            reject("invariant root must be Boolean, but was " + describe(rootType));
        }
        verifyExpression(invariant.expression());
    }

    private static void verifyExpression(OclSemanticBinder.BoundExpression expression) {
        verifyType(expression.type(), expression.getClass().getSimpleName());
        if (expression instanceof OclSemanticBinder.BoundVariable variable) {
            if (variable.type().isClassReference()) {
                reject("UML class reference `" + variable.ast().name
                        + "` is only valid as the receiver of allInstances() or as a type-operation argument");
            }
            return;
        }
        if (expression instanceof OclSemanticBinder.BoundLiteral literal) {
            OclScalarClosureChecker.Result result = OclScalarClosureChecker.checkValues(
                    java.util.Collections.singletonList(literal.value()), "OCL literal");
            if (!result.passed()) reject(String.join("; ", result.violations()));
            return;
        }
        if (expression instanceof OclSemanticBinder.BoundSetLiteral value) {
            value.elements().forEach(OclValBoundAdmissionPolicy::verifyExpression);
            return;
        }
        if (expression instanceof OclSemanticBinder.BoundNot value) {
            verifyExpression(value.expression());
            return;
        }
        if (expression instanceof OclSemanticBinder.BoundIf value) {
            verifyExpression(value.condition()); verifyExpression(value.thenBranch()); verifyExpression(value.elseBranch());
            return;
        }
        if (expression instanceof OclSemanticBinder.BoundLet value) {
            verifyType(value.variableType(), "let variable `" + value.ast().variableName + "`");
            verifyExpression(value.value()); verifyExpression(value.body());
            return;
        }
        if (expression instanceof OclSemanticBinder.BoundBinary value) {
            verifyExpression(value.left()); verifyExpression(value.right());
            return;
        }
        if (expression instanceof OclSemanticBinder.BoundProperty value) {
            verifyExpression(value.source());
            if (value.isAttribute()) verifyAttribute(value);
            else if (!value.type().isCollection()
                    || value.type().collectionKind() != OclTypeBinding.CollectionKind.SET
                    || !value.type().elementType().isNode()) {
                reject("association navigation must bind to Set(Entity): " + value.ast().name);
            }
            value.qualifiers().forEach(OclValBoundAdmissionPolicy::verifyExpression);
            return;
        }
        if (expression instanceof OclSemanticBinder.BoundMethodCall value) {
            verifyCertifiedMethodCall(value);
            return;
        }
        if (expression instanceof OclSemanticBinder.BoundCollectionOperation value) {
            verifyCollectionSource(value.source(), value.sourceCollectionType(),
                    "collection operation `" + value.ast().opName + "`", value.ast().opName);
            verifyCertifiedCollectionSignature(value);
            value.arguments().forEach(OclValBoundAdmissionPolicy::verifyExpression);
            return;
        }
        if (expression instanceof OclSemanticBinder.BoundIterator value) {
            verifyType(value.iteratorVariableType(), "iterator variable `" + value.ast().iteratorName + "`");
            if (!value.source().type().isCollection()) {
                reject("iterator `" + value.ast().operation
                        + "` requires a native collection source; use an explicit asSet() "
                        + "only where native USE accepts that source expression");
            }
            verifyCollectionSource(value.source(), value.sourceCollectionType(),
                    "iterator `" + value.ast().operation + "`", null);
            if ("collect".equalsIgnoreCase(value.ast().operation) && value.body().type().isCollection()) {
                reject("collect body must be non-collection in OCL_val");
            }
            verifyExpression(value.body());
            return;
        }
        reject("unsupported bound constructor " + expression.getClass().getSimpleName());
    }

    private static void verifyCertifiedMethodCall(OclSemanticBinder.BoundMethodCall value) {
        String methodName = value.ast().methodName;
        if ("allInstances".equalsIgnoreCase(methodName)) {
            if (!(value.source() instanceof OclSemanticBinder.BoundVariable)
                    || !value.source().type().isClassReference()
                    || !value.arguments().isEmpty()) {
                reject("allInstances() requires one UML class receiver and no arguments");
            }
            return;
        }
        if ("oclIsKindOf".equalsIgnoreCase(methodName)
                || "oclAsType".equalsIgnoreCase(methodName)) {
            verifyExpression(value.source());
            if (value.arguments().size() != 1
                    || !(value.arguments().get(0) instanceof OclSemanticBinder.BoundVariable)
                    || !value.arguments().get(0).type().isClassReference()) {
                reject(methodName + "() requires exactly one UML class argument");
            }
            return;
        }
        reject("method `" + methodName + "` is outside the certified BoundOCL_val fragment");
    }

    private static void verifyCertifiedCollectionSignature(
            OclSemanticBinder.BoundCollectionOperation value) {
        String operation = value.ast().opName;
        int arity = value.arguments().size();
        switch (operation) {
            case "size", "isEmpty", "notEmpty", "asSet" -> {
                if (arity != 0) reject(operation + "() does not accept arguments");
            }
            case "includes", "excludes" -> {
                if (arity != 1 || value.arguments().get(0).type().isCollection()) {
                    reject(operation + "() requires exactly one scalar/entity element argument");
                }
            }
            case "includesAll", "excludesAll", "union", "intersection" -> {
                if (arity != 1 || !value.arguments().get(0).type().isCollection()) {
                    reject(operation + "() requires exactly one collection argument");
                }
            }
            default -> reject("collection operation `" + operation
                    + "` is outside the certified BoundOCL_val fragment");
        }
    }

    private static void verifyAttribute(OclSemanticBinder.BoundProperty property) {
        Type umlType = property.attribute().type();
        if (property.type().isCollection()
                || umlType.isKindOfCollection(Type.VoidHandling.EXCLUDE_VOID)
                || property.type().isNode()
                || umlType.isKindOfClass(Type.VoidHandling.EXCLUDE_VOID)) {
            reject("attribute `" + property.attribute().owner().name() + "."
                    + property.attribute().name() + "` is collection/reference-valued; PA5 currently certifies scalar slots only");
        }
    }

    private static void verifyCollectionSource(OclSemanticBinder.BoundExpression source,
                                               OclTypeBinding sourceCollectionType,
                                               String operation,
                                               String scalarOperationName) {
        if (sourceCollectionType == null || !sourceCollectionType.isCollection()) {
            reject(operation + " requires a collection source");
        }
        verifyType(sourceCollectionType, operation + " source view");
        if (source.type().isCollection()) {
            verifyExpression(source);
            return;
        }
        if (source instanceof OclSemanticBinder.BoundProperty navigation
                && NATIVE_SCALAR_COLLECTION_OPERATIONS.contains(scalarOperationName)
                && !navigation.isAttribute()
                && navigation.navigation() != null
                && navigation.navigation().targetSingleValued()
                && !navigation.navigation().resultBinding().isCollection()
                && sourceCollectionType.collectionKind() == OclTypeBinding.CollectionKind.SET
                && sourceCollectionType.elementType().isNode()
                && sourceCollectionType.elementType().typeName()
                .equals(navigation.navigation().targetClassName())) {
            verifyExpression(navigation.source());
            navigation.qualifiers().forEach(OclValBoundAdmissionPolicy::verifyExpression);
            return;
        }
        reject(operation + " may only lift a resolved native-scalar [0..1]/[1] navigation to Set(Entity)");
    }

    private static void verifyType(OclTypeBinding type, String location) {
        if (type == null) reject(location + " has no canonical type");
        if (type.isCollection()) {
            if (type.collectionKind() != OclTypeBinding.CollectionKind.SET) {
                reject(location + " has " + type.collectionKind() + "; OCL_val admits finite Set only");
            }
            verifyType(type.elementType(), location + " element");
            return;
        }
        if (!type.isNode() && !type.isClassReference()
                && !CERTIFIED_SCALAR_TYPES.contains(type.typeName())) {
            reject(location + " has scalar type " + type.typeName()
                    + "; certified scalar types are Boolean, Integer, Real, String, "
                    + "and the internal null-bottom type Void");
        }
    }

    private static String describe(OclTypeBinding type) {
        return type == null ? "<missing>" : type.collectionKind() + "(" + type.typeName() + ")";
    }

    private static void reject(String reason) {
        throw new OclCodedUnsupportedOperationException(
                OclDiagnosticCode.OCL_VAL_EXCLUDED_CONSTRUCT,
                reason + "; the certified theorem profile is narrower than general compiler support.");
    }
}
