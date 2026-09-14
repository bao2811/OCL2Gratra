package org.uet.dse.ocl2cypher.core;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Objects;
import org.uet.dse.ocl2cypher.diagnostics.SourceSpan;
import org.uet.dse.ocl2cypher.runtime.OclType;

/**
 * Typed Core expression — the direct children of one {@link CoreUnit}.
 *
 * <p>Every constructor keeps its static {@link OclType}. A literal's carrier
 * is the exact lexical value, not a lossy host literal: an integer token is
 * stored as a {@link BigInteger} and a real token as a {@link BigDecimal}, so
 * {@code E_NUMERIC_FIDELITY} is checked before a Core expression is built.
 */
public abstract sealed class CoreExpr
        permits CoreExpr.LiteralBoolean,
                CoreExpr.LiteralInteger,
                CoreExpr.LiteralReal,
                CoreExpr.LiteralString,
                CoreExpr.Bottom,
                CoreExpr.Variable,
                CoreExpr.Let,
                CoreExpr.IfExpr,
                CoreExpr.Coerce,
                CoreExpr.AttributeRead,
                CoreExpr.Navigation,
                CoreExpr.AssociationClassNavigation,
                CoreExpr.AllInstances,
                CoreExpr.TypeTest,
                CoreExpr.TypeCast,
                CoreExpr.Unary,
                CoreExpr.Binary,
                CoreExpr.CollectionLiteral,
                CoreExpr.Iterator {

    public abstract OclType type();

    /** Source trace span; public so lowering/translation passes in other packages can thread it. */
    public final SourceSpan span;

    protected CoreExpr(SourceSpan span) {
        this.span = Objects.requireNonNull(span, "core expr span");
    }

    public static final class LiteralBoolean extends CoreExpr {
        public final boolean literal;

        public LiteralBoolean(SourceSpan span, boolean literal) {
            super(span);
            this.literal = literal;
        }

        @Override public OclType type() {
            return OclType.BOOLEAN;
        }
    }

    public static final class LiteralInteger extends CoreExpr {
        public final BigInteger literal;

        public LiteralInteger(SourceSpan span, BigInteger literal) {
            super(span);
            this.literal = Objects.requireNonNull(literal, "literal");
        }

        @Override public OclType type() {
            return OclType.INTEGER;
        }
    }

    public static final class LiteralReal extends CoreExpr {
        public final BigDecimal literal;

        public LiteralReal(SourceSpan span, BigDecimal literal) {
            super(span);
            this.literal = Objects.requireNonNull(literal, "literal");
        }

        @Override public OclType type() {
            return OclType.REAL;
        }
    }

    public static final class LiteralString extends CoreExpr {
        public final String literal;

        public LiteralString(SourceSpan span, String literal) {
            super(span);
            this.literal = Objects.requireNonNull(literal, "literal");
        }

        @Override public OclType type() {
            return OclType.STRING;
        }
    }

    /** The single inhabitant of {@code bottom_tau} for the carried type {@code tau}. */
    public static final class Bottom extends CoreExpr {
        private final OclType tau;

        public Bottom(SourceSpan span, OclType tau) {
            super(span);
            this.tau = Objects.requireNonNull(tau, "bottom carrier type");
        }

        @Override public OclType type() {
            return tau;
        }
    }

    public static final class Variable extends CoreExpr {
        public final CoreDeclaration declaration;

        public Variable(SourceSpan span, CoreDeclaration declaration) {
            super(span);
            this.declaration = Objects.requireNonNull(declaration, "variable declaration");
        }

        @Override public OclType type() {
            return declaration.type();
        }
    }

    // Lexical scoping: value and body share the surrounding environment, but the
    // body also sees the single let binder and its declaration identity.
    public static final class Let extends CoreExpr {
        public final CoreDeclaration binder;
        public final CoreExpr value;
        public final CoreExpr inExpr;

        public Let(SourceSpan span, CoreDeclaration binder, CoreExpr value, CoreExpr inExpr) {
            super(span);
            this.binder = Objects.requireNonNull(binder, "binder");
            this.value = Objects.requireNonNull(value, "value");
            this.inExpr = Objects.requireNonNull(inExpr, "in-expression");
        }

        @Override public OclType type() {
            return inExpr.type();
        }
    }

    // Branch evaluation is lazy and type-joining has already been applied, so no
    // runtime coercion is needed to distinguish the unselected branch.
    public static final class IfExpr extends CoreExpr {
        public final CoreExpr condition;
        public final CoreExpr thenExpr;
        public final CoreExpr elseExpr;
        private final OclType join;

        public IfExpr(SourceSpan span, CoreExpr condition, CoreExpr thenExpr,
                      CoreExpr elseExpr, OclType join) {
            super(span);
            this.condition = Objects.requireNonNull(condition, "condition");
            this.thenExpr = Objects.requireNonNull(thenExpr, "then-expression");
            this.elseExpr = Objects.requireNonNull(elseExpr, "else-expression");
            this.join = Objects.requireNonNull(join, "branch join");
        }

        @Override public OclType type() {
            return join;
        }
    }

    public enum CoercionKind {
        INTEGER_TO_REAL,
        CLASS_UPCAST,
        COLLECTION_ELEMENT_COERCION
    }

    public static final class Coerce extends CoreExpr {
        public final CoercionKind kind;
        public final OclType sourceType;
        public final CoreExpr source;
        private final OclType target;

        public Coerce(SourceSpan span, CoercionKind kind, OclType sourceType,
                      CoreExpr source, OclType target) {
            super(span);
            this.kind = Objects.requireNonNull(kind, "coercion");
            this.sourceType = Objects.requireNonNull(sourceType, "source type");
            this.source = Objects.requireNonNull(source, "source");
            this.target = Objects.requireNonNull(target, "target type");
        }

        @Override public OclType type() {
            return target;
        }
    }

    public static final class AttributeRead extends CoreExpr {
        public final CoreExpr source;
        public final String ownerClassKey;
        public final String attributeName;
        private final OclType declaredType;

        public AttributeRead(SourceSpan span, CoreExpr source, String ownerClassKey,
                             String attributeName, OclType declaredType) {
            super(span);
            this.source = Objects.requireNonNull(source, "source");
            this.ownerClassKey = Objects.requireNonNull(ownerClassKey, "owner class");
            this.attributeName = Objects.requireNonNull(attributeName, "attribute");
            this.declaredType = Objects.requireNonNull(declaredType, "declared type");
        }

        @Override public OclType type() {
            return declaredType;
        }
    }

    public enum NavKind {
        TO_ONE,
        TO_MANY
    }

    public static final class Navigation extends CoreExpr {
        public final NavKind kind;
        public final CoreExpr source;
        public final String associationName;
        public final String roleName;
        public final boolean reverse;
        public final boolean viaAssociationClass;
        public final List<CoreExpr> qualifiers;
        private final OclType declaredType;

        public Navigation(SourceSpan span, NavKind kind, CoreExpr source,
                          String associationName, String roleName,
                          List<CoreExpr> qualifiers, OclType declaredType) {
            this(span, kind, source, associationName, roleName, qualifiers, declaredType, false);
        }

        public Navigation(SourceSpan span, NavKind kind, CoreExpr source,
                          String associationName, String roleName,
                          List<CoreExpr> qualifiers, OclType declaredType,
                          boolean reverse) {
            this(span, kind, source, associationName, roleName, qualifiers,
                    declaredType, reverse, false);
        }

        public Navigation(SourceSpan span, NavKind kind, CoreExpr source,
                          String associationName, String roleName,
                          List<CoreExpr> qualifiers, OclType declaredType,
                          boolean reverse, boolean viaAssociationClass) {
            super(span);
            this.kind = Objects.requireNonNull(kind, "navigation kind");
            this.source = Objects.requireNonNull(source, "source");
            this.associationName = Objects.requireNonNull(associationName, "association");
            this.roleName = Objects.requireNonNull(roleName, "role");
            this.reverse = reverse;
            this.viaAssociationClass = viaAssociationClass;
            this.qualifiers = List.copyOf(Objects.requireNonNull(qualifiers, "qualifiers"));
            this.declaredType = Objects.requireNonNull(declaredType, "declared type");
        }

        @Override public OclType type() {
            return declaredType;
        }
    }

    public static final class AssociationClassNavigation extends CoreExpr {
        public final NavKind kind;
        public final CoreExpr source;
        public final String associationClassKey;
        public final String navigationSource;
        public final boolean receiverIsTarget;
        public final List<CoreExpr> qualifiers;
        private final OclType declaredType;

        public AssociationClassNavigation(SourceSpan span, NavKind kind, CoreExpr source,
                                          String associationClassKey, String navigationSource,
                                          List<CoreExpr> qualifiers, OclType declaredType) {
            this(span, kind, source, associationClassKey, navigationSource,
                    qualifiers, declaredType, false);
        }

        public AssociationClassNavigation(SourceSpan span, NavKind kind, CoreExpr source,
                                          String associationClassKey, String navigationSource,
                                          List<CoreExpr> qualifiers, OclType declaredType,
                                          boolean receiverIsTarget) {
            super(span);
            this.kind = Objects.requireNonNull(kind, "navigation kind");
            this.source = Objects.requireNonNull(source, "source");
            this.associationClassKey = Objects.requireNonNull(associationClassKey, "assoc class");
            this.navigationSource = Objects.requireNonNull(navigationSource, "navigation source");
            this.receiverIsTarget = receiverIsTarget;
            this.qualifiers = List.copyOf(Objects.requireNonNull(qualifiers, "qualifiers"));
            this.declaredType = Objects.requireNonNull(declaredType, "declared type");
        }

        @Override public OclType type() {
            return declaredType;
        }
    }

    public static final class AllInstances extends CoreExpr {
        public final String classKey;

        public AllInstances(SourceSpan span, String classKey) {
            super(span);
            this.classKey = Objects.requireNonNull(classKey, "class key");
        }

        @Override public OclType type() {
            return OclType.set(OclType.clazz(classKey));
        }
    }

    public enum TypeTestKind {
        EXACT_TYPE,
        CONFORMS_TO
    }

    public static final class TypeTest extends CoreExpr {
        public final TypeTestKind kind;
        public final CoreExpr source;
        public final String targetClassKey;

        public TypeTest(SourceSpan span, TypeTestKind kind, CoreExpr source, String targetClassKey) {
            super(span);
            this.kind = Objects.requireNonNull(kind, "test kind");
            this.source = Objects.requireNonNull(source, "source");
            this.targetClassKey = Objects.requireNonNull(targetClassKey, "target class");
        }

        @Override public OclType type() {
            return OclType.BOOLEAN;
        }
    }

    public static final class TypeCast extends CoreExpr {
        public final CoreExpr source;
        public final String targetClassKey;

        public TypeCast(SourceSpan span, CoreExpr source, String targetClassKey) {
            super(span);
            this.source = Objects.requireNonNull(source, "source");
            this.targetClassKey = Objects.requireNonNull(targetClassKey, "target class");
        }

        @Override public OclType type() {
            return OclType.clazz(targetClassKey);
        }
    }

    public enum UnaryOp {
        BOOLEAN_NOT,
        NUMERIC_NEGATE,
        NUMERIC_ABS,
        REAL_FLOOR,
        REAL_ROUND,
        COLLECTION_SIZE,
        COLLECTION_IS_EMPTY,
        COLLECTION_NOT_EMPTY,
        COLLECTION_SUM
    }

    public static final class Unary extends CoreExpr {
        public final UnaryOp operator;
        public final CoreExpr operand;
        private final OclType resultType;

        public Unary(SourceSpan span, UnaryOp operator, CoreExpr operand, OclType resultType) {
            super(span);
            this.operator = Objects.requireNonNull(operator, "operator");
            this.operand = Objects.requireNonNull(operand, "operand");
            this.resultType = Objects.requireNonNull(resultType, "result type");
        }

        @Override public OclType type() {
            return resultType;
        }
    }

    public enum BinaryOp {
        NUMERIC_ADD,
        NUMERIC_SUBTRACT,
        NUMERIC_MULTIPLY,
        REAL_DIVIDE,
        INTEGER_DIVIDE,
        INTEGER_MOD,
        NUMERIC_MAX,
        NUMERIC_MIN,
        LESS_THAN,
        LESS_THAN_OR_EQUAL,
        GREATER_THAN,
        GREATER_THAN_OR_EQUAL,
        VALUE_EQUAL,
        VALUE_NOT_EQUAL,
        BOOLEAN_AND,
        BOOLEAN_OR,
        BOOLEAN_XOR,
        BOOLEAN_IMPLIES,
        COLLECTION_COUNT,
        COLLECTION_INCLUDES,
        COLLECTION_EXCLUDES,
        COLLECTION_INCLUDES_ALL,
        COLLECTION_EXCLUDES_ALL,
        SET_UNION,
        SET_INTERSECTION
    }

    public static final class Binary extends CoreExpr {
        public final BinaryOp operator;
        public final CoreExpr left;
        public final CoreExpr right;
        private final OclType resultType;

        public Binary(SourceSpan span, BinaryOp operator, CoreExpr left, CoreExpr right,
                      OclType resultType) {
            super(span);
            this.operator = Objects.requireNonNull(operator, "operator");
            this.left = Objects.requireNonNull(left, "left operand");
            this.right = Objects.requireNonNull(right, "right operand");
            this.resultType = Objects.requireNonNull(resultType, "result type");
        }

        @Override public OclType type() {
            return resultType;
        }
    }

    /** Core's own collection-kind vocabulary; mirrors the AS enum without importing it. */
    public enum CollectionKind {
        SET,
        BAG
    }

    public static final class CollectionLiteral extends CoreExpr {
        public final CollectionKind kind;
        public final List<CoreExpr> elements;
        private final OclType litType;

        public CollectionLiteral(SourceSpan span, CollectionKind kind,
                                 List<CoreExpr> elements, OclType litType) {
            super(span);
            this.kind = Objects.requireNonNull(kind, "collection kind");
            this.elements = List.copyOf(Objects.requireNonNull(elements, "elements"));
            this.litType = Objects.requireNonNull(litType, "literal type");
        }

        @Override public OclType type() {
            return litType;
        }
    }

    public enum IteratorKind {
        EXISTS,
        FORALL,
        SELECT,
        REJECT,
        COLLECT
    }

    /**
     * One of {@code exists/forAll/select/reject/collect} — including the source
     * kind ({@code SET} or {@code BAG}) that determines whether the iteration
     * is member-based or occurrence-based.
     */
    public static final class Iterator extends CoreExpr {
        public final IteratorKind iteratorKind;
        public final CollectionKind sourceKind;
        public final CoreExpr source;
        public final CoreDeclaration iterator;
        public final CoreExpr body;
        private final OclType resultType;

        public Iterator(SourceSpan span, IteratorKind iteratorKind,
                        CollectionKind sourceKind, CoreExpr source,
                        CoreDeclaration iterator, CoreExpr body, OclType resultType) {
            super(span);
            this.iteratorKind = Objects.requireNonNull(iteratorKind, "iterator kind");
            this.sourceKind = Objects.requireNonNull(sourceKind, "source kind");
            this.source = Objects.requireNonNull(source, "source");
            this.iterator = Objects.requireNonNull(iterator, "iterator binder");
            this.body = Objects.requireNonNull(body, "iterator body");
            this.resultType = Objects.requireNonNull(resultType, "result type");
        }

        @Override public OclType type() {
            return resultType;
        }
    }
}
