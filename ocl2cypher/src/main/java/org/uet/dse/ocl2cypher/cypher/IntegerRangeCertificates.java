package org.uet.dse.ocl2cypher.cypher;

import java.math.BigInteger;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import org.uet.dse.ocl2cypher.qcyp.QNode;

/** Checked range witnesses, conditional on exact in-range Cypher integer ops.
 * Stored attribute payloads are checked against the concrete graph by Realization
 * before this conservative [MIN,MAX] certificate is consumed. Numeric variables
 * inherit the INT64 invariant from their already-certified realization binding. */
final class IntegerRangeCertificates {
    static final BigInteger MIN = BigInteger.valueOf(Long.MIN_VALUE);
    static final BigInteger MAX = BigInteger.valueOf(Long.MAX_VALUE);

    record Certificate(BigInteger lower, BigInteger upper, String rule,
                       List<Certificate> children) {
        Certificate { children = List.copyOf(children); }
    }

    private final IdentityHashMap<QNode.QExpr, Optional<Certificate>> cache = new IdentityHashMap<>();

    Optional<Certificate> certify(QNode.QExpr expression) {
        var existing = cache.get(expression);
        if (existing != null) return existing;
        var result = derive(expression);
        cache.put(expression, result);
        return result;
    }

    private Optional<Certificate> derive(QNode.QExpr e) {
        if (!e.type.isInteger()) return Optional.empty();
        if (e instanceof QNode.QExpr.Unary u
                && u.operator == org.uet.dse.ocl2cypher.core.CoreExpr.UnaryOp.COLLECTION_SUM)
            return sum(u.operand);
        if (e instanceof QNode.QExpr.CountFamily c && c.countKind == QNode.QKind.SUM)
            return sum(c.source);
        if (e instanceof QNode.QExpr.Constant c && c.literalValue instanceof BigInteger n) {
            return bounded(n, n, "I64-LITERAL", List.of());
        }
        if (e instanceof QNode.QExpr.Unary u
                && u.operator == org.uet.dse.ocl2cypher.core.CoreExpr.UnaryOp.NUMERIC_ABS) {
            return certify(u.operand).flatMap(child -> {
                BigInteger lo = child.lower.signum() >= 0 ? child.lower
                        : child.upper.signum() <= 0 ? child.upper.negate() : BigInteger.ZERO;
                BigInteger hi = child.lower.abs().max(child.upper.abs());
                return bounded(lo, hi, "I64-ABS", List.of(child));
            });
        }
        if (e instanceof QNode.QExpr.Unary u
                && u.operator == org.uet.dse.ocl2cypher.core.CoreExpr.UnaryOp.NUMERIC_NEGATE) {
            return certify(u.operand).flatMap(child -> bounded(
                    child.upper.negate(), child.lower.negate(),
                    "I64-NEGATE", List.of(child)));
        }
        if (e instanceof QNode.QExpr.Unary u
                && u.operator == org.uet.dse.ocl2cypher.core.CoreExpr.UnaryOp.COLLECTION_SIZE) {
            // Collection size is always a non-negative integer bounded by Neo4j graph cardinality.
            return bounded(BigInteger.ZERO, MAX, "I64-COLLECTION-SIZE", List.of());
        }
        if (e instanceof QNode.QExpr.IfExpr ifExpr && ifExpr.type.equals(org.uet.dse.ocl2cypher.runtime.OclType.INTEGER)) {
            var thenCert = certify(ifExpr.thenExpr);
            var elseCert = certify(ifExpr.elseExpr);
            if (thenCert.isPresent() && elseCert.isPresent()) {
                var t = thenCert.get(); var el = elseCert.get();
                return bounded(t.lower.min(el.lower), t.upper.max(el.upper),
                        "I64-IF-EXPR", List.of(t, el));
            }
        }
        if (e instanceof QNode.QExpr.ReadAttribute) {
            return bounded(MIN, MAX, "I64-STORED-DECIMAL", List.of());
        }
        if (e instanceof QNode.QExpr.Variable) {
            return bounded(MIN, MAX, "I64-VARIABLE", List.of());
        }
        if (e instanceof QNode.QExpr.Parameter) {
            return bounded(MIN, MAX, "I64-PARAMETER", List.of());
        }
        if (!(e instanceof QNode.QExpr.Binary b)) return Optional.empty();
        // Unsupported operators never acquire a certificate merely from a type.
        switch (b.operator) {
            case NUMERIC_ADD, NUMERIC_SUBTRACT, NUMERIC_MULTIPLY, NUMERIC_MIN, NUMERIC_MAX,
                 INTEGER_DIVIDE, INTEGER_MOD -> { }
            default -> { return Optional.empty(); }
        }
        var left = certify(b.left);
        var right = certify(b.right);
        if (left.isEmpty() || right.isEmpty()) return Optional.empty();
        var l = left.get(); var r = right.get();
        BigInteger lo, hi;
        switch (b.operator) {
            case INTEGER_DIVIDE, INTEGER_MOD -> {
                // Exact closed-expression domain only. No caller/snapshot bounds.
                if (!l.lower.equals(l.upper) || !r.lower.equals(r.upper)
                        || r.lower.signum() == 0) return Optional.empty();
                BigInteger quotient = l.lower.divide(r.lower); // truncates toward zero
                var q = bounded(quotient, quotient, "I64-DIV-QUOTIENT", List.of(l, r));
                if (q.isEmpty()) return Optional.empty(); // excludes MIN/-1 also for mod
                BigInteger product = r.lower.multiply(quotient);
                var p = bounded(product, product, "I64-DIV-PRODUCT", List.of(r, q.get()));
                if (p.isEmpty()) return Optional.empty();
                BigInteger result = b.operator == org.uet.dse.ocl2cypher.core.CoreExpr.BinaryOp.INTEGER_DIVIDE
                        ? quotient : l.lower.subtract(product);
                return bounded(result, result, "I64-EXACT-" + b.operator, List.of(l, r, q.get(), p.get()));
            }
            case NUMERIC_MIN -> { lo = l.lower.min(r.lower); hi = l.upper.min(r.upper); }
            case NUMERIC_MAX -> { lo = l.lower.max(r.lower); hi = l.upper.max(r.upper); }
            case NUMERIC_ADD -> { lo = l.lower.add(r.lower); hi = l.upper.add(r.upper); }
            case NUMERIC_SUBTRACT -> { lo = l.lower.subtract(r.upper); hi = l.upper.subtract(r.lower); }
            case NUMERIC_MULTIPLY -> {
                var corners = List.of(l.lower.multiply(r.lower), l.lower.multiply(r.upper),
                        l.upper.multiply(r.lower), l.upper.multiply(r.upper));
                lo = corners.stream().min(BigInteger::compareTo).orElseThrow();
                hi = corners.stream().max(BigInteger::compareTo).orElseThrow();
            }
            default -> throw new IllegalStateException("checked above");
        }
        return bounded(lo, hi, "I64-" + b.operator, List.of(l, r));
    }

    private static Optional<Certificate> bounded(BigInteger lo, BigInteger hi,
                                                 String rule, List<Certificate> children) {
        if (lo.compareTo(MIN) < 0 || hi.compareTo(MAX) > 0 || lo.compareTo(hi) > 0) {
            return Optional.empty();
        }
        return Optional.of(new Certificate(lo, hi, rule, children));
    }

    private Optional<Certificate> sum(QNode.QExpr source) {
        if (!(source instanceof QNode.QExpr.CollectionLiteral literal)
                || !source.type.isCollection() || !source.type.elementType().isInteger())
            return Optional.empty();
        boolean set = literal.collectionKind == org.uet.dse.ocl2cypher.core.CoreExpr.CollectionKind.SET;
        if (!source.type.equals(set ? org.uet.dse.ocl2cypher.runtime.OclType.set(
                org.uet.dse.ocl2cypher.runtime.OclType.INTEGER)
                : org.uet.dse.ocl2cypher.runtime.OclType.bag(org.uet.dse.ocl2cypher.runtime.OclType.INTEGER)))
            return Optional.empty();
        var children = new java.util.ArrayList<Certificate>();
        var seen = new java.util.HashSet<BigInteger>();
        BigInteger acc = BigInteger.ZERO;
        children.add(bounded(acc, acc, "I64-SUM-INITIAL", List.of()).orElseThrow());
        for (QNode.QExpr element : literal.elements) {
            var child = certify(element);
            // Bottom has no numeric interval: do not invent a zero witness.
            if (child.isEmpty() || !child.get().lower.equals(child.get().upper)) return Optional.empty();
            children.add(child.get()); // even duplicate Set expressions must certify
            BigInteger value = child.get().lower;
            if (set && !seen.add(value)) continue;
            acc = acc.add(value);
            var prefix = bounded(acc, acc, "I64-SUM-PREFIX", List.of(child.get()));
            if (prefix.isEmpty()) return Optional.empty();
            children.add(prefix.get());
        }
        return bounded(acc, acc, set ? "I64-SET-SUM" : "I64-BAG-SUM", children);
    }
}
