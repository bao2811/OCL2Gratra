package org.uet.dse.ocl2cypher.source.model;

import java.util.Objects;
import java.util.Optional;
import org.uet.dse.ocl2cypher.diagnostics.Diagnostic;
import org.uet.dse.ocl2cypher.diagnostics.Stage;
import org.uet.dse.ocl2cypher.runtime.OclType;
import org.uet.dse.ocl2cypher.source.omg.OmgAs;
import org.uet.dse.ocl2cypher.source.omg.OclOperation;

/**
 * OCL_val's admission boundary, validated by the frontend before any
 * lower-to-core lowering is attempted. The policy accepts a well-typed
 * resolved AS, or returns exactly one diagnostic that will be wrapped by the
 * caller's stage wrapper with {@code N-...} outer code. Surface
 * {@code null/invalid} and standalone {@code VoidType} literals were already
 * kept as {@code N-...} failures by the AS parser; they are never mapped into
 * a runtime bottom.
 */
public final class Admission {

    private Admission() {
    }

    /** Admission for the public resolved OMG constraint carrier. */
    public static Optional<Diagnostic> check(OmgAs.Constraint constraint) {
        Objects.requireNonNull(constraint, "constraint");
        Optional<Diagnostic> nodeError = checkOmgNode(
                constraint.specification.bodyExpression, false);
        if (nodeError.isPresent()) {
            return nodeError;
        }
        if (!OclType.BOOLEAN.equals(constraint.specification.bodyExpression.type)) {
            return Optional.of(error("N_INVARIANT_TYPE",
                    "invariant body must be Boolean, found "
                            + constraint.specification.bodyExpression.type,
                    constraint.specification.bodyExpression.span));
        }
        return Optional.empty();
    }

    /** Admission for an independent public OMG value expression. */
    public static Optional<Diagnostic> checkQueryExpression(
            OmgAs.OclExpression expression) {
        Objects.requireNonNull(expression, "expression");
        return checkOmgNode(expression, false);
    }

    private static Optional<Diagnostic> checkOmgNode(OmgAs.OclExpression node,
                                                      boolean classifierOperand) {
        if (node instanceof OmgAs.NullLiteralExp) {
            return Optional.of(error("N_UNSUPPORTED_NULL_LITERAL",
                    "surface null is outside OCL_val", node.span));
        }
        if (node instanceof OmgAs.InvalidLiteralExp) {
            return Optional.of(error("N_UNSUPPORTED_INVALID_LITERAL",
                    "surface invalid is outside OCL_val", node.span));
        }
        if (node instanceof OmgAs.TypeExp && !classifierOperand) {
            return Optional.of(error("N_TYPE_LITERAL_CONSUMED",
                    "classifier is only admitted as an operation type operand", node.span));
        }
        if (!(node instanceof OmgAs.TypeExp) && !supportedType(node.type)) {
            return Optional.of(error("N_UNSUPPORTED_TYPE",
                    "type is outside OCL_val: " + node.type, node.span));
        }
        if (node instanceof OmgAs.PropertyCallExp property) {
            Optional<Diagnostic> failure = checkOmgNode(property.source, false);
            if (failure.isPresent()) return failure;
            for (OmgAs.OclExpression qualifier : property.qualifier) {
                failure = checkOmgNode(qualifier, false);
                if (failure.isPresent()) return failure;
                if (qualifier.type == null || !qualifier.type.isAtomic()) {
                    return Optional.of(error("N_QUALIFIER_TYPE",
                            "qualifier must be scalar", qualifier.span));
                }
            }
        } else if (node instanceof OmgAs.AssociationClassCallExp associationClass) {
            // Association-class navigation is an admitted N_SM constructor.  Its
            // source and qualifier expressions are checked exactly like ordinary
            // navigation; the resolved association-class identity is retained for
            // the G-AC observer and must not be rejected at admission.
            Optional<Diagnostic> failure = checkOmgNode(associationClass.source, false);
            if (failure.isPresent()) return failure;
            for (OmgAs.OclExpression qualifier : associationClass.qualifier) {
                failure = checkOmgNode(qualifier, false);
                if (failure.isPresent()) return failure;
                if (qualifier.type == null || !qualifier.type.isAtomic()) {
                    return Optional.of(error("N_QUALIFIER_TYPE",
                            "qualifier must be scalar", qualifier.span));
                }
            }
        } else if (node instanceof OmgAs.OperationCallExp operation) {
            OclOperation kind;
            try {
                kind = OclOperation.resolved(operation.referredOperation);
            } catch (IllegalArgumentException invalidOperation) {
                return Optional.of(error("N_UNSUPPORTED_OPERATION",
                        "unknown resolved operation: " + operation.referredOperation,
                        node.span));
            }
            if (isUnsupportedOp(kind)) {
                return Optional.of(error("N_UNSUPPORTED_OPERATION",
                        "operation is outside OCL_val: " + kind, node.span));
            }
            Optional<Diagnostic> failure = checkOmgNode(operation.source,
                    kind == OclOperation.ALL_INSTANCES);
            if (failure.isPresent()) return failure;
            for (int index = 0; index < operation.argument.size(); index++) {
                boolean typeArgument = (kind == OclOperation.OCL_IS_TYPE_OF
                        || kind == OclOperation.OCL_IS_KIND_OF
                        || kind == OclOperation.OCL_AS_TYPE) && index == 0;
                failure = checkOmgNode(operation.argument.get(index), typeArgument);
                if (failure.isPresent()) return failure;
            }
        } else if (node instanceof OmgAs.IteratorExp iterator) {
            if (!java.util.Set.of("select", "reject", "collect", "exists", "forAll")
                    .contains(iterator.name)) {
                return Optional.of(error("N_UNSUPPORTED_ITERATOR",
                        "iterator is outside OCL_val: " + iterator.name, node.span));
            }
            if (iterator.iterator.size() != 1) {
                return Optional.of(error("N_ITERATOR_ARITY",
                        "OCL_val iterators require exactly one iterator variable", node.span));
            }
            Optional<Diagnostic> failure = checkOmgNode(iterator.source, false);
            if (failure.isPresent()) return failure;
            failure = checkOmgNode(iterator.body, false);
            if (failure.isPresent()) return failure;
            if (iterator.source.type == null || !iterator.source.type.isCollection()) {
                return Optional.of(error("N_ITERATOR_SOURCE",
                        "iterator source must be Set or Bag", iterator.source.span));
            }
            if (!"collect".equals(iterator.name)
                    && !OclType.BOOLEAN.equals(iterator.body.type)) {
                return Optional.of(error("N_ITERATOR_BODY",
                        "predicate iterator body must be Boolean", iterator.body.span));
            }
        } else if (node instanceof OmgAs.CollectionLiteralExp collection) {
            for (OmgAs.CollectionLiteralPart part : collection.part) {
                if (part instanceof OmgAs.CollectionRange) {
                    return Optional.of(error("N_UNSUPPORTED_COLLECTION_RANGE",
                            "collection ranges are outside OCL_val", part.span));
                }
                Optional<Diagnostic> failure = checkOmgNode(
                        ((OmgAs.CollectionItem) part).item, false);
                if (failure.isPresent()) return failure;
            }
        } else if (node instanceof OmgAs.IfExp conditional) {
            Optional<Diagnostic> failure = checkOmgNode(conditional.condition, false);
            if (failure.isPresent()) return failure;
            failure = checkOmgNode(conditional.thenExpression, false);
            return failure.isPresent() ? failure
                    : checkOmgNode(conditional.elseExpression, false);
        } else if (node instanceof OmgAs.LetExp let) {
            Optional<Diagnostic> failure = checkOmgNode(
                    let.variable.initExpression, false);
            return failure.isPresent() ? failure : checkOmgNode(let.in, false);
        } else if (node instanceof OmgAs.CoerceExp coercion) {
            return checkOmgNode(coercion.sourceExpression, false);
        }
        return Optional.empty();
    }

    private static boolean supportedType(OclType type) {
        return type != null && (!type.isCollection()
                || (type.elementType() != null && !type.elementType().isCollection()));
    }

    private static Diagnostic error(String code, String message,
                                    org.uet.dse.ocl2cypher.diagnostics.SourceSpan span) {
        return Diagnostic.builder(code, Stage.N_SM, message).span(span).build();
    }

    private static boolean isUnsupportedOp(OclOperation op) {
        return !CapabilityMatrix.isAdmitted(Objects.requireNonNull(op, "op"));
    }
}
