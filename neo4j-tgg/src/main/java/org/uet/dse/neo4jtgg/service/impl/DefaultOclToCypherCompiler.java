package org.uet.dse.neo4jtgg.service.impl;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.tzi.use.uml.mm.MModel;
import org.uet.dse.neo4j.OCLLexer;
import org.uet.dse.neo4j.OCLParser;
import org.uet.dse.neo4j.oclite.ast.ASTContext;
import org.uet.dse.neo4j.oclite.ast.ASTNode;
import org.uet.dse.neo4j.oclite.ast.ASTVisitor;
import org.uet.dse.neo4jtgg.ocl.diagnostic.OclCompilationException;
import org.uet.dse.neo4jtgg.ocl.diagnostic.OclCodedUnsupportedOperationException;
import org.uet.dse.neo4jtgg.ocl.diagnostic.OclDiagnosticCode;
import org.uet.dse.neo4jtgg.ocl.diagnostic.OclDiagnostic;
import org.uet.dse.neo4jtgg.ocl.diagnostic.OclDiagnosticPhase;
import org.uet.dse.neo4jtgg.ocl.OclMetamodelIndex;
import org.uet.dse.neo4jtgg.ocl.OclSemanticBinder;
import org.uet.dse.neo4jtgg.ocl.ir.OclCypherRenderer;
import org.uet.dse.neo4jtgg.ocl.ir.OclCypherPlanner;
import org.uet.dse.neo4jtgg.ocl.ir.OclIr;
import org.uet.dse.neo4jtgg.ocl.ir.OclIrBuilder;
import org.uet.dse.neo4jtgg.ocl.ir.OclIrOptimizer;
import org.uet.dse.neo4jtgg.model.CypherCompilationResult;
import org.uet.dse.neo4jtgg.service.OclToCypherCompiler;

import java.util.List;
import java.util.Map;

public class DefaultOclToCypherCompiler implements OclToCypherCompiler {
    private final OclMetamodelIndex metamodelIndex;
    private final OclSemanticBinder binder;
    private final OclIrBuilder irBuilder;
    private final OclIrOptimizer irOptimizer;
    private final OclCypherPlanner cypherPlanner;
    private final OclCypherRenderer cypherRenderer;

    public DefaultOclToCypherCompiler(MModel model) {
        this.metamodelIndex = new OclMetamodelIndex(model);
        this.binder = new OclSemanticBinder(metamodelIndex);
        this.irBuilder = new OclIrBuilder();
        this.irOptimizer = new OclIrOptimizer();
        this.cypherPlanner = new OclCypherPlanner();
        this.cypherRenderer = new OclCypherRenderer();
    }

    DefaultOclToCypherCompiler(OclMetamodelIndex metamodelIndex,
                               OclSemanticBinder binder,
                               OclIrBuilder irBuilder,
                               OclIrOptimizer irOptimizer,
                               OclCypherPlanner cypherPlanner,
                               OclCypherRenderer cypherRenderer) {
        this.metamodelIndex = metamodelIndex;
        this.binder = binder;
        this.irBuilder = irBuilder;
        this.irOptimizer = irOptimizer;
        this.cypherPlanner = cypherPlanner;
        this.cypherRenderer = cypherRenderer;
    }

    @Override
    public CypherCompilationResult compile(String oclExpression) {
        try {
            ASTNode ast = parseAst(oclExpression);
            if (!(ast instanceof ASTContext context)) {
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

    private ASTNode parseAst(String oclExpression) {
        try {
            OCLLexer lexer = new OCLLexer(CharStreams.fromString(oclExpression));
            OCLParser parser = new OCLParser(new CommonTokenStream(lexer));
            ASTNode ast = new ASTVisitor().visit(parser.oclFile());
            if (parser.getNumberOfSyntaxErrors() > 0) {
                Token token = parser.getCurrentToken();
                Integer line = token != null ? token.getLine() : null;
                Integer column = token != null ? token.getCharPositionInLine() : null;
                Integer endLine = line;
                Integer endColumn = token != null
                        ? token.getCharPositionInLine() + Math.max(token.getText() != null ? token.getText().length() - 1 : 0, 0)
                        : null;
                String tokenText = token != null ? token.getText() : null;
                String sourceSnippet = extractSourceSnippet(oclExpression, line);
                throw new OclCompilationException(OclDiagnosticPhase.PARSE, OclDiagnosticCode.PARSE_ERROR,
                        "Failed to parse OCL input.", line, column, endLine, endColumn, tokenText, sourceSnippet);
            }
            return ast;
        } catch (OclCompilationException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new OclCompilationException(OclDiagnosticPhase.PARSE,
                    ex.getMessage() != null ? ex.getMessage() : "Failed to parse OCL input.",
                    ex);
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
                        "Hint: remodel this as navigation, or project the collection through supported operations before Cypher compilation.",
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
                        "Hint: expose the qualified lookup as an explicit attribute/filter pair or refactor the model to a supported binary navigation.",
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
                        "Hint: rewrite this iterator into a supported one such as select/collect/exists/forall/one/any, or extend semantic binding first.",
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
                        "Hint: rewrite this iterator into select/collect/exists/forall/one/any, or add planner-renderer support for the missing iterator.",
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
        if (message.contains("Collection-valued attributes are not supported yet")) {
            return OclDiagnosticCode.COLLECTION_VALUED_ATTRIBUTE_UNSUPPORTED;
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
        if (source == null || line == null || line < 1) {
            return null;
        }
        String[] lines = source.split("\\R", -1);
        if (line > lines.length) {
            return null;
        }
        String snippet = lines[line - 1].trim();
        return snippet.isEmpty() ? null : snippet;
    }
}
