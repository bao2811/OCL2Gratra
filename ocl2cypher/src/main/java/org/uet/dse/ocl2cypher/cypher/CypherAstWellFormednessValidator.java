package org.uet.dse.ocl2cypher.cypher;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Set;
import org.uet.dse.ocl2cypher.cypher.CypherAst.*;

/** Lightweight R-4 structural validator; semantic graph obligations remain separate. */
public final class CypherAstWellFormednessValidator {
    private final Set<String> parameters;
    private final IdentityHashMap<CypherExpr, Boolean> observedComputations =
            new IdentityHashMap<>();
    private CypherAstWellFormednessValidator(Set<String> parameters) {
        this.parameters = Set.copyOf(parameters);
    }

    public static void validate(GeneratedArtifact artifact) {
        if (artifact == null || artifact.query() == null || artifact.contract() == null)
            throw new IllegalArgumentException("R_TARGET_WF: missing artifact/query/contract");
        Set<String> names = new HashSet<>();
        for (QueryParameter p : artifact.parameters()) {
            if (!isParameterName(p.name()) || p.logicalTypeTag() == null
                    || p.logicalTypeTag().isBlank())
                throw new IllegalArgumentException("R_TARGET_WF: empty parameter name/type");
            if ((p.origin() == QueryParameter.Origin.GENERATED) != (p.canonicalValue() != null))
                throw new IllegalArgumentException("R_TARGET_WF: binding contradicts parameter origin " + p.name());
            if (p.name().equals("__oclContextId") && (p.origin() != QueryParameter.Origin.PUBLIC
                    || !p.logicalTypeTag().equals("Physical:StableObjectId")))
                throw new IllegalArgumentException("R_TARGET_WF: invalid context identity declaration");
            if (!names.add(p.name())) throw new IllegalArgumentException("R_TARGET_WF: duplicate parameter " + p.name());
            if (p.origin() == QueryParameter.Origin.GENERATED && !p.name().startsWith("__ocl"))
                throw new IllegalArgumentException("R_TARGET_WF: generated namespace " + p.name());
            if (p.origin() == QueryParameter.Origin.PUBLIC && p.name().startsWith("__ocl")
                    && !p.name().equals("__oclContextId"))
                throw new IllegalArgumentException("R_TARGET_WF: public namespace " + p.name());
        }
        CypherQuery q = artifact.query();
        validateQuery(q);
        if (q.requiresFinalReturn() && (q.clauses().isEmpty()
                || !(q.clauses().get(q.clauses().size() - 1) instanceof ReturnClause)))
            throw new IllegalArgumentException("R_TARGET_WF: missing final RETURN");
        if (artifact.contract().resultVariable() == null || artifact.contract().resultVariable().isBlank())
            throw new IllegalArgumentException("R_TARGET_WF: empty result variable");
        ResultContract rc = artifact.contract();
        boolean needsDistinct = rc.shape() == ResultShape.SET || rc.shape() == ResultShape.IDS;
        if (rc.distinctRequired() != needsDistinct)
            throw new IllegalArgumentException("R_TARGET_WF: distinct policy contradicts result shape");
        validateResultContract(rc);
        if (q.requiresFinalReturn()) {
            ReturnClause ret = (ReturnClause) q.clauses().get(q.clauses().size() - 1);
            if (ret.items().stream().noneMatch(i -> rc.resultVariable().equals(i.alias())))
                throw new IllegalArgumentException("R_TARGET_WF: result variable not projected");
        }
        new CypherAstWellFormednessValidator(names).validateQueryScope(q, Set.of());
    }

    /** Expressions in a projection see the old scope, not sibling aliases. */
    private Set<String> project(java.util.List<ProjectionItem> items, Set<String> scope) {
        Set<String> next = new HashSet<>();
        for (ProjectionItem item : items) {
            validateExprScope(item.expression(), scope);
            String alias = item.alias();
            if (alias == null && item.expression() instanceof VariableExpr v) alias = v.name();
            if (alias != null && (alias.isBlank() || !next.add(alias)))
                throw new IllegalArgumentException("R_TARGET_WF: empty/duplicate projection alias");
        }
        return next;
    }

    private void validateQueryScope(CypherQuery q, Set<String> imported) {
        validateQuery(q);
        Set<String> scope = new HashSet<>(imported);
        for (int index = 0; index < q.clauses().size(); index++) {
            CypherClause clause = q.clauses().get(index);
            if (clause instanceof MatchClause m) {
                for (PathPattern path : m.pattern().paths()) {
                    for (NodePattern n : path.nodes()) if (n.variable() != null) scope.add(n.variable());
                    for (RelPattern r : path.relationships()) if (r.variable() != null) scope.add(r.variable());
                }
                for (PathPattern path : m.pattern().paths()) {
                    for (NodePattern n : path.nodes())
                        for (PropertyMapEntry p : n.properties()) validateExprScope(p.value(), scope);
                    for (RelPattern r : path.relationships())
                        for (PropertyMapEntry p : r.properties()) validateExprScope(p.value(), scope);
                }
                if (m.where() != null) validateExprScope(m.where(), scope);
            } else if (clause instanceof WithClause w) {
                Set<String> next = project(w.items(), scope);
                // WITH WHERE can refer to incoming variables as well as projected aliases.
                Set<String> predicateScope = new HashSet<>(scope);
                predicateScope.addAll(next);
                if (w.where() != null) validateExprScope(w.where(), predicateScope);
                scope = next;
            } else if (clause instanceof UnwindClause u) {
                validateExprScope(u.expression(), scope);
                requireBinder(u.alias(), "UNWIND");
                if (scope.contains(u.alias()))
                    throw new IllegalArgumentException("R_TARGET_WF: UNWIND binder captures "
                            + u.alias());
                scope.add(u.alias());
            } else if (clause instanceof ReturnClause r) {
                if (index != q.clauses().size() - 1)
                    throw new IllegalArgumentException("R_TARGET_WF: RETURN must be final");
                project(r.items(), scope);
            } else throw new IllegalArgumentException("R_TARGET_WF: unsupported clause");
        }
    }

    private void validateExprScope(CypherExpr e, Set<String> scope) {
        if (e == null) throw new IllegalArgumentException("R_TARGET_WF: missing expression");
        if (!isReferenceCarrier(e) && observedComputations.put(e, Boolean.TRUE) != null) {
            throw new IllegalArgumentException(
                    "R_TARGET_WF: " + e.getClass().getSimpleName()
                            + " is serialized more than once; bind the computation first");
        }
        if (e instanceof LocatedExpr located) {
            validateExprScope(located.expression(), scope);
            return;
        }
        if (e instanceof ParameterExpr p && !parameters.contains(p.name()))
            throw new IllegalArgumentException("R_TARGET_WF: undeclared parameter " + p.name());
        if (e instanceof VariableExpr v) {
            requireBinder(v.name(), "variable");
            if (!scope.contains(v.name()))
                throw new IllegalArgumentException("R_TARGET_WF: variable outside scope "
                        + v.name());
        }
        if (e instanceof ParameterExpr p && (p.name() == null || p.name().isBlank()))
            throw new IllegalArgumentException("R_TARGET_WF: empty parameter reference");
        if (e instanceof IntegerLiteral i && i.value() == null)
            throw new IllegalArgumentException("R_TARGET_WF: null integer literal");
        if (e instanceof FloatLiteral f && f.value() == null)
            throw new IllegalArgumentException("R_TARGET_WF: null float literal");
        if (e instanceof StringLiteral s && s.value() == null)
            throw new IllegalArgumentException("R_TARGET_WF: null string literal");
        if (e instanceof PropertyAccess p) validateExprScope(p.source(), scope);
        if (e instanceof BinaryExpr b) { validateExprScope(b.left(), scope); validateExprScope(b.right(), scope); }
        if (e instanceof UnaryExpr u) validateExprScope(u.operand(), scope);
        if (e instanceof ListExpr l) l.items().forEach(x -> validateExprScope(x, scope));
        if (e instanceof MapExpr m) {
            Set<String> keys = new HashSet<>();
            for (MapEntry entry : m.entries()) {
                if (entry.key() == null || entry.key().isEmpty() || !keys.add(entry.key()))
                    throw new IllegalArgumentException("R_TARGET_WF: empty/duplicate map key");
                validateExprScope(entry.value(), scope);
            }
        }
        if (e instanceof CaseExpr c) {
            if (c.branches().isEmpty())
                throw new IllegalArgumentException("R_TARGET_WF: CASE requires a branch");
            for (WhenThen branch : c.branches()) {
                validateExprScope(branch.when(), scope);
                validateExprScope(branch.then(), scope);
            }
            if (c.elseExpr() != null) validateExprScope(c.elseExpr(), scope);
        }
        if (e instanceof ReduceExpr r) {
            validateExprScope(r.initial(), scope);
            validateExprScope(r.list(), scope);
            requireBinder(r.accumulator(), "reduce accumulator");
            requireBinder(r.variable(), "reduce variable");
            if (r.accumulator().equals(r.variable()) || scope.contains(r.accumulator())
                    || scope.contains(r.variable()))
                throw new IllegalArgumentException("R_TARGET_WF: invalid reduce binders");
            Set<String> child = new HashSet<>(scope);
            child.add(r.accumulator()); child.add(r.variable());
            validateExprScope(r.step(), child);
        }
        if (e instanceof FunctionCall f) {
            Set<String> allowed = Set.of("size", "head", "last", "toInteger", "toFloat",
                    "toString", "abs", "floor", "round", "type", "count", "collect");
            if (!allowed.contains(f.functionName()) || f.arguments().size() != 1
                    || (f.distinct() && !Set.of("count", "collect").contains(f.functionName())))
                throw new IllegalArgumentException("R_TARGET_WF: unsupported function/arity " + f.functionName());
            f.arguments().forEach(x -> validateExprScope(x, scope));
        }
        if (e instanceof ListComprehension l) {
            validateExprScope(l.list(), scope);
            requireBinder(l.variable(), "list comprehension");
            if (scope.contains(l.variable()))
                throw new IllegalArgumentException(
                        "R_TARGET_WF: list-comprehension binder captures " + l.variable());
            if (l.predicate() == null && l.projection() == null)
                throw new IllegalArgumentException(
                        "R_TARGET_WF: list comprehension requires predicate or projection");
            Set<String> child = new HashSet<>(scope); child.add(l.variable());
            if (l.predicate() != null) validateExprScope(l.predicate(), child);
            if (l.projection() != null) validateExprScope(l.projection(), child);
        }
        if (e instanceof QuantifiedPredicateExpression q) {
            validateExprScope(q.list(), scope);
            requireBinder(q.variable(), "quantified predicate");
            if (scope.contains(q.variable()))
                throw new IllegalArgumentException(
                        "R_TARGET_WF: quantified binder captures " + q.variable());
            Set<String> child = new HashSet<>(scope); child.add(q.variable());
            validateExprScope(q.predicate(), child);
        }
        if (e instanceof ExistsSubquery s) validateQueryScope(s.query(), scope);
        if (e instanceof CollectSubquery s) {
            validateQueryScope(s.query(), scope);
            var clauses = s.query().clauses();
            if (!s.query().requiresFinalReturn() || clauses.isEmpty()
                    || !(clauses.get(clauses.size() - 1) instanceof ReturnClause r)
                    || r.items().size() != 1)
                throw new IllegalArgumentException("R_TARGET_WF: COLLECT requires one returned item");
        }
    }

    /**
     * Values made only from literals, parameters, variables, and their field
     * projections are references/data, not computations. Repeating them cannot
     * expand a recursive realization tree.
     */
    private static boolean isReferenceCarrier(CypherExpr expression) {
        if (expression instanceof LocatedExpr located) {
            return isReferenceCarrier(located.expression());
        }
        if (expression instanceof VariableExpr || expression instanceof ParameterExpr
                || expression instanceof NullLiteral || expression instanceof BooleanLiteral
                || expression instanceof IntegerLiteral || expression instanceof FloatLiteral
                || expression instanceof StringLiteral) {
            return true;
        }
        if (expression instanceof PropertyAccess property) {
            return isReferenceCarrier(property.source());
        }
        if (expression instanceof UnaryExpr unary) {
            return isReferenceCarrier(unary.operand());
        }
        if (expression instanceof BinaryExpr binary) {
            return isReferenceCarrier(binary.left()) && isReferenceCarrier(binary.right());
        }
        if (expression instanceof FunctionCall function) {
            return function.arguments().stream().allMatch(
                    CypherAstWellFormednessValidator::isReferenceCarrier);
        }
        if (expression instanceof ListExpr list) {
            return list.items().stream().allMatch(
                    CypherAstWellFormednessValidator::isReferenceCarrier);
        }
        if (expression instanceof MapExpr map) {
            return map.entries().stream().allMatch(entry -> isReferenceCarrier(entry.value()));
        }
        return false;
    }

    private static void validateQuery(CypherQuery q) {
        if (q.clauses().isEmpty() && q.requiresFinalReturn())
            throw new IllegalArgumentException("R_TARGET_WF: empty returning query");
        for (CypherClause clause : q.clauses()) {
            if (clause instanceof MatchClause m) {
                if (m.pattern().paths().isEmpty())
                    throw new IllegalArgumentException("R_TARGET_WF: MATCH requires a path");
                for (PathPattern p : m.pattern().paths()) {
                    if (p.nodes().size() != p.relationships().size() + 1)
                        throw new IllegalArgumentException("R_TARGET_WF: invalid path arity");
                    for (NodePattern n : p.nodes()) {
                        if (n.variable() != null && n.variable().isBlank())
                            throw new IllegalArgumentException("R_TARGET_WF: empty node variable");
                        if (n.labels().stream().anyMatch(x -> x == null || x.isBlank())
                                || new HashSet<>(n.labels()).size() != n.labels().size()
                                || n.properties().stream().anyMatch(x -> x.key() == null
                                        || x.key().isBlank())
                                || n.properties().stream().map(PropertyMapEntry::key).distinct().count() != n.properties().size())
                            throw new IllegalArgumentException("R_TARGET_WF: duplicate node label/property");
                    }
                    for (RelPattern r : p.relationships()) {
                        if (r.variable() != null && r.variable().isBlank())
                            throw new IllegalArgumentException(
                                    "R_TARGET_WF: empty relationship variable");
                        if (r.typeName() == null || r.typeName().isBlank()
                                || r.direction() == null
                                || r.properties().stream().anyMatch(x -> x.key() == null
                                        || x.key().isBlank())
                                || r.properties().stream().map(PropertyMapEntry::key).distinct().count() != r.properties().size())
                            throw new IllegalArgumentException("R_TARGET_WF: duplicate relationship property");
                    }
                }
            }
        }
        if (q.requiresFinalReturn() && (q.clauses().isEmpty()
                || !(q.clauses().get(q.clauses().size()-1) instanceof ReturnClause)))
            throw new IllegalArgumentException("R_TARGET_WF: nested query missing final RETURN");
    }

    private static void validateResultContract(ResultContract contract) {
        if (contract.shape() == null || contract.elementTypeTag() == null
                || contract.elementTypeTag().isBlank()) {
            throw new IllegalArgumentException("R_TARGET_WF: incomplete result contract");
        }
        switch (contract.shape()) {
            case IDS -> {
                if (!"StableObjectId".equals(contract.elementTypeTag())
                        || contract.wholeBottomTag() != null) {
                    throw new IllegalArgumentException("R_TARGET_WF: malformed IDS contract");
                }
            }
            case SCALAR -> {
                if (!isCanonicalAtomicTag(contract.elementTypeTag())
                        || contract.wholeBottomTag() != null) {
                    throw new IllegalArgumentException("R_TARGET_WF: malformed scalar contract");
                }
            }
            case SET -> requireCollectionContract(contract, "Set<");
            case BAG -> requireCollectionContract(contract, "Bag<");
        }
    }

    private static void requireCollectionContract(ResultContract contract, String prefix) {
        String tag = contract.elementTypeTag();
        if (!tag.startsWith(prefix) || !tag.endsWith(">")
                || !isCanonicalAtomicTag(tag.substring(prefix.length(), tag.length() - 1))
                || !CypherArtifacts.OCL_BOTTOM.equals(contract.wholeBottomTag())) {
            throw new IllegalArgumentException("R_TARGET_WF: malformed collection contract");
        }
    }

    private static boolean isCanonicalAtomicTag(String tag) {
        return Set.of("Boolean3", "Integer", "Real", "String").contains(tag)
                || (tag.startsWith("Class:") && tag.length() > "Class:".length());
    }

    private static void requireBinder(String name, String owner) {
        if (name == null || name.isBlank() || hasControlOrUnpairedSurrogate(name))
            throw new IllegalArgumentException("R_TARGET_WF: empty " + owner + " binder");
    }

    private static boolean isParameterName(String name) {
        return name != null && name.matches("[A-Za-z_][A-Za-z0-9_]*");
    }

    private static boolean hasControlOrUnpairedSurrogate(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isISOControl(c)) return true;
            if (Character.isHighSurrogate(c)) {
                if (i + 1 >= value.length()
                        || !Character.isLowSurrogate(value.charAt(i + 1))) return true;
                i++;
            } else if (Character.isLowSurrogate(c)) {
                return true;
            }
        }
        return false;
    }
}
