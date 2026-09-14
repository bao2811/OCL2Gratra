package org.uet.dse.ocl2cypher.qcyp;

import java.util.List;
import java.util.Objects;
import org.uet.dse.ocl2cypher.core.CoreDeclaration;
import org.uet.dse.ocl2cypher.diagnostics.SourceSpan;
import org.uet.dse.ocl2cypher.runtime.OclType;

/**
 * Q_CYP model — the occurrence-preserving counterpart of the Core IR that the
 * translator then emits. Every Q value lives in the graph's carrier, but the
 * structural view (per-operation {@code evalE}/{@code evalP}) already matters
 * here: a set observation stays extensional while a bag stays
 * occurrence-preserved, and the {@code Case} realization of a Boolean₃ has to
 * use tagged receiver values to avoid collapsing bottom/empty.
 *
 * <p>Note the carrier choice used later by Cypher: materializing a plan as a
 * single tagged collection value has to keep the whole-collection bottom, not an
 * empty collection, when row sources were missing.
 */
public final class QNode {

    private QNode() {
    }

    public enum QKind {
        // QExpr (scalar / collection-valued)
        VARIABLE,
        PARAMETER,
        BOTTOM,
        CONSTANT,
        COERCE,
        LET,
        IF,
        READ_ATTRIBUTE,
        NAVIGATE_ONE,
        TYPE_TEST,
        TYPE_CAST,
        UNARY,
        BINARY,
        EXISTS3,
        FORALL3,
        SET_LITERAL,
        BAG_LITERAL,
        INCLUDES,
        EXCLUDES,
        INCLUDES_ALL,
        EXCLUDES_ALL,
        COUNT,
        SIZE,
        IS_EMPTY,
        NOT_EMPTY,
        SUM,
        SET_UNION,
        SET_INTERSECTION,
        MATERIALIZE,
        // QPlan (occurrence-oriented)
        FROM_COLLECTION,
        SCAN_CLASS,
        NAVIGATE_MANY,
        FILTER_SELECT,
        FILTER_REJECT,
        COLLECT,
        DISTINCT,
        PLAN_LET
    }

    // ---- QExpr ----------------------------------------------------------

    public abstract static sealed class QExpr
            permits QExpr.Variable, QExpr.Parameter, QExpr.Bottom, QExpr.Constant, QExpr.Coerce,
                    QExpr.Let, QExpr.IfExpr, QExpr.ReadAttribute, QExpr.NavigateOne,
                    QExpr.TypeTest, QExpr.TypeCast, QExpr.Unary, QExpr.Binary,
                    QExpr.Exists3, QExpr.ForAll3, QExpr.CollectionLiteral,
                    QExpr.IncludesFamily, QExpr.CountFamily, QExpr.SetAlgebra,
                    QExpr.Materialize {
        public final SourceSpan span;
        public final OclType type;

        protected QExpr(SourceSpan span, OclType type) {
            this.span = Objects.requireNonNull(span, "span");
            this.type = Objects.requireNonNull(type, "type");
        }

        public abstract QKind kind();

        public static final class Variable extends QExpr {
            public final CoreDeclaration declaration;

            public Variable(SourceSpan span, CoreDeclaration declaration) {
                super(span, declaration.type());
                this.declaration = Objects.requireNonNull(declaration, "declaration");
            }

            @Override public QKind kind() { return QKind.VARIABLE; }
        }

        /** Public runtime parameter, separate from lexical variable bindings. */
        public static final class Parameter extends QExpr {
            public final QParameter parameter;

            public Parameter(SourceSpan span, QParameter parameter) {
                super(span, Objects.requireNonNull(parameter, "parameter").expectedType());
                this.parameter = parameter;
            }

            @Override public QKind kind() { return QKind.PARAMETER; }
        }

        public static final class Bottom extends QExpr {
            public Bottom(SourceSpan span, OclType type) { super(span, type); }
            @Override public QKind kind() { return QKind.BOTTOM; }
        }

        public static final class Constant extends QExpr {
            /**
             * Boolean, BigInteger, BigDecimal, String, or a non-empty stable
             * object id String when {@code type.isClass()}.
             */
            public final Object literalValue;

            public Constant(SourceSpan span, OclType type, Object literalValue) {
                super(span, type);
                this.literalValue = Objects.requireNonNull(literalValue, "literal");
            }

            @Override public QKind kind() {
                if (type.equals(OclType.BOOLEAN)) return QKind.CONSTANT;
                if (type.equals(OclType.INTEGER)) return QKind.CONSTANT;
                if (type.equals(OclType.REAL)) return QKind.CONSTANT;
                return QKind.CONSTANT;
            }
        }

        public static final class Coerce extends QExpr {
            public final org.uet.dse.ocl2cypher.core.CoreExpr.CoercionKind kind;
            public final OclType sourceType;
            public final QExpr source;

            public Coerce(SourceSpan span, org.uet.dse.ocl2cypher.core.CoreExpr.CoercionKind kind,
                          OclType sourceType, QExpr source, OclType target) {
                super(span, target);
                this.kind = kind;
                this.sourceType = sourceType;
                this.source = source;
            }

            @Override public QKind kind() { return QKind.COERCE; }
        }

        public static final class Let extends QExpr {
            public final CoreDeclaration binder;
            public final QExpr value;
            public final QExpr body;

            public Let(SourceSpan span, CoreDeclaration binder, QExpr value, QExpr body) {
                super(span, body.type);
                this.binder = binder;
                this.value = value;
                this.body = body;
            }

            @Override public QKind kind() { return QKind.LET; }
        }

        public static final class IfExpr extends QExpr {
            public final QExpr condition;
            public final QExpr thenExpr;
            public final QExpr elseExpr;

            public IfExpr(SourceSpan span, QExpr condition, QExpr thenExpr, QExpr elseExpr, OclType join) {
                super(span, join);
                this.condition = condition;
                this.thenExpr = thenExpr;
                this.elseExpr = elseExpr;
            }

            @Override public QKind kind() { return QKind.IF; }
        }

        public static final class ReadAttribute extends QExpr {
            public final QExpr source;
            public final String ownerClassKey;
            public final String attributeName;

            public ReadAttribute(SourceSpan span, QExpr source, String ownerClassKey,
                                 String attributeName, OclType declaredType) {
                super(span, declaredType);
                this.source = source;
                this.ownerClassKey = ownerClassKey;
                this.attributeName = attributeName;
            }

            @Override public QKind kind() { return QKind.READ_ATTRIBUTE; }
        }

        public static final class NavigateOne extends QExpr {
            public final QExpr source;
            public final String associationName;
            public final String roleName;
            public final boolean reverse;
            public final boolean associationClass;
            /** Ordinary participant navigation whose link is encoded as a link object. */
            public final boolean viaAssociationClass;
            public final List<QExpr> qualifiers;

            public NavigateOne(SourceSpan span, QExpr source, String associationName,
                               String roleName, List<QExpr> qualifiers, OclType targetType) {
                this(span, source, associationName, roleName, qualifiers, targetType, false);
            }

            public NavigateOne(SourceSpan span, QExpr source, String associationName,
                               String roleName, List<QExpr> qualifiers, OclType targetType,
                               boolean reverse) {
                this(span, source, associationName, roleName, qualifiers, targetType,
                        reverse, false, false);
            }

            public NavigateOne(SourceSpan span, QExpr source, String associationName,
                               String roleName, List<QExpr> qualifiers, OclType targetType,
                               boolean reverse, boolean associationClass) {
                this(span, source, associationName, roleName, qualifiers, targetType,
                        reverse, associationClass, false);
            }

            public NavigateOne(SourceSpan span, QExpr source, String associationName,
                               String roleName, List<QExpr> qualifiers, OclType targetType,
                               boolean reverse, boolean associationClass,
                               boolean viaAssociationClass) {
                super(span, targetType);
                this.source = source;
                this.associationName = associationName;
                this.roleName = roleName;
                this.reverse = reverse;
                this.associationClass = associationClass;
                this.viaAssociationClass = viaAssociationClass;
                this.qualifiers = List.copyOf(qualifiers);
            }

            @Override public QKind kind() { return QKind.NAVIGATE_ONE; }
        }

        public static final class TypeTest extends QExpr {
            public final org.uet.dse.ocl2cypher.core.CoreExpr.TypeTestKind testKind;
            public final QExpr source;
            public final String targetClassKey;

            public TypeTest(SourceSpan span, org.uet.dse.ocl2cypher.core.CoreExpr.TypeTestKind kind,
                            QExpr source, String targetClassKey) {
                super(span, OclType.BOOLEAN);
                this.testKind = kind;
                this.source = source;
                this.targetClassKey = targetClassKey;
            }

            @Override public QKind kind() { return QKind.TYPE_TEST; }
        }

        public static final class TypeCast extends QExpr {
            public final QExpr source;
            public final String targetClassKey;

            public TypeCast(SourceSpan span, QExpr source, String targetClassKey) {
                super(span, OclType.clazz(targetClassKey));
                this.source = source;
                this.targetClassKey = targetClassKey;
            }

            @Override public QKind kind() { return QKind.TYPE_CAST; }
        }

        public static final class Unary extends QExpr {
            public final org.uet.dse.ocl2cypher.core.CoreExpr.UnaryOp operator;
            public final QExpr operand;

            public Unary(SourceSpan span, org.uet.dse.ocl2cypher.core.CoreExpr.UnaryOp op,
                         QExpr operand, OclType resultType) {
                super(span, resultType);
                this.operator = op;
                this.operand = operand;
            }

            @Override public QKind kind() { return QKind.UNARY; }
        }

        public static final class Binary extends QExpr {
            public final org.uet.dse.ocl2cypher.core.CoreExpr.BinaryOp operator;
            public final QExpr left;
            public final QExpr right;

            public Binary(SourceSpan span, org.uet.dse.ocl2cypher.core.CoreExpr.BinaryOp op,
                          QExpr left, QExpr right, OclType resultType) {
                super(span, resultType);
                this.operator = op;
                this.left = left;
                this.right = right;
            }

            @Override public QKind kind() { return QKind.BINARY; }
        }

        public static final class Exists3 extends QExpr {
            public final QPlan source;
            public final CoreDeclaration iterator;
            public final QExpr predicate;

            public Exists3(SourceSpan span, QPlan source, CoreDeclaration iterator, QExpr predicate) {
                super(span, OclType.BOOLEAN);
                this.source = source;
                this.iterator = iterator;
                this.predicate = predicate;
            }

            @Override public QKind kind() { return QKind.EXISTS3; }
        }

        public static final class ForAll3 extends QExpr {
            public final QPlan source;
            public final CoreDeclaration iterator;
            public final QExpr predicate;

            public ForAll3(SourceSpan span, QPlan source, CoreDeclaration iterator, QExpr predicate) {
                super(span, OclType.BOOLEAN);
                this.source = source;
                this.iterator = iterator;
                this.predicate = predicate;
            }

            @Override public QKind kind() { return QKind.FORALL3; }
        }

        public static final class CollectionLiteral extends QExpr {
            public final org.uet.dse.ocl2cypher.core.CoreExpr.CollectionKind collectionKind;
            public final List<QExpr> elements;

            public CollectionLiteral(SourceSpan span,
                                     org.uet.dse.ocl2cypher.core.CoreExpr.CollectionKind kind,
                                     List<QExpr> elements, OclType type) {
                super(span, type);
                this.collectionKind = kind;
                this.elements = List.copyOf(elements);
            }

            @Override public QKind kind() {
                return collectionKind == org.uet.dse.ocl2cypher.core.CoreExpr.CollectionKind.SET
                        ? QKind.SET_LITERAL : QKind.BAG_LITERAL;
            }
        }

        public static final class IncludesFamily extends QExpr {
            public final QKind includesKind;
            public final QExpr source;
            public final QExpr element;

            public IncludesFamily(SourceSpan span, QKind includesKind,
                                  QExpr source, QExpr element, OclType resultType) {
                super(span, resultType);
                this.includesKind = includesKind;
                this.source = source;
                this.element = element;
            }

            @Override public QKind kind() { return includesKind; }
        }

        public static final class CountFamily extends QExpr {
            public final QKind countKind;
            public final QExpr source;
            public final QExpr element; // null for SIZE

            public CountFamily(SourceSpan span, QKind countKind, QExpr source,
                               QExpr element, OclType resultType) {
                super(span, resultType);
                this.countKind = countKind;
                this.source = source;
                this.element = element;
            }

            @Override public QKind kind() { return countKind; }
        }

        public static final class SetAlgebra extends QExpr {
            public final org.uet.dse.ocl2cypher.core.CoreExpr.BinaryOp operator;
            public final QExpr left;
            public final QExpr right;

            public SetAlgebra(SourceSpan span, org.uet.dse.ocl2cypher.core.CoreExpr.BinaryOp op,
                              QExpr left, QExpr right, OclType resultType) {
                super(span, resultType);
                this.operator = op;
                this.left = left;
                this.right = right;
            }

            @Override public QKind kind() {
                return operator == org.uet.dse.ocl2cypher.core.CoreExpr.BinaryOp.SET_UNION
                        ? QKind.SET_UNION : QKind.SET_INTERSECTION;
            }
        }

        /** Plan→Expr bridge: the unique materialization that preserves whole-bottom. */
        public static final class Materialize extends QExpr {
            public final QPlan plan;

            public Materialize(SourceSpan span, QPlan plan) {
                super(span, plan.type);
                this.plan = plan;
            }

            @Override public QKind kind() { return QKind.MATERIALIZE; }
        }
    }

    /** Static declaration from which realization constructs Δπ_public. */
    public record QParameter(String name, OclType expectedType) {
        public QParameter {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(expectedType, "expectedType");
            if (name.isBlank() || name.startsWith("__ocl")) {
                throw new IllegalArgumentException(
                        "public parameter name must be non-blank and outside __ocl namespace");
            }
            if (expectedType.isCollection() && !expectedType.elementType().isAtomic()) {
                throw new IllegalArgumentException(
                        "nested public collection parameters are outside OCL_val");
            }
        }
    }

    // ---- QPlan ----------------------------------------------------------

    public abstract static sealed class QPlan
            permits QPlan.FromCollection, QPlan.ScanClass, QPlan.NavigateMany,
                    QPlan.Filter, QPlan.Collect, QPlan.Distinct, QPlan.PlanLet {
        public final SourceSpan span;
        public final OclType type;

        protected QPlan(SourceSpan span, OclType type) {
            this.span = Objects.requireNonNull(span, "span");
            this.type = Objects.requireNonNull(type, "type");
        }

        public abstract QKind kind();

        public static final class FromCollection extends QPlan {
            public final QExpr collection;

            public FromCollection(SourceSpan span, QExpr collection) {
                super(span, collection.type);
                this.collection = collection;
            }

            @Override public QKind kind() { return QKind.FROM_COLLECTION; }
        }

        public static final class ScanClass extends QPlan {
            public final String classKey;
            public final CoreDeclaration variable;

            public ScanClass(SourceSpan span, String classKey, CoreDeclaration variable) {
                super(span, OclType.set(OclType.clazz(classKey)));
                this.classKey = classKey;
                this.variable = variable;
            }

            @Override public QKind kind() { return QKind.SCAN_CLASS; }
        }

        public static final class NavigateMany extends QPlan {
            public final QExpr source;
            public final String associationName;
            public final String roleName;
            public final boolean reverse;
            public final boolean associationClass;
            public final boolean viaAssociationClass;
            public final List<QExpr> qualifiers;
            public final OclType elementType;

            public NavigateMany(SourceSpan span, QExpr source, String associationName,
                                String roleName, List<QExpr> qualifiers, OclType elementType) {
                this(span, source, associationName, roleName, qualifiers,
                        OclType.set(elementType), false);
            }

            public NavigateMany(SourceSpan span, QExpr source, String associationName,
                                String roleName, List<QExpr> qualifiers, OclType collectionType,
                                boolean reverse) {
                this(span, source, associationName, roleName, qualifiers, collectionType,
                        reverse, false, false);
            }

            public NavigateMany(SourceSpan span, QExpr source, String associationName,
                                String roleName, List<QExpr> qualifiers, OclType collectionType,
                                boolean reverse, boolean associationClass) {
                this(span, source, associationName, roleName, qualifiers, collectionType,
                        reverse, associationClass, false);
            }

            public NavigateMany(SourceSpan span, QExpr source, String associationName,
                                String roleName, List<QExpr> qualifiers, OclType collectionType,
                                boolean reverse, boolean associationClass,
                                boolean viaAssociationClass) {
                super(span, collectionType);
                this.source = source;
                this.associationName = associationName;
                this.roleName = roleName;
                this.reverse = reverse;
                this.associationClass = associationClass;
                this.viaAssociationClass = viaAssociationClass;
                this.qualifiers = List.copyOf(qualifiers);
                this.elementType = collectionType.elementType();
            }

            @Override public QKind kind() { return QKind.NAVIGATE_MANY; }
        }

        public static final class Filter extends QPlan {
            public final QPlan source;
            public final CoreDeclaration iterator;
            public final QExpr predicate;
            public final boolean isSelect;

            public Filter(SourceSpan span, QPlan source, CoreDeclaration iterator,
                          QExpr predicate, boolean isSelect) {
                super(span, source.type);
                this.source = source;
                this.iterator = iterator;
                this.predicate = predicate;
                this.isSelect = isSelect;
            }

            @Override public QKind kind() {
                return isSelect ? QKind.FILTER_SELECT : QKind.FILTER_REJECT;
            }
        }

        public static final class Collect extends QPlan {
            public final QPlan source;
            public final CoreDeclaration iterator;
            public final QExpr body;

            public Collect(SourceSpan span, QPlan source, CoreDeclaration iterator, QExpr body) {
                super(span, OclType.bag(body.type));
                this.source = source;
                this.iterator = iterator;
                this.body = body;
            }

            @Override public QKind kind() { return QKind.COLLECT; }
        }

        public static final class Distinct extends QPlan {
            public final QPlan source;

            public Distinct(SourceSpan span, QPlan source) {
                super(span, source.type.isCollection()
                        ? OclType.set(source.type.elementType()) : source.type);
                this.source = source;
            }

            @Override public QKind kind() { return QKind.DISTINCT; }
        }

        public static final class PlanLet extends QPlan {
            public final CoreDeclaration binder;
            public final QExpr value;
            public final QPlan body;

            public PlanLet(SourceSpan span, CoreDeclaration binder, QExpr value, QPlan body) {
                super(span, body.type);
                this.binder = binder;
                this.value = value;
                this.body = body;
            }

            @Override public QKind kind() { return QKind.PLAN_LET; }
        }
    }
}
