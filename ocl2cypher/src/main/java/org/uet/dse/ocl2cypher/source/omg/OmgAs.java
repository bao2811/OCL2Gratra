package org.uet.dse.ocl2cypher.source.omg;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Objects;
import org.uet.dse.ocl2cypher.diagnostics.SourceSpan;
import org.uet.dse.ocl2cypher.runtime.OclType;

/**
 * OMG OCL 2.4 Abstract Syntax profile — the resolved, typed AS that
 * {@code E_SM} produces and {@code N_SM} consumes.
 *
 * <p>The class hierarchy mirrors {@code research/OCLscope/OCL-Abstract-Syntax.emf}
 * (EssentialOCL core + full-OCL navigation features):
 *
 * <pre>
 * OclExpression
 * ├── LiteralExp
 * │   ├── PrimitiveLiteralExp
 * │   │   ├── BooleanLiteralExp
 * │   │   ├── NumericLiteralExp
 * │   │   │   ├── IntegerLiteralExp
 * │   │   │   └── RealLiteralExp
 * │   │   ├── StringLiteralExp
 * │   │   └── NullLiteralExp          [OMG vocabulary; rejected by admission]
 * │   ├── InvalidLiteralExp           [OMG vocabulary; rejected by admission]
 * │   └── CollectionLiteralExp
 * ├── VariableExp
 * ├── CallExp
 * │   ├── FeatureCallExp
 * │   │   ├── NavigationCallExp
 * │   │   │   ├── PropertyCallExp
 * │   │   │   └── AssociationClassCallExp
 * │   │   └── OperationCallExp
 * │   └── LoopExp
 * │       └── IteratorExp
 * ├── IfExp
 * ├── LetExp
 * └── TypeExp
 * </pre>
 *
 * <p>References use stable keys into the {@code SchemaModel} (class keys,
 * attribute keys, association names) because this reference implementation
 * does not carry the full UML instance graph; the keys play the role of
 * {@code referredProperty}/{@code referredOperation}/{@code referredVariable}
 * identity. Every expression carries its static {@code OclType} assigned
 * during elaboration — {@code N_SM} never re-types.
 */
public final class OmgAs {

    private OmgAs() {
    }

    // ---- type universe (maps to umlscope types) ----------------------------

    /** Maps to {@code umlscope::Type} — the shared UML type universe. */
    public enum OmgCollectionKind {
        SET, BAG
    }

    // ---- base hierarchy -----------------------------------------------------

    public abstract static sealed class OclElement {
        public final SourceSpan span;

        protected OclElement(SourceSpan span) {
            this.span = Objects.requireNonNull(span, "span");
        }

        public final SourceSpan span() {
            return span;
        }
    }

    public abstract static sealed class TypedElement extends OclElement
            permits OclExpression, Variable, CollectionLiteralPart {
        public final OclType type;

        protected TypedElement(SourceSpan span, OclType type) {
            super(span);
            this.type = type;
        }

        public final OclType type() {
            return type;
        }
    }

    // ---- expressions ----------------------------------------------------------

    public abstract static sealed class OclExpression extends TypedElement
            permits LiteralExp, VariableExp, CallExp,
                    IfExp, LetExp, TypeExp, CoerceExp {

        protected OclExpression(SourceSpan span, OclType type) {
            super(span, type);
        }
    }

    // -- LiteralExp hierarchy

    public abstract static sealed class LiteralExp extends OclExpression
            permits PrimitiveLiteralExp, InvalidLiteralExp, CollectionLiteralExp {
        protected LiteralExp(SourceSpan span, OclType type) {
            super(span, type);
        }
    }

    public abstract static sealed class PrimitiveLiteralExp extends LiteralExp
            permits BooleanLiteralExp, NumericLiteralExp, StringLiteralExp, NullLiteralExp {
        protected PrimitiveLiteralExp(SourceSpan span, OclType type) {
            super(span, type);
        }
    }

    public abstract static sealed class NumericLiteralExp extends PrimitiveLiteralExp
            permits IntegerLiteralExp, RealLiteralExp {
        protected NumericLiteralExp(SourceSpan span, OclType type) {
            super(span, type);
        }
    }

    public static final class BooleanLiteralExp extends PrimitiveLiteralExp {
        public final boolean booleanSymbol;

        public BooleanLiteralExp(SourceSpan span, boolean booleanSymbol) {
            super(span, OclType.BOOLEAN);
            this.booleanSymbol = booleanSymbol;
        }
    }

    public static final class IntegerLiteralExp extends NumericLiteralExp {
        /** OMG OCL mathematical Integer — exact, unbounded. */
        public final BigInteger integerSymbol;

        public IntegerLiteralExp(SourceSpan span, BigInteger integerSymbol) {
            super(span, OclType.INTEGER);
            this.integerSymbol = Objects.requireNonNull(integerSymbol);
        }
    }

    public static final class RealLiteralExp extends NumericLiteralExp {
        /** Exact decimal carrier for the OCL_val Real subset. */
        public final BigDecimal realSymbol;

        public RealLiteralExp(SourceSpan span, BigDecimal realSymbol) {
            super(span, OclType.REAL);
            this.realSymbol = Objects.requireNonNull(realSymbol);
        }
    }

    public static final class StringLiteralExp extends PrimitiveLiteralExp {
        public final String stringSymbol;

        public StringLiteralExp(SourceSpan span, String stringSymbol) {
            super(span, OclType.STRING);
            this.stringSymbol = Objects.requireNonNull(stringSymbol);
        }
    }

    /** OMG vocabulary retained; admission rejects every instance. */
    public static final class NullLiteralExp extends PrimitiveLiteralExp {
        public NullLiteralExp(SourceSpan span) {
            super(span, null); // VoidType has no OCL_val carrier
        }
    }

    /** OMG vocabulary retained; admission rejects every instance. */
    public static final class InvalidLiteralExp extends LiteralExp {
        public InvalidLiteralExp(SourceSpan span) {
            super(span, null); // InvalidType has no OCL_val carrier
        }
    }

    // -- CollectionLiteralExp + parts

    public abstract static sealed class CollectionLiteralPart extends TypedElement
            permits CollectionItem, CollectionRange {
        protected CollectionLiteralPart(SourceSpan span, OclType type) {
            super(span, type);
        }
    }

    public static final class CollectionItem extends CollectionLiteralPart {
        public final OclExpression item;

        public CollectionItem(SourceSpan span, OclExpression item) {
            super(span, item.type);
            this.item = item;
        }
    }

    /** Parsed but rejected by admission (no lowering rule). */
    public static final class CollectionRange extends CollectionLiteralPart {
        public final OclExpression first;
        public final OclExpression last;

        public CollectionRange(SourceSpan span, OclExpression first, OclExpression last) {
            super(span, first.type);
            this.first = first;
            this.last = last;
        }
    }

    public static final class CollectionLiteralExp extends LiteralExp {
        public final OmgCollectionKind kind;
        public final List<CollectionLiteralPart> part;

        public CollectionLiteralExp(SourceSpan span, OmgCollectionKind kind,
                                    List<CollectionLiteralPart> part, OclType type) {
            super(span, type);
            this.kind = Objects.requireNonNull(kind);
            this.part = List.copyOf(part);
        }
    }

    // -- VariableExp / Variable

    /** Maps to {@code oclas::Variable} — a typed binder declaration. */
    public static final class Variable extends TypedElement {
        public final String name;
        public final OclExpression initExpression; // null for self/iterator

        public Variable(SourceSpan span, String name, OclType type, OclExpression init) {
            super(span, type);
            this.name = Objects.requireNonNull(name);
            this.initExpression = init;
        }
    }

    public static final class VariableExp extends OclExpression {
        /** Stable declaration identity (not a surface name copy). */
        public final Variable referredVariable;

        public VariableExp(SourceSpan span, Variable referredVariable) {
            super(span, referredVariable.type);
            this.referredVariable = Objects.requireNonNull(referredVariable);
        }
    }

    // -- CallExp hierarchy

    public abstract static sealed class CallExp extends OclExpression
            permits FeatureCallExp, LoopExp {
        public final OclExpression source;

        protected CallExp(SourceSpan span, OclType type, OclExpression source) {
            super(span, type);
            this.source = source;
        }
    }

    public abstract static sealed class FeatureCallExp extends CallExp
            permits NavigationCallExp, OperationCallExp {
        protected FeatureCallExp(SourceSpan span, OclType type, OclExpression source) {
            super(span, type, source);
        }
    }

    public abstract static sealed class NavigationCallExp extends FeatureCallExp
            permits PropertyCallExp, AssociationClassCallExp {
        public final List<OclExpression> qualifier;
        /** Attribute key or association role that navigation originates from. */
        public final String navigationSource;

        protected NavigationCallExp(SourceSpan span, OclType type, OclExpression source,
                                    List<OclExpression> qualifier, String navigationSource) {
            super(span, type, source);
            this.qualifier = List.copyOf(qualifier);
            this.navigationSource = navigationSource;
        }
    }

    /** Attribute read ({@code e.a}) or association-end navigation ({@code e.r[qs]}). */
    public static final class PropertyCallExp extends NavigationCallExp {
        /** {@code referredProperty}: attribute key {@code Class::name} or role key. */
        public final String referredProperty;

        public PropertyCallExp(SourceSpan span, OclType type, OclExpression source,
                               List<OclExpression> qualifier, String navigationSource,
                               String referredProperty) {
            super(span, type, source, qualifier, navigationSource);
            this.referredProperty = Objects.requireNonNull(referredProperty);
        }
    }

    /** Association-class navigation ({@code e.A}). */
    public static final class AssociationClassCallExp extends NavigationCallExp {
        public final String referredAssociationClass;

        public AssociationClassCallExp(SourceSpan span, OclType type, OclExpression source,
                                       List<OclExpression> qualifier, String navigationSource,
                                       String referredAssociationClass) {
            super(span, type, source, qualifier, navigationSource);
            this.referredAssociationClass = Objects.requireNonNull(referredAssociationClass);
        }
    }

    /**
     * All standard operators (infix, prefix, dot-call) normalize to this node.
     * {@code referredOperation} is the resolved canonical signature key.
     */
    public static final class OperationCallExp extends FeatureCallExp {
        public final String referredOperation;
        public final List<OclExpression> argument;

        public OperationCallExp(SourceSpan span, OclType type, OclExpression source,
                                String referredOperation, List<OclExpression> argument) {
            super(span, type, source);
            this.referredOperation = Objects.requireNonNull(referredOperation);
            this.argument = List.copyOf(argument);
        }
    }

    // -- LoopExp / IteratorExp

    public abstract static sealed class LoopExp extends CallExp
            permits IteratorExp {
        public final OclExpression body;
        public final List<Variable> iterator;

        protected LoopExp(SourceSpan span, OclType type, OclExpression source,
                          OclExpression body, List<Variable> iterator) {
            super(span, type, source);
            this.body = Objects.requireNonNull(body);
            this.iterator = List.copyOf(iterator);
        }
    }

    public static final class IteratorExp extends LoopExp {
        public final String name; // select | reject | exists | forAll | collect

        public IteratorExp(SourceSpan span, String name, OclExpression source,
                           List<Variable> iterator, OclExpression body, OclType type) {
            super(span, type, source, body, iterator);
            this.name = Objects.requireNonNull(name);
        }
    }

    // -- IfExp / LetExp / TypeExp

    public static final class IfExp extends OclExpression {
        public final OclExpression condition;
        public final OclExpression thenExpression;
        public final OclExpression elseExpression;

        public IfExp(SourceSpan span, OclExpression condition,
                     OclExpression thenExpression, OclExpression elseExpression,
                     OclType type) {
            super(span, type);
            this.condition = condition;
            this.thenExpression = thenExpression;
            this.elseExpression = elseExpression;
        }
    }

    public static final class LetExp extends OclExpression {
        public final Variable variable;
        public final OclExpression in;

        public LetExp(SourceSpan span, Variable variable, OclExpression in) {
            super(span, in.type);
            this.variable = variable;
            this.in = in;
        }
    }

    /** Type operand of allInstances/oclIsTypeOf/oclIsKindOf/oclAsType only. */
    public static final class TypeExp extends OclExpression {
        /** The UML type the literal refers to (e.g. a class key). */
        public final String referredType;

        public TypeExp(SourceSpan span, String referredType) {
            super(span, null); // OclType classifier — not a runtime value
            this.referredType = Objects.requireNonNull(referredType);
        }
    }

    /** Explicit coercion inserted by the typed frontend (for example Integer to Real). */
    public static final class CoerceExp extends OclExpression {
        public final OclExpression sourceExpression;
        public final OclType sourceType;
        public final org.uet.dse.ocl2cypher.core.CoreExpr.CoercionKind kind;

        public CoerceExp(SourceSpan span, OclExpression sourceExpression,
                         OclType sourceType, OclType targetType,
                         org.uet.dse.ocl2cypher.core.CoreExpr.CoercionKind kind) {
            super(span, Objects.requireNonNull(targetType, "target type"));
            this.sourceExpression = Objects.requireNonNull(sourceExpression, "source expression");
            this.sourceType = Objects.requireNonNull(sourceType, "source type");
            this.kind = Objects.requireNonNull(kind, "coercion kind");
        }
    }

    // ---- document wrapper (maps to UML::Constraint + ExpressionInOcl) --------

    /**
     * Maps to {@code oclas::ExpressionInOcl} with its context variable.
     * The enclosing {@code UML::Constraint} is represented by
     * {@link Constraint}.
     */
    public static final class ExpressionInOcl {
        public final SourceSpan span;
        public final OclExpression bodyExpression;
        public final Variable contextVariable;

        public ExpressionInOcl(SourceSpan span, OclExpression bodyExpression,
                               Variable contextVariable) {
            this.span = Objects.requireNonNull(span);
            this.bodyExpression = Objects.requireNonNull(bodyExpression);
            this.contextVariable = contextVariable;
        }
    }

    /** One constraint block: {@code context C inv Name: e}. */
    public static final class Constraint {
        public final String name;            // null for unnamed invariants
        public final String constrainedElement; // context class key
        public final ExpressionInOcl specification;

        public Constraint(String name, String constrainedElement,
                          ExpressionInOcl specification) {
            this.name = name;
            this.constrainedElement = Objects.requireNonNull(constrainedElement);
            this.specification = Objects.requireNonNull(specification);
        }
    }

    /** Maps to the document root: a classifier context with its constraints. */
    public static final class OmgDocument {
        public final SourceSpan span;
        public final String contextClassKey;
        public final String contextVariableName;
        public final List<Constraint> constraints;

        public OmgDocument(SourceSpan span, String contextClassKey,
                           String contextVariableName, List<Constraint> constraints) {
            this.span = Objects.requireNonNull(span);
            this.contextClassKey = Objects.requireNonNull(contextClassKey);
            this.contextVariableName = Objects.requireNonNull(contextVariableName);
            this.constraints = List.copyOf(constraints);
        }
    }
}
