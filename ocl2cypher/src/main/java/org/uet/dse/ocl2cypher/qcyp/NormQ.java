package org.uet.dse.ocl2cypher.qcyp;

import java.util.ArrayList;
import java.util.List;

/** Deterministic, recursive normalization pass for Q_CYP. */
public final class NormQ {
    private NormQ() {
    }

    /** Normalize the unique query body while preserving every root contract field. */
    public static QQuery normalize(QQuery q) {
        if (q == null) {
            throw new IllegalArgumentException("Q query is required");
        }
        QNode.QExpr expression = q.expressionBody() == null
                ? null : normalizeExpr(q.expressionBody());
        QNode.QPlan plan = q.planBody() == null ? null : normalizePlan(q.planBody());
        if (expression == q.expressionBody() && plan == q.planBody()) {
            return q;
        }
        return new QQuery(expression, plan,
                q.resultShape(), q.mode(), q.resultType(), q.contextClassKey(),
                q.selfVariable(), q.identityProjection());
    }

    /** Normalize every expression subtree exactly once, preserving its type and kind. */
    public static QNode.QExpr normalizeExpr(QNode.QExpr e) {
        if (e instanceof QNode.QExpr.Let x) {
            QNode.QExpr value = normalizeExpr(x.value);
            QNode.QExpr body = normalizeExpr(x.body);
            return value == x.value && body == x.body ? e
                    : new QNode.QExpr.Let(x.span, x.binder, value, body);
        }
        if (e instanceof QNode.QExpr.IfExpr x) {
            QNode.QExpr condition = normalizeExpr(x.condition);
            QNode.QExpr thenExpr = normalizeExpr(x.thenExpr);
            QNode.QExpr elseExpr = normalizeExpr(x.elseExpr);
            return condition == x.condition && thenExpr == x.thenExpr && elseExpr == x.elseExpr
                    ? e : new QNode.QExpr.IfExpr(x.span, condition, thenExpr, elseExpr, x.type);
        }
        if (e instanceof QNode.QExpr.ReadAttribute x) {
            QNode.QExpr source = normalizeExpr(x.source);
            return source == x.source ? e : new QNode.QExpr.ReadAttribute(x.span, source,
                    x.ownerClassKey, x.attributeName, x.type);
        }
        if (e instanceof QNode.QExpr.NavigateOne x) {
            QNode.QExpr source = normalizeExpr(x.source);
            List<QNode.QExpr> qualifiers = normalizeExpressions(x.qualifiers);
            return source == x.source && qualifiers == x.qualifiers ? e
                    : new QNode.QExpr.NavigateOne(x.span, source,
                    x.associationName, x.roleName, qualifiers, x.type,
                    x.reverse, x.associationClass, x.viaAssociationClass);
        }
        if (e instanceof QNode.QExpr.TypeTest x) {
            QNode.QExpr source = normalizeExpr(x.source);
            return source == x.source ? e
                    : new QNode.QExpr.TypeTest(x.span, x.testKind, source, x.targetClassKey);
        }
        if (e instanceof QNode.QExpr.TypeCast x) {
            QNode.QExpr source = normalizeExpr(x.source);
            return source == x.source ? e
                    : new QNode.QExpr.TypeCast(x.span, source, x.targetClassKey);
        }
        if (e instanceof QNode.QExpr.Unary x) {
            QNode.QExpr operand = normalizeExpr(x.operand);
            return operand == x.operand ? e
                    : new QNode.QExpr.Unary(x.span, x.operator, operand, x.type);
        }
        if (e instanceof QNode.QExpr.Binary x) {
            QNode.QExpr left = normalizeExpr(x.left);
            QNode.QExpr right = normalizeExpr(x.right);
            return left == x.left && right == x.right ? e
                    : new QNode.QExpr.Binary(x.span, x.operator, left, right, x.type);
        }
        if (e instanceof QNode.QExpr.Coerce x) {
            QNode.QExpr source = normalizeExpr(x.source);
            return source == x.source ? e
                    : new QNode.QExpr.Coerce(x.span, x.kind, x.sourceType, source, x.type);
        }
        if (e instanceof QNode.QExpr.Exists3 x) {
            QNode.QPlan source = normalizePlan(x.source);
            QNode.QExpr predicate = normalizeExpr(x.predicate);
            return source == x.source && predicate == x.predicate ? e
                    : new QNode.QExpr.Exists3(x.span, source, x.iterator, predicate);
        }
        if (e instanceof QNode.QExpr.ForAll3 x) {
            QNode.QPlan source = normalizePlan(x.source);
            QNode.QExpr predicate = normalizeExpr(x.predicate);
            return source == x.source && predicate == x.predicate ? e
                    : new QNode.QExpr.ForAll3(x.span, source, x.iterator, predicate);
        }
        if (e instanceof QNode.QExpr.CollectionLiteral x) {
            List<QNode.QExpr> elements = normalizeExpressions(x.elements);
            return elements == x.elements ? e
                    : new QNode.QExpr.CollectionLiteral(x.span, x.collectionKind,
                            elements, x.type);
        }
        if (e instanceof QNode.QExpr.IncludesFamily x) {
            org.uet.dse.ocl2cypher.core.CoreExpr.BinaryOp op = switch (x.includesKind) {
                case INCLUDES -> org.uet.dse.ocl2cypher.core.CoreExpr.BinaryOp.COLLECTION_INCLUDES;
                case EXCLUDES -> org.uet.dse.ocl2cypher.core.CoreExpr.BinaryOp.COLLECTION_EXCLUDES;
                case INCLUDES_ALL -> org.uet.dse.ocl2cypher.core.CoreExpr.BinaryOp.COLLECTION_INCLUDES_ALL;
                case EXCLUDES_ALL -> org.uet.dse.ocl2cypher.core.CoreExpr.BinaryOp.COLLECTION_EXCLUDES_ALL;
                default -> throw new IllegalArgumentException(
                        "not an includes-family constructor: " + x.includesKind);
            };
            return new QNode.QExpr.Binary(x.span, op, normalizeExpr(x.source),
                    normalizeExpr(x.element), x.type);
        }
        if (e instanceof QNode.QExpr.CountFamily x) {
            QNode.QExpr source = normalizeExpr(x.source);
            return switch (x.countKind) {
                case SIZE -> new QNode.QExpr.Unary(x.span,
                        org.uet.dse.ocl2cypher.core.CoreExpr.UnaryOp.COLLECTION_SIZE,
                        source, x.type);
                case IS_EMPTY -> new QNode.QExpr.Unary(x.span,
                        org.uet.dse.ocl2cypher.core.CoreExpr.UnaryOp.COLLECTION_IS_EMPTY,
                        source, x.type);
                case NOT_EMPTY -> new QNode.QExpr.Unary(x.span,
                        org.uet.dse.ocl2cypher.core.CoreExpr.UnaryOp.COLLECTION_NOT_EMPTY,
                        source, x.type);
                case SUM -> new QNode.QExpr.Unary(x.span,
                        org.uet.dse.ocl2cypher.core.CoreExpr.UnaryOp.COLLECTION_SUM,
                        source, x.type);
                case COUNT -> new QNode.QExpr.Binary(x.span,
                        org.uet.dse.ocl2cypher.core.CoreExpr.BinaryOp.COLLECTION_COUNT,
                        source, normalizeExpr(x.element), x.type);
                default -> throw new IllegalArgumentException(
                        "not a count-family constructor: " + x.countKind);
            };
        }
        if (e instanceof QNode.QExpr.SetAlgebra x) {
            return new QNode.QExpr.Binary(x.span, x.operator,
                    normalizeExpr(x.left), normalizeExpr(x.right), x.type);
        }
        if (e instanceof QNode.QExpr.Materialize x) {
            QNode.QPlan plan = normalizePlan(x.plan);
            return plan == x.plan ? e : new QNode.QExpr.Materialize(x.span, plan);
        }
        if (e instanceof QNode.QExpr.Variable || e instanceof QNode.QExpr.Parameter
                || e instanceof QNode.QExpr.Bottom || e instanceof QNode.QExpr.Constant) {
            return e;
        }
        throw new IllegalArgumentException("uncovered QExpr normalization case: "
                + e.getClass().getName());
    }

    /** Normalize plan subtrees, including expressions embedded in plan nodes. */
    public static QNode.QPlan normalizePlan(QNode.QPlan p) {
        if (p instanceof QNode.QPlan.FromCollection x) {
            QNode.QExpr collection = normalizeExpr(x.collection);
            return collection == x.collection ? p
                    : new QNode.QPlan.FromCollection(x.span, collection);
        }
        if (p instanceof QNode.QPlan.NavigateMany x) {
            QNode.QExpr source = normalizeExpr(x.source);
            List<QNode.QExpr> qualifiers = normalizeExpressions(x.qualifiers);
            return source == x.source && qualifiers == x.qualifiers ? p
                    : new QNode.QPlan.NavigateMany(x.span, source,
                    x.associationName, x.roleName, qualifiers, x.type,
                    x.reverse, x.associationClass, x.viaAssociationClass);
        }
        if (p instanceof QNode.QPlan.Filter x) {
            QNode.QPlan source = normalizePlan(x.source);
            QNode.QExpr predicate = normalizeExpr(x.predicate);
            return source == x.source && predicate == x.predicate ? p
                    : new QNode.QPlan.Filter(x.span, source, x.iterator, predicate, x.isSelect);
        }
        if (p instanceof QNode.QPlan.Collect x) {
            QNode.QPlan source = normalizePlan(x.source);
            QNode.QExpr body = normalizeExpr(x.body);
            return source == x.source && body == x.body ? p
                    : new QNode.QPlan.Collect(x.span, source, x.iterator, body);
        }
        if (p instanceof QNode.QPlan.Distinct x) {
            QNode.QPlan source = normalizePlan(x.source);
            return source == x.source ? p : new QNode.QPlan.Distinct(x.span, source);
        }
        if (p instanceof QNode.QPlan.PlanLet x) {
            QNode.QExpr value = normalizeExpr(x.value);
            QNode.QPlan body = normalizePlan(x.body);
            return value == x.value && body == x.body ? p
                    : new QNode.QPlan.PlanLet(x.span, x.binder, value, body);
        }
        if (p instanceof QNode.QPlan.ScanClass) {
            return p;
        }
        throw new IllegalArgumentException("uncovered QPlan normalization case: "
                + p.getClass().getName());
    }

    /** Preserve list identity when no child required normalization. */
    private static List<QNode.QExpr> normalizeExpressions(List<QNode.QExpr> expressions) {
        List<QNode.QExpr> normalized = new ArrayList<>(expressions.size());
        boolean changed = false;
        for (QNode.QExpr expression : expressions) {
            QNode.QExpr next = normalizeExpr(expression);
            normalized.add(next);
            changed |= next != expression;
        }
        return changed ? List.copyOf(normalized) : expressions;
    }

    /** Deterministic semantic-tree key used by normalization/property tests and traces. */
    public static String structuralKey(QNode.QExpr expression) {
        StringBuilder out = new StringBuilder();
        appendExpr(out, expression);
        return out.toString();
    }

    /** Deterministic semantic-tree key for occurrence plans. */
    public static String structuralKey(QNode.QPlan plan) {
        StringBuilder out = new StringBuilder();
        appendPlan(out, plan);
        return out.toString();
    }

    private static void appendExpr(StringBuilder out, QNode.QExpr e) {
        out.append(e.kind()).append('<').append(e.type).append('>');
        if (e instanceof QNode.QExpr.Variable x) {
            out.append('#').append(x.declaration.id());
        } else if (e instanceof QNode.QExpr.Parameter x) {
            out.append('(').append(x.parameter.name()).append(')');
        } else if (e instanceof QNode.QExpr.Bottom) {
            // kind and type are the complete structure
        } else if (e instanceof QNode.QExpr.Constant x) {
            out.append('(').append(x.literalValue.getClass().getName()).append(':')
                    .append(x.literalValue).append(')');
        } else if (e instanceof QNode.QExpr.Coerce x) {
            out.append('(').append(x.kind).append(',').append(x.sourceType).append(',');
            appendExpr(out, x.source);
            out.append(')');
        } else if (e instanceof QNode.QExpr.Let x) {
            out.append("(#").append(x.binder.id()).append(',');
            appendExpr(out, x.value);
            out.append(',');
            appendExpr(out, x.body);
            out.append(')');
        } else if (e instanceof QNode.QExpr.IfExpr x) {
            out.append('(');
            appendExpr(out, x.condition);
            out.append(',');
            appendExpr(out, x.thenExpr);
            out.append(',');
            appendExpr(out, x.elseExpr);
            out.append(')');
        } else if (e instanceof QNode.QExpr.ReadAttribute x) {
            out.append('(').append(x.ownerClassKey).append("::").append(x.attributeName)
                    .append(',');
            appendExpr(out, x.source);
            out.append(')');
        } else if (e instanceof QNode.QExpr.NavigateOne x) {
            out.append('(').append(x.associationName).append(',').append(x.roleName)
                    .append(',').append(x.reverse).append(',').append(x.associationClass)
                    .append(',').append(x.viaAssociationClass).append(',');
            appendExpr(out, x.source);
            appendExprList(out, x.qualifiers);
            out.append(')');
        } else if (e instanceof QNode.QExpr.TypeTest x) {
            out.append('(').append(x.testKind).append(',').append(x.targetClassKey).append(',');
            appendExpr(out, x.source);
            out.append(')');
        } else if (e instanceof QNode.QExpr.TypeCast x) {
            out.append('(').append(x.targetClassKey).append(',');
            appendExpr(out, x.source);
            out.append(')');
        } else if (e instanceof QNode.QExpr.Unary x) {
            out.append('(').append(x.operator).append(',');
            appendExpr(out, x.operand);
            out.append(')');
        } else if (e instanceof QNode.QExpr.Binary x) {
            out.append('(').append(x.operator).append(',');
            appendExpr(out, x.left);
            out.append(',');
            appendExpr(out, x.right);
            out.append(')');
        } else if (e instanceof QNode.QExpr.Exists3 x) {
            appendFold(out, x.source, x.iterator.id(), x.predicate);
        } else if (e instanceof QNode.QExpr.ForAll3 x) {
            appendFold(out, x.source, x.iterator.id(), x.predicate);
        } else if (e instanceof QNode.QExpr.CollectionLiteral x) {
            appendExprList(out, x.elements);
        } else if (e instanceof QNode.QExpr.IncludesFamily x) {
            out.append('(').append(x.includesKind).append(',');
            appendExpr(out, x.source);
            out.append(',');
            appendExpr(out, x.element);
            out.append(')');
        } else if (e instanceof QNode.QExpr.CountFamily x) {
            out.append('(').append(x.countKind).append(',');
            appendExpr(out, x.source);
            if (x.element != null) {
                out.append(',');
                appendExpr(out, x.element);
            }
            out.append(')');
        } else if (e instanceof QNode.QExpr.SetAlgebra x) {
            out.append('(').append(x.operator).append(',');
            appendExpr(out, x.left);
            out.append(',');
            appendExpr(out, x.right);
            out.append(')');
        } else if (e instanceof QNode.QExpr.Materialize x) {
            out.append('(');
            appendPlan(out, x.plan);
            out.append(')');
        } else {
            throw new IllegalArgumentException("uncovered QExpr " + e.getClass().getName());
        }
    }

    private static void appendFold(StringBuilder out, QNode.QPlan source, long binderId,
                                   QNode.QExpr predicate) {
        out.append("(#").append(binderId).append(',');
        appendPlan(out, source);
        out.append(',');
        appendExpr(out, predicate);
        out.append(')');
    }

    private static void appendExprList(StringBuilder out,
                                       java.util.List<QNode.QExpr> expressions) {
        out.append('[');
        for (QNode.QExpr expression : expressions) {
            appendExpr(out, expression);
            out.append(';');
        }
        out.append(']');
    }

    private static void appendPlan(StringBuilder out, QNode.QPlan p) {
        out.append(p.kind()).append('<').append(p.type).append('>');
        if (p instanceof QNode.QPlan.FromCollection x) {
            out.append('(');
            appendExpr(out, x.collection);
            out.append(')');
        } else if (p instanceof QNode.QPlan.ScanClass x) {
            out.append('(').append(x.classKey).append(",#").append(x.variable.id()).append(')');
        } else if (p instanceof QNode.QPlan.NavigateMany x) {
            out.append('(').append(x.associationName).append(',').append(x.roleName)
                    .append(',').append(x.reverse).append(',').append(x.associationClass)
                    .append(',').append(x.viaAssociationClass).append(',');
            appendExpr(out, x.source);
            appendExprList(out, x.qualifiers);
            out.append(')');
        } else if (p instanceof QNode.QPlan.Filter x) {
            out.append("(#").append(x.iterator.id()).append(',').append(x.isSelect).append(',');
            appendPlan(out, x.source);
            out.append(',');
            appendExpr(out, x.predicate);
            out.append(')');
        } else if (p instanceof QNode.QPlan.Collect x) {
            out.append("(#").append(x.iterator.id()).append(',');
            appendPlan(out, x.source);
            out.append(',');
            appendExpr(out, x.body);
            out.append(')');
        } else if (p instanceof QNode.QPlan.Distinct x) {
            out.append('(');
            appendPlan(out, x.source);
            out.append(')');
        } else if (p instanceof QNode.QPlan.PlanLet x) {
            out.append("(#").append(x.binder.id()).append(',');
            appendExpr(out, x.value);
            out.append(',');
            appendPlan(out, x.body);
            out.append(')');
        } else {
            throw new IllegalArgumentException("uncovered QPlan " + p.getClass().getName());
        }
    }

}
