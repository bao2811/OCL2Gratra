package org.uet.dse.neo4jtgg.service.impl;

import java.util.List;
import java.util.Map;

import org.tzi.use.uml.mm.MModel;
import org.uet.dse.neo4j.oclite.ast.ASTBinary;
import org.uet.dse.neo4j.oclite.ast.ASTCollectionOp;
import org.uet.dse.neo4j.oclite.ast.ASTFile;
import org.uet.dse.neo4j.oclite.ast.ASTContext;
import org.uet.dse.neo4j.oclite.ast.ASTExpression;
import org.uet.dse.neo4j.oclite.ast.ASTIf;
import org.uet.dse.neo4j.oclite.ast.ASTIterator;
import org.uet.dse.neo4j.oclite.ast.ASTLet;
import org.uet.dse.neo4j.oclite.ast.ASTMethodCall;
import org.uet.dse.neo4j.oclite.ast.ASTNot;
import org.uet.dse.neo4j.oclite.ast.ASTOperationConstraint;
import org.uet.dse.neo4j.oclite.ast.ASTProperty;
import org.uet.dse.neo4j.oclite.ast.ASTVar;
import org.uet.dse.neo4jtgg.model.CypherCompilationResult;
import org.uet.dse.neo4jtgg.model.OclFileCompilationResult;
import org.uet.dse.neo4jtgg.model.OclResultLocation;
import org.uet.dse.neo4jtgg.model.OclRuleDescriptor;
import org.uet.dse.neo4jtgg.model.OclRuleCompilationResult;
import org.uet.dse.neo4jtgg.model.OclRuleKind;
import org.uet.dse.neo4jtgg.model.OclRuleOwnerKind;
import org.uet.dse.neo4jtgg.ocl.OclMetamodelIndex;
import org.uet.dse.neo4jtgg.ocl.OclMetamodelSnapshot;
import org.uet.dse.neo4jtgg.ocl.OclSemanticBinder;
import org.uet.dse.neo4jtgg.ocl.OclValAdmissionPolicy;
import org.uet.dse.neo4jtgg.ocl.OclValBoundAdmissionPolicy;
import org.uet.dse.neo4jtgg.ocl.diagnostic.OclCodedUnsupportedOperationException;
import org.uet.dse.neo4jtgg.ocl.diagnostic.OclCompilationException;
import org.uet.dse.neo4jtgg.ocl.diagnostic.OclDiagnostic;
import org.uet.dse.neo4jtgg.ocl.diagnostic.OclDiagnosticCode;
import org.uet.dse.neo4jtgg.ocl.diagnostic.OclDiagnosticPhase;
import org.uet.dse.neo4jtgg.ocl.ir.OclCypherPlanner;
import org.uet.dse.neo4jtgg.ocl.ir.OclCypherRenderer;
import org.uet.dse.neo4jtgg.ocl.ir.OclIr;
import org.uet.dse.neo4jtgg.ocl.ir.OclIrBuilder;
import org.uet.dse.neo4jtgg.ocl.ir.OclIrOptimizer;
import org.uet.dse.neo4jtgg.service.OclToCypherCompiler;
import org.uet.dse.neo4jtgg.experiment.InstrumentedCompilationResult;
import org.uet.dse.neo4jtgg.experiment.PipelineStageTimings;

public class DefaultOclToCypherCompiler implements OclToCypherCompiler {

    private final OclMetamodelIndex metamodelIndex;
    private final OclSemanticBinder binder;
    private final OclSemanticBinder certifiedBinder;
    private final OclIrBuilder irBuilder;
    private final OclIrOptimizer irOptimizer;
    private final OclCypherPlanner cypherPlanner;
    private final OclCypherRenderer cypherRenderer;

    public DefaultOclToCypherCompiler(MModel model) {
        this.metamodelIndex = new OclMetamodelIndex(model);
        this.binder = new OclSemanticBinder(metamodelIndex);
        this.certifiedBinder = this.binder.forCertifiedProfile();
        this.irBuilder = new OclIrBuilder();
        this.irOptimizer = new OclIrOptimizer();
        this.cypherPlanner = new OclCypherPlanner();
        this.cypherRenderer = new OclCypherRenderer(model.name());
    }

    DefaultOclToCypherCompiler(OclMetamodelIndex metamodelIndex,
            OclSemanticBinder binder,
            OclIrBuilder irBuilder,
            OclIrOptimizer irOptimizer,
            OclCypherPlanner cypherPlanner,
            OclCypherRenderer cypherRenderer) {
        this.metamodelIndex = metamodelIndex;
        this.binder = binder;
        this.certifiedBinder = binder.forCertifiedProfile();
        this.irBuilder = irBuilder;
        this.irOptimizer = irOptimizer;
        this.cypherPlanner = cypherPlanner;
        this.cypherRenderer = cypherRenderer;
    }

    @Override
    public CypherCompilationResult compile(String oclExpression) {
        OclFileCompilationResult fileResult = compileFile(oclExpression);
        if (!fileResult.getRuleResults().isEmpty()) {
            return fileResult.getRuleResults().get(0).getCompilation();
        }
        if (!fileResult.getDocumentDiagnostics().isEmpty()) {
            OclDiagnostic primary = fileResult.getDocumentDiagnostics().get(0);
            return new CypherCompilationResult(false, "", Map.of(), primary.toUserMessage(), false,
                    fileResult.getDocumentDiagnostics());
        }
        return unsupported(new OclDiagnostic(OclDiagnosticPhase.SEMANTIC, OclDiagnosticCode.UNSUPPORTED_AST_NODE,
                "Only context invariants are compiled to Cypher in v1.", null, null, null, null));
    }

    @Override
    public OclFileCompilationResult compileFile(String oclText) {
        long responseStartedAt = System.nanoTime();
        long parseStartedAt = System.nanoTime();
        try {
            ASTFile astFile = OclDocumentParser.parse(oclText);
            long parseTimeMs = elapsedMillis(parseStartedAt);
            long compileStartedAt = System.nanoTime();
            OclFileCompilationResult result = compileFile(astFile);
            long compileTimeMs = elapsedMillis(compileStartedAt);
            return new OclFileCompilationResult("document",
                    result.getRuleResults(),
                    result.getDocumentDiagnostics(),
                    result.getFreeExpressionCount(),
                    elapsedMillis(responseStartedAt),
                    parseTimeMs,
                    compileTimeMs);
        } catch (OclCompilationException ex) {
            return new OclFileCompilationResult("document", List.of(), buildDiagnostics(ex), 0,
                    elapsedMillis(responseStartedAt), elapsedMillis(parseStartedAt), 0L);
        } catch (Exception ex) {
            OclDiagnostic diagnostic = new OclDiagnostic(OclDiagnosticPhase.RENDERING, OclDiagnosticCode.GENERIC_FAILURE,
                    "INTERNAL: Compilation failed: " + ex.getMessage(), null, null, null, null);
            return new OclFileCompilationResult("document", List.of(), List.of(diagnostic), 0,
                    elapsedMillis(responseStartedAt), elapsedMillis(parseStartedAt), 0L);
        }
    }

    OclFileCompilationResult compileFile(ASTFile astFile) {
        return compileFile(astFile, false);
    }

    /**
     * Compiles class invariants through the exact certified T1--T6 admission
     * path while retaining the general compiler for explicitly non-theorem
     * rule kinds such as operation contracts and free expressions.
     *
     * <p>A context invariant rejected by OCL_val admission is returned as an
     * unsupported rule and may be handled by an explicitly labelled fallback;
     * it is never emitted as compiled Cypher carrying the theorem claim.</p>
     */
    OclFileCompilationResult compileFileWithCertifiedContextInvariants(ASTFile astFile) {
        return compileFile(astFile, true);
    }

    private OclFileCompilationResult compileFile(ASTFile astFile, boolean certifyContextInvariants) {
        List<OclRuleCompilationResult> results = new java.util.ArrayList<>();
        List<OclDiagnostic> documentDiagnostics = new java.util.ArrayList<>();
        List<OclRuleDescriptor> rules = OclRuleExtractor.extractRules(astFile);
        for (OclRuleDescriptor rule : rules) {
            long ruleStartedAt = System.nanoTime();
            CypherCompilationResult compilation;
            if (rule.ast() instanceof ASTContext context) {
                compilation = certifyContextInvariants
                        ? compileCertifiedContext(context)
                        : compileContext(context);
            } else if (rule.ast() instanceof ASTOperationConstraint operationConstraint
                    && (rule.ruleKind() == OclRuleKind.PRE || rule.ruleKind() == OclRuleKind.POST)) {
                compilation = compileOperationConstraint(operationConstraint);
            } else if (rule.expression() != null && rule.className() == null) {
                compilation = compileTopLevelExpression(rule.expression());
            } else {
                compilation = unsupportedRuleKind(rule);
            }
            results.add(new OclRuleCompilationResult(
                    rule.ownerKind(),
                    rule.ruleKind(),
                    rule.className(),
                    rule.operationName(),
                    rule.attributeName(),
                    rule.ruleName(),
                    compilation,
                    elapsedMillis(ruleStartedAt),
                    inferResultLocation(rule.className(), rule.ruleName(), compilation),
                    inferRequiredInputs(rule)));
        }
        return new OclFileCompilationResult(results, documentDiagnostics, astFile.freeExpressions().size());
    }

    private CypherCompilationResult compileCertifiedContext(ASTContext context) {
        try {
            InstrumentedCompilationResult certified = compileInvariantInstrumented(context);
            return new CypherCompilationResult(true, certified.cypher(), certified.parameters(), "", true);
        } catch (OclCompilationException ex) {
            return unsupported(ex);
        } catch (OclCodedUnsupportedOperationException ex) {
            return unsupported(new OclDiagnostic(
                    OclDiagnosticPhase.SEMANTIC, ex.code(), ex.getMessage(),
                    null, null, null, null));
        } catch (UnsupportedOperationException ex) {
            return unsupported(new OclDiagnostic(
                    OclDiagnosticPhase.SEMANTIC, classifySemanticCode(ex.getMessage()), ex.getMessage(),
                    null, null, null, null));
        } catch (Exception ex) {
            return unsupported(new OclDiagnostic(
                    OclDiagnosticPhase.RENDERING, OclDiagnosticCode.GENERIC_FAILURE,
                    "INTERNAL: Certified compilation failed: " + ex.getMessage(),
                    null, null, null, null));
        }
    }

    /** Builds the exact same binder pipeline from an M2 view read from the graph. */
    public DefaultOclToCypherCompiler(OclMetamodelSnapshot graphBackedMetamodel) {
        this(graphBackedMetamodel.toUseModel());
    }

    /** Compiles one context invariant while retaining every research artifact. */
    public InstrumentedCompilationResult compileInvariantInstrumented(String oclText) {
        long parseStart = System.nanoTime();
        ASTFile file = OclDocumentParser.parse(oclText);
        long parseNs = System.nanoTime() - parseStart;
        if (file.invariants().size() != 1 || file.elements().size() != 1) {
            throw new IllegalArgumentException("Instrumented compilation requires exactly one context invariant.");
        }
        return compileInvariantInstrumented(file.invariants().get(0), parseNs);
    }

    /** Compiles every context invariant in one OCL research suite. */
    public List<InstrumentedCompilationResult> compileInvariantsInstrumented(String oclText) {
        long parseStart = System.nanoTime();
        ASTFile file = OclDocumentParser.parse(oclText);
        long parseNs = System.nanoTime() - parseStart;
        if (file.invariants().isEmpty()) {
            throw new IllegalArgumentException("Research suite requires at least one context invariant.");
        }
        long allocatedParseNs = parseNs / file.invariants().size();
        List<InstrumentedCompilationResult> results = new java.util.ArrayList<>();
        for (ASTContext invariant : file.invariants()) {
            results.add(compileInvariantInstrumented(invariant, allocatedParseNs));
        }
        return List.copyOf(results);
    }

    /** Parses the invariant suite once so callers can isolate per-rule failures. */
    public List<ASTContext> parseContextInvariants(String oclText) {
        ASTFile file = OclDocumentParser.parse(oclText);
        if (file.invariants().isEmpty()) {
            throw new IllegalArgumentException("Research suite requires at least one context invariant.");
        }
        return List.copyOf(file.invariants());
    }

    /** Compiles an already parsed invariant; parsing time is intentionally zero. */
    public InstrumentedCompilationResult compileInvariantInstrumented(ASTContext invariant) {
        if (invariant == null) throw new IllegalArgumentException("Invariant is required.");
        return compileInvariantInstrumented(invariant, 0L);
    }

    private InstrumentedCompilationResult compileInvariantInstrumented(ASTContext ast, long parseNs) {

        OclValAdmissionPolicy.verify(ast);

        long bindStart = System.nanoTime();
        OclSemanticBinder.BoundContextInvariant bound = certifiedBinder.bindContext(ast);
        OclValBoundAdmissionPolicy.verify(bound);
        long bindNs = System.nanoTime() - bindStart;

        long vaStart = System.nanoTime();
        OclIr.InvariantQuery va = irBuilder.buildInvariant(bound);
        long vaNs = System.nanoTime() - vaStart;

        long normalizeStart = System.nanoTime();
        OclIr.InvariantQuery normalized = irOptimizer.optimizeInvariant(va);
        long normalizeNs = System.nanoTime() - normalizeStart;

        long planStart = System.nanoTime();
        org.uet.dse.neo4jtgg.ocl.ir.OclCypherPlan.InvariantPlan plan = cypherPlanner.planInvariant(normalized);
        long planNs = System.nanoTime() - planStart;

        long renderStart = System.nanoTime();
        OclCypherRenderer.RenderedInvariant rendered = cypherRenderer.renderInvariant(plan);
        long renderNs = System.nanoTime() - renderStart;

        return new InstrumentedCompilationResult(ast, bound, va, normalized, plan,
                rendered.cypher(), rendered.parameters(),
                new PipelineStageTimings(parseNs, bindNs, vaNs, normalizeNs, planNs, renderNs));
    }

    private CypherCompilationResult compileTopLevelExpression(ASTExpression expression) {
        try {
            OclSemanticBinder.BoundExpression boundExpression = binder.bind(expression, new OclSemanticBinder.Scope());
            OclIr.SemanticExpression semanticExpression = irBuilder.buildExpression(boundExpression);
            OclIr.OptimizedExpression optimizedExpression = irOptimizer.optimizeExpression(semanticExpression);
            org.uet.dse.neo4jtgg.ocl.ir.OclCypherPlan.ExpressionPlan plan = cypherPlanner.planExpression(optimizedExpression);
            OclCypherRenderer.RenderedTopLevelExpression renderedExpression =
                    cypherRenderer.renderTopLevelExpression(plan);
            return new CypherCompilationResult(true, renderedExpression.cypher(), renderedExpression.parameters(), "", false);
        } catch (OclCompilationException ex) {
            return unsupported(ex);
        } catch (OclCodedUnsupportedOperationException ex) {
            return unsupported(new OclDiagnostic(OclDiagnosticPhase.SEMANTIC, ex.code(), ex.getMessage(),
                    null, null, null, null));
        } catch (UnsupportedOperationException ex) {
            return unsupported(new OclDiagnostic(OclDiagnosticPhase.SEMANTIC, classifySemanticCode(ex.getMessage()),
                    ex.getMessage(), null, null, null, null));
        } catch (Exception ex) {
            return unsupported(new OclDiagnostic(OclDiagnosticPhase.RENDERING, OclDiagnosticCode.GENERIC_FAILURE,
                    "INTERNAL: Compilation failed: " + ex.getMessage(), null, null, null, null));
        }
    }

    private CypherCompilationResult compileOperationConstraint(ASTOperationConstraint constraint) {
        try {
            OclSemanticBinder.BoundContextInvariant boundConstraint = binder.bindOperationConstraint(constraint);
            OclIr.InvariantQuery invariantQuery = buildIr(boundConstraint);
            OclCypherRenderer.RenderedInvariant renderedInvariant = renderInvariant(invariantQuery);
            return new CypherCompilationResult(true, renderedInvariant.cypher(), renderedInvariant.parameters(), "", true);
        } catch (OclCompilationException ex) {
            return unsupported(ex);
        } catch (Exception ex) {
            return unsupported(new OclDiagnostic(OclDiagnosticPhase.RENDERING, OclDiagnosticCode.GENERIC_FAILURE,
                    "INTERNAL: Compilation failed: " + ex.getMessage(), null, null, null, null));
        }
    }

    private CypherCompilationResult unsupportedRuleKind(OclRuleDescriptor rule) {
        String ownerLabel = switch (rule.ownerKind()) {
            case CLASS -> "class";
            case OPERATION -> "operation";
            case ATTRIBUTE -> "attribute";
        };
        OclDiagnostic diagnostic = new OclDiagnostic(
                OclDiagnosticPhase.SEMANTIC,
                OclDiagnosticCode.UNSUPPORTED_RULE_KIND,
                "Unsupported OCL rule kind `" + rule.ruleKind().name().toLowerCase()
                        + "` on " + ownerLabel + " context. Current compiler path executes only `Class -> inv`.",
                null, null, null, null);
        return unsupported(diagnostic);
    }

    private CypherCompilationResult compileContext(ASTContext context) {
        try {
            if (context == null) {
                throw new OclCompilationException(OclDiagnosticPhase.SEMANTIC,
                        "Only context invariants are compiled to Cypher in v1.");
            }
            OclSemanticBinder.BoundContextInvariant boundInvariant = bindContext(context);
            OclIr.InvariantQuery invariantQuery = buildIr(boundInvariant);
            OclCypherRenderer.RenderedInvariant renderedInvariant = renderInvariant(invariantQuery);

            return new CypherCompilationResult(true, renderedInvariant.cypher(), renderedInvariant.parameters(), "", true);
        } catch (OclCompilationException ex) {
            return unsupported(ex);
        } catch (Exception ex) {
            return unsupported(new OclDiagnostic(OclDiagnosticPhase.RENDERING, OclDiagnosticCode.GENERIC_FAILURE,
                    "INTERNAL: Compilation failed: " + ex.getMessage(), null, null, null, null));
        }
    }

    private OclSemanticBinder.BoundContextInvariant bindContext(ASTContext context) {
        try {
            metamodelIndex.requireClass(context.className);
            return binder.bindContext(context);
        } catch (OclCodedUnsupportedOperationException ex) {
            throw new OclCompilationException(
                    OclDiagnosticPhase.SEMANTIC,
                    ex.code(),
                    ex.getMessage(),
                    ex);
        } catch (UnsupportedOperationException ex) {
            throw new OclCompilationException(
                    OclDiagnosticPhase.SEMANTIC,
                    classifySemanticCode(ex.getMessage()),
                    ex.getMessage(),
                    ex);
        }
    }

    private OclIr.InvariantQuery buildIr(OclSemanticBinder.BoundContextInvariant boundInvariant) {
        try {
            return irOptimizer.optimizeInvariant(irBuilder.buildInvariant(boundInvariant));
        } catch (OclCodedUnsupportedOperationException ex) {
            throw new OclCompilationException(OclDiagnosticPhase.IR, ex.code(), ex.getMessage(), ex);
        } catch (UnsupportedOperationException ex) {
            throw new OclCompilationException(OclDiagnosticPhase.IR, OclDiagnosticCode.GENERIC_FAILURE, ex.getMessage(), ex);
        }
    }

    private OclCypherRenderer.RenderedInvariant renderInvariant(OclIr.InvariantQuery invariantQuery) {
        org.uet.dse.neo4jtgg.ocl.ir.OclCypherPlan.InvariantPlan plan;
        try {
            plan = cypherPlanner.planInvariant(invariantQuery);
        } catch (OclCodedUnsupportedOperationException ex) {
            throw new OclCompilationException(
                    OclDiagnosticPhase.PLANNING,
                    ex.code(),
                    ex.getMessage(),
                    ex);
        } catch (UnsupportedOperationException ex) {
            throw new OclCompilationException(
                    OclDiagnosticPhase.PLANNING,
                    classifyBackendCode(OclDiagnosticPhase.PLANNING, ex.getMessage()),
                    ex.getMessage(),
                    ex);
        } catch (IllegalStateException ex) {
            throw new OclCompilationException(
                    OclDiagnosticPhase.PLANNING,
                    classifyBackendCode(OclDiagnosticPhase.PLANNING, ex.getMessage()),
                    ex.getMessage(),
                    ex);
        }
        try {
            return cypherRenderer.renderInvariant(plan);
        } catch (OclCodedUnsupportedOperationException ex) {
            throw new OclCompilationException(
                    OclDiagnosticPhase.RENDERING,
                    ex.code(),
                    ex.getMessage(),
                    ex);
        } catch (UnsupportedOperationException ex) {
            throw new OclCompilationException(
                    OclDiagnosticPhase.RENDERING,
                    classifyBackendCode(OclDiagnosticPhase.RENDERING, ex.getMessage()),
                    ex.getMessage(),
                    ex);
        }
    }

    private CypherCompilationResult unsupported(OclDiagnostic diagnostic) {
        return new CypherCompilationResult(false, "", Map.of(), diagnostic.toUserMessage(), false, List.of(diagnostic));
    }

    private CypherCompilationResult unsupported(OclCompilationException exception) {
        List<OclDiagnostic> diagnostics = buildDiagnostics(exception);
        OclDiagnostic primary = diagnostics.get(0);
        return new CypherCompilationResult(false, "", Map.of(), primary.toUserMessage(), false, diagnostics);
    }

    private List<OclDiagnostic> buildDiagnostics(OclCompilationException exception) {
        OclDiagnostic primary = exception.toDiagnostic();
        OclDiagnostic hint = buildHintDiagnostic(primary);
        return hint == null ? List.of(primary) : List.of(primary, hint);
    }

    private OclDiagnostic buildHintDiagnostic(OclDiagnostic primary) {
        if (primary.code() == OclDiagnosticCode.PARSE_ERROR) {
            if (looksLikeMethodCallOnIfWithoutParentheses(primary)) {
                return new OclDiagnostic(
                        OclDiagnosticPhase.PARSE,
                        primary.code(),
                        "Hint: wrap `if ... then ... else ... endif` in parentheses before calling a method or property on its result, for example `(if ... endif).isDefined()`.",
                        primary.line(), primary.column(), primary.endLine(), primary.endColumn(),
                        primary.tokenText(), primary.sourceSnippet());
            }
            return new OclDiagnostic(
                    OclDiagnosticPhase.PARSE,
                    primary.code(),
                    "Hint: check for incomplete expressions, missing operands, or unmatched parentheses near the reported location.",
                    primary.line(), primary.column(), primary.endLine(), primary.endColumn(),
                    primary.tokenText(), primary.sourceSnippet());
        }

        if (primary.code() == OclDiagnosticCode.UNSUPPORTED_RULE_KIND) {
            return new OclDiagnostic(
                    OclDiagnosticPhase.PARSE,
                    primary.code(),
                    "Hint: keep `inv` on the current production path; `pre/post/body/init/derive` need the generalized Class/Operation/Attribute rule model and are planned next.",
                    primary.line(), primary.column(), primary.endLine(), primary.endColumn(),
                    primary.tokenText(), primary.sourceSnippet());
        }

        if (primary.phase() == OclDiagnosticPhase.SEMANTIC && primary.message() != null) {
            if (primary.code() == OclDiagnosticCode.UNKNOWN_CONTEXT_CLASS) {
                return new OclDiagnostic(
                        OclDiagnosticPhase.SEMANTIC,
                        primary.code(),
                        "Hint: verify the context class name against the USE model before Cypher compilation.",
                        primary.line(), primary.column(), primary.endLine(), primary.endColumn(),
                        primary.tokenText(), primary.sourceSnippet());
            }
            if (primary.code() == OclDiagnosticCode.UNSUPPORTED_FREE_VARIABLE) {
                return new OclDiagnostic(
                        OclDiagnosticPhase.SEMANTIC,
                        primary.code(),
                        "Hint: bind this variable through `let`, an iterator, or use `self`/a known class name from the model.",
                        primary.line(), primary.column(), primary.endLine(), primary.endColumn(),
                        primary.tokenText(), primary.sourceSnippet());
            }
            if (primary.code() == OclDiagnosticCode.UNSUPPORTED_AST_NODE) {
                return new OclDiagnostic(
                        OclDiagnosticPhase.SEMANTIC,
                        primary.code(),
                        "Hint: this AST form is outside the supported semantic subset; extend the binder before Cypher compilation.",
                        primary.line(), primary.column(), primary.endLine(), primary.endColumn(),
                        primary.tokenText(), primary.sourceSnippet());
            }
            if (primary.code() == OclDiagnosticCode.INVALID_PROPERTY_SOURCE) {
                return new OclDiagnostic(
                        OclDiagnosticPhase.SEMANTIC,
                        primary.code(),
                        "Hint: only object-valued expressions may use property navigation; rewrite this scalar expression first.",
                        primary.line(), primary.column(), primary.endLine(), primary.endColumn(),
                        primary.tokenText(), primary.sourceSnippet());
            }
            if (primary.code() == OclDiagnosticCode.INVALID_ITERATOR_SOURCE) {
                return new OclDiagnostic(
                        OclDiagnosticPhase.SEMANTIC,
                        primary.code(),
                        "Hint: iterators require a collection source; convert or navigate to a collection before using exists/forall/select/collect.",
                        primary.line(), primary.column(), primary.endLine(), primary.endColumn(),
                        primary.tokenText(), primary.sourceSnippet());
            }
            if (primary.code() == OclDiagnosticCode.INVALID_METHOD_RECEIVER) {
                return new OclDiagnostic(
                        OclDiagnosticPhase.SEMANTIC,
                        primary.code(),
                        "Hint: call this method on the expected receiver kind, for example `allInstances()` only on a class name.",
                        primary.line(), primary.column(), primary.endLine(), primary.endColumn(),
                        primary.tokenText(), primary.sourceSnippet());
            }
            if (primary.code() == OclDiagnosticCode.INVALID_METHOD_ARGUMENT) {
                return new OclDiagnostic(
                        OclDiagnosticPhase.SEMANTIC,
                        primary.code(),
                        "Hint: this method call expects a different argument shape; normalize the arguments before compilation.",
                        primary.line(), primary.column(), primary.endLine(), primary.endColumn(),
                        primary.tokenText(), primary.sourceSnippet());
            }
            if (primary.code() == OclDiagnosticCode.INVALID_COLLECTION_ARGUMENT) {
                return new OclDiagnostic(
                        OclDiagnosticPhase.SEMANTIC,
                        primary.code(),
                        "Hint: this collection operation expects one collection argument; normalize the argument list before compilation.",
                        primary.line(), primary.column(), primary.endLine(), primary.endColumn(),
                        primary.tokenText(), primary.sourceSnippet());
            }
            if (primary.code() == OclDiagnosticCode.INVALID_COLLECTION_SOURCE) {
                return new OclDiagnostic(
                        OclDiagnosticPhase.SEMANTIC,
                        primary.code(),
                        "Hint: this collection operation requires a collection source; project or navigate to a collection first.",
                        primary.line(), primary.column(), primary.endLine(), primary.endColumn(),
                        primary.tokenText(), primary.sourceSnippet());
            }
            if (primary.code() == OclDiagnosticCode.ITERATOR_TYPE_MISMATCH) {
                return new OclDiagnostic(
                        OclDiagnosticPhase.SEMANTIC,
                        primary.code(),
                        "Hint: align the iterator type annotation with the actual element type of the source collection.",
                        primary.line(), primary.column(), primary.endLine(), primary.endColumn(),
                        primary.tokenText(), primary.sourceSnippet());
            }
            if (primary.code() == OclDiagnosticCode.INVALID_IF_CONDITION) {
                return new OclDiagnostic(
                        OclDiagnosticPhase.SEMANTIC,
                        primary.code(),
                        "Hint: the condition of `if` must be Boolean; compare or normalize the value before branching.",
                        primary.line(), primary.column(), primary.endLine(), primary.endColumn(),
                        primary.tokenText(), primary.sourceSnippet());
            }
            if (primary.code() == OclDiagnosticCode.INCOMPATIBLE_IF_BRANCH_TYPES) {
                return new OclDiagnostic(
                        OclDiagnosticPhase.SEMANTIC,
                        primary.code(),
                        "Hint: make both `if` branches return compatible types, or cast/rewrite one branch before compilation.",
                        primary.line(), primary.column(), primary.endLine(), primary.endColumn(),
                        primary.tokenText(), primary.sourceSnippet());
            }
            if (primary.code() == OclDiagnosticCode.UNORDERED_POSITIONAL_ACCESS) {
                return new OclDiagnostic(
                        OclDiagnosticPhase.SEMANTIC,
                        primary.code(),
                        "Hint: use an ordered source such as Sequence/OrderedSet, or rewrite with any()/select() instead of positional access.",
                        primary.line(), primary.column(), primary.endLine(), primary.endColumn(),
                        primary.tokenText(), primary.sourceSnippet());
            }
            if (primary.code() == OclDiagnosticCode.COLLECTION_VALUED_ATTRIBUTE_UNSUPPORTED) {
                return new OclDiagnostic(
                        OclDiagnosticPhase.SEMANTIC,
                        primary.code(),
                        "Hint: collection-valued attributes are supported, but this case still needs valid collection metadata or the nested collection graph path during rendering.",
                        primary.line(), primary.column(), primary.endLine(), primary.endColumn(),
                        primary.tokenText(), primary.sourceSnippet());
            }
            if (primary.code() == OclDiagnosticCode.NON_BINARY_ASSOCIATION_UNSUPPORTED) {
                return new OclDiagnostic(
                        OclDiagnosticPhase.SEMANTIC,
                        primary.code(),
                        "Hint: remodel this navigation through an intermediate association class or rewrite the query into supported binary navigations.",
                        primary.line(), primary.column(), primary.endLine(), primary.endColumn(),
                        primary.tokenText(), primary.sourceSnippet());
            }
            if (primary.code() == OclDiagnosticCode.QUALIFIED_ASSOCIATION_UNSUPPORTED) {
                return new OclDiagnostic(
                        OclDiagnosticPhase.SEMANTIC,
                        primary.code(),
                        "Hint: primitive scalar qualifier expressions are supported, but custom typed qualifier serialization still needs dedicated support.",
                        primary.line(), primary.column(), primary.endLine(), primary.endColumn(),
                        primary.tokenText(), primary.sourceSnippet());
            }
            if (primary.code() == OclDiagnosticCode.REDEFINING_ASSOCIATION_UNSUPPORTED) {
                return new OclDiagnostic(
                        OclDiagnosticPhase.SEMANTIC,
                        primary.code(),
                        "Hint: navigate through the base association or normalize the model metadata before Cypher compilation.",
                        primary.line(), primary.column(), primary.endLine(), primary.endColumn(),
                        primary.tokenText(), primary.sourceSnippet());
            }
            if (primary.code() == OclDiagnosticCode.UNSUPPORTED_METHOD_CALL) {
                return new OclDiagnostic(
                        OclDiagnosticPhase.SEMANTIC,
                        primary.code(),
                        "Hint: rewrite this method call into the supported semantic subset, or add binder support before Cypher compilation.",
                        primary.line(), primary.column(), primary.endLine(), primary.endColumn(),
                        primary.tokenText(), primary.sourceSnippet());
            }
            if (primary.code() == OclDiagnosticCode.UNSUPPORTED_COLLECTION_OPERATION) {
                return new OclDiagnostic(
                        OclDiagnosticPhase.SEMANTIC,
                        primary.code(),
                        "Hint: rewrite this collection operation into the supported OCL subset, or extend semantic binding for it first.",
                        primary.line(), primary.column(), primary.endLine(), primary.endColumn(),
                        primary.tokenText(), primary.sourceSnippet());
            }
            if (primary.code() == OclDiagnosticCode.UNSUPPORTED_ITERATOR) {
                return new OclDiagnostic(
                        OclDiagnosticPhase.SEMANTIC,
                        primary.code(),
                        "Hint: rewrite this iterator into a supported one such as select/reject/collect/exists/forall/one/any/isUnique/sortedBy, or extend semantic binding first.",
                        primary.line(), primary.column(), primary.endLine(), primary.endColumn(),
                        primary.tokenText(), primary.sourceSnippet());
            }
            if (primary.code() == OclDiagnosticCode.UNSUPPORTED_OPERATOR) {
                return new OclDiagnostic(
                        OclDiagnosticPhase.SEMANTIC,
                        primary.code(),
                        "Hint: normalize this expression to a supported boolean, comparison, or arithmetic operator before Cypher compilation.",
                        primary.line(), primary.column(), primary.endLine(), primary.endColumn(),
                        primary.tokenText(), primary.sourceSnippet());
            }
            return new OclDiagnostic(
                    OclDiagnosticPhase.SEMANTIC,
                    primary.code(),
                    "Hint: narrow the expression to the supported OCL subset or rewrite the unsupported construct into an equivalent supported form.",
                    primary.line(), primary.column(), primary.endLine(), primary.endColumn(),
                    primary.tokenText(), primary.sourceSnippet());
        }

        if (primary.phase() == OclDiagnosticPhase.PLANNING || primary.phase() == OclDiagnosticPhase.RENDERING) {
            if (primary.code() == OclDiagnosticCode.UNSUPPORTED_COUNT_OPERATOR) {
                return new OclDiagnostic(
                        primary.phase(),
                        primary.code(),
                        "Hint: normalize the comparison into a supported count predicate such as =, <>, >, >=, <, <= before Cypher planning.",
                        primary.line(), primary.column(), primary.endLine(), primary.endColumn(),
                        primary.tokenText(), primary.sourceSnippet());
            }
            if (primary.code() == OclDiagnosticCode.UNSUPPORTED_COLLECTION_OPERATION) {
                return new OclDiagnostic(
                        primary.phase(),
                        primary.code(),
                        "Hint: rewrite this collection operation into the supported subset, or extend the Cypher backend with an explicit renderer path.",
                        primary.line(), primary.column(), primary.endLine(), primary.endColumn(),
                        primary.tokenText(), primary.sourceSnippet());
            }
            if (primary.code() == OclDiagnosticCode.UNSUPPORTED_METHOD_CALL) {
                return new OclDiagnostic(
                        primary.phase(),
                        primary.code(),
                        "Hint: rewrite this method call into a supported operation such as split()/isDefined()/isUndefined(), or add an explicit backend mapping.",
                        primary.line(), primary.column(), primary.endLine(), primary.endColumn(),
                        primary.tokenText(), primary.sourceSnippet());
            }
            if (primary.code() == OclDiagnosticCode.INVALID_METHOD_ARGUMENT) {
                return new OclDiagnostic(
                        primary.phase(),
                        primary.code(),
                        "Hint: this method call reached the backend with an invalid argument shape; normalize the argument list before Cypher generation.",
                        primary.line(), primary.column(), primary.endLine(), primary.endColumn(),
                        primary.tokenText(), primary.sourceSnippet());
            }
            if (primary.code() == OclDiagnosticCode.INVALID_COLLECTION_ARGUMENT) {
                return new OclDiagnostic(
                        primary.phase(),
                        primary.code(),
                        "Hint: this collection operation reached the backend with an invalid argument shape; normalize the argument list before Cypher generation.",
                        primary.line(), primary.column(), primary.endLine(), primary.endColumn(),
                        primary.tokenText(), primary.sourceSnippet());
            }
            if (primary.code() == OclDiagnosticCode.UNSUPPORTED_ITERATOR) {
                return new OclDiagnostic(
                        primary.phase(),
                        primary.code(),
                        "Hint: rewrite this iterator into select/reject/collect/exists/forall/one/any/isUnique/sortedBy, or add planner-renderer support for the missing iterator.",
                        primary.line(), primary.column(), primary.endLine(), primary.endColumn(),
                        primary.tokenText(), primary.sourceSnippet());
            }
            if (primary.code() == OclDiagnosticCode.UNSUPPORTED_OPERATOR) {
                return new OclDiagnostic(
                        primary.phase(),
                        primary.code(),
                        "Hint: normalize the expression to a supported boolean, comparison, or arithmetic operator before Cypher generation.",
                        primary.line(), primary.column(), primary.endLine(), primary.endColumn(),
                        primary.tokenText(), primary.sourceSnippet());
            }
            if (primary.code() == OclDiagnosticCode.UNSUPPORTED_PLAN_SOURCE) {
                return new OclDiagnostic(
                        primary.phase(),
                        primary.code(),
                        "Hint: this OCL construct reached planning without a dedicated plan node; extend the planner before rendering.",
                        primary.line(), primary.column(), primary.endLine(), primary.endColumn(),
                        primary.tokenText(), primary.sourceSnippet());
            }
            return new OclDiagnostic(
                    primary.phase(),
                    primary.code(),
                    "Hint: inspect the planned OCL construct near this phase boundary; the current Cypher backend may need a planner or renderer extension.",
                    primary.line(), primary.column(), primary.endLine(), primary.endColumn(),
                    primary.tokenText(), primary.sourceSnippet());
        }

        if (primary.phase() == OclDiagnosticPhase.IR) {
            if (primary.code() == OclDiagnosticCode.UNSUPPORTED_BOUND_EXPRESSION) {
                return new OclDiagnostic(
                        OclDiagnosticPhase.IR,
                        primary.code(),
                        "Hint: this bound semantic form has no IR lowering yet; extend OclIrBuilder before planning.",
                        primary.line(), primary.column(), primary.endLine(), primary.endColumn(),
                        primary.tokenText(), primary.sourceSnippet());
            }
            if (primary.code() == OclDiagnosticCode.UNSUPPORTED_IR_EXPRESSION) {
                return new OclDiagnostic(
                        OclDiagnosticPhase.IR,
                        primary.code(),
                        "Hint: this IR expression has no Cypher rendering path yet; add renderer support or lower it earlier.",
                        primary.line(), primary.column(), primary.endLine(), primary.endColumn(),
                        primary.tokenText(), primary.sourceSnippet());
            }
        }

        return null;
    }

    private boolean looksLikeMethodCallOnIfWithoutParentheses(OclDiagnostic primary) {
        String snippet = primary.sourceSnippet();
        if (snippet == null) {
            return false;
        }
        String normalized = snippet.toLowerCase();
        return normalized.contains("endif.")
                && normalized.contains("if ")
                && normalized.contains(" then ")
                && normalized.contains(" else ");
    }

    private OclDiagnosticCode classifySemanticCode(String message) {
        if (message == null) {
            return OclDiagnosticCode.GENERIC_FAILURE;
        }
        if (message.contains("Unknown property or navigation")) {
            return OclDiagnosticCode.UNKNOWN_PROPERTY;
        }
        if (message.contains("Unsupported free variable")) {
            return OclDiagnosticCode.UNSUPPORTED_FREE_VARIABLE;
        }
        if (message.contains("Unsupported AST node")) {
            return OclDiagnosticCode.UNSUPPORTED_AST_NODE;
        }
        if (message.contains("Unknown class in context")) {
            return OclDiagnosticCode.UNKNOWN_CONTEXT_CLASS;
        }
        if (message.contains("Property access requires node source")) {
            return OclDiagnosticCode.INVALID_PROPERTY_SOURCE;
        }
        if (message.contains("Iterator source must be a collection")) {
            return OclDiagnosticCode.INVALID_ITERATOR_SOURCE;
        }
        if (message.contains("allInstances() must be called on a class name")) {
            return OclDiagnosticCode.INVALID_METHOD_RECEIVER;
        }
        if (message.contains("requires a single delimiter argument")) {
            return OclDiagnosticCode.INVALID_METHOD_ARGUMENT;
        }
        if (message.contains("requires a single collection argument")) {
            return OclDiagnosticCode.INVALID_COLLECTION_ARGUMENT;
        }
        if (message.contains("requires a collection source")) {
            return OclDiagnosticCode.INVALID_COLLECTION_SOURCE;
        }
        if (message.contains("Iterator type mismatch")) {
            return OclDiagnosticCode.ITERATOR_TYPE_MISMATCH;
        }
        if (message.contains("if condition must be Boolean")) {
            return OclDiagnosticCode.INVALID_IF_CONDITION;
        }
        if (message.contains("if branches must have compatible types")) {
            return OclDiagnosticCode.INCOMPATIBLE_IF_BRANCH_TYPES;
        }
        if (message.contains("only supported on ordered collections")) {
            return OclDiagnosticCode.UNORDERED_POSITIONAL_ACCESS;
        }
        if (message.contains("non-binary associations")) {
            return OclDiagnosticCode.NON_BINARY_ASSOCIATION_UNSUPPORTED;
        }
        if (message.contains("qualified associations")) {
            return OclDiagnosticCode.QUALIFIED_ASSOCIATION_UNSUPPORTED;
        }
        if (message.contains("redefining associations")) {
            return OclDiagnosticCode.REDEFINING_ASSOCIATION_UNSUPPORTED;
        }
        return OclDiagnosticCode.GENERIC_FAILURE;
    }

    private OclDiagnosticCode classifyBackendCode(OclDiagnosticPhase phase, String message) {
        if (message == null) {
            return OclDiagnosticCode.GENERIC_FAILURE;
        }
        if (message.contains("Unsupported count operator")) {
            return OclDiagnosticCode.UNSUPPORTED_COUNT_OPERATOR;
        }
        if (message.contains("Unsupported bound expression")) {
            return OclDiagnosticCode.UNSUPPORTED_BOUND_EXPRESSION;
        }
        if (message.contains("Unsupported IR expression")) {
            return OclDiagnosticCode.UNSUPPORTED_IR_EXPRESSION;
        }
        if (message.contains("Unsupported collection operation")) {
            return OclDiagnosticCode.UNSUPPORTED_COLLECTION_OPERATION;
        }
        if (message.contains("Unsupported method call")) {
            return OclDiagnosticCode.UNSUPPORTED_METHOD_CALL;
        }
        if (message.contains("Unsupported iterator")) {
            return OclDiagnosticCode.UNSUPPORTED_ITERATOR;
        }
        if (message.contains("Unsupported operator")) {
            return OclDiagnosticCode.UNSUPPORTED_OPERATOR;
        }
        if (message.contains("Unsupported plan source")) {
            return OclDiagnosticCode.UNSUPPORTED_PLAN_SOURCE;
        }
        return OclDiagnosticCode.GENERIC_FAILURE;
    }

    private String extractSourceSnippet(String source, Integer line) {
        return OclDocumentParser.extractSourceSnippet(source, line);
    }

    private OclResultLocation inferResultLocation(String contextClassName,
                                                  String invariantName,
                                                  CypherCompilationResult compilation) {
        if (compilation != null && !compilation.getDiagnostics().isEmpty()) {
            return OclResultLocation.fromDiagnostic(contextClassName, invariantName, compilation.getDiagnostics().get(0));
        }
        return new OclResultLocation(contextClassName, invariantName, null, null, null, null, null, null, List.of());
    }

    private List<String> inferRequiredInputs(OclRuleDescriptor rule) {
        List<String> requiredInputs = new java.util.ArrayList<>();
        switch (rule.ruleKind()) {
            case PRE -> {
                for (String parameterName : rule.parameterNames()) {
                    requiredInputs.add("parameter:" + parameterName);
                }
            }
            case POST -> {
                for (String parameterName : rule.parameterNames()) {
                    requiredInputs.add("parameter:" + parameterName);
                }
                if (referencesVariable(rule.expression(), "result")) {
                    requiredInputs.add("resultValue");
                }
            }
            case BODY -> {
                requiredInputs.add("self");
                for (String parameterName : rule.parameterNames()) {
                    requiredInputs.add("parameter:" + parameterName);
                }
            }
            case INIT, DERIVE -> requiredInputs.add("self");
            case INV -> {
            }
        }
        return List.copyOf(requiredInputs);
    }

    private boolean referencesVariable(ASTExpression expression, String variableName) {
        if (expression == null || variableName == null || variableName.isBlank()) {
            return false;
        }
        if (expression instanceof ASTVar variable) {
            return variableName.equals(variable.name);
        }
        if (expression instanceof ASTBinary binary) {
            return referencesVariable(binary.left, variableName)
                    || referencesVariable(binary.right, variableName);
        }
        if (expression instanceof ASTProperty property) {
            if (referencesVariable(property.source, variableName)) {
                return true;
            }
            for (ASTExpression qualifier : property.qualifiers) {
                if (referencesVariable(qualifier, variableName)) {
                    return true;
                }
            }
            return false;
        }
        if (expression instanceof ASTMethodCall methodCall) {
            if (referencesVariable(methodCall.source, variableName)) {
                return true;
            }
            for (ASTExpression argument : methodCall.args) {
                if (referencesVariable(argument, variableName)) {
                    return true;
                }
            }
            return false;
        }
        if (expression instanceof ASTCollectionOp collectionOp) {
            if (referencesVariable(collectionOp.source, variableName)) {
                return true;
            }
            for (ASTExpression argument : collectionOp.args) {
                if (referencesVariable(argument, variableName)) {
                    return true;
                }
            }
            return false;
        }
        if (expression instanceof ASTIterator iterator) {
            if (referencesVariable(iterator.source, variableName)) {
                return true;
            }
            if (variableName.equals(iterator.iteratorName)) {
                return false;
            }
            return referencesVariable(iterator.body, variableName);
        }
        if (expression instanceof ASTIf ifExpression) {
            return referencesVariable(ifExpression.condition, variableName)
                    || referencesVariable(ifExpression.thenBranch, variableName)
                    || referencesVariable(ifExpression.elseBranch, variableName);
        }
        if (expression instanceof ASTLet letExpression) {
            if (referencesVariable(letExpression.value, variableName)) {
                return true;
            }
            if (variableName.equals(letExpression.variableName)) {
                return false;
            }
            return referencesVariable(letExpression.body, variableName);
        }
        if (expression instanceof ASTNot not) {
            return referencesVariable(not.expression, variableName);
        }
        return false;
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }
}
