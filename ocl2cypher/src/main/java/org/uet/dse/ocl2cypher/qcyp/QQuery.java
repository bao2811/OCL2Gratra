package org.uet.dse.ocl2cypher.qcyp;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.uet.dse.ocl2cypher.core.CoreDeclaration;
import org.uet.dse.ocl2cypher.core.CoreExpr;
import org.uet.dse.ocl2cypher.core.CoreUnit;
import org.uet.dse.ocl2cypher.diagnostics.SourceSpan;
import org.uet.dse.ocl2cypher.runtime.OclType;

/**
 * The query root produced by {@code T_G}: exactly one of {@code expressionBody}
 * or {@code planBody} is present (the {@code ExactlyOneBody} invariant).
 *
 * <p>{@code VIOLATIONS} requires {@code expressionBody : Boolean₃} plus the
 * context class and identity projection; {@code VALUE} returns a scalar or a
 * collection according to {@code resultShape}.
 */
public record QQuery(QNode.QExpr expressionBody,
                     QNode.QPlan planBody,
                     QResultShape resultShape,
                     QueryMode mode,
                     OclType resultType,
                     String contextClassKey,
                     CoreDeclaration selfVariable,
                     boolean identityProjection) {

    public enum QueryMode {
        VALUE,
        VIOLATIONS
    }

    public enum QResultShape {
        SCALAR,
        SET,
        BAG,
        IDS
    }

    public QQuery {
        boolean hasExpr = expressionBody != null;
        boolean hasPlan = planBody != null;
        if (hasExpr == hasPlan) {
            throw new IllegalArgumentException("exactly one of expressionBody / planBody required");
        }
        if (mode == QueryMode.VIOLATIONS
                && !(resultShape == QResultShape.IDS && identityProjection
                        && hasExpr && !hasPlan && contextClassKey != null && selfVariable != null)) {
            throw new IllegalArgumentException(
                    "VIOLATIONS requires IDS + identityProjection + expressionBody + context");
        }
        if (mode == QueryMode.VALUE && resultShape == QResultShape.IDS) {
            throw new IllegalArgumentException("VALUE must not use IDS");
        }
        if (mode == QueryMode.VIOLATIONS
                && !OclType.BOOLEAN.equals(expressionBody.type)) {
            throw new IllegalArgumentException("VIOLATIONS body must be Boolean");
        }
        if (mode == QueryMode.VALUE) {
            if (resultType == null) {
                throw new IllegalArgumentException("VALUE requires a result type");
            }
            if ((contextClassKey == null) != (selfVariable == null)) {
                throw new IllegalArgumentException(
                        "VALUE context class and self declaration must be present together");
            }
            OclType bodyType = hasExpr ? expressionBody.type : planBody.type;
            if (!resultType.equals(bodyType)) {
                throw new IllegalArgumentException("VALUE result type does not match body type");
            }
            QResultShape expectedShape = switch (resultType.kind()) {
                case SET -> QResultShape.SET;
                case BAG -> QResultShape.BAG;
                default -> QResultShape.SCALAR;
            };
            if (resultShape != expectedShape) {
                throw new IllegalArgumentException("VALUE result shape does not match result type");
            }
        }
    }
}
