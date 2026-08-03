package org.uet.dse.neo4jtgg.ocl;

import org.tzi.use.uml.ocl.type.Type;
import org.uet.dse.neo4jtgg.ocl.diagnostic.OclCodedUnsupportedOperationException;
import org.uet.dse.neo4jtgg.ocl.diagnostic.OclDiagnosticCode;

import java.util.Set;

/** Semantic/type admission checks that can only run after metamodel binding. */
public final class OclValBoundAdmissionPolicy {
    private static final Set<String> CERTIFIED_SCALAR_TYPES = Set.of(
            "Boolean", "Integer", "Real", "String", "Void");

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
        if (expression instanceof OclSemanticBinder.BoundVariable) {
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
            verifyExpression(value.source()); value.arguments().forEach(OclValBoundAdmissionPolicy::verifyExpression);
            return;
        }
        if (expression instanceof OclSemanticBinder.BoundCollectionOperation value) {
            if (!value.source().type().isCollection()) {
                reject("collection operation `" + value.ast().opName + "` requires a collection source");
            }
            verifyExpression(value.source()); value.arguments().forEach(OclValBoundAdmissionPolicy::verifyExpression);
            return;
        }
        if (expression instanceof OclSemanticBinder.BoundIterator value) {
            if (!value.source().type().isCollection()) {
                reject("iterator `" + value.ast().operation + "` requires a collection source");
            }
            if ("collect".equalsIgnoreCase(value.ast().operation) && value.body().type().isCollection()) {
                reject("collect body must be non-collection in OCL_val");
            }
            verifyExpression(value.source()); verifyExpression(value.body());
            return;
        }
        reject("unsupported bound constructor " + expression.getClass().getSimpleName());
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
                    + "; certified scalar types are Boolean, Integer, Real, and String");
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
