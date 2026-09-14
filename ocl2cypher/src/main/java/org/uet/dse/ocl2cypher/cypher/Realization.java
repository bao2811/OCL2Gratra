package org.uet.dse.ocl2cypher.cypher;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.uet.dse.ocl2cypher.core.CoreDeclaration;
import org.uet.dse.ocl2cypher.cypher.CypherAst.*;
import org.uet.dse.ocl2cypher.diagnostics.Result;
import org.uet.dse.ocl2cypher.diagnostics.RuleId;
import org.uet.dse.ocl2cypher.diagnostics.SourceSpan;
import org.uet.dse.ocl2cypher.diagnostics.Stage;
import org.uet.dse.ocl2cypher.graph.GraphModel;
import org.uet.dse.ocl2cypher.graph.GraphValueCodec;
import org.uet.dse.ocl2cypher.qcyp.NormQ;
import org.uet.dse.ocl2cypher.qcyp.QNode;
import org.uet.dse.ocl2cypher.qcyp.QQuery;
import org.uet.dse.ocl2cypher.runtime.OclType;
import org.uet.dse.ocl2cypher.trace.Trace;
import org.uet.dse.ocl2cypher.trace.TraceCollector;

/**
 * {@code R : Q_CYP → CypherAS}, factored as {@code R₀(Norm_Q(q))}.
 *
 * <p>Realization is observer-driven and read-only: object typing goes through
 * {@code ObjectInstanceOf}, an attribute through the
 * {@code ObjectHasAttribute → AttributeValue} slot path, and navigation through
 * {@code LinkAssociateWith}. A tagged collection carries the whole-collection
 * bottom flag so an empty result stays distinct from a missing/bottom source,
 * and an element-bottom is kept as an element, never folded into the wrapper.
 *
 * <p>{@code WHERE} is a subclause of {@code MATCH}/{@code WITH}; no
 * {@code OPTIONAL MATCH} is ever emitted to fake a bottom, and native
 * {@code null} appears only as the guarded payload of a bottom tag.
 */
public final class Realization {

    private final GraphModel graph;
    private final List<QueryParameter> parameters = new ArrayList<>();
    private final FreshAliasSupply aliases = new FreshAliasSupply();
    private final IntegerRangeCertificates numericCertificates = new IntegerRangeCertificates();
    private String modelKeyParameter;

    private Realization(GraphModel graph) {
        this.graph = graph;
        // VIOLATIONS deliberately keeps the conventional root alias `self`.
        // Reserve it so no generated local can ever capture that live binding.
        this.aliases.reserve("self");
    }

    public static Result<GeneratedArtifact> realize(QQuery q, GraphModel graph,
                                                    CypherAst.Dialect dialect) {
        Result<GeneratedArtifact> construction = constructWithoutValidation(q, graph, dialect);
        if (construction.isFailure()) {
            return construction;
        }
        GeneratedArtifact artifact = construction.value();
        try {
            CypherAstWellFormednessValidator.validate(artifact);
            return Result.success(artifact);
        } catch (IllegalArgumentException invalidArtifact) {
            return Result.failure(Stage.R, "R_TARGET_WF", invalidArtifact.getMessage());
        }
    }

    /**
     * Construction boundary used by the R-4 refinement test. It deliberately
     * omits the postcondition validator, allowing the test to establish that
     * successful construction already satisfies WF rather than relying on the
     * public method to validate its own output.
     */
    static Result<GeneratedArtifact> constructWithoutValidation(QQuery q, GraphModel graph,
                                                                 CypherAst.Dialect dialect) {
        try {
            Realization r = new Realization(graph);
            return Result.success(r.realizeQuery(NormQ.normalize(q), dialect));
        } catch (RealizeError e) {
            return Result.failure(Stage.R, e.code, e.getMessage());
        }
    }

    public static Result<GeneratedArtifact> realize(QQuery q, GraphModel graph,
                                                    CypherAst.Dialect dialect,
                                                    TraceCollector traces) {
        Result<GeneratedArtifact> result = realize(q, graph, dialect);
        if (result.isSuccess()) {
            String rule = q.mode() == QQuery.QueryMode.VIOLATIONS
                    ? RuleId.R_Q_VIOLATIONS : RuleId.R_Q_VALUE_SCALAR;
            String sourceId = q.mode() == QQuery.QueryMode.VIOLATIONS
                    ? "q-violations:" + q.contextClassKey() + "::<anonymous>"
                    : "q-value-query";
            // Named invariants are not retained by QQuery. Match the exact T_G
            // target whenever possible through the collector's latest target.
            if (q.mode() == QQuery.QueryMode.VIOLATIONS) {
                sourceId = traces.traces().stream()
                        .filter(t -> t.stage() == Trace.Stage.T_G)
                        .reduce((a, b) -> b).map(Trace::targetId).orElse(sourceId);
            }
            traces.record(Trace.Stage.R, sourceId,
                    "cypher-artifact:" + q.mode().name().toLowerCase(), rule);
        }
        return result;
    }

    private static SourceSpan querySpan(QQuery q) {
        return q.expressionBody() != null ? q.expressionBody().span : q.planBody().span;
    }

    private GeneratedArtifact realizeQuery(QQuery q, CypherAst.Dialect dialect) {
        if (q.mode() == QQuery.QueryMode.VIOLATIONS) {
            return realizeViolations(q, dialect);
        }
        return realizeValue(q, dialect);
    }

    /**
     * VALUE root: bind one context object by its stable application id, then
     * return one tagged scalar or collection value.  The context typing path
     * follows the dynamic class through the bounded inheritance closure, so a
     * value query observes subclasses in the same way as OCL.
     */
    private GeneratedArtifact realizeValue(QQuery q, CypherAst.Dialect dialect) {
        List<CypherClause> clauses = new ArrayList<>();
        Ctx ctx = new Ctx();
        if (q.contextClassKey() != null || q.selfVariable() != null) {
            if (q.contextClassKey() == null || q.selfVariable() == null) {
                throw new RealizeError("R_VALUE_CONTEXT",
                        "VALUE context class and self declaration must be present together");
            }
            String selfAlias = fresh("self");
            String directClass = fresh("directClass");
            String contextClass = fresh("contextClass");
            String idParam = "__oclContextId";
            addParam(idParam, "Physical:StableObjectId", QueryParameter.Origin.PUBLIC, null);
            String classParam = "__oclContextClassKey";
            addParam(classParam, "Physical:ClassKey", QueryParameter.Origin.GENERATED,
                    q.contextClassKey());

            Pattern typing = new Pattern(List.of(new PathPattern(
                    List.of(new NodePattern(selfAlias, List.of("Object"),
                                    withModel(new PropertyMapEntry("use_id",
                                            new ParameterExpr(idParam)))),
                            new NodePattern(directClass, List.of("UmlClass"), modelProperty()),
                            new NodePattern(contextClass, List.of("UmlClass"),
                                    List.of(new PropertyMapEntry("modelKey",
                                                    modelKeyExpr()),
                                            new PropertyMapEntry("classKey",
                                                    new ParameterExpr(classParam))))),
                    List.of(new RelPattern(null, GraphModel.OBJECT_INSTANCE_OF,
                                    RelDirection.OUTGOING, List.of()),
                            new RelPattern(null, GraphModel.EXTENDS, RelDirection.OUTGOING,
                                    List.of(), 0, inheritanceBound())))));
            clauses.add(new MatchClause(typing, null));
            OclType selfType = OclType.clazz(q.contextClassKey());
            ctx.bind(q.selfVariable(), taggedObject(selfAlias, selfType), selfType);
        }
        CypherExpr result;
        if (q.expressionBody() != null) {
            result = realizeExpr(q.expressionBody(), ctx);
        } else {
            MaterializedPlan materialized = materializePlan(q.planBody(), ctx);
            result = bindOnce("valuePlan", materialized.value(), plan ->
                    CypherArtifacts.collectionTag(q.resultType(),
                            new PropertyAccess(plan, CypherArtifacts.OCL_ITEMS),
                            new PropertyAccess(plan, CypherArtifacts.OCL_BOTTOM)));
        }
        ReturnClause ret = new ReturnClause(false,
                List.of(new ProjectionItem(result, "result")));
        clauses.add(ret);
        CypherQuery query = new CypherQuery(clauses, true, querySpan(q));
        ResultShape shape = switch (q.resultShape()) {
            case SCALAR -> ResultShape.SCALAR;
            case SET -> ResultShape.SET;
            case BAG -> ResultShape.BAG;
            case IDS -> throw new RealizeError("R_VALUE_SHAPE", "VALUE cannot return IDS");
        };
        String wholeBottom = q.resultType().isCollection()
                ? CypherArtifacts.OCL_BOTTOM : null;
        ResultContract contract = new ResultContract(shape, "result",
                CypherArtifacts.typeTag(q.resultType()),
                q.resultShape() == QQuery.QResultShape.SET,
                wholeBottom);
        return new GeneratedArtifact(dialect, query, contract, sortedParameters());
    }

    /**
     * VIOLATIONS: scan the context class, evaluate the tagged Boolean₃ body per
     * object, keep the rows whose body is not tagged-true (F or bottom), and
     * RETURN DISTINCT the stable id. Bottom is a violation, so the WHERE keeps
     * both {@code __oclBottom = true} and {@code __oclValue = false}.
     */
    private GeneratedArtifact realizeViolations(QQuery q, CypherAst.Dialect dialect) {
        String selfAlias = "self";
        String classAlias = fresh("c");

        String ctxParam = "__oclContextClassKey";
        addParam(ctxParam, "Physical:ClassKey", QueryParameter.Origin.GENERATED,
                q.contextClassKey());
        String directClassAlias = fresh("directClass");
        Pattern typing = new Pattern(List.of(new PathPattern(
                List.of(new NodePattern(selfAlias, List.of("Object"), modelProperty()),
                        new NodePattern(directClassAlias, List.of("UmlClass"), modelProperty()),
                        new NodePattern(classAlias, List.of("UmlClass"),
                                List.of(new PropertyMapEntry("modelKey", modelKeyExpr()),
                                        new PropertyMapEntry("classKey",
                                        new ParameterExpr(ctxParam))))),
                List.of(new RelPattern(null, GraphModel.OBJECT_INSTANCE_OF,
                        RelDirection.OUTGOING, List.of()),
                        new RelPattern(null, GraphModel.EXTENDS, RelDirection.OUTGOING,
                                List.of(), 0, inheritanceBound())))));
        MatchClause scan = new MatchClause(typing, null);

        Ctx ctx = new Ctx();
        OclType selfType = OclType.clazz(q.contextClassKey());
        ctx.bind(q.selfVariable(), taggedObject(selfAlias, selfType), selfType);
        CypherExpr body = realizeExpr(q.expressionBody(), ctx);
        String bodyAlias = fresh("body");
        WithClause withBody = new WithClause(false,
                List.of(new ProjectionItem(new VariableExpr(selfAlias), selfAlias),
                        new ProjectionItem(body, bodyAlias)),
                violationPredicate(bodyAlias));

        CypherExpr idExpr = new PropertyAccess(new VariableExpr(selfAlias), "use_id");
        ReturnClause ret = new ReturnClause(true,
                List.of(new ProjectionItem(idExpr, "violationId")));

        CypherQuery query = new CypherQuery(List.of(scan, withBody, ret), true, querySpan(q));
        ResultContract contract = new ResultContract(ResultShape.IDS, "violationId",
                "StableObjectId", true, null);
        return new GeneratedArtifact(dialect, query, contract, sortedParameters());
    }

    /** {@code body.__oclBottom = true OR body.__oclValue = false}. */
    private CypherExpr violationPredicate(String bodyAlias) {
        CypherExpr isBottom = new BinaryExpr(BinaryOp.EQUAL,
                new PropertyAccess(new VariableExpr(bodyAlias), CypherArtifacts.OCL_BOTTOM),
                new BooleanLiteral(true));
        CypherExpr valueFalse = new BinaryExpr(BinaryOp.EQUAL,
                new PropertyAccess(new VariableExpr(bodyAlias), CypherArtifacts.OCL_VALUE),
                new BooleanLiteral(false));
        return new BinaryExpr(BinaryOp.OR, isBottom, valueFalse);
    }

    // ---- R_E : QExpr → CypherExpr ---------------------------------------

    private CypherExpr realizeExpr(QNode.QExpr e, Ctx ctx) {
        return CypherAst.withSpan(realizeExprUnspanned(e, ctx), e.span);
    }

    private CypherExpr realizeExprUnspanned(QNode.QExpr e, Ctx ctx) {
        if (e.type.isCollection() && e.type.elementType().isNumeric()
                && !(e instanceof QNode.QExpr.Bottom)
                && !(e instanceof QNode.QExpr.CollectionLiteral)
                && !(e instanceof QNode.QExpr.Parameter)) {
            throw numericCapability("numeric collection carrier : " + e.type);
        }
        // Only an explicit certificate authorizes native numeric realization.
        if (e.type.isNumeric() && !(e instanceof QNode.QExpr.Bottom)) {
            if (numericCertificates.certify(e).isEmpty()) {
                if (e.type.equals(org.uet.dse.ocl2cypher.runtime.OclType.REAL)) {
                    if (e instanceof QNode.QExpr.Constant constant
                            && constant.literalValue instanceof BigDecimal value) {
                        requireExactBinary64(value, "Real literal");
                    } else if (e instanceof QNode.QExpr.ReadAttribute attribute) {
                        requireExactStoredReal(attribute);
                    } else if (e instanceof QNode.QExpr.Parameter) {
                        // The execution adapter validates the runtime value against the
                        // exact-binary64 public parameter domain before query execution.
                    } else {
                        throw new RealizeError(org.uet.dse.ocl2cypher.diagnostics.RuleId.R_REAL_EXACT_UNSUPPORTED,
                                "Real expression requires an exact realization certificate: "
                                        + e.getClass().getSimpleName());
                    }
                } else {
                    throw numericCapability(e.getClass().getSimpleName() + " : " + e.type);
                }
            }
        }
        if (e instanceof QNode.QExpr.Constant c) {
            return CypherArtifacts.taggedScalar(c.type, literal(c));
        }
        if (e instanceof QNode.QExpr.Bottom b) {
            return CypherArtifacts.bottomLiteral(b.type);
        }
        if (e instanceof QNode.QExpr.Parameter p) {
            return realizeParameter(p);
        }
        if (e instanceof QNode.QExpr.Variable v) {
            Binding b = ctx.lookup(v.declaration);
            if (b == null) {
                throw new RealizeError("R_SCOPE",
                        "unbound variable " + v.declaration.name());
            }
            return b.value;
        }
        if (e instanceof QNode.QExpr.ReadAttribute ar) {
            return realizeAttribute(ar, ctx);
        }
        if (e instanceof QNode.QExpr.Binary b) {
            return realizeBinary(b, ctx);
        }
        if (e instanceof QNode.QExpr.Unary u) {
            return realizeUnary(u, ctx);
        }
        if (e instanceof QNode.QExpr.Materialize mat) {
            return realizeMaterialize(mat, ctx);
        }
        if (e instanceof QNode.QExpr.Exists3 ex) {
            return realizeExists(ex, ctx);
        }
        if (e instanceof QNode.QExpr.ForAll3 fa) {
            return realizeForAll(fa, ctx);
        }
        if (e instanceof QNode.QExpr.IfExpr iff) {
            return realizeIf(iff, ctx);
        }
        if (e instanceof QNode.QExpr.CollectionLiteral cl) {
            List<CypherExpr> els = new ArrayList<>();
            for (QNode.QExpr el : cl.elements) {
                els.add(realizeExpr(el, ctx));
            }
            CypherExpr items = new ListExpr(els);
            if (cl.collectionKind
                    == org.uet.dse.ocl2cypher.core.CoreExpr.CollectionKind.SET) {
                items = distinctAtomicItems(items);
            }
            return CypherArtifacts.collectionTag(cl.type, items);
        }
        if (e instanceof QNode.QExpr.Let let) {
            return realizeLet(let, ctx);
        }
        if (e instanceof QNode.QExpr.Coerce co) {
            return realizeCoerce(co, ctx);
        }
        if (e instanceof QNode.QExpr.NavigateOne nav) {
            return realizeNavigateOne(nav, ctx);
        }
        if (e instanceof QNode.QExpr.TypeTest tt) {
            return realizeTypeTest(tt, ctx);
        }
        if (e instanceof QNode.QExpr.TypeCast tc) {
            return realizeTypeCast(tc, ctx);
        }
        if (e instanceof QNode.QExpr.IncludesFamily inc) {
            return realizeIncludes(inc, ctx);
        }
        if (e instanceof QNode.QExpr.CountFamily cf) {
            return realizeCount(cf, ctx);
        }
        if (e instanceof QNode.QExpr.SetAlgebra sa) {
            return realizeSetAlgebra(sa, ctx);
        }
        throw new RealizeError("R_UNCOVERED_CONSTRUCTOR",
                "no R rule for " + e.getClass().getSimpleName());
    }

    /** R-E-PARAMETER: intern a public declaration and convert its wire payload. */
    private CypherExpr realizeParameter(QNode.QExpr.Parameter expression) {
        QNode.QParameter parameter = expression.parameter;
        addParam(parameter.name(), CypherArtifacts.typeTag(parameter.expectedType()),
                QueryParameter.Origin.PUBLIC, null);
        return physicalizePublicParameter(new ParameterExpr(parameter.name()),
                parameter.expectedType());
    }

    /**
     * The caller wire map carries exact decimal strings for numeric values.
     * Adapter validation runs first; these conversions therefore cannot turn a
     * malformed parameter into OCL bottom. Collection conversion maps every
     * occurrence and preserves the outer whole-bottom flag and Set/Bag kind.
     */
    private CypherExpr physicalizePublicParameter(CypherExpr wire, OclType type) {
        CypherExpr bottom = new PropertyAccess(wire, CypherArtifacts.OCL_BOTTOM);
        if (type.isCollection()) {
            String item = fresh("parameterItem");
            CypherExpr items = new PropertyAccess(wire, CypherArtifacts.OCL_ITEMS);
            CypherExpr converted = new ListComprehension(item, items, null,
                    physicalizePublicParameter(new VariableExpr(item), type.elementType()));
            return CypherArtifacts.collectionTag(type, converted, bottom);
        }
        CypherExpr payload = new PropertyAccess(wire, CypherArtifacts.OCL_VALUE);
        CypherExpr converted = payload;
        if (type.equals(OclType.INTEGER)) {
            converted = new FunctionCall("toInteger", false, List.of(payload));
        } else if (type.equals(OclType.REAL)) {
            converted = new FunctionCall("toFloat", false, List.of(payload));
        }
        return new CaseExpr(List.of(
                new WhenThen(bottom, CypherArtifacts.bottomLiteral(type))),
                CypherArtifacts.taggedScalar(type, converted));
    }

    /** {@code let x = v in b}: bind the tagged value of v to χ(x), realize b. */
    private CypherExpr realizeLet(QNode.QExpr.Let let, Ctx ctx) {
        int uses = referenceCount(let.body, let.binder);
        if (uses == 0) {
            return realizeExpr(let.body, ctx);
        }
        CypherExpr v = realizeExpr(let.value, ctx);
        Ctx inner = ctx.child();
        if (uses == 1) {
            inner.bind(let.binder, v, let.value.type);
            return realizeExpr(let.body, inner);
        }
        String alias = fresh("letValue");
        inner.bind(let.binder, new VariableExpr(alias), let.value.type);
        CypherExpr body = realizeExpr(let.body, inner);
        return bindOnce(alias, v, body);
    }

    /**
     * Coercion over a tagged value: bottom propagates; INTEGER_TO_REAL wraps the
     * payload in toFloat; class upcast keeps the object id but re-tags the type;
     * collection element coercion maps every tagged item and preserves the
     * outer Set/Bag kind and whole-collection bottom flag.
     */
    private CypherExpr realizeCoerce(QNode.QExpr.Coerce co, Ctx ctx) {
        if (co.type.isCollection() && co.type.elementType().isNumeric()) {
            throw numericCapability("numeric collection element coercion");
        }
        CypherExpr s = realizeExpr(co.source, ctx);
        return bindOnce("coerceSource", s, source -> switch (co.kind) {
            case INTEGER_TO_REAL -> new CaseExpr(List.of(
                    new WhenThen(isBottom(source), CypherArtifacts.bottomLiteral(co.type))),
                    CypherArtifacts.taggedScalar(co.type,
                            new FunctionCall("toFloat", false, List.of(payload(source)))));
            case CLASS_UPCAST -> new CaseExpr(List.of(
                    new WhenThen(isBottom(source), CypherArtifacts.bottomLiteral(co.type))),
                    CypherArtifacts.taggedScalar(co.type, payload(source)));
            case COLLECTION_ELEMENT_COERCION -> {
                String alias = fresh("coerceItem");
                CypherExpr item = new VariableExpr(alias);
                OclType targetElement = co.type.elementType();
                CypherExpr converted = new CaseExpr(List.of(
                        new WhenThen(isBottom(item),
                                CypherArtifacts.bottomLiteral(targetElement))),
                        CypherArtifacts.taggedScalar(targetElement,
                                targetElement.equals(OclType.REAL)
                                        ? new FunctionCall("toFloat", false,
                                                List.of(payload(item)))
                                        : payload(item)));
                CypherExpr mapped = new ListComprehension(alias,
                        new PropertyAccess(source, CypherArtifacts.OCL_ITEMS), null, converted);
                yield new CaseExpr(List.of(
                        new WhenThen(isBottom(source),
                                CypherArtifacts.bottomLiteral(co.type))),
                        CypherArtifacts.collectionTag(co.type, mapped));
            }
        });
    }

    /** To-one navigation: exactly one matching target, else typed bottom. */
    private CypherExpr realizeNavigateOne(QNode.QExpr.NavigateOne nav, Ctx ctx) {
        if (nav.associationClass) {
            return realizeAssociationClassOne(nav, ctx);
        }
        if (nav.viaAssociationClass) {
            return realizeParticipantThroughAssociationClassOne(nav, ctx);
        }
        CypherExpr src = realizeExpr(nav.source, ctx);
        return bindOnce("navigationSource", src, sourceValue ->
                bindQualifiersOnce(nav.qualifiers, ctx, qualifierValues -> {
        String roleParam = fresh("__oclRole");
        addParam(roleParam, "Physical:Role", QueryParameter.Origin.GENERATED, nav.roleName);
        String o = fresh("o");
        String t = fresh("t");
        Pattern navP = new Pattern(List.of(new PathPattern(
                List.of(new NodePattern(o, List.of("Object"),
                                withModel(new PropertyMapEntry("use_id", payload(sourceValue)))),
                        new NodePattern(t, List.of("Object"), modelProperty())),
                List.of(new RelPattern(fresh("rel"), GraphModel.LINK_ASSOCIATE_WITH,
                        nav.reverse ? RelDirection.INCOMING : RelDirection.OUTGOING,
                        navigationProperties(roleParam, nav.associationName, nav.reverse,
                                qualifierValues))))));
        CypherQuery sub = new CypherQuery(List.of(
                new MatchClause(navP, null),
                new ReturnClause(false, List.of(new ProjectionItem(
                        new PropertyAccess(new VariableExpr(t), "use_id"), null)))),
                true);
        CypherExpr xs = new CollectSubquery(sub);
        return bindOnce("navigationMatches", xs, matches -> {
        CypherExpr sizeIsOne = new BinaryExpr(BinaryOp.EQUAL,
                new FunctionCall("size", false, List.of(matches)),
                new IntegerLiteral(java.math.BigInteger.ONE));
        CypherExpr target = CypherArtifacts.taggedScalar(nav.type,
                new FunctionCall("head", false, List.of(matches)));
        return new CaseExpr(List.of(
                new WhenThen(or(isBottom(sourceValue), qualifierBottom(qualifierValues)),
                        CypherArtifacts.bottomLiteral(nav.type)),
                new WhenThen(sizeIsOne, target)),
                CypherArtifacts.bottomLiteral(nav.type));
        });
        }));
    }

    private CypherExpr realizeAssociationClassOne(QNode.QExpr.NavigateOne nav, Ctx ctx) {
        CypherExpr src = realizeExpr(nav.source, ctx);
        String roleParam = fresh("__oclRole");
        addParam(roleParam, "Physical:Role", QueryParameter.Origin.GENERATED, nav.roleName);
        return bindOnce("associationSource", src, source ->
                bindQualifiersOnce(nav.qualifiers, ctx, qualifierValues -> {
        String participant = fresh("participant");
        String occurrence = fresh("associationClass");
        Pattern pattern = new Pattern(List.of(new PathPattern(
                List.of(new NodePattern(participant, List.of("Object"),
                                withModel(new PropertyMapEntry("use_id", payload(source)))),
                        new NodePattern(occurrence, List.of("AssociationClassObject"),
                                modelProperty())),
                List.of(new RelPattern(fresh("participantRel"),
                        nav.reverse
                                ? GraphModel.associationClassTargetParticipant(nav.associationName)
                                : GraphModel.associationClassSourceParticipant(nav.associationName),
                        RelDirection.INCOMING,
                        associationClassProperties(roleParam, nav.associationName,
                                nav.reverse, qualifierValues))))));
        CypherQuery sub = new CypherQuery(List.of(
                new MatchClause(pattern, null),
                new ReturnClause(false, List.of(new ProjectionItem(
                        new PropertyAccess(new VariableExpr(occurrence), "use_id"), null)))),
                true);
        CypherExpr matches = new CollectSubquery(sub);
        return bindOnce("associationMatches", matches, matchValues -> {
            CypherExpr exactlyOne = new BinaryExpr(BinaryOp.EQUAL,
                    new FunctionCall("size", false, List.of(matchValues)),
                    new IntegerLiteral(java.math.BigInteger.ONE));
            CypherExpr target = CypherArtifacts.taggedScalar(nav.type,
                    new FunctionCall("head", false, List.of(matchValues)));
            return new CaseExpr(List.of(
                    new WhenThen(or(isBottom(source),
                            qualifierBottom(qualifierValues)),
                            CypherArtifacts.bottomLiteral(nav.type)),
                    new WhenThen(exactlyOne, target)),
                    CypherArtifacts.bottomLiteral(nav.type));
        });
        }));
    }

    private List<PropertyMapEntry> associationClassProperties(String roleParam,
                                                               String associationName,
                                                               boolean receiverIsTarget,
                                                               List<CypherExpr> qualifiers) {
        List<PropertyMapEntry> properties = new ArrayList<>();
        properties.add(new PropertyMapEntry("associationKey",
                new StringLiteral(associationName)));
        properties.add(new PropertyMapEntry("modelKey",
                modelKeyExpr()));
        properties.add(new PropertyMapEntry(receiverIsTarget ? "targetRole" : "sourceRole",
                new ParameterExpr(roleParam)));
        for (int i = 0; i < qualifiers.size(); i++) {
            CypherExpr qualifier = qualifiers.get(i);
            properties.add(new PropertyMapEntry("qualifier::" + i,
                    new FunctionCall("toString", false, List.of(payload(qualifier)))));
        }
        return properties;
    }

    private CypherExpr realizeParticipantThroughAssociationClassOne(
            QNode.QExpr.NavigateOne nav, Ctx ctx) {
        CypherExpr src = realizeExpr(nav.source, ctx);
        String roleParam = fresh("__oclRole");
        addParam(roleParam, "Physical:Role", QueryParameter.Origin.GENERATED, nav.roleName);
        return bindOnce("associationSource", src, sourceValue ->
                bindQualifiersOnce(nav.qualifiers, ctx, qualifierValues -> {
        String source = fresh("participant");
        String occurrence = fresh("associationClass");
        String target = fresh("target");
        Pattern pattern = associationClassParticipantPath(source, occurrence, target,
                sourceValue, roleParam, nav.associationName, nav.reverse, qualifierValues);
        CypherQuery sub = new CypherQuery(List.of(
                new MatchClause(pattern, null),
                new ReturnClause(false, List.of(new ProjectionItem(
                        new PropertyAccess(new VariableExpr(target), "use_id"), null)))), true);
        CypherExpr matches = new CollectSubquery(sub);
        return bindOnce("associationMatches", matches, matchValues -> {
            CypherExpr exactlyOne = new BinaryExpr(BinaryOp.EQUAL,
                    new FunctionCall("size", false, List.of(matchValues)),
                    new IntegerLiteral(java.math.BigInteger.ONE));
            return new CaseExpr(List.of(
                    new WhenThen(or(isBottom(sourceValue),
                            qualifierBottom(qualifierValues)),
                            CypherArtifacts.bottomLiteral(nav.type)),
                    new WhenThen(exactlyOne, CypherArtifacts.taggedScalar(nav.type,
                            new FunctionCall("head", false, List.of(matchValues))))),
                    CypherArtifacts.bottomLiteral(nav.type));
        });
        }));
    }

    private Pattern associationClassParticipantPath(String source, String occurrence,
                                                    String target, CypherExpr src,
                                                    String roleParam, String associationKey,
                                                    boolean reverse,
                                                    List<CypherExpr> qualifiers) {
        String sourceType = reverse
                ? GraphModel.associationClassTargetParticipant(associationKey)
                : GraphModel.associationClassSourceParticipant(associationKey);
        String targetType = reverse
                ? GraphModel.associationClassSourceParticipant(associationKey)
                : GraphModel.associationClassTargetParticipant(associationKey);
        List<PropertyMapEntry> sourceProperties = associationClassEdgeProperties(
                associationKey, null, null, qualifiers);
        List<PropertyMapEntry> targetProperties = associationClassEdgeProperties(
                associationKey, reverse ? "sourceRole" : "targetRole", roleParam,
                qualifiers);
        return new Pattern(List.of(new PathPattern(
                List.of(new NodePattern(source, List.of("Object"),
                                withModel(new PropertyMapEntry("use_id", payload(src)))),
                        new NodePattern(occurrence, List.of("AssociationClassObject"),
                                modelProperty()),
                        new NodePattern(target, List.of("Object"), modelProperty())),
                List.of(new RelPattern(fresh("sourceParticipant"), sourceType,
                                RelDirection.INCOMING, sourceProperties),
                        new RelPattern(fresh("targetParticipant"), targetType,
                                RelDirection.OUTGOING, targetProperties)))));
    }

    private List<PropertyMapEntry> associationClassEdgeProperties(
            String associationKey, String roleProperty, String roleParam,
            List<CypherExpr> qualifiers) {
        List<PropertyMapEntry> properties = new ArrayList<>();
        properties.add(new PropertyMapEntry("associationKey", new StringLiteral(associationKey)));
        properties.add(new PropertyMapEntry("modelKey", modelKeyExpr()));
        if (roleProperty != null) {
            properties.add(new PropertyMapEntry(roleProperty, new ParameterExpr(roleParam)));
        }
        for (int i = 0; i < qualifiers.size(); i++) {
            CypherExpr qualifier = qualifiers.get(i);
            properties.add(new PropertyMapEntry("qualifier::" + i,
                    new FunctionCall("toString", false, List.of(payload(qualifier)))));
        }
        return properties;
    }

    private List<PropertyMapEntry> navigationProperties(String roleParam,
                                                         String associationName,
                                                         boolean reverse,
                                                         List<CypherExpr> qualifiers) {
        List<PropertyMapEntry> props = new ArrayList<>();
        props.add(new PropertyMapEntry("associationName", new StringLiteral(associationName)));
        props.add(new PropertyMapEntry("modelKey", modelKeyExpr()));
        props.add(new PropertyMapEntry(reverse ? "sourceRole" : "targetRole",
                new ParameterExpr(roleParam)));
        for (int i = 0; i < qualifiers.size(); i++) {
            CypherExpr q = qualifiers.get(i);
            props.add(new PropertyMapEntry("qualifier::" + i,
                    new FunctionCall("toString", false, List.of(payload(q)))));
        }
        return props;
    }

    private CypherExpr qualifierBottom(List<CypherExpr> qualifiers) {
        CypherExpr result = new BooleanLiteral(false);
        for (CypherExpr q : qualifiers) {
            result = new BinaryExpr(BinaryOp.OR, result,
                    isBottom(q));
        }
        return result;
    }

    /** Realize every qualifier once, then expose only its bound reference. */
    private CypherExpr bindQualifiersOnce(List<QNode.QExpr> qualifiers, Ctx ctx,
            java.util.function.Function<List<CypherExpr>, CypherExpr> body) {
        return bindQualifierAt(qualifiers, ctx, 0, List.of(), body);
    }

    private CypherExpr bindQualifierAt(List<QNode.QExpr> qualifiers, Ctx ctx, int index,
            List<CypherExpr> realized,
            java.util.function.Function<List<CypherExpr>, CypherExpr> body) {
        if (index == qualifiers.size()) {
            return body.apply(realized);
        }
        CypherExpr value = realizeExpr(qualifiers.get(index), ctx);
        return bindOnce("navigationQualifier", value, reference -> {
            List<CypherExpr> next = new ArrayList<>(realized);
            next.add(reference);
            return bindQualifierAt(qualifiers, ctx, index + 1, List.copyOf(next), body);
        });
    }

    /**
     * oclIsTypeOf/oclIsKindOf: a typed bottom is tested using its carried class
     * type; defined objects use the graph's direct/conformance observers.
     */
    private CypherExpr realizeTypeTest(QNode.QExpr.TypeTest tt, Ctx ctx) {
        CypherExpr src = realizeExpr(tt.source, ctx);
        String ckParam = fresh("__oclTestClass");
        addParam(ckParam, "Physical:ClassKey", QueryParameter.Origin.GENERATED, tt.targetClassKey);
        return bindOnce("typeTestSource", src, source -> {
        String o = fresh("o");
        String c = fresh("c");
        // exact: direct ObjectInstanceOf; conformance: chain via Extends* (variable length
        // is outside the profile, so we express conformance as a bounded EXISTS over the
        // schema closure the graph already materialized as direct Extends edges + self).
        boolean exact = tt.testKind == org.uet.dse.ocl2cypher.core.CoreExpr.TypeTestKind.EXACT_TYPE;
        Pattern typing = new Pattern(List.of(new PathPattern(
                 List.of(new NodePattern(o, List.of("Object"),
                                withModel(new PropertyMapEntry("use_id", payload(source)))),
                        new NodePattern(c, List.of("UmlClass"),
                                List.of(new PropertyMapEntry("modelKey", modelKeyExpr()),
                                        new PropertyMapEntry("classKey",
                                        new ParameterExpr(ckParam))))),
                List.of(new RelPattern(null, GraphModel.OBJECT_INSTANCE_OF,
                        RelDirection.OUTGOING, List.of())))));
        // matchesExact = EXISTS { direct typing to that class }
        CypherExpr matches = existsMatch(typing, o);
        // conformance: also accept a strict superclass reached by Extends+;
        // direct self-conformance is already covered by the first EXISTS.
        // Variable-length is only permitted here because the schema Extends graph is finite and
        // acyclic (WF); the serializer emits it as a bounded pattern.
        CypherExpr result = matches;
        if (!exact) {
            result = new BinaryExpr(BinaryOp.OR, matches, conformanceExists(source, ckParam));
        }
        CypherExpr bottomMatch = bottomTypeTest(tt, source);
        return new CaseExpr(List.of(
                new WhenThen(isBottom(source),
                        CypherArtifacts.taggedScalar(OclType.BOOLEAN, bottomMatch))),
                CypherArtifacts.taggedScalar(OclType.BOOLEAN, result));
        });
    }

    private CypherExpr bottomTypeTest(QNode.QExpr.TypeTest tt, CypherExpr src) {
        CypherExpr carriedType = new PropertyAccess(src, CypherArtifacts.OCL_TYPE);
        if (tt.testKind == org.uet.dse.ocl2cypher.core.CoreExpr.TypeTestKind.EXACT_TYPE) {
            return new BinaryExpr(BinaryOp.EQUAL, carriedType,
                    new StringLiteral(CypherArtifacts.typeTag(OclType.clazz(tt.targetClassKey))));
        }
        List<CypherExpr> acceptedTypes = new ArrayList<>();
        for (GraphModel.Node target : graph.nodes()) {
            if ("UML_CLASS".equals(target.observationRole())
                    && tt.targetClassKey.equals(target.properties().get("classKey"))) {
                for (String key : graph.subclassClosure(target.stableKey())) {
                    String name = graph.node(key).properties().get("classKey");
                    acceptedTypes.add(new StringLiteral(CypherArtifacts.typeTag(OclType.clazz(name))));
                }
            }
        }
        return new BinaryExpr(BinaryOp.IN, carriedType, new ListExpr(acceptedTypes));
    }

    private CypherExpr conformanceExists(CypherExpr src, String ckParam) {
        String o = fresh("o");
        String d = fresh("d");
        String sup = fresh("sup");
        // (o)-[:ObjectInstanceOf]->(d)-[:Extends+]->(sup {classKey:$target})
        Pattern p = new Pattern(List.of(new PathPattern(
                List.of(new NodePattern(o, List.of("Object"),
                                withModel(new PropertyMapEntry("use_id", payload(src)))),
                        new NodePattern(d, List.of("UmlClass"), modelProperty()),
                        new NodePattern(sup, List.of("UmlClass"),
                                List.of(new PropertyMapEntry("modelKey", modelKeyExpr()),
                                        new PropertyMapEntry("classKey",
                                        new ParameterExpr(ckParam))))),
                List.of(new RelPattern(null, GraphModel.OBJECT_INSTANCE_OF,
                                RelDirection.OUTGOING, List.of()),
                        new RelPattern(null, GraphModel.EXTENDS,
                                RelDirection.OUTGOING, List.of(), 1,
                                Math.max(1, inheritanceBound()))))));
        return existsMatch(p, o);
    }

    /** Direct dynamic-class match used by exact tests and reflexive casts. */
    private CypherExpr directTypeExists(CypherExpr src, String ckParam) {
        String o = fresh("o");
        String c = fresh("c");
        Pattern p = new Pattern(List.of(new PathPattern(
                List.of(new NodePattern(o, List.of("Object"),
                                withModel(new PropertyMapEntry("use_id", payload(src)))),
                        new NodePattern(c, List.of("UmlClass"),
                                List.of(new PropertyMapEntry("modelKey",
                                                modelKeyExpr()),
                                        new PropertyMapEntry("classKey",
                                                new ParameterExpr(ckParam))))),
                List.of(new RelPattern(null, GraphModel.OBJECT_INSTANCE_OF,
                        RelDirection.OUTGOING, List.of())))));
        return existsMatch(p, o);
    }

    private CypherExpr existsMatch(Pattern pattern, String projectedVariable) {
        return new ExistsSubquery(new CypherQuery(
                List.of(new MatchClause(pattern, null),
                        new ReturnClause(false, List.of(new ProjectionItem(
                                new VariableExpr(projectedVariable), null)))),
                true));
    }

    private int inheritanceBound() {
        int classes = 0;
        for (GraphModel.Node n : graph.nodes()) {
            if ("UML_CLASS".equals(n.observationRole())) {
                classes++;
            }
        }
        return Math.max(0, classes);
    }

    /** oclAsType: bottom or non-conforming → typed bottom; else re-tag identity. */
    private CypherExpr realizeTypeCast(QNode.QExpr.TypeCast tc, Ctx ctx) {
        CypherExpr src = realizeExpr(tc.source, ctx);
        String ckParam = fresh("__oclCastClass");
        addParam(ckParam, "Physical:ClassKey", QueryParameter.Origin.GENERATED, tc.targetClassKey);
        return bindOnce("typeCastSource", src, source -> {
        CypherExpr conforms = new BinaryExpr(BinaryOp.OR,
                directTypeExists(source, ckParam), conformanceExists(source, ckParam));
        return new CaseExpr(List.of(
                new WhenThen(isBottom(source), CypherArtifacts.bottomLiteral(tc.type)),
                new WhenThen(conforms, CypherArtifacts.taggedScalar(tc.type, payload(source)))),
                CypherArtifacts.bottomLiteral(tc.type));
        });
    }

    /** includes/excludes/includesAll/excludesAll over a tagged collection. */
    private CypherExpr realizeIncludes(QNode.QExpr.IncludesFamily inc, Ctx ctx) {
        CypherExpr src = realizeExpr(inc.source, ctx);
        CypherExpr el = realizeExpr(inc.element, ctx);
        boolean exclude = switch (inc.includesKind) {
            case INCLUDES -> false;
            case EXCLUDES -> true;
            case INCLUDES_ALL, EXCLUDES_ALL -> inc.includesKind == QNode.QKind.EXCLUDES_ALL;
            default -> throw new RealizeError("R_UNCOVERED_CONSTRUCTOR",
                    "not an includes-family constructor: " + inc.includesKind);
        };
        return bindPair("membershipSource", src, "membershipElement", el,
                (source, element) -> inc.includesKind == QNode.QKind.INCLUDES
                        || inc.includesKind == QNode.QKind.EXCLUDES
                        ? realizeMembership(source, element, exclude)
                        : realizeMembershipAll(source, element, exclude));
    }

    /** Canonical includes/excludes body shared by legacy and Binary Q nodes. */
    private CypherExpr realizeMembership(CypherExpr src, CypherExpr el, boolean exclude) {
        CypherExpr items = new PropertyAccess(src, CypherArtifacts.OCL_ITEMS);
        String x = fresh("x");
        CypherExpr memberEq = atomicTaggedEqual(new VariableExpr(x), el);
        CypherExpr anyMember = new QuantifiedPredicateExpression(
                QuantifierKind.ANY, x, items, memberEq);
        CypherExpr value = exclude ? new UnaryExpr(UnaryOp.NOT, anyMember) : anyMember;
        return new CaseExpr(List.of(
                new WhenThen(isBottom(src), CypherArtifacts.bottomLiteral(OclType.BOOLEAN))),
                CypherArtifacts.taggedScalar(OclType.BOOLEAN, value));
    }

    /** size/count/sum over a tagged collection; whole-bottom propagates. */
    private CypherExpr realizeCount(QNode.QExpr.CountFamily cf, Ctx ctx) {
        if (cf.countKind == QNode.QKind.SUM) {
            return realizeUnary(new QNode.QExpr.Unary(cf.span,
                    org.uet.dse.ocl2cypher.core.CoreExpr.UnaryOp.COLLECTION_SUM,
                    cf.source, cf.type), ctx);
        }
        CypherExpr src = realizeExpr(cf.source, ctx);
        if (cf.countKind == QNode.QKind.COUNT) {
            CypherExpr element = realizeExpr(cf.element, ctx);
            return bindPair("countSource", src, "countElement", element,
                    (source, item) -> collectionCount(
                            new QNode.QExpr.Binary(cf.span,
                                    org.uet.dse.ocl2cypher.core.CoreExpr.BinaryOp.COLLECTION_COUNT,
                                    cf.source, cf.element, cf.type), source, item));
        }
        return bindOnce("countSource", src, source -> {
        CypherExpr items = new PropertyAccess(source, CypherArtifacts.OCL_ITEMS);
        return switch (cf.countKind) {
            case SIZE -> new CaseExpr(List.of(
                    new WhenThen(isBottom(source), CypherArtifacts.bottomLiteral(OclType.INTEGER))),
                    CypherArtifacts.taggedScalar(OclType.INTEGER,
                            new FunctionCall("size", false, List.of(items))));
            case IS_EMPTY -> new CaseExpr(List.of(
                    new WhenThen(isBottom(source), CypherArtifacts.bottomLiteral(OclType.BOOLEAN))),
                    CypherArtifacts.taggedScalar(OclType.BOOLEAN,
                            new BinaryExpr(BinaryOp.EQUAL,
                                    new FunctionCall("size", false, List.of(items)),
                                    new IntegerLiteral(java.math.BigInteger.ZERO))));
            case NOT_EMPTY -> new CaseExpr(List.of(
                    new WhenThen(isBottom(source), CypherArtifacts.bottomLiteral(OclType.BOOLEAN))),
                    CypherArtifacts.taggedScalar(OclType.BOOLEAN,
                            new BinaryExpr(BinaryOp.GREATER_THAN,
                                    new FunctionCall("size", false, List.of(items)),
                                    new IntegerLiteral(java.math.BigInteger.ZERO))));
            default -> throw new RealizeError("R_UNCOVERED_CONSTRUCTOR",
                    "not a count-family constructor: " + cf.countKind);
        };
        });
    }

    private CypherExpr realizeSetAlgebra(QNode.QExpr.SetAlgebra sa, Ctx ctx) {
        CypherExpr left = realizeExpr(sa.left, ctx);
        CypherExpr right = realizeExpr(sa.right, ctx);
        boolean union = sa.operator
                == org.uet.dse.ocl2cypher.core.CoreExpr.BinaryOp.SET_UNION;
        if (!union && sa.operator
                != org.uet.dse.ocl2cypher.core.CoreExpr.BinaryOp.SET_INTERSECTION) {
            throw new RealizeError("R_UNCOVERED_CONSTRUCTOR",
                    "not a set-algebra constructor: " + sa.operator);
        }
        // Reuse the canonical Binary realization after evaluating both operands;
        // this keeps direct QExpr.SetAlgebra construction semantically identical
        // to the normalized path used by the translator.
        return bindPair("setLeft", left, "setRight", right,
                (leftValue, rightValue) -> realizeSetBinary(
                        new QNode.QExpr.Binary(sa.span, sa.operator,
                                sa.left, sa.right, sa.type),
                        leftValue, rightValue, union));
    }

    private CypherExpr realizeIf(QNode.QExpr.IfExpr iff, Ctx ctx) {
        CypherExpr c = realizeExpr(iff.condition, ctx);
        CypherExpr t = realizeExpr(iff.thenExpr, ctx);
        CypherExpr f = realizeExpr(iff.elseExpr, ctx);
        // CASE WHEN bottom(c) THEN bottom
        //      WHEN value(c)=true THEN t ELSE f END
        return bindOnce("ifCondition", c, condition -> new CaseExpr(List.of(
                new WhenThen(isBottom(condition), CypherArtifacts.bottomLiteral(iff.type)),
                new WhenThen(new BinaryExpr(BinaryOp.EQUAL, payload(condition),
                        new BooleanLiteral(true)), t)),
                f));
    }

    private CypherExpr realizeAttribute(QNode.QExpr.ReadAttribute ar, Ctx ctx) {
        if (ar.type.equals(OclType.INTEGER)) {
            requireStoredIntegerInt64(ar);
        }
        CypherExpr src = realizeExpr(ar.source, ctx);
        String akParam = fresh("__oclAttr");
        addParam(akParam, "Physical:AttributeKey", QueryParameter.Origin.GENERATED,
                ar.ownerClassKey + "::" + ar.attributeName);
        return bindOnce("attributeSource", src, source -> {
        String o = fresh("o");
        String s = fresh("s");
        // Slot path: (o:Object {use_id: src.value})-[:ObjectHasAttribute]->
        //            (s:AttributeValue {attributeKey: $ak}).  Collect the
        // tagged storage carrier, not only s.value: a defined String equal to
        // the old __BOTTOM__ sentinel must remain distinguishable from bottom.
        Pattern slotPattern = new Pattern(List.of(new PathPattern(
                List.of(new NodePattern(o, List.of("Object"),
                                withModel(new PropertyMapEntry("use_id", payload(source)))),
                        new NodePattern(s, List.of("AttributeValue"),
                                List.of(new PropertyMapEntry("modelKey", modelKeyExpr()),
                                        new PropertyMapEntry("attributeKey",
                                        new ParameterExpr(akParam))))),
                List.of(new RelPattern(null, GraphModel.OBJECT_HAS_ATTRIBUTE,
                        RelDirection.OUTGOING, List.of())))));
        CypherQuery sub = new CypherQuery(List.of(
                new MatchClause(slotPattern, null),
                new ReturnClause(false, List.of(new ProjectionItem(
                        new MapExpr(List.of(
                                new MapEntry("state", new PropertyAccess(
                                        new VariableExpr(s), GraphValueCodec.VALUE_STATE)),
                                new MapEntry("type", new PropertyAccess(
                                        new VariableExpr(s), GraphValueCodec.VALUE_TYPE)),
                                new MapEntry("codec", new PropertyAccess(
                                        new VariableExpr(s), GraphValueCodec.CODEC_ID)),
                                new MapEntry("payload", new PropertyAccess(
                                        new VariableExpr(s), GraphValueCodec.PAYLOAD)))), null)))),
                true);
        CypherExpr xs = new CollectSubquery(sub);
        return bindOnce("attributeSlots", xs, slotList -> {
        // CASE WHEN IsBottom(src) THEN bottom
        //      WHEN size(xs)=1 THEN Defined(decode(head(xs)))
        //      ELSE bottom END   (missing / multiplicity-corrupt → bottom)
        CypherExpr sizeIsOne = new BinaryExpr(BinaryOp.EQUAL,
                new FunctionCall("size", false, List.of(slotList)),
                new IntegerLiteral(java.math.BigInteger.ONE));
        CypherExpr stored = new FunctionCall("head", false, List.of(slotList));
        return bindOnce("attributeSlot", stored, storedValue -> {
        CypherExpr storedIsDefined = new BinaryExpr(BinaryOp.EQUAL,
                new PropertyAccess(storedValue, "state"),
                new StringLiteral(GraphValueCodec.DEFINED));
        CypherExpr storedHasExpectedType = new BinaryExpr(BinaryOp.EQUAL,
                new PropertyAccess(storedValue, "type"), new StringLiteral(ar.type.toString()));
        CypherExpr storedHasExpectedCodec = new BinaryExpr(BinaryOp.EQUAL,
                new PropertyAccess(storedValue, "codec"),
                new StringLiteral(GraphValueCodec.codecId(ar.type)));
        CypherExpr exactlyOneDefined = new BinaryExpr(BinaryOp.AND, sizeIsOne,
                new BinaryExpr(BinaryOp.AND, storedIsDefined,
                        new BinaryExpr(BinaryOp.AND, storedHasExpectedType,
                                storedHasExpectedCodec)));
        CypherExpr decoded = decodeScalar(ar.type,
                new PropertyAccess(storedValue, "payload"));
        return new CaseExpr(List.of(
                new WhenThen(isBottom(source), CypherArtifacts.bottomLiteral(ar.type)),
                new WhenThen(exactlyOneDefined,
                        CypherArtifacts.taggedScalar(ar.type, decoded))),
                CypherArtifacts.bottomLiteral(ar.type));
        });
        });
        });
    }

    /**
     * Plan→Expr bridge. For the Slice-B case the plan is a filtered/collected
     * navigation; the materialized value is the tagged collection of the
     * plan's element tags. When the plan source is bottom the whole-collection
     * bottom is kept, not an empty collection.
     */
    private CypherExpr realizeMaterialize(QNode.QExpr.Materialize mat, Ctx ctx) {
        MaterializedPlan mp = materializePlan(mat.plan, ctx);
        // Items are already tagged element maps.
        return bindOnce("materializedPlan", mp.value(), plan -> {
            CypherExpr bottom = new PropertyAccess(plan, CypherArtifacts.OCL_BOTTOM);
            CypherExpr items = new PropertyAccess(plan, CypherArtifacts.OCL_ITEMS);
            return new CaseExpr(List.of(
                    new WhenThen(bottom, CypherArtifacts.bottomLiteral(mat.type))),
                    CypherArtifacts.collectionTag(mat.type, items));
        });
    }

    /**
     * exists₃: {@code CASE WHEN anyBottom AND noTrue THEN ⊥ WHEN anyTrue THEN T
     * ELSE F END}. A predicate-bottom only makes the whole result bottom when no
     * occurrence is already true (Kleene OR), so a single true still wins.
     */
    private CypherExpr realizeExists(QNode.QExpr.Exists3 ex, Ctx ctx) {
        FoldedPredicates folded = foldPlanPredicates(ex.source, ex.iterator,
                ex.predicate, ctx);
        return bindOnce("existsFold", folded.value(), fold -> {
            CypherExpr bottom = new PropertyAccess(fold, CypherArtifacts.OCL_BOTTOM);
            CypherExpr predicates = new PropertyAccess(fold, "predicates");
            return bindOnce("existsPredicates", predicates, preds -> {
                CypherExpr anyTrue = anyPredValue(preds, true);
                CypherExpr anyBottom = anyPredBottom(preds);
                return new CaseExpr(List.of(
                        new WhenThen(bottom,
                                CypherArtifacts.bottomLiteral(OclType.BOOLEAN)),
                        new WhenThen(anyTrue,
                                CypherArtifacts.taggedScalar(OclType.BOOLEAN,
                                        new BooleanLiteral(true))),
                        new WhenThen(anyBottom,
                                CypherArtifacts.bottomLiteral(OclType.BOOLEAN))),
                        CypherArtifacts.taggedScalar(OclType.BOOLEAN,
                                new BooleanLiteral(false)));
            });
        });
    }

    /**
     * forAll₃: {@code CASE WHEN anyFalse THEN F WHEN anyBottom THEN ⊥ ELSE T END}.
     * A single false wins (Kleene AND); a bottom makes it bottom only if no
     * occurrence is already false.
     */
    private CypherExpr realizeForAll(QNode.QExpr.ForAll3 fa, Ctx ctx) {
        FoldedPredicates folded = foldPlanPredicates(fa.source, fa.iterator,
                fa.predicate, ctx);
        return bindOnce("forAllFold", folded.value(), fold -> {
            CypherExpr bottom = new PropertyAccess(fold, CypherArtifacts.OCL_BOTTOM);
            CypherExpr predicates = new PropertyAccess(fold, "predicates");
            return bindOnce("forAllPredicates", predicates, preds -> {
                CypherExpr anyFalse = anyPredValue(preds, false);
                CypherExpr anyBottom = anyPredBottom(preds);
                return new CaseExpr(List.of(
                        new WhenThen(bottom,
                                CypherArtifacts.bottomLiteral(OclType.BOOLEAN)),
                        new WhenThen(anyFalse,
                                CypherArtifacts.taggedScalar(OclType.BOOLEAN,
                                        new BooleanLiteral(false))),
                        new WhenThen(anyBottom,
                                CypherArtifacts.bottomLiteral(OclType.BOOLEAN))),
                        CypherArtifacts.taggedScalar(OclType.BOOLEAN,
                                new BooleanLiteral(true)));
            });
        });
    }

    // ---- R_P : QPlan → occurrence list of tagged element maps ------------

    /** A plan is one carrier so its items and whole-bottom flag share evaluation. */
    private record MaterializedPlan(CypherExpr value) {
        CypherExpr items() {
            return new PropertyAccess(value, CypherArtifacts.OCL_ITEMS);
        }

        CypherExpr bottomGuard() {
            return new PropertyAccess(value, CypherArtifacts.OCL_BOTTOM);
        }
    }

    private MaterializedPlan planValue(CypherExpr items, CypherExpr bottomGuard) {
        return new MaterializedPlan(new MapExpr(List.of(
                new MapEntry(CypherArtifacts.OCL_ITEMS, items),
                new MapEntry(CypherArtifacts.OCL_BOTTOM, bottomGuard))));
    }

    private MaterializedPlan bindPlanOnce(String base, MaterializedPlan plan,
            java.util.function.BiFunction<CypherExpr, CypherExpr, MaterializedPlan> body) {
        String alias = fresh(base);
        CypherExpr reference = new VariableExpr(alias);
        CypherExpr items = new PropertyAccess(reference, CypherArtifacts.OCL_ITEMS);
        CypherExpr bottom = new PropertyAccess(reference, CypherArtifacts.OCL_BOTTOM);
        MaterializedPlan transformed = body.apply(items, bottom);
        return new MaterializedPlan(bindOnce(alias, plan.value(), transformed.value()));
    }

    private MaterializedPlan materializePlan(QNode.QPlan plan, Ctx ctx) {
        if (plan instanceof QNode.QPlan.FromCollection fc) {
            CypherExpr source = realizeExpr(fc.collection, ctx);
            return new MaterializedPlan(bindOnce("collectionSource", source, value ->
                    planValue(new PropertyAccess(value, CypherArtifacts.OCL_ITEMS),
                            isBottom(value)).value()));
        }
        if (plan instanceof QNode.QPlan.NavigateMany nav) {
            if (nav.associationClass) {
                return materializeAssociationClassPlan(nav, ctx);
            }
            if (nav.viaAssociationClass) {
                return materializeParticipantThroughAssociationClassPlan(nav, ctx);
            }
            CypherExpr src = realizeExpr(nav.source, ctx);
            String roleParam = fresh("__oclRole");
            addParam(roleParam, "Physical:Role", QueryParameter.Origin.GENERATED, nav.roleName);
            CypherExpr value = bindOnce("navigationSource", src, source ->
                    bindQualifiersOnce(nav.qualifiers, ctx, qualifierValues -> {
            String o = fresh("o");
            String t = fresh("t");
            Pattern navP = new Pattern(List.of(new PathPattern(
                    List.of(new NodePattern(o, List.of("Object"),
                                    withModel(new PropertyMapEntry("use_id", payload(source)))),
                            new NodePattern(t, List.of("Object"), modelProperty())),
                    List.of(new RelPattern(fresh("rel"), GraphModel.LINK_ASSOCIATE_WITH,
                             nav.reverse ? RelDirection.INCOMING : RelDirection.OUTGOING,
                             navigationProperties(roleParam, nav.associationName, nav.reverse,
                                    qualifierValues))))));
            CypherQuery sub = new CypherQuery(List.of(
                    new MatchClause(navP, null),
                    new ReturnClause(nav.type.kind() == OclType.Kind.SET,
                            List.of(new ProjectionItem(taggedObject(t, nav.elementType),
                                    null)))),
                    true);
            return planValue(new CollectSubquery(sub),
                    or(isBottom(source), qualifierBottom(qualifierValues))).value();
            }));
            return new MaterializedPlan(value);
        }
        if (plan instanceof QNode.QPlan.Filter flt) {
            MaterializedPlan inner = materializePlan(flt.source, ctx);
            String x = fresh("x");
            Ctx filtered = ctx.child();
            filtered.bind(flt.iterator, new VariableExpr(x),
                    flt.source.type.elementType());
            // pred is a tagged Boolean₃ map expressed over the element alias x,
            // It is bound inside the comprehension and evaluated once per item.
            CypherExpr pred = realizeExpr(flt.predicate, filtered);
            // keep(x) = pred defined AND pred.__oclValue = <true|false>
            // predicate-bottom on ANY occurrence ⇒ whole-collection bottom.
            return bindPlanOnce("filterInputPlan", inner, (innerItems, innerBottom) -> {
                CypherExpr evaluatedEntry = bindOnce("filterPredicate", pred, predicate ->
                        new MapExpr(List.of(
                                new MapEntry("item", new VariableExpr(x)),
                                new MapEntry("predicate", predicate))));
                CypherExpr evaluated = new ListComprehension(x, innerItems, null,
                        evaluatedEntry);
                return new MaterializedPlan(bindOnce("filterRows", evaluated, rows -> {
                    String rowName = fresh("filterRow");
                    CypherExpr row = new VariableExpr(rowName);
                    CypherExpr predicate = new PropertyAccess(row, "predicate");
                    CypherExpr keep = new BinaryExpr(BinaryOp.AND,
                            new BinaryExpr(BinaryOp.EQUAL,
                                    new PropertyAccess(predicate,
                                            CypherArtifacts.OCL_BOTTOM),
                                    new BooleanLiteral(false)),
                            new BinaryExpr(BinaryOp.EQUAL,
                                    new PropertyAccess(predicate,
                                            CypherArtifacts.OCL_VALUE),
                                    new BooleanLiteral(flt.isSelect)));
                    CypherExpr filteredItems = new ListComprehension(rowName, rows,
                            keep, new PropertyAccess(row, "item"));
                    String bottomRowName = fresh("filterBottomRow");
                    CypherExpr bottomPredicate = new PropertyAccess(
                            new PropertyAccess(new VariableExpr(bottomRowName), "predicate"),
                            CypherArtifacts.OCL_BOTTOM);
                    CypherExpr anyBottom = new QuantifiedPredicateExpression(
                            QuantifierKind.ANY, bottomRowName, rows,
                            new BinaryExpr(BinaryOp.EQUAL, bottomPredicate,
                                    new BooleanLiteral(true)));
                    return planValue(filteredItems,
                            new BinaryExpr(BinaryOp.OR, innerBottom, anyBottom)).value();
                }));
            });
        }
        if (plan instanceof QNode.QPlan.Collect collect) {
            MaterializedPlan inner = materializePlan(collect.source, ctx);
            String x = fresh("x");
            Ctx collected = ctx.child();
            collected.bind(collect.iterator, new VariableExpr(x),
                    collect.source.type.elementType());
            // collect always yields a Bag.  A bottom predicate result is kept
            // as an element of that Bag; only a bottom source makes the whole
            // collection bottom.
            CypherExpr body = realizeExpr(collect.body, collected);
            return bindPlanOnce("collectInputPlan", inner, (innerItems, innerBottom) ->
                    planValue(new ListComprehension(x, innerItems, null, body),
                            innerBottom));
        }
        if (plan instanceof QNode.QPlan.ScanClass scan) {
            String ck = fresh("__oclClass");
            addParam(ck, "Physical:ClassKey", QueryParameter.Origin.GENERATED,
                    scan.classKey);
            String o = fresh("o");
            String direct = fresh("directClass");
            String c = fresh("c");
            Pattern scanP = new Pattern(List.of(new PathPattern(
                    List.of(new NodePattern(o, List.of("Object"), modelProperty()),
                            new NodePattern(direct, List.of("UmlClass"), modelProperty()),
                            new NodePattern(c, List.of("UmlClass"),
                                    List.of(new PropertyMapEntry("modelKey", modelKeyExpr()),
                                            new PropertyMapEntry("classKey",
                                            new ParameterExpr(ck))))),
                    List.of(new RelPattern(null, GraphModel.OBJECT_INSTANCE_OF,
                            RelDirection.OUTGOING, List.of()),
                            new RelPattern(null, GraphModel.EXTENDS, RelDirection.OUTGOING,
                                    List.of(), 0, inheritanceBound())))));
            CypherQuery sub = new CypherQuery(List.of(
                    new MatchClause(scanP, null),
                    new ReturnClause(true, List.of(new ProjectionItem(
                            taggedObject(o, OclType.clazz(scan.classKey)), null)))),
                    true);
            return planValue(new CollectSubquery(sub), new BooleanLiteral(false));
        }
        if (plan instanceof QNode.QPlan.Distinct distinct) {
            MaterializedPlan inner = materializePlan(distinct.source, ctx);
            return bindPlanOnce("distinctInputPlan", inner, (innerItems, innerBottom) ->
                    planValue(distinctAtomicItems(innerItems), innerBottom));
        }
        if (plan instanceof QNode.QPlan.PlanLet let) {
            int uses = referenceCount(let.body, let.binder);
            if (uses == 0) {
                return materializePlan(let.body, ctx);
            }
            CypherExpr value = realizeExpr(let.value, ctx);
            Ctx bodyCtx = ctx.child();
            if (uses == 1) {
                bodyCtx.bind(let.binder, value, let.value.type);
                return materializePlan(let.body, bodyCtx);
            }
            String alias = fresh("planLetValue");
            bodyCtx.bind(let.binder, new VariableExpr(alias), let.value.type);
            MaterializedPlan body = materializePlan(let.body, bodyCtx);
            return new MaterializedPlan(bindOnce(alias, value, body.value()));
        }
        throw new RealizeError("R_UNCOVERED_CONSTRUCTOR",
                "materialize of plan " + plan.getClass().getSimpleName() + " is future work");
    }

    private MaterializedPlan materializeAssociationClassPlan(QNode.QPlan.NavigateMany nav,
                                                              Ctx ctx) {
        CypherExpr src = realizeExpr(nav.source, ctx);
        String roleParam = fresh("__oclRole");
        addParam(roleParam, "Physical:Role", QueryParameter.Origin.GENERATED, nav.roleName);
        CypherExpr value = bindOnce("associationSource", src, source ->
                bindQualifiersOnce(nav.qualifiers, ctx, qualifierValues -> {
        String participant = fresh("participant");
        String occurrence = fresh("associationClass");
        Pattern pattern = new Pattern(List.of(new PathPattern(
                List.of(new NodePattern(participant, List.of("Object"),
                                withModel(new PropertyMapEntry("use_id", payload(source)))),
                        new NodePattern(occurrence, List.of("AssociationClassObject"),
                                modelProperty())),
                List.of(new RelPattern(fresh("participantRel"),
                        nav.reverse
                                ? GraphModel.associationClassTargetParticipant(nav.associationName)
                                : GraphModel.associationClassSourceParticipant(nav.associationName),
                        RelDirection.INCOMING,
                        associationClassProperties(roleParam, nav.associationName,
                                nav.reverse, qualifierValues))))));
        CypherQuery sub = new CypherQuery(List.of(
                new MatchClause(pattern, null),
                new ReturnClause(nav.type.kind() == OclType.Kind.SET,
                        List.of(new ProjectionItem(
                                taggedObject(occurrence, nav.elementType), null)))),
                true);
        return planValue(new CollectSubquery(sub),
                or(isBottom(source), qualifierBottom(qualifierValues))).value();
        }));
        return new MaterializedPlan(value);
    }

    private MaterializedPlan materializeParticipantThroughAssociationClassPlan(
            QNode.QPlan.NavigateMany nav, Ctx ctx) {
        CypherExpr src = realizeExpr(nav.source, ctx);
        String roleParam = fresh("__oclRole");
        addParam(roleParam, "Physical:Role", QueryParameter.Origin.GENERATED, nav.roleName);
        CypherExpr value = bindOnce("associationSource", src, sourceValue ->
                bindQualifiersOnce(nav.qualifiers, ctx, qualifierValues -> {
        String source = fresh("participant");
        String occurrence = fresh("associationClass");
        String target = fresh("target");
        Pattern pattern = associationClassParticipantPath(source, occurrence, target,
                sourceValue, roleParam, nav.associationName, nav.reverse, qualifierValues);
        CypherQuery sub = new CypherQuery(List.of(
                new MatchClause(pattern, null),
                new ReturnClause(nav.type.kind() == OclType.Kind.SET,
                        List.of(new ProjectionItem(taggedObject(target, nav.elementType), null)))),
                true);
        return planValue(new CollectSubquery(sub),
                or(isBottom(sourceValue), qualifierBottom(qualifierValues))).value();
        }));
        return new MaterializedPlan(value);
    }

    /** Predicate tags and source-bottom guard used by exists/forAll folds. */
    private record FoldedPredicates(CypherExpr value) {
    }

    private FoldedPredicates foldPlanPredicates(QNode.QPlan plan,
                                                org.uet.dse.ocl2cypher.core.CoreDeclaration binder,
                                                QNode.QExpr predicate, Ctx ctx) {
        MaterializedPlan mp = materializePlan(plan, ctx);
        String x = fresh("x");
        Ctx sub = ctx.child();
        sub.bind(binder, new VariableExpr(x), plan.type.elementType());
        CypherExpr pred = realizeExpr(predicate, sub);
        return new FoldedPredicates(bindOnce("foldInputPlan", mp.value(), planValue ->
                new MapExpr(List.of(
                        new MapEntry("predicates", new ListComprehension(x,
                                new PropertyAccess(planValue, CypherArtifacts.OCL_ITEMS),
                                null, pred)),
                        new MapEntry(CypherArtifacts.OCL_BOTTOM,
                                new PropertyAccess(planValue,
                                        CypherArtifacts.OCL_BOTTOM))))));
    }

    // ---- predicate-list quantifiers -------------------------------------

    /**
     * {@code any(p IN preds WHERE p.__oclBottom = true)} — the tagged predicate
     * elements are already Boolean₃ maps, so "some predicate is bottom" is a
     * single quantified check over the list, not native null testing.
     */
    private CypherExpr anyPredBottom(CypherExpr preds) {
        String p = fresh("p");
        CypherExpr cond = new BinaryExpr(BinaryOp.EQUAL,
                new PropertyAccess(new VariableExpr(p), CypherArtifacts.OCL_BOTTOM),
                new BooleanLiteral(true));
        return new QuantifiedPredicateExpression(QuantifierKind.ANY, p, preds, cond);
    }

    /** {@code any(p IN preds WHERE p.__oclBottom = false AND p.__oclValue = <value>)}. */
    private CypherExpr anyPredValue(CypherExpr preds, boolean value) {
        String p = fresh("p");
        CypherExpr defined = new BinaryExpr(BinaryOp.EQUAL,
                new PropertyAccess(new VariableExpr(p), CypherArtifacts.OCL_BOTTOM),
                new BooleanLiteral(false));
        CypherExpr valueEq = new BinaryExpr(BinaryOp.EQUAL,
                new PropertyAccess(new VariableExpr(p), CypherArtifacts.OCL_VALUE),
                new BooleanLiteral(value));
        CypherExpr cond = new BinaryExpr(BinaryOp.AND, defined, valueEq);
        return new QuantifiedPredicateExpression(QuantifierKind.ANY, p, preds, cond);
    }

    // ---- binary/unary ----------------------------------------------------

    private CypherExpr realizeBinary(QNode.QExpr.Binary b, Ctx ctx) {
        CypherExpr l = realizeExpr(b.left, ctx);
        CypherExpr r = realizeExpr(b.right, ctx);
        return bindPair("leftOperand", l, "rightOperand", r, (left, right) -> switch (b.operator) {
            case GREATER_THAN, GREATER_THAN_OR_EQUAL, LESS_THAN, LESS_THAN_OR_EQUAL ->
                    strictCompare(b, left, right);
            case VALUE_EQUAL -> totalEqual(left, right, b.left.type, false);
            case VALUE_NOT_EQUAL -> totalEqual(left, right, b.left.type, true);
            case NUMERIC_ADD, NUMERIC_SUBTRACT, NUMERIC_MULTIPLY -> arithmetic(b, left, right);
            case REAL_DIVIDE, INTEGER_DIVIDE, INTEGER_MOD -> numericDivision(b, left, right);
            case NUMERIC_MAX, NUMERIC_MIN -> numericExtremum(b, left, right);
            case COLLECTION_COUNT -> collectionCount(b, left, right);
            case COLLECTION_INCLUDES -> realizeMembership(left, right, false);
            case COLLECTION_EXCLUDES -> realizeMembership(left, right, true);
            case COLLECTION_INCLUDES_ALL -> realizeMembershipAll(left, right, false);
            case COLLECTION_EXCLUDES_ALL -> realizeMembershipAll(left, right, true);
            case SET_UNION -> realizeSetBinary(b, left, right, true);
            case SET_INTERSECTION -> realizeSetBinary(b, left, right, false);
            case BOOLEAN_AND, BOOLEAN_OR, BOOLEAN_XOR, BOOLEAN_IMPLIES -> boolean3(b, left, right);
            default -> throw new RealizeError("R_UNCOVERED_CONSTRUCTOR",
                    "binary " + b.operator + " is future work");
        });
    }

    private CypherExpr arithmetic(QNode.QExpr.Binary b, CypherExpr l, CypherExpr r) {
        CypherExpr anyBottom = new BinaryExpr(BinaryOp.OR, isBottom(l), isBottom(r));
        BinaryOp op = switch (b.operator) {
            case NUMERIC_ADD -> BinaryOp.ADD;
            case NUMERIC_SUBTRACT -> BinaryOp.SUBTRACT;
            default -> BinaryOp.MULTIPLY;
        };
        return new CaseExpr(List.of(
                new WhenThen(anyBottom, CypherArtifacts.bottomLiteral(b.type))),
                CypherArtifacts.taggedScalar(b.type, new BinaryExpr(op, payload(l), payload(r))));
    }

    private CypherExpr numericDivision(QNode.QExpr.Binary b, CypherExpr l, CypherExpr r) {
        if (b.operator == org.uet.dse.ocl2cypher.core.CoreExpr.BinaryOp.INTEGER_DIVIDE
                || b.operator == org.uet.dse.ocl2cypher.core.CoreExpr.BinaryOp.INTEGER_MOD) {
            // Closed certified operands: emit the exact integer result, not native
            // division followed by conversion. Dynamic division remains fail-closed.
            var certificate = numericCertificates.certify(b).orElseThrow(
                    () -> numericCapability("uncertified integer div/mod"));
            if (!certificate.lower().equals(certificate.upper()))
                throw numericCapability("non-singleton integer div/mod");
            return new CaseExpr(List.of(new WhenThen(or(isBottom(l), isBottom(r)),
                    CypherArtifacts.bottomLiteral(b.type))),
                    CypherArtifacts.taggedScalar(b.type, new IntegerLiteral(certificate.lower())));
        }
        CypherExpr denominatorZero = new BinaryExpr(BinaryOp.EQUAL, payload(r),
                b.operator == org.uet.dse.ocl2cypher.core.CoreExpr.BinaryOp.REAL_DIVIDE
                        ? new FloatLiteral(java.math.BigDecimal.ZERO)
                        : new IntegerLiteral(java.math.BigInteger.ZERO));
        CypherExpr invalid = new BinaryExpr(BinaryOp.OR,
                new BinaryExpr(BinaryOp.OR, isBottom(l), isBottom(r)), denominatorZero);
        BinaryOp op = switch (b.operator) {
            case REAL_DIVIDE, INTEGER_DIVIDE -> BinaryOp.DIVIDE;
            case INTEGER_MOD -> BinaryOp.MODULO;
            default -> throw new IllegalStateException("not a division operator");
        };
        CypherExpr quotient = new BinaryExpr(op, payload(l), payload(r));
        if (b.operator == org.uet.dse.ocl2cypher.core.CoreExpr.BinaryOp.INTEGER_DIVIDE) {
            quotient = new FunctionCall("toInteger", false, List.of(quotient));
        }
        return new CaseExpr(List.of(
                new WhenThen(invalid, CypherArtifacts.bottomLiteral(b.type))),
                CypherArtifacts.taggedScalar(b.type,
                        quotient));
    }

    private CypherExpr numericExtremum(QNode.QExpr.Binary b, CypherExpr l, CypherExpr r) {
        CypherExpr invalid = new BinaryExpr(BinaryOp.OR, isBottom(l), isBottom(r));
        boolean max = b.operator == org.uet.dse.ocl2cypher.core.CoreExpr.BinaryOp.NUMERIC_MAX;
        CypherExpr chooseLeft = new BinaryExpr(max ? BinaryOp.GREATER_THAN_OR_EQUAL
                : BinaryOp.LESS_THAN_OR_EQUAL, payload(l), payload(r));
        CypherExpr chosen = new CaseExpr(List.of(
                new WhenThen(chooseLeft, payload(l))), payload(r));
        return new CaseExpr(List.of(new WhenThen(invalid,
                        CypherArtifacts.bottomLiteral(b.type))),
                CypherArtifacts.taggedScalar(b.type, chosen));
    }

    private CypherExpr collectionCount(QNode.QExpr.Binary b, CypherExpr source,
                                       CypherExpr element) {
        CypherExpr items = new PropertyAccess(source, CypherArtifacts.OCL_ITEMS);
        String x = fresh("countItem");
        CypherExpr keep = atomicTaggedEqual(new VariableExpr(x), element);
        CypherExpr count = new FunctionCall("size", false,
                List.of(new ListComprehension(x, items, keep, null)));
        return new CaseExpr(List.of(new WhenThen(isBottom(source),
                        CypherArtifacts.bottomLiteral(b.type))),
                CypherArtifacts.taggedScalar(b.type, count));
    }

    /** includesAll/excludesAll quantify over support, not Bag multiplicity. */
    private CypherExpr realizeMembershipAll(CypherExpr left, CypherExpr right,
                                            boolean excludeAll) {
        CypherExpr leftItems = new PropertyAccess(left, CypherArtifacts.OCL_ITEMS);
        CypherExpr rightItems = new PropertyAccess(right, CypherArtifacts.OCL_ITEMS);
        String sought = fresh("membershipItem");
        CypherExpr contained = containsAtomic(leftItems, new VariableExpr(sought));
        CypherExpr predicate = excludeAll ? new UnaryExpr(UnaryOp.NOT, contained) : contained;
        CypherExpr quantified = new QuantifiedPredicateExpression(QuantifierKind.ALL,
                sought, rightItems, predicate);
        CypherExpr anyBottom = new BinaryExpr(BinaryOp.OR, isBottom(left), isBottom(right));
        return new CaseExpr(List.of(
                new WhenThen(anyBottom, CypherArtifacts.bottomLiteral(OclType.BOOLEAN))),
                CypherArtifacts.taggedScalar(OclType.BOOLEAN, quantified));
    }

    /** Set union/intersection with typed equality and strict whole-bottom propagation. */
    private CypherExpr realizeSetBinary(QNode.QExpr.Binary operation, CypherExpr left,
                                        CypherExpr right, boolean union) {
        CypherExpr leftItems = new PropertyAccess(left, CypherArtifacts.OCL_ITEMS);
        CypherExpr rightItems = new PropertyAccess(right, CypherArtifacts.OCL_ITEMS);
        CypherExpr items;
        if (union) {
            items = distinctAtomicItems(new BinaryExpr(BinaryOp.LIST_CONCAT,
                    leftItems, rightItems));
        } else {
            String member = fresh("intersectionItem");
            CypherExpr filtered = new ListComprehension(member, leftItems,
                    containsAtomic(rightItems, new VariableExpr(member)), null);
            items = distinctAtomicItems(filtered);
        }
        CypherExpr wholeBottom = new BinaryExpr(BinaryOp.OR,
                isBottom(left), isBottom(right));
        return CypherArtifacts.collectionTag(operation.type, items, wholeBottom);
    }

    private CypherExpr strictCompare(QNode.QExpr.Binary b, CypherExpr l, CypherExpr r) {
        CypherExpr anyBottom = new BinaryExpr(BinaryOp.OR, isBottom(l), isBottom(r));
        BinaryOp op = switch (b.operator) {
            case GREATER_THAN -> BinaryOp.GREATER_THAN;
            case GREATER_THAN_OR_EQUAL -> BinaryOp.GREATER_THAN_OR_EQUAL;
            case LESS_THAN -> BinaryOp.LESS_THAN;
            default -> BinaryOp.LESS_THAN_OR_EQUAL;
        };
        CypherExpr cmp = new BinaryExpr(op, payload(l), payload(r));
        return new CaseExpr(List.of(
                new WhenThen(anyBottom, CypherArtifacts.bottomLiteral(OclType.BOOLEAN))),
                CypherArtifacts.taggedScalar(OclType.BOOLEAN, cmp));
    }

    private CypherExpr totalEqual(CypherExpr l, CypherExpr r, OclType operandType,
                                  boolean negate) {
        CypherExpr bothBottom = new BinaryExpr(BinaryOp.AND, isBottom(l), isBottom(r));
        CypherExpr oneBottom = new BinaryExpr(BinaryOp.XOR, isBottom(l), isBottom(r));
        CypherExpr payloadEq = operandType.isCollection()
                ? collectionPayloadEqual(l, r, operandType)
                : atomicTaggedEqual(l, r);
        CypherExpr defined = negate ? new UnaryExpr(UnaryOp.NOT, payloadEq) : payloadEq;
        CypherExpr trueTag = CypherArtifacts.taggedScalar(OclType.BOOLEAN,
                new BooleanLiteral(!negate));
        CypherExpr falseTag = CypherArtifacts.taggedScalar(OclType.BOOLEAN,
                new BooleanLiteral(negate));
        return new CaseExpr(List.of(
                new WhenThen(bothBottom, trueTag),
                new WhenThen(oneBottom, falseTag)),
                CypherArtifacts.taggedScalar(OclType.BOOLEAN, defined));
    }

    /** Extensional Set equality and multiplicity-aware Bag equality. */
    private CypherExpr collectionPayloadEqual(CypherExpr left, CypherExpr right,
                                               OclType collectionType) {
        CypherExpr leftItems = new PropertyAccess(left, CypherArtifacts.OCL_ITEMS);
        CypherExpr rightItems = new PropertyAccess(right, CypherArtifacts.OCL_ITEMS);
        if (collectionType.kind() == OclType.Kind.SET) {
            String x = fresh("leftMember");
            String y = fresh("rightMember");
            CypherExpr leftSubset = new QuantifiedPredicateExpression(QuantifierKind.ALL,
                    x, leftItems, containsAtomic(rightItems, new VariableExpr(x)));
            CypherExpr rightSubset = new QuantifiedPredicateExpression(QuantifierKind.ALL,
                    y, rightItems, containsAtomic(leftItems, new VariableExpr(y)));
            return new BinaryExpr(BinaryOp.AND, leftSubset, rightSubset);
        }

        CypherExpr sameSize = new BinaryExpr(BinaryOp.EQUAL,
                new FunctionCall("size", false, List.of(leftItems)),
                new FunctionCall("size", false, List.of(rightItems)));
        String x = fresh("bagMember");
        CypherExpr sameMultiplicity = new BinaryExpr(BinaryOp.EQUAL,
                countAtomic(leftItems, new VariableExpr(x)),
                countAtomic(rightItems, new VariableExpr(x)));
        CypherExpr allMultiplicitiesEqual = new QuantifiedPredicateExpression(
                QuantifierKind.ALL, x, leftItems, sameMultiplicity);
        return new BinaryExpr(BinaryOp.AND, sameSize, allMultiplicitiesEqual);
    }

    private CypherExpr containsAtomic(CypherExpr items, CypherExpr sought) {
        String candidate = fresh("candidate");
        return new QuantifiedPredicateExpression(QuantifierKind.ANY, candidate, items,
                atomicTaggedEqual(new VariableExpr(candidate), sought));
    }

    private CypherExpr countAtomic(CypherExpr items, CypherExpr sought) {
        String candidate = fresh("candidate");
        CypherExpr matches = new ListComprehension(candidate, items,
                atomicTaggedEqual(new VariableExpr(candidate), sought), null);
        return new FunctionCall("size", false, List.of(matches));
    }

    /** Native Boolean equality for two tagged atomic values. */
    private CypherExpr atomicTaggedEqual(CypherExpr left, CypherExpr right) {
        CypherExpr sameType = new BinaryExpr(BinaryOp.EQUAL,
                new PropertyAccess(left, CypherArtifacts.OCL_TYPE),
                new PropertyAccess(right, CypherArtifacts.OCL_TYPE));
        CypherExpr bothBottom = new BinaryExpr(BinaryOp.AND, isBottom(left), isBottom(right));
        CypherExpr bothDefined = new BinaryExpr(BinaryOp.AND,
                new UnaryExpr(UnaryOp.NOT, isBottom(left)),
                new UnaryExpr(UnaryOp.NOT, isBottom(right)));
        CypherExpr samePayload = new BinaryExpr(BinaryOp.EQUAL, payload(left), payload(right));
        CypherExpr sameValueState = new BinaryExpr(BinaryOp.OR, bothBottom,
                new BinaryExpr(BinaryOp.AND, bothDefined, samePayload));
        return new BinaryExpr(BinaryOp.AND, sameType, sameValueState);
    }

    /**
     * Stable first-occurrence deduplication for the atomic element domain of
     * OCL_val.  Bag paths do not call this helper and therefore retain every
     * occurrence.
     */
    private CypherExpr distinctAtomicItems(CypherExpr items) {
        String acc = fresh("distinctAcc");
        String x = fresh("distinctItem");
        String y = fresh("existingItem");
        CypherExpr seen = new QuantifiedPredicateExpression(QuantifierKind.ANY, y,
                new VariableExpr(acc),
                atomicTaggedEqual(new VariableExpr(y), new VariableExpr(x)));
        CypherExpr append = new BinaryExpr(BinaryOp.LIST_CONCAT,
                new VariableExpr(acc), new ListExpr(List.of(new VariableExpr(x))));
        CypherExpr step = new CaseExpr(List.of(
                new WhenThen(seen, new VariableExpr(acc))), append);
        return new ReduceExpr(acc, new ListExpr(List.of()), x, items, step);
    }

    private CypherExpr boolean3(QNode.QExpr.Binary b, CypherExpr l, CypherExpr r) {
        return switch (b.operator) {
            case BOOLEAN_AND -> {
                // Kleene AND: false dominates, then true dominates, then bottom
                CypherExpr lFalse = new BinaryExpr(BinaryOp.AND,
                        new UnaryExpr(UnaryOp.NOT, isBottom(l)),
                        new BinaryExpr(BinaryOp.EQUAL, payload(l),
                                new BooleanLiteral(false)));
                CypherExpr rFalse = new BinaryExpr(BinaryOp.AND,
                        new UnaryExpr(UnaryOp.NOT, isBottom(r)),
                        new BinaryExpr(BinaryOp.EQUAL, payload(r),
                                new BooleanLiteral(false)));
                CypherExpr anyFalse = new BinaryExpr(BinaryOp.OR, lFalse, rFalse);
                CypherExpr anyB = new BinaryExpr(BinaryOp.OR, isBottom(l), isBottom(r));
                yield new CaseExpr(List.of(
                        new WhenThen(anyFalse, CypherArtifacts.taggedScalar(OclType.BOOLEAN,
                                new BooleanLiteral(false))),
                        new WhenThen(anyB, CypherArtifacts.bottomLiteral(OclType.BOOLEAN))),
                        CypherArtifacts.taggedScalar(OclType.BOOLEAN,
                                new BinaryExpr(BinaryOp.AND, payload(l), payload(r))));
            }
            case BOOLEAN_OR -> {
                CypherExpr lTrue = new BinaryExpr(BinaryOp.AND,
                        new UnaryExpr(UnaryOp.NOT, isBottom(l)),
                        new BinaryExpr(BinaryOp.EQUAL, payload(l), new BooleanLiteral(true)));
                CypherExpr rTrue = new BinaryExpr(BinaryOp.AND,
                        new UnaryExpr(UnaryOp.NOT, isBottom(r)),
                        new BinaryExpr(BinaryOp.EQUAL, payload(r), new BooleanLiteral(true)));
                CypherExpr anyTrue = new BinaryExpr(BinaryOp.OR, lTrue, rTrue);
                CypherExpr bothB = new BinaryExpr(BinaryOp.AND, isBottom(l), isBottom(r));
                yield new CaseExpr(List.of(
                        new WhenThen(anyTrue, CypherArtifacts.taggedScalar(OclType.BOOLEAN,
                                new BooleanLiteral(true))),
                        new WhenThen(bothB, CypherArtifacts.bottomLiteral(OclType.BOOLEAN)),
                        new WhenThen(new BinaryExpr(BinaryOp.OR, isBottom(l), isBottom(r)),
                                CypherArtifacts.bottomLiteral(OclType.BOOLEAN))),
                        CypherArtifacts.taggedScalar(OclType.BOOLEAN,
                                new BinaryExpr(BinaryOp.OR, payload(l), payload(r))));
            }
            case BOOLEAN_XOR -> {
                // xor bottom => bottom (XOR_B table)
                CypherExpr anyB = new BinaryExpr(BinaryOp.OR, isBottom(l), isBottom(r));
                CypherExpr pxor = new BinaryExpr(BinaryOp.XOR, payload(l), payload(r));
                yield new CaseExpr(List.of(
                        new WhenThen(anyB, CypherArtifacts.bottomLiteral(OclType.BOOLEAN))),
                        CypherArtifacts.taggedScalar(OclType.BOOLEAN, pxor));
            }
            case BOOLEAN_IMPLIES -> {
                // implies = not l or r (Kleene)
                CypherExpr lb = isBottom(l);
                CypherExpr rb = isBottom(r);
                CypherExpr lTrue = new BinaryExpr(BinaryOp.AND,
                        new UnaryExpr(UnaryOp.NOT, isBottom(l)),
                        new BinaryExpr(BinaryOp.EQUAL, payload(l), new BooleanLiteral(true)));
                CypherExpr rFalse = new BinaryExpr(BinaryOp.AND,
                        new UnaryExpr(UnaryOp.NOT, isBottom(r)),
                        new BinaryExpr(BinaryOp.EQUAL, payload(r), new BooleanLiteral(false)));
                CypherExpr lFalse = new BinaryExpr(BinaryOp.AND,
                        new UnaryExpr(UnaryOp.NOT, lb),
                        new BinaryExpr(BinaryOp.EQUAL, payload(l), new BooleanLiteral(false)));
                CypherExpr lBottomRTrue = new BinaryExpr(BinaryOp.AND, lb,
                        new BinaryExpr(BinaryOp.AND, new UnaryExpr(UnaryOp.NOT, rb),
                                new BinaryExpr(BinaryOp.EQUAL, payload(r), new BooleanLiteral(true))));
                CypherExpr lBottomRFalse = new BinaryExpr(BinaryOp.AND, lb, rFalse);
                CypherExpr rTrue = new BinaryExpr(BinaryOp.AND,
                        new UnaryExpr(UnaryOp.NOT, rb),
                        new BinaryExpr(BinaryOp.EQUAL, payload(r), new BooleanLiteral(true)));
                yield new CaseExpr(List.of(
                        // false implies anything = true; true implies true = true — fall through
                        new WhenThen(lFalse,
                                CypherArtifacts.taggedScalar(OclType.BOOLEAN, new BooleanLiteral(true))),
                        new WhenThen(lBottomRTrue,
                                CypherArtifacts.taggedScalar(OclType.BOOLEAN, new BooleanLiteral(true))),
                        new WhenThen(lBottomRFalse,
                                CypherArtifacts.bottomLiteral(OclType.BOOLEAN)),
                        new WhenThen(lTrue,
                                new CaseExpr(List.of(
                                        new WhenThen(rFalse,
                                                CypherArtifacts.taggedScalar(OclType.BOOLEAN, new BooleanLiteral(false))),
                                        new WhenThen(rTrue,
                                                CypherArtifacts.taggedScalar(OclType.BOOLEAN, new BooleanLiteral(true)))),
                                        CypherArtifacts.bottomLiteral(OclType.BOOLEAN))),
                        new WhenThen(rb, CypherArtifacts.bottomLiteral(OclType.BOOLEAN))),
                        CypherArtifacts.taggedScalar(OclType.BOOLEAN, new BooleanLiteral(true)));
            }
            default -> throw new RealizeError("R_UNCOVERED_CONSTRUCTOR",
                    "Boolean3 connective " + b.operator + " is future work");
        };
    }

    private CypherExpr realizeUnary(QNode.QExpr.Unary u, Ctx ctx) {
        CypherExpr realizedOperand = realizeExpr(u.operand, ctx);
        return bindOnce("unaryOperand", realizedOperand, op -> switch (u.operator) {
            case COLLECTION_SIZE -> {
                CypherExpr items = new PropertyAccess(op, CypherArtifacts.OCL_ITEMS);
                CypherExpr sz = new FunctionCall("size", false, List.of(items));
                yield new CaseExpr(List.of(
                        new WhenThen(isBottom(op), CypherArtifacts.bottomLiteral(OclType.INTEGER))),
                        CypherArtifacts.taggedScalar(OclType.INTEGER, sz));
            }
            case COLLECTION_IS_EMPTY, COLLECTION_NOT_EMPTY -> {
                CypherExpr items = new PropertyAccess(op, CypherArtifacts.OCL_ITEMS);
                CypherExpr szEq0 = new BinaryExpr(BinaryOp.EQUAL,
                        new FunctionCall("size", false, List.of(items)),
                        new IntegerLiteral(java.math.BigInteger.ZERO));
                CypherExpr cond = u.operator == org.uet.dse.ocl2cypher.core.CoreExpr.UnaryOp.COLLECTION_IS_EMPTY
                        ? szEq0
                        : new BinaryExpr(BinaryOp.GREATER_THAN,
                                new FunctionCall("size", false, List.of(items)),
                                new IntegerLiteral(java.math.BigInteger.ZERO));
                yield new CaseExpr(List.of(
                        new WhenThen(isBottom(op), CypherArtifacts.bottomLiteral(OclType.BOOLEAN))),
                        CypherArtifacts.taggedScalar(OclType.BOOLEAN, cond));
            }
            case COLLECTION_SUM -> {
                // sum of Items(op); each item is a tagged numeric map
                CypherExpr items = new PropertyAccess(op, CypherArtifacts.OCL_ITEMS);
                String x = fresh("sumItem");
                String acc = fresh("sumAcc");
                CypherExpr anyBottom = new QuantifiedPredicateExpression(
                        QuantifierKind.ANY, x, items,
                        new BinaryExpr(BinaryOp.EQUAL,
                                new PropertyAccess(new VariableExpr(x),
                                        CypherArtifacts.OCL_BOTTOM),
                                new BooleanLiteral(true)));
                CypherExpr initial = u.type.equals(OclType.REAL)
                        ? new FloatLiteral(java.math.BigDecimal.ZERO)
                        : new IntegerLiteral(java.math.BigInteger.ZERO);
                CypherExpr step = new BinaryExpr(BinaryOp.ADD,
                        new VariableExpr(acc),
                        new PropertyAccess(new VariableExpr(x), CypherArtifacts.OCL_VALUE));
                CypherExpr sum = new ReduceExpr(acc, initial, x, items, step);
                yield new CaseExpr(List.of(
                        new WhenThen(new BinaryExpr(BinaryOp.OR, isBottom(op), anyBottom),
                                CypherArtifacts.bottomLiteral(u.type))),
                        CypherArtifacts.taggedScalar(u.type, sum));
            }
            case BOOLEAN_NOT -> {
                CypherExpr payload = payload(op);
                CypherExpr neg = new UnaryExpr(UnaryOp.NOT, payload);
                yield new CaseExpr(List.of(
                        new WhenThen(isBottom(op), CypherArtifacts.bottomLiteral(OclType.BOOLEAN))),
                        CypherArtifacts.taggedScalar(OclType.BOOLEAN, neg));
            }
            case NUMERIC_NEGATE -> new CaseExpr(List.of(
                    new WhenThen(isBottom(op), CypherArtifacts.bottomLiteral(u.type))),
                    CypherArtifacts.taggedScalar(u.type,
                            new UnaryExpr(UnaryOp.NEGATE, payload(op))));
            case NUMERIC_ABS -> new CaseExpr(List.of(
                    new WhenThen(isBottom(op), CypherArtifacts.bottomLiteral(u.type))),
                    CypherArtifacts.taggedScalar(u.type,
                            new FunctionCall("abs", false, List.of(payload(op)))));
            case REAL_FLOOR -> {
                CypherExpr floored = new FunctionCall("toInteger", false, List.of(
                        new FunctionCall("floor", false, List.of(payload(op)))));
                yield new CaseExpr(List.of(
                        new WhenThen(isBottom(op),
                                CypherArtifacts.bottomLiteral(OclType.INTEGER))),
                        CypherArtifacts.taggedScalar(OclType.INTEGER, floored));
            }
            case REAL_ROUND -> {
                CypherExpr shifted = new BinaryExpr(BinaryOp.ADD, payload(op),
                        new FloatLiteral(new java.math.BigDecimal("0.5")));
                CypherExpr rounded = new FunctionCall("toInteger", false, List.of(
                        new FunctionCall("floor", false, List.of(shifted))));
                yield new CaseExpr(List.of(
                        new WhenThen(isBottom(op),
                                CypherArtifacts.bottomLiteral(OclType.INTEGER))),
                        CypherArtifacts.taggedScalar(OclType.INTEGER, rounded));
            }
            default -> throw new RealizeError("R_UNCOVERED_CONSTRUCTOR",
                    "unary " + u.operator + " is future work");
        });
    }

    // ---- helpers ---------------------------------------------------------

    private CypherExpr decodeScalar(OclType t, CypherExpr stored) {
        // Codec ocl-integer-decimal-v1 stores BigInteger.toString() as decimal text.
        // The attribute-specific certificate checks all concrete payloads before
        // Cypher toInteger() is allowed to parse them into signed INT64.
        if (t.equals(OclType.INTEGER)) {
            return new FunctionCall("toInteger", false, List.of(stored));
        }
        // This branch is reachable only after requireExactBinary64 succeeds;
        // finite-decimal storage alone is deliberately insufficient.
        if (t.equals(OclType.REAL)) {
            return new FunctionCall("toFloat", false, List.of(stored));
        }
        if (t.equals(OclType.BOOLEAN)) {
            return new BinaryExpr(BinaryOp.EQUAL, stored, new StringLiteral("true"));
        }
        return stored;
    }

    /** Discharge the stored-decimal premise for the concrete attribute read. */
    private void requireStoredIntegerInt64(QNode.QExpr.ReadAttribute attribute) {
        String attributeKey = attribute.ownerClassKey + "::" + attribute.attributeName;
        for (GraphModel.Node node : graph.nodes()) {
            if (!"ATTRIBUTE_VALUE".equals(node.observationRole())
                    || !attributeKey.equals(node.properties().get("attributeKey"))
                    || !GraphValueCodec.DEFINED.equals(
                            node.properties().get(GraphValueCodec.VALUE_STATE))) {
                continue;
            }
            String payload = node.properties().get(GraphValueCodec.PAYLOAD);
            try {
                BigInteger value = new BigInteger(payload);
                if (value.compareTo(IntegerRangeCertificates.MIN) < 0
                        || value.compareTo(IntegerRangeCertificates.MAX) > 0) {
                    throw numericCapability("stored Integer attribute " + attributeKey
                            + " contains a value outside signed INT64");
                }
            } catch (NumberFormatException malformed) {
                throw numericCapability("stored Integer attribute " + attributeKey
                        + " has a non-canonical payload");
            }
        }
    }

    /** Require equality between the exact OCL decimal and its binary64 carrier. */
    private void requireExactStoredReal(QNode.QExpr.ReadAttribute attribute) {
        String attributeKey = attribute.ownerClassKey + "::" + attribute.attributeName;
        for (GraphModel.Node node : graph.nodes()) {
            if (!"ATTRIBUTE_VALUE".equals(node.observationRole())
                    || !attributeKey.equals(node.properties().get("attributeKey"))
                    || !GraphValueCodec.DEFINED.equals(
                            node.properties().get(GraphValueCodec.VALUE_STATE))) {
                continue;
            }
            String payload = node.properties().get(GraphValueCodec.PAYLOAD);
            try {
                requireExactBinary64(new BigDecimal(payload),
                        "stored Real attribute " + attributeKey);
            } catch (NumberFormatException malformed) {
                throw new RealizeError(RuleId.R_REAL_EXACT_UNSUPPORTED,
                        "stored Real attribute has an invalid decimal payload: "
                                + attributeKey);
            }
        }
    }

    private static void requireExactBinary64(BigDecimal value, String origin) {
        double binary = value.doubleValue();
        if (!Double.isFinite(binary) || value.compareTo(new BigDecimal(binary)) != 0) {
            throw new RealizeError(RuleId.R_REAL_EXACT_UNSUPPORTED,
                    origin + " is not exactly representable as IEEE-754 binary64");
        }
    }

    private static RealizeError numericCapability(String operation) {
        return new RealizeError("R_NUMERIC_CAPABILITY",
                "No exact native range/precision certificate for " + operation);
    }

    private CypherExpr taggedObject(String alias, OclType type) {
        return new MapExpr(List.of(
                new MapEntry(CypherArtifacts.OCL_BOTTOM, new BooleanLiteral(false)),
                new MapEntry(CypherArtifacts.OCL_TYPE,
                        new StringLiteral(CypherArtifacts.typeTag(type))),
                new MapEntry(CypherArtifacts.OCL_VALUE,
                        new PropertyAccess(new VariableExpr(alias), "use_id"))));
    }

    private CypherExpr isBottom(CypherExpr e) {
        return new BinaryExpr(BinaryOp.EQUAL,
                new PropertyAccess(e, CypherArtifacts.OCL_BOTTOM), new BooleanLiteral(true));
    }

    /** Build a Boolean OR expression without relying on host-language null semantics. */
    private CypherExpr or(CypherExpr left, CypherExpr right) {
        return new BinaryExpr(BinaryOp.OR, left, right);
    }

    /** Count declaration-identity uses, saturated at two for the let policy. */
    private static int referenceCount(QNode.QExpr expression, CoreDeclaration declaration) {
        if (expression instanceof QNode.QExpr.Variable variable) {
            return variable.declaration == declaration ? 1 : 0;
        }
        if (expression instanceof QNode.QExpr.Parameter
                || expression instanceof QNode.QExpr.Bottom
                || expression instanceof QNode.QExpr.Constant) {
            return 0;
        }
        if (expression instanceof QNode.QExpr.Coerce coerce) {
            return referenceCount(coerce.source, declaration);
        }
        if (expression instanceof QNode.QExpr.Let let) {
            return uses(referenceCount(let.value, declaration),
                    referenceCount(let.body, declaration));
        }
        if (expression instanceof QNode.QExpr.IfExpr conditional) {
            return uses(referenceCount(conditional.condition, declaration),
                    referenceCount(conditional.thenExpr, declaration),
                    referenceCount(conditional.elseExpr, declaration));
        }
        if (expression instanceof QNode.QExpr.ReadAttribute attribute) {
            return referenceCount(attribute.source, declaration);
        }
        if (expression instanceof QNode.QExpr.NavigateOne navigation) {
            return uses(referenceCount(navigation.source, declaration),
                    referenceCount(navigation.qualifiers, declaration));
        }
        if (expression instanceof QNode.QExpr.TypeTest test) {
            return referenceCount(test.source, declaration);
        }
        if (expression instanceof QNode.QExpr.TypeCast cast) {
            return referenceCount(cast.source, declaration);
        }
        if (expression instanceof QNode.QExpr.Unary unary) {
            return referenceCount(unary.operand, declaration);
        }
        if (expression instanceof QNode.QExpr.Binary binary) {
            return uses(referenceCount(binary.left, declaration),
                    referenceCount(binary.right, declaration));
        }
        if (expression instanceof QNode.QExpr.Exists3 exists) {
            return uses(referenceCount(exists.source, declaration),
                    referenceCount(exists.predicate, declaration));
        }
        if (expression instanceof QNode.QExpr.ForAll3 forAll) {
            return uses(referenceCount(forAll.source, declaration),
                    referenceCount(forAll.predicate, declaration));
        }
        if (expression instanceof QNode.QExpr.CollectionLiteral collection) {
            return referenceCount(collection.elements, declaration);
        }
        if (expression instanceof QNode.QExpr.IncludesFamily includes) {
            return uses(referenceCount(includes.source, declaration),
                    referenceCount(includes.element, declaration));
        }
        if (expression instanceof QNode.QExpr.CountFamily count) {
            return uses(referenceCount(count.source, declaration),
                    count.element == null ? 0 : referenceCount(count.element, declaration));
        }
        if (expression instanceof QNode.QExpr.SetAlgebra algebra) {
            return uses(referenceCount(algebra.left, declaration),
                    referenceCount(algebra.right, declaration));
        }
        if (expression instanceof QNode.QExpr.Materialize materialize) {
            return referenceCount(materialize.plan, declaration);
        }
        throw new IllegalStateException("uncovered Q expression in reference count: "
                + expression.getClass().getSimpleName());
    }

    private static int referenceCount(QNode.QPlan plan, CoreDeclaration declaration) {
        if (plan instanceof QNode.QPlan.FromCollection from) {
            return referenceCount(from.collection, declaration);
        }
        if (plan instanceof QNode.QPlan.ScanClass) {
            return 0;
        }
        if (plan instanceof QNode.QPlan.NavigateMany navigation) {
            return uses(referenceCount(navigation.source, declaration),
                    referenceCount(navigation.qualifiers, declaration));
        }
        if (plan instanceof QNode.QPlan.Filter filter) {
            return uses(referenceCount(filter.source, declaration),
                    referenceCount(filter.predicate, declaration));
        }
        if (plan instanceof QNode.QPlan.Collect collect) {
            return uses(referenceCount(collect.source, declaration),
                    referenceCount(collect.body, declaration));
        }
        if (plan instanceof QNode.QPlan.Distinct distinct) {
            return referenceCount(distinct.source, declaration);
        }
        if (plan instanceof QNode.QPlan.PlanLet let) {
            return uses(referenceCount(let.value, declaration),
                    referenceCount(let.body, declaration));
        }
        throw new IllegalStateException("uncovered Q plan in reference count: "
                + plan.getClass().getSimpleName());
    }

    private static int referenceCount(List<QNode.QExpr> expressions,
                                      CoreDeclaration declaration) {
        int count = 0;
        for (QNode.QExpr expression : expressions) {
            count = uses(count, referenceCount(expression, declaration));
            if (count == 2) return count;
        }
        return count;
    }

    private static int uses(int... counts) {
        int total = 0;
        for (int count : counts) {
            total += count;
            if (total >= 2) return 2;
        }
        return total;
    }

    /**
     * Scope-safe scalar binding encoded only with admitted Cypher syntax:
     * {@code head([x IN [value] | body])}.  The value occurs exactly once in
     * the serialized query; repeated observations in the body read {@code x}.
     */
    private CypherExpr bindOnce(String alias, CypherExpr value, CypherExpr body) {
        return new FunctionCall("head", false, List.of(
                new ListComprehension(alias, new ListExpr(List.of(value)), null, body)));
    }

    private CypherExpr bindOnce(String base, CypherExpr value,
                                java.util.function.Function<CypherExpr, CypherExpr> body) {
        CypherExpr unwrapped = value instanceof LocatedExpr located
                ? located.expression() : value;
        if (unwrapped instanceof VariableExpr || unwrapped instanceof ParameterExpr
                || unwrapped instanceof NullLiteral || unwrapped instanceof BooleanLiteral
                || unwrapped instanceof IntegerLiteral || unwrapped instanceof FloatLiteral
                || unwrapped instanceof StringLiteral) {
            return body.apply(value);
        }
        String alias = fresh(base);
        return bindOnce(alias, value, body.apply(new VariableExpr(alias)));
    }

    private CypherExpr bindPair(String leftBase, CypherExpr left,
                                String rightBase, CypherExpr right,
                                java.util.function.BiFunction<CypherExpr, CypherExpr,
                                        CypherExpr> body) {
        return bindOnce(leftBase, left, leftRef ->
                bindOnce(rightBase, right, rightRef -> body.apply(leftRef, rightRef)));
    }

    /** One generated parameter replaces every repeated model-key literal. */
    private CypherExpr modelKeyExpr() {
        if (modelKeyParameter == null) {
            modelKeyParameter = "__oclModelKey";
            addParam(modelKeyParameter, "Physical:ModelKey",
                    QueryParameter.Origin.GENERATED, graph.modelKey());
        }
        return new ParameterExpr(modelKeyParameter);
    }

    private List<PropertyMapEntry> modelProperty() {
        return List.of(new PropertyMapEntry("modelKey", modelKeyExpr()));
    }

    private List<PropertyMapEntry> withModel(PropertyMapEntry extra) {
        List<PropertyMapEntry> properties = new ArrayList<>();
        properties.add(new PropertyMapEntry("modelKey", modelKeyExpr()));
        properties.add(extra);
        return properties;
    }


    private CypherExpr payload(CypherExpr e) {
        return new PropertyAccess(e, CypherArtifacts.OCL_VALUE);
    }

    private CypherExpr literal(QNode.QExpr.Constant c) {
        Object v = c.literalValue;
        return switch (c.type.kind()) {
            case BOOLEAN -> {
                if (!(v instanceof Boolean value)) {
                    throw uncoveredConstant(c);
                }
                yield new BooleanLiteral(value);
            }
            case INTEGER -> {
                if (!(v instanceof java.math.BigInteger value)) {
                    throw uncoveredConstant(c);
                }
                yield new IntegerLiteral(value);
            }
            case REAL -> {
                if (!(v instanceof java.math.BigDecimal value)) {
                    throw uncoveredConstant(c);
                }
                yield new FloatLiteral(value);
            }
            case STRING -> {
                if (!(v instanceof String value)) {
                    throw uncoveredConstant(c);
                }
                yield new StringLiteral(value);
            }
            case CLASS -> {
                if (!(v instanceof String stableId) || stableId.isEmpty()) {
                    throw uncoveredConstant(c);
                }
                yield new StringLiteral(stableId);
            }
            case SET, BAG -> throw uncoveredConstant(c);
        };
    }

    private RealizeError uncoveredConstant(QNode.QExpr.Constant constant) {
        return new RealizeError("R_UNCOVERED_CONSTRUCTOR",
                "constant carrier does not match " + constant.type);
    }

    private void addParam(String name, String tag, QueryParameter.Origin origin, String value) {
        for (QueryParameter p : parameters) {
            if (p.name().equals(name)) {
                if (!p.logicalTypeTag().equals(tag) || p.origin() != origin
                        || !java.util.Objects.equals(p.canonicalValue(), value)) {
                    throw new RealizeError("R_PARAMETER_COLLISION",
                            "incompatible parameter declarations share name " + name);
                }
                return;
            }
        }
        parameters.add(new QueryParameter(name, tag, origin, value));
    }

    private List<QueryParameter> sortedParameters() {
        return parameters.stream()
                .sorted(java.util.Comparator.comparing(QueryParameter::name))
                .toList();
    }

    private String fresh(String base) {
        return aliases.fresh(base);
    }

    /** Scope of tagged values keyed by declaration identity, never surface name. */
    private static final class Binding {
        final CypherExpr value;
        final OclType type;

        Binding(CypherExpr value, OclType type) {
            this.value = value;
            this.type = type;
        }
    }

    private static final class Ctx {
        private final java.util.Map<org.uet.dse.ocl2cypher.core.CoreDeclaration, Binding> scope
                = new java.util.IdentityHashMap<>();

        void bind(org.uet.dse.ocl2cypher.core.CoreDeclaration declaration,
                  CypherExpr value, OclType type) {
            if (scope.containsKey(declaration)) {
                throw new RealizeError("R_SCOPE",
                        "declaration identity is already live: " + declaration.name());
            }
            scope.put(declaration, new Binding(value, type));
        }

        Binding lookup(org.uet.dse.ocl2cypher.core.CoreDeclaration declaration) {
            return scope.get(declaration);
        }

        Ctx child() {
            Ctx c = new Ctx();
            c.scope.putAll(scope);
            return c;
        }
    }

    private static final class RealizeError extends RuntimeException {
        final String code;

        RealizeError(String code, String msg) {
            super(msg, null, false, false);
            this.code = code;
        }
    }
}
