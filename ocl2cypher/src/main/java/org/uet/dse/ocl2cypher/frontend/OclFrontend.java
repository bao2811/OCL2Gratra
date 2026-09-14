package org.uet.dse.ocl2cypher.frontend;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.uet.dse.ocl2cypher.diagnostics.Diagnostic;
import org.uet.dse.ocl2cypher.diagnostics.Result;
import org.uet.dse.ocl2cypher.diagnostics.SourceSpan;
import org.uet.dse.ocl2cypher.diagnostics.Stage;
import org.uet.dse.ocl2cypher.runtime.OclType;
import org.uet.dse.ocl2cypher.source.model.SchemaModel;
import org.uet.dse.ocl2cypher.source.model.Snapshot;
import org.uet.dse.ocl2cypher.source.model.UmlAttribute;
import org.uet.dse.ocl2cypher.source.omg.OmgAs;
import org.uet.dse.ocl2cypher.source.omg.OclOperation;

/**
 * Package-private recognizer/typechecker used inside {@code E_SM}.
 *
 * <p>Parse, name resolution and type analysis are one boundary here, matching
 * the specification's {@code E_SM = TypeCheck_SM ∘ Resolve_SM ∘ Parse} fusion.
 * Surface forms the grammar recognizes but {@code OCL_val} does not admit
 * ({@code null}, {@code invalid}, collection ranges) are kept as their
 * recognized AS identity so that {@code N_SM} can issue the admission failure;
 * the frontend does not decide final admission and does not invent
 * {@code BinaryExpression}/{@code UnaryExpression} metaclasses — operators
 * become resolved {@link OmgAs.OperationCallExp}s.
 *
 * <p>Precedence (weakest first), all infix levels left-associative per OMG OCL
 * 2.4: implies &lt; xor &lt; or &lt; and &lt; equality &lt; relational &lt;
 * additive &lt; multiplicative (incl. contextual {@code div}/{@code mod}) &lt;
 * unary &lt; postfix.
 */
final class OclFrontend {

    private static final List<String> ITERATORS =
            List.of("select", "reject", "exists", "forAll", "collect");

    private final SchemaModel sm;
    private List<OclLexer.Tok> toks;
    private int idx;
    private boolean requireBooleanBody = true;

    OclFrontend(SchemaModel sm) {
        this.sm = java.util.Objects.requireNonNull(sm, "schema model");
    }

    /** Public frontend product: resolved OMG-AS, never the legacy parser tree. */
    Result<List<OmgAs.OmgDocument>> elaborateOmgDocuments(String source) {
        return elaborateInternal(source, true);
    }

    /** Public value-query product: resolved OMG-AS with declaration identity. */
    Result<OmgAs.ExpressionInOcl> elaborateOmgValueExpression(
            String source, String contextClassKey, String contextVariableName) {
        return elaborateValueExpression(source, contextClassKey, contextVariableName);
    }

    /** Parse one expression without wrapping it in a surface invariant. */
    Result<OmgAs.ExpressionInOcl> elaborateValueExpression(String source,
                                                               String contextClassKey,
                                                               String contextVariableName) {
        this.requireBooleanBody = false;
        this.toks = new OclLexer(source).tokenize();
        this.idx = 0;
        try {
            if ((contextClassKey == null) != (contextVariableName == null)) {
                throw error("E_VALUE_CONTEXT",
                        "context class and variable must be present together", SourceSpan.UNKNOWN);
            }
            Scope scope = new Scope();
            OmgAs.Variable context = null;
            if (contextClassKey != null) {
                if (sm.clazz(contextClassKey) == null) {
                    throw error("E_CONTEXT_CLASS", "classifier not found: " + contextClassKey,
                            SourceSpan.UNKNOWN);
                }
                context = new OmgAs.Variable(SourceSpan.UNKNOWN, contextVariableName,
                        OclType.clazz(contextClassKey), null);
                scope.declare(context);
            }
            OmgAs.OclExpression body = parseExpression(scope);
            if (!peek().isEnd()) {
                throw error("E_PARSE", "unexpected trailing token '" + peek().text() + "'",
                        peek().span());
            }
            return Result.success(new OmgAs.ExpressionInOcl(body.span(), body, context));
        } catch (FrontendError error) {
            return Result.failure(error.diagnostic());
        }
    }

    private Result<List<OmgAs.OmgDocument>> elaborateInternal(String source,
                                                             boolean requireBooleanBody) {
        this.requireBooleanBody = requireBooleanBody;
        this.toks = new OclLexer(source).tokenize();
        this.idx = 0;
        List<Diagnostic> diags = new ArrayList<>();
        List<OmgAs.OmgDocument> docs = new ArrayList<>();
        try {
            while (!peek().isEnd()) {
                OmgAs.OmgDocument doc = parseDocumentEntry();
                if (doc != null) {
                    docs.add(doc);
                }
            }
            if (!diags.isEmpty()) {
                return Result.failure(diags);
            }
            return Result.success(docs);
        } catch (FrontendError e) {
            diags.add(e.diagnostic());
            return Result.failure(diags);
        }
    }

    /** One {@code context} block, or a bare {@code inv} continuation of the last context. */
    private OmgAs.OmgDocument parseDocumentEntry() {
        if (peek().isWord("package")) {
            // Package only scopes classifier lookup; it is not a semantic OCL package.
            advance();
            String path = parsePathName();
            List<OmgAs.OmgDocument> inner = new ArrayList<>();
            while (!peek().isEnd() && !peek().isWord("endpackage")) {
                OmgAs.OmgDocument d = parseDocumentEntry();
                if (d != null) {
                    inner.add(d);
                }
            }
            expectWord("endpackage");
            if (inner.size() != 1) {
                throw error("E_PACKAGE_CONTEXT_COUNT",
                        "package " + path + " must contain exactly one classifier context",
                        SourceSpan.UNKNOWN);
            }
            return inner.get(0);
        }
        if (peek().isWord("context")) {
            advance();
            return parseClassifierContext();
        }
        throw error("E_PARSE", "unexpected token '" + peek().text() + "'", peek().span());
    }

    private OmgAs.OmgDocument parseClassifierContext() {
        // head: pathName | simpleName ':' pathName
        String first = parseSimpleName();
        String classPath;
        String contextVar = "self";
        if (peek().isSymbol(":")) {
            advance();
            contextVar = first;
            classPath = parsePathName();
        } else {
            classPath = first;
        }
        if (sm.clazz(classPath) == null) {
            throw error("E_CONTEXT_CLASS", "classifier not found: " + classPath, peek().span());
        }
        Scope scope = new Scope();
        OmgAs.Variable context = new OmgAs.Variable(SourceSpan.UNKNOWN, contextVar,
                OclType.clazz(classPath), null);
        scope.declare(context);
        List<OmgAs.Constraint> invs = new ArrayList<>();
        while (peek().isWord("inv")) {
            advance();
            String invName = null;
            SourceSpan span = peek().span();
            if (peek().kind() == OclLexer.Kind.WORD && !peek().isSymbol(":")) {
                invName = parseSimpleName();
            }
            expectSymbol(":");
            OmgAs.OclExpression body = parseExpression(scope);
            if (requireBooleanBody
                    && (body.type() == null || !body.type().equals(OclType.BOOLEAN))) {
                throw error("E_INVARIANT_TYPE",
                        "invariant body must be Boolean, found " + body.type(), span);
            }
            invs.add(new OmgAs.Constraint(invName, classPath,
                    new OmgAs.ExpressionInOcl(span, body, context)));
        }
        if (invs.isEmpty()) {
            throw error("E_PARSE", "classifier context requires at least one invariant",
                    peek().span());
        }
        return new OmgAs.OmgDocument(SourceSpan.UNKNOWN, classPath, contextVar, invs);
    }

    // ---- expression precedence chain -------------------------------------

    private OmgAs.OclExpression parseExpression(Scope scope) {
        return parseImplies(scope);
    }

    /**
     * {@code implies} is RIGHT-associative, per the concrete grammar
     * (§2: {@code impliesExpression = xorExpression, [ "implies", impliesExpression ]}).
     * So {@code a implies b implies c} parses as {@code a implies (b implies c)}.
     */
    private OmgAs.OclExpression parseImplies(Scope scope) {
        OmgAs.OclExpression left = parseXor(scope);
        if (peek().isWord("implies")) {
            SourceSpan sp = peek().span();
            advance();
            OmgAs.OclExpression right = parseImplies(scope);
            requireBoolean(left.type(), sp);
            requireBoolean(right.type(), sp);
            return binary(OclOperation.BOOLEAN_IMPLIES, left, right, OclType.BOOLEAN, sp);
        }
        return left;
    }

    private OmgAs.OclExpression parseXor(Scope scope) {
        OmgAs.OclExpression left = parseOr(scope);
        while (peek().isWord("xor")) {
            SourceSpan sp = peek().span();
            advance();
            OmgAs.OclExpression right = parseOr(scope);
            requireBoolean(left.type(), sp);
            requireBoolean(right.type(), sp);
            left = binary(OclOperation.BOOLEAN_XOR, left, right, OclType.BOOLEAN, sp);
        }
        return left;
    }

    private OmgAs.OclExpression parseOr(Scope scope) {
        OmgAs.OclExpression left = parseAnd(scope);
        while (peek().isWord("or")) {
            SourceSpan sp = peek().span();
            advance();
            OmgAs.OclExpression right = parseAnd(scope);
            requireBoolean(left.type(), sp);
            requireBoolean(right.type(), sp);
            left = binary(OclOperation.BOOLEAN_OR, left, right, OclType.BOOLEAN, sp);
        }
        return left;
    }

    private OmgAs.OclExpression parseAnd(Scope scope) {
        OmgAs.OclExpression left = parseEquality(scope);
        while (peek().isWord("and")) {
            SourceSpan sp = peek().span();
            advance();
            OmgAs.OclExpression right = parseEquality(scope);
            requireBoolean(left.type(), sp);
            requireBoolean(right.type(), sp);
            left = binary(OclOperation.BOOLEAN_AND, left, right, OclType.BOOLEAN, sp);
        }
        return left;
    }

    private OmgAs.OclExpression parseEquality(Scope scope) {
        OmgAs.OclExpression left = parseRelational(scope);
        while (peek().isSymbol("=") || peek().isSymbol("<>")) {
            boolean eq = peek().isSymbol("=");
            SourceSpan sp = peek().span();
            advance();
            OmgAs.OclExpression right = parseRelational(scope);
            OmgAs.OclExpression[] inferred = inferEmptyCollectionPair(left, right, sp);
            left = inferred[0];
            right = inferred[1];
            OclType join = joinType(left.type(), right.type(), sp);
            left = binary(eq ? OclOperation.VALUE_EQUAL : OclOperation.VALUE_NOT_EQUAL,
                    coerce(left, join, sp), coerce(right, join, sp), OclType.BOOLEAN, sp);
        }
        return left;
    }

    private OmgAs.OclExpression parseRelational(Scope scope) {
        OmgAs.OclExpression left = parseAdditive(scope);
        while (peek().isSymbol("<") || peek().isSymbol("<=")
                || peek().isSymbol(">") || peek().isSymbol(">=")) {
            String op = peek().text();
            SourceSpan sp = peek().span();
            advance();
            OmgAs.OclExpression right = parseAdditive(scope);
            requireNumeric(left.type(), sp);
            requireNumeric(right.type(), sp);
            OclType join = joinType(left.type(), right.type(), sp);
            OclOperation kind = switch (op) {
                case "<" -> OclOperation.LESS_THAN;
                case "<=" -> OclOperation.LESS_THAN_OR_EQUAL;
                case ">" -> OclOperation.GREATER_THAN;
                default -> OclOperation.GREATER_THAN_OR_EQUAL;
            };
            left = binary(kind, coerce(left, join, sp), coerce(right, join, sp),
                    OclType.BOOLEAN, sp);
        }
        return left;
    }

    private OmgAs.OclExpression parseAdditive(Scope scope) {
        OmgAs.OclExpression left = parseMultiplicative(scope);
        while (peek().isSymbol("+") || peek().isSymbol("-")) {
            boolean add = peek().isSymbol("+");
            SourceSpan sp = peek().span();
            advance();
            OmgAs.OclExpression right = parseMultiplicative(scope);
            requireNumeric(left.type(), sp);
            requireNumeric(right.type(), sp);
            OclType join = joinType(left.type(), right.type(), sp);
            left = binary(add ? OclOperation.NUMERIC_ADD : OclOperation.NUMERIC_SUBTRACT,
                    coerce(left, join, sp), coerce(right, join, sp), join, sp);
        }
        return left;
    }

    private OmgAs.OclExpression parseMultiplicative(Scope scope) {
        OmgAs.OclExpression left = parseUnary(scope);
        while (true) {
            String op = null;
            if (peek().isSymbol("*")) {
                op = "*";
            } else if (peek().isSymbol("/")) {
                op = "/";
            } else if (peek().isWord("div")) {
                op = "div";
            } else if (peek().isWord("mod")) {
                op = "mod";
            }
            if (op == null) {
                return left;
            }
            SourceSpan sp = peek().span();
            advance();
            OmgAs.OclExpression right = parseUnary(scope);
            switch (op) {
                case "*" -> {
                    requireNumeric(left.type(), sp);
                    requireNumeric(right.type(), sp);
                    OclType join = joinType(left.type(), right.type(), sp);
                    left = binary(OclOperation.NUMERIC_MULTIPLY,
                            coerce(left, join, sp), coerce(right, join, sp), join, sp);
                }
                case "/" -> {
                    requireNumeric(left.type(), sp);
                    requireNumeric(right.type(), sp);
                    left = binary(OclOperation.REAL_DIVIDE,
                            coerce(left, OclType.REAL, sp), coerce(right, OclType.REAL, sp),
                            OclType.REAL, sp);
                }
                default -> {
                    requireInteger(left.type(), sp);
                    requireInteger(right.type(), sp);
                    left = binary("div".equals(op)
                                    ? OclOperation.INTEGER_DIVIDE : OclOperation.INTEGER_MOD,
                            left, right, OclType.INTEGER, sp);
                }
            }
        }
    }

    private OmgAs.OclExpression parseUnary(Scope scope) {
        if (peek().isWord("not")) {
            SourceSpan sp = peek().span();
            advance();
            OmgAs.OclExpression operand = parseUnary(scope);
            if (operand.type() == null || !operand.type().equals(OclType.BOOLEAN)) {
                throw error("E_TYPE", "not requires Boolean operand", sp);
            }
            return operation(sp, OclOperation.BOOLEAN_NOT, List.of(operand), OclType.BOOLEAN);
        }
        if (peek().isSymbol("-")) {
            SourceSpan sp = peek().span();
            advance();
            OmgAs.OclExpression operand = parseUnary(scope);
            requireNumeric(operand.type(), sp);
            return operation(sp, OclOperation.NUMERIC_NEGATE, List.of(operand), operand.type());
        }
        return parsePostfix(scope);
    }

    private OmgAs.OclExpression parsePostfix(Scope scope) {
        OmgAs.OclExpression e = parsePrimary(scope);
        while (true) {
            if (peek().isSymbol(".")) {
                SourceSpan sp = peek().span();
                advance();
                e = parseCallOn(sp, e, /*dotCall*/ true, scope);
            } else if (peek().isSymbol("->")) {
                SourceSpan sp = peek().span();
                advance();
                e = parseCallOn(sp, e, /*dotCall*/ false, scope);
            } else {
                return e;
            }
        }
    }

    private OmgAs.OclExpression parseCallOn(SourceSpan sp, OmgAs.OclExpression receiver,
                                             boolean dotCall, Scope scope) {
        String name = parseSimpleName();
        if (ITERATORS.contains(name) && !dotCall) {
            return parseIterator(sp, name, receiver, scope);
        }
        // Qualified navigation: e.r[q1, q2]
        if (peek().isSymbol("[")) {
            advance();
            List<OmgAs.OclExpression> quals = new ArrayList<>();
            if (!peek().isSymbol("]")) {
                quals.add(parseExpression(scope));
                while (peek().isSymbol(",")) {
                    advance();
                    quals.add(parseExpression(scope));
                }
            }
            expectSymbol("]");
            return resolveProperty(sp, receiver, name, quals, dotCall);
        }
        if (peek().isSymbol("(")) {
            advance();
            List<OmgAs.OclExpression> args = new ArrayList<>();
            if (!peek().isSymbol(")")) {
                args.add(parseExpression(scope));
                while (peek().isSymbol(",")) {
                    advance();
                    args.add(parseExpression(scope));
                }
            }
            expectSymbol(")");
            return resolveOperation(sp, receiver, name, args, dotCall);
        }
        // Bare name access: property or zero-arg operation
        return resolveProperty(sp, receiver, name, List.of(), dotCall);
    }

    private OmgAs.OclExpression parseIterator(SourceSpan sp, String name,
                                               OmgAs.OclExpression source, Scope scope) {
        expectSymbol("(");
        if (source.type() == null || !source.type().isCollection()) {
            throw error("E_INVALID_CALL_SHAPE",
                    "iterator source must be Set or Bag, found " + source.type(), sp);
        }
        OclType elementType = source.type().elementType();
        String varName = null;
        OclType declared = null;
        OmgAs.OclExpression body = null;
        OmgAs.Variable iteratorVariable = null;
        if (peek().isSymbol("|")) {
            throw error("E_ARITY", "iterator requires a body", peek().span());
        }
        // iteratorDeclaration: simpleName [':' type] '|' expression
        if (peek().kind() == OclLexer.Kind.WORD) {
            int save = idx;
            String candidate = parseSimpleName();
            if (peek().isSymbol(":")) {
                advance();
                declared = parseType();
                expectSymbol("|");
                varName = candidate;
            } else if (peek().isSymbol("|")) {
                expectSymbol("|");
                varName = candidate;
            } else {
                // Not a declaration: parse the already-read token as the body
                // of the zero-iterator shorthand (implicit `it`).  The old
                // implementation restored the token index but then expected a
                // second opening parenthesis, rejecting valid forms such as
                // `xs->forAll(age >= 18)`.
                idx = save;
                Scope inner = scope.child();
                iteratorVariable = new OmgAs.Variable(sp, "it", elementType, null);
                inner.declare(iteratorVariable);
                body = parseExpression(inner);
                varName = "it";
            }
        } else {
            expectSymbol("|");
            varName = null;
        }
        if (body == null && varName != null) {
            OclType binder = declared == null ? elementType : declared;
            if (declared != null && !conforms(elementType, declared)) {
                throw error("E_TYPE", "iterator binder type " + declared
                        + " is not compatible with element type " + elementType, sp);
            }
            Scope inner = scope.child();
            iteratorVariable = new OmgAs.Variable(sp, varName, binder, null);
            inner.declare(iteratorVariable);
            body = parseExpression(inner);
        } else if (body == null) {
            // implicit iterator name `it`
            Scope inner = scope.child();
            iteratorVariable = new OmgAs.Variable(sp, "it", elementType, null);
            inner.declare(iteratorVariable);
            body = parseExpression(inner);
            varName = "it";
        }
        expectSymbol(")");
        OclType result;
        if (name.equals("select") || name.equals("reject")) {
            if (body.type() == null || !body.type().equals(OclType.BOOLEAN)) {
                throw error("E_TYPE", name + " body must be Boolean", sp);
            }
            result = source.type();
        } else if (name.equals("exists") || name.equals("forAll")) {
            if (body.type() == null || !body.type().equals(OclType.BOOLEAN)) {
                throw error("E_TYPE", name + " body must be Boolean", sp);
            }
            result = OclType.BOOLEAN;
        } else {
            if (body.type() == null || !body.type().isAtomic()) {
                throw error("N_UNSUPPORTED_CONSTRUCT",
                        "collect body must be atomic in OCL_val", sp);
            }
            result = OclType.bag(body.type());
        }
        return new OmgAs.IteratorExp(sp, name, source, List.of(iteratorVariable), body, result);
    }

    private OmgAs.OclExpression parsePrimary(Scope scope) {
        OclLexer.Tok t = peek();
        SourceSpan sp = t.span();
        if (t.isSymbol("(")) {
            advance();
            OmgAs.OclExpression e = parseExpression(scope);
            expectSymbol(")");
            return e;
        }
        if (t.isWord("if")) {
            advance();
            OmgAs.OclExpression c = parseExpression(scope);
            expectWord("then");
            OmgAs.OclExpression thenE = parseExpression(scope);
            expectWord("else");
            OmgAs.OclExpression elseE = parseExpression(scope);
            expectWord("endif");
            if (c.type() == null || !c.type().equals(OclType.BOOLEAN)) {
                throw error("E_TYPE", "if condition must be Boolean", sp);
            }
            OmgAs.OclExpression[] inferred = inferEmptyCollectionPair(thenE, elseE, sp);
            thenE = inferred[0];
            elseE = inferred[1];
            OclType join = joinType(thenE.type(), elseE.type(), sp);
            return new OmgAs.IfExp(sp, c, coerce(thenE, join, sp),
                    coerce(elseE, join, sp), join);
        }
        if (t.isWord("let")) {
            advance();
            String name = parseSimpleName();
            OclType declared = null;
            if (peek().isSymbol(":")) {
                advance();
                declared = parseType();
            }
            expectSymbol("=");
            OmgAs.OclExpression init = parseExpression(scope);
            expectWord("in");
            if (declared != null) {
                init = coerce(init, declared, sp);
            } else if (init.type() == null) {
                throw error("E_UNINFERRED_EMPTY_COLLECTION_TYPE",
                        "empty collection literal requires a declared let type", sp);
            }
            OclType binderType = declared == null ? init.type() : declared;
            Scope inner = scope.child();
            OmgAs.Variable binder = new OmgAs.Variable(sp, name, binderType, init);
            inner.declare(binder);
            OmgAs.OclExpression inExpr = parseExpression(inner);
            return new OmgAs.LetExp(sp, binder, inExpr);
        }
        if (t.kind() == OclLexer.Kind.INTEGER) {
            advance();
            return new OmgAs.IntegerLiteralExp(sp, t.intValue());
        }
        if (t.kind() == OclLexer.Kind.REAL) {
            advance();
            return new OmgAs.RealLiteralExp(sp, t.realValue());
        }
        if (t.kind() == OclLexer.Kind.STRING) {
            advance();
            return new OmgAs.StringLiteralExp(sp, t.text());
        }
        if (t.isWord("true")) {
            advance();
            return new OmgAs.BooleanLiteralExp(sp, true);
        }
        if (t.isWord("false")) {
            advance();
            return new OmgAs.BooleanLiteralExp(sp, false);
        }
        if (t.isWord("null")) {
            advance();
            return new OmgAs.NullLiteralExp(sp);
        }
        if (t.isWord("invalid")) {
            advance();
            return new OmgAs.InvalidLiteralExp(sp);
        }
        if (t.isWord("Set") || t.isWord("Bag")) {
            return parseCollectionLiteral(scope);
        }
        if (t.kind() == OclLexer.Kind.WORD) {
            // pathName possibly ending in a call or a variable / classifier
            int save = idx;
            List<String> segs = parseQualifiedNameSegments();
            String joined = String.join("::", segs);
            // self / variable lookup
            if (segs.size() == 1) {
                Optional<OmgAs.Variable> var = scope.lookup(segs.get(0));
                if (var.isPresent()) {
                    // segments already consumed by parseQualifiedNameSegments
                    return new OmgAs.VariableExp(sp, var.get());
                }
            }
            if (sm.hasClass(joined)) {
                return new OmgAs.TypeExp(sp, joined);
            }
            idx = save;
            String name = parseSimpleName();
            if (peek().isSymbol("(")) {
                advance();
                List<OmgAs.OclExpression> args = new ArrayList<>();
                if (!peek().isSymbol(")")) {
                    args.add(parseExpression(scope));
                    while (peek().isSymbol(",")) {
                        advance();
                        args.add(parseExpression(scope));
                    }
                }
                expectSymbol(")");
                // implicit-operation-call on self
                OmgAs.Variable self = scope.lookup("self").orElseThrow(() ->
                        error("E_RESOLUTION", "implicit operation requires self", sp));
                OmgAs.OclExpression selfRef = new OmgAs.VariableExp(sp, self);
                return resolveOperation(sp, selfRef, name, args, true);
            }
            Optional<OmgAs.Variable> var = scope.lookup(name);
            if (var.isEmpty()) {
                throw error("E_RESOLUTION", "unresolved name: " + name, sp);
            }
            return new OmgAs.VariableExp(sp, var.get());
        }
        throw error("E_PARSE", "unexpected token '" + t.text() + "'", sp);
    }

    private OmgAs.OclExpression parseCollectionLiteral(Scope scope) {
        SourceSpan sp = peek().span();
        boolean isSet = peek().isWord("Set");
        advance();
        expectSymbol("{");
        List<OmgAs.CollectionLiteralPart> parts = new ArrayList<>();
        if (!peek().isSymbol("}")) {
            while (true) {
                OmgAs.OclExpression first = parseExpression(scope);
                if (peek().isSymbol("..")) {
                    advance();
                    OmgAs.OclExpression last = parseExpression(scope);
                    if (!OclType.INTEGER.equals(first.type())
                            || !OclType.INTEGER.equals(last.type())) {
                        throw error("E_TYPE",
                                "collection range endpoints must be Integer, found "
                                        + first.type() + " and " + last.type(), sp);
                    }
                    parts.add(new OmgAs.CollectionRange(first.span(), first, last));
                } else {
                    parts.add(new OmgAs.CollectionItem(first.span(), first));
                }
                if (!peek().isSymbol(",")) {
                    break;
                }
                advance();
            }
        }
        expectSymbol("}");
        if (parts.isEmpty()) {
            return new OmgAs.CollectionLiteralExp(sp, isSet ? OmgAs.OmgCollectionKind.SET
                    : OmgAs.OmgCollectionKind.BAG, List.of(), null);
        }
        OclType join = null;
        for (OmgAs.CollectionLiteralPart part : parts) {
            if (part.type() == null) {
                throw error("E_TYPE", "collection element is unadmitted", sp);
            }
            join = join == null ? part.type() : joinType(join, part.type(), sp);
        }
        List<OmgAs.CollectionLiteralPart> coercedParts = new ArrayList<>(parts.size());
        for (OmgAs.CollectionLiteralPart part : parts) {
            if (part instanceof OmgAs.CollectionItem item) {
                OmgAs.OclExpression coerced = coerce(item.item, join, sp);
                coercedParts.add(new OmgAs.CollectionItem(coerced.span(), coerced));
            } else {
                // A range denotes Integer elements. Keep the resolved range in
                // OMG-AS so N_SM can enforce the executable-slice boundary.
                coercedParts.add(part);
            }
        }
        OclType type = isSet ? OclType.set(join) : OclType.bag(join);
        return new OmgAs.CollectionLiteralExp(sp, isSet ? OmgAs.OmgCollectionKind.SET
                : OmgAs.OmgCollectionKind.BAG, coercedParts, type);
    }

    // ---- resolution helpers ----------------------------------------------

    private OmgAs.OclExpression resolveProperty(SourceSpan sp, OmgAs.OclExpression receiver,
            String name, List<OmgAs.OclExpression> qualifiers, boolean dotCall) {
        OclType rt = receiver.type();
        if (rt == null) {
            throw error("E_TYPE", "receiver of '." + name + "' is unadmitted", sp);
        }
        if (!rt.isClass()) {
            throw error("E_INVALID_CALL_SHAPE",
                    "property access requires an object receiver, found " + rt, sp);
        }
        UmlAttribute attr = resolveAttribute(rt.className(), name, sp);
        if (attr != null) {
            if (!qualifiers.isEmpty()) {
                throw error("E_ARITY", "attributes take no qualifiers", sp);
            }
            return new OmgAs.PropertyCallExp(sp, attr.declaredType(), receiver,
                    List.of(), name, attr.key());
        }
        org.uet.dse.ocl2cypher.source.model.SchemaModel.AssociationClassNavigation acNavigation;
        try {
            acNavigation = sm.associationClassNavigation(rt.className(), name);
        } catch (IllegalArgumentException ambiguous) {
            throw error("E_RESOLUTION", ambiguous.getMessage(), sp);
        }
        if (acNavigation != null) {
            var assoc = acNavigation.association();
            validateQualifiers(sp, name, qualifiers, assoc);
            OclType element = OclType.clazz(acNavigation.associationClass().key());
            OclType result = acNavigation.toMany()
                    ? (acNavigation.unique() ? OclType.set(element) : OclType.bag(element))
                    : element;
            return new OmgAs.AssociationClassCallExp(sp, result, receiver, qualifiers,
                    acNavigation.participantRole(), acNavigation.associationClass().key());
        }
        org.uet.dse.ocl2cypher.source.model.SchemaModel.Navigation navigation;
        try {
            navigation = sm.navigation(rt.className(), name);
        } catch (IllegalArgumentException ambiguous) {
            throw error("E_RESOLUTION", ambiguous.getMessage(), sp);
        }
        if (navigation == null) {
            // navigation must start from one declared end
            throw error("E_RESOLUTION", "role '" + name + "' is not navigable from " + rt, sp);
        }
        var assoc = navigation.association();
        OclType target = OclType.clazz(navigation.targetClassKey());
        OclType type = navigation.toMany()
                ? (navigation.unique() ? OclType.set(target) : OclType.bag(target))
                : target;
        validateQualifiers(sp, name, qualifiers, assoc);
        return new OmgAs.PropertyCallExp(sp, type, receiver, qualifiers, name, assoc.key());
    }

    private void validateQualifiers(SourceSpan sp, String name,
                                    List<OmgAs.OclExpression> qualifiers,
                                    org.uet.dse.ocl2cypher.source.model.UmlAssociation assoc) {
        if (qualifiers.size() != assoc.qualifierNames().size()) {
            throw error("E_ARITY", "role '" + name + "' expects "
                    + assoc.qualifierNames().size() + " qualifier(s), found "
                    + qualifiers.size(), sp);
        }
        for (int i = 0; i < qualifiers.size(); i++) {
            OmgAs.OclExpression q = qualifiers.get(i);
            if (q.type() == null || !q.type().isAtomic()) {
                throw error("E_TYPE", "qualifiers must be scalar", sp);
            }
            OclType declaredQualifierType = assoc.qualifiers().get(i).declaredType();
            if (!q.type().equals(declaredQualifierType)) {
                throw error("E_TYPE", "qualifier '" + assoc.qualifierNames().get(i)
                        + "' requires " + declaredQualifierType + ", found " + q.type(), sp);
            }
        }
    }

    /** Resolve once at E_SM and reject unrelated inherited declarations as ambiguous. */
    private UmlAttribute resolveAttribute(String classKey, String attrName, SourceSpan sp) {
        Map<String, UmlAttribute> candidates = new LinkedHashMap<>();
        collectAttributeCandidates(classKey, attrName, new java.util.LinkedHashSet<>(), candidates);
        if (candidates.isEmpty()) {
            return null;
        }
        List<UmlAttribute> mostSpecific = new ArrayList<>(candidates.values());
        mostSpecific.removeIf(a -> candidates.values().stream().anyMatch(b ->
                !a.key().equals(b.key())
                        && sm.conforms(b.ownerClassKey(), a.ownerClassKey())
                        && !sm.conforms(a.ownerClassKey(), b.ownerClassKey())));
        if (mostSpecific.size() != 1) {
            throw error("E_AMBIGUOUS_PROPERTY", "attribute '" + attrName
                    + "' is inherited from multiple unrelated declarations on " + classKey,
                    sp);
        }
        return mostSpecific.get(0);
    }

    private void collectAttributeCandidates(String classKey, String attrName,
                                            java.util.Set<String> visited,
                                            Map<String, UmlAttribute> out) {
        if (classKey == null || !visited.add(classKey)) {
            return;
        }
        UmlAttribute own = sm.attribute(classKey, attrName);
        if (own != null) {
            out.putIfAbsent(own.key(), own);
        }
        var klass = sm.clazz(classKey);
        if (klass != null) {
            for (String superclass : klass.directSuperclassKeys()) {
                collectAttributeCandidates(superclass, attrName, visited, out);
            }
        }
    }

    private OmgAs.OclExpression resolveOperation(SourceSpan sp,
            OmgAs.OclExpression receiver, String name,
            List<OmgAs.OclExpression> args, boolean dotCall) {
        // allInstances on a classifier reference arrives via ClassRef handling
        OclType rt = receiver == null ? null : receiver.type();
        switch (name) {
            case "allInstances" -> {
                if (receiver instanceof OmgAs.TypeExp type) {
                    return operation(sp, OclOperation.ALL_INSTANCES, List.of(type),
                            OclType.set(OclType.clazz(type.referredType)));
                }
                throw error("E_INVALID_CALL_SHAPE", "allInstances applies to a classifier", sp);
            }
            case "oclIsTypeOf", "oclIsKindOf", "oclAsType" -> {
                OclOperation op = switch (name) {
                    case "oclIsTypeOf" -> OclOperation.OCL_IS_TYPE_OF;
                    case "oclIsKindOf" -> OclOperation.OCL_IS_KIND_OF;
                    default -> OclOperation.OCL_AS_TYPE;
                };
                if (args.size() != 1 || !(args.get(0) instanceof OmgAs.TypeExp ref)) {
                    throw error("E_ARITY", name + " requires exactly one classifier operand", sp);
                }
                if (rt == null || !rt.isClass()) {
                    throw error("E_TYPE", name + " receiver must be an object", sp);
                }
                OclType result = op.equals(OclOperation.OCL_AS_TYPE)
                        // The source profile admits only related class operands.
                        ? OclType.clazz(ref.referredType) : OclType.BOOLEAN;
                if (!sm.hasClass(ref.referredType)
                        || !(sm.conforms(rt.className(), ref.referredType)
                            || sm.conforms(ref.referredType, rt.className()))) {
                    throw error("E_TYPE", name + " requires related class types", sp);
                }
                return operation(sp, op, List.of(receiver, ref), result);
            }
            default -> {
            }
        }
        if (args.size() == 0) {
            OclOperation u = switch (name) {
                case "not" -> OclOperation.BOOLEAN_NOT;
                case "abs" -> OclOperation.NUMERIC_ABS;
                case "floor" -> OclOperation.REAL_FLOOR;
                case "round" -> OclOperation.REAL_ROUND;
                case "size" -> OclOperation.COLLECTION_SIZE;
                case "isEmpty" -> OclOperation.COLLECTION_IS_EMPTY;
                case "notEmpty" -> OclOperation.COLLECTION_NOT_EMPTY;
                case "sum" -> OclOperation.COLLECTION_SUM;
                default -> null;
            };
            if (u == null) {
                throw error("E_RESOLUTION", "unsupported zero-arg operation '" + name + "'", sp);
            }
            if (isUntypedEmptyCollection(receiver)) {
                throw error("E_UNINFERRED_EMPTY_COLLECTION_TYPE",
                        "empty collection literal has no element type for operation '"
                                + name + "'", sp);
            }
            switch (u) {
                case BOOLEAN_NOT -> requireBoolean(rt, sp);
                case NUMERIC_ABS -> requireNumeric(rt, sp);
                case REAL_FLOOR, REAL_ROUND -> requireReal(rt, sp);
                case COLLECTION_SIZE, COLLECTION_IS_EMPTY, COLLECTION_NOT_EMPTY ->
                        requireCollection(rt, sp);
                case COLLECTION_SUM -> {
                    OclType elementType = elementOf(rt, sp);
                    requireNumeric(elementType, sp);
                }
                default -> throw error("E_RESOLUTION", "unhandled op " + u, sp);
            }
            OclType result = switch (u) {
                case BOOLEAN_NOT -> OclType.BOOLEAN;
                case REAL_FLOOR, REAL_ROUND -> OclType.INTEGER;
                case NUMERIC_ABS -> rt;
                case COLLECTION_SIZE -> OclType.INTEGER;
                case COLLECTION_IS_EMPTY, COLLECTION_NOT_EMPTY -> OclType.BOOLEAN;
                case COLLECTION_SUM -> elementOf(rt, sp);
                default -> throw error("E_RESOLUTION", "unhandled op " + u, sp);
            };
            if (dotCall && requiresArrow(u)) {
                throw error("E_INVALID_CALL_SHAPE",
                        "collection operation '" + name + "' requires '->'", sp);
            }
            return operation(sp, u, List.of(receiver), result);
        }
        if (args.size() == 1) {
            OmgAs.OclExpression arg = args.get(0);
            OclOperation b = switch (name) {
                case "div" -> OclOperation.INTEGER_DIVIDE;
                case "mod" -> OclOperation.INTEGER_MOD;
                case "max" -> OclOperation.NUMERIC_MAX;
                case "min" -> OclOperation.NUMERIC_MIN;
                case "count" -> OclOperation.COLLECTION_COUNT;
                case "includes" -> OclOperation.COLLECTION_INCLUDES;
                case "excludes" -> OclOperation.COLLECTION_EXCLUDES;
                case "includesAll" -> OclOperation.COLLECTION_INCLUDES_ALL;
                case "excludesAll" -> OclOperation.COLLECTION_EXCLUDES_ALL;
                case "union" -> OclOperation.SET_UNION;
                case "intersection" -> OclOperation.SET_INTERSECTION;
                default -> null;
            };
            if (b == null) {
                throw error("E_RESOLUTION", "unsupported operation '" + name + "'", sp);
            }
            if (isUntypedEmptyCollection(receiver)) {
                if (b == OclOperation.COLLECTION_COUNT
                        || b == OclOperation.COLLECTION_INCLUDES
                        || b == OclOperation.COLLECTION_EXCLUDES) {
                    if (arg.type() == null || !arg.type().isAtomic()) {
                        throw error("E_UNINFERRED_EMPTY_COLLECTION_TYPE",
                                "cannot infer empty collection element type from " + arg.type(), sp);
                    }
                    receiver = typeEmptyCollection(receiver, collectionTypeLike(receiver,
                            arg.type()), sp);
                } else if (arg.type() != null && arg.type().isCollection()) {
                    receiver = typeEmptyCollection(receiver, arg.type(), sp);
                }
                rt = receiver.type();
            }
            if (isUntypedEmptyCollection(arg) && rt != null && rt.isCollection()) {
                arg = typeEmptyCollection(arg, rt, sp);
            }
            OmgAs.OclExpression effectiveReceiver = receiver;
            OmgAs.OclExpression effectiveArg = arg;
            switch (b) {
                case INTEGER_DIVIDE, INTEGER_MOD -> {
                    requireInteger(rt, sp);
                    requireInteger(arg.type(), sp);
                    if (!dotCall) {
                        throw error("E_INVALID_CALL_SHAPE",
                                "numeric operation '" + name + "' requires '.'", sp);
                    }
                }
                case NUMERIC_MAX, NUMERIC_MIN -> {
                    requireNumeric(rt, sp);
                    requireNumeric(arg.type(), sp);
                    OclType numericJoin = joinType(rt, arg.type(), sp);
                    effectiveReceiver = coerce(receiver, numericJoin, sp);
                    effectiveArg = coerce(arg, numericJoin, sp);
                }
                case COLLECTION_COUNT, COLLECTION_INCLUDES, COLLECTION_EXCLUDES -> {
                    requireCollectionCall(name, rt, dotCall, sp);
                    OclType elementJoin = joinType(rt.elementType(), arg.type(), sp);
                    OclType targetCollection = rt.kind() == OclType.Kind.SET
                            ? OclType.set(elementJoin) : OclType.bag(elementJoin);
                    effectiveReceiver = coerce(receiver, targetCollection, sp);
                    effectiveArg = coerce(arg, elementJoin, sp);
                }
                case COLLECTION_INCLUDES_ALL, COLLECTION_EXCLUDES_ALL -> {
                    requireCollectionCall(name, rt, dotCall, sp);
                    requireSameCollectionType(rt, arg.type(), name, sp);
                }
                case SET_UNION, SET_INTERSECTION -> {
                    requireCollectionCall(name, rt, dotCall, sp);
                    if (rt.kind() != OclType.Kind.SET) {
                        throw error("E_TYPE", name + " requires a Set receiver", sp);
                    }
                    requireSameCollectionType(rt, arg.type(), name, sp);
                }
                default -> throw error("E_RESOLUTION", "unhandled op " + b, sp);
            }
            OclType result = switch (b) {
                case INTEGER_DIVIDE, INTEGER_MOD -> OclType.INTEGER;
                case NUMERIC_MAX, NUMERIC_MIN -> effectiveReceiver.type();
                case COLLECTION_COUNT -> OclType.INTEGER;
                case COLLECTION_INCLUDES, COLLECTION_EXCLUDES,
                     COLLECTION_INCLUDES_ALL, COLLECTION_EXCLUDES_ALL -> OclType.BOOLEAN;
                default -> rt; // Set algebra keeps the receiver set type
            };
            return operation(sp, b, List.of(effectiveReceiver, effectiveArg), result);
        }
        throw error("E_ARITY", "operation '" + name + "' expects 0 or 1 argument(s)", sp);
    }

    private boolean requiresArrow(OclOperation u) {
        return switch (u) {
            case COLLECTION_SIZE, COLLECTION_IS_EMPTY, COLLECTION_NOT_EMPTY, COLLECTION_SUM -> true;
            default -> false;
        };
    }

    private OclType elementOf(OclType t, SourceSpan sp) {
        if (t == null || !t.isCollection()) {
            throw error("E_TYPE", "operation requires a collection receiver", sp);
        }
        return t.elementType();
    }

    private void requireCollection(OclType type, SourceSpan sp) {
        if (type == null || !type.isCollection()) {
            throw error("E_TYPE", "collection receiver required, found " + type, sp);
        }
    }

    private void requireCollectionCall(String name, OclType receiverType,
                                       boolean dotCall, SourceSpan sp) {
        requireCollection(receiverType, sp);
        if (dotCall) {
            throw error("E_INVALID_CALL_SHAPE",
                    "collection operation '" + name + "' requires '->'", sp);
        }
    }

    private void requireSameCollectionType(OclType left, OclType right,
                                           String operation, SourceSpan sp) {
        if (right == null || !right.isCollection() || !left.equals(right)) {
            throw error("E_TYPE", operation + " requires two collections of the same type; found "
                    + left + " and " + right, sp);
        }
    }

    // ---- typing helpers ---------------------------------------------------

    private OmgAs.OclExpression binary(OclOperation op, OmgAs.OclExpression left,
            OmgAs.OclExpression right, OclType result, SourceSpan sp) {
        return operation(sp, op, List.of(left, right), result);
    }

    private OmgAs.OclExpression operation(SourceSpan span, OclOperation operation,
            List<OmgAs.OclExpression> operands, OclType result) {
        if (operands.isEmpty()) {
            throw new IllegalArgumentException("resolved operation requires a source");
        }
        return new OmgAs.OperationCallExp(span, result, operands.get(0),
                operation.name(), operands.subList(1, operands.size()));
    }

    private OmgAs.OclExpression coerce(OmgAs.OclExpression e, OclType to, SourceSpan sp) {
        if (isUntypedEmptyCollection(e)) {
            return typeEmptyCollection(e, to, sp);
        }
        if (e.type() != null && e.type().equals(to)) {
            return e;
        }
        if (e.type() == null) {
            throw error("E_TYPE", "unadmitted operand cannot be coerced", sp);
        }
        // Only Integer→Real and class upcast change the value carrier at AS level.
        if (e.type().equals(OclType.INTEGER) && to.equals(OclType.REAL)) {
            return new OmgAs.CoerceExp(sp, e, e.type(), to,
                    org.uet.dse.ocl2cypher.core.CoreExpr.CoercionKind.INTEGER_TO_REAL);
        }
        if (e.type().isClass() && to.isClass() && conforms(e.type(), to)) {
            return new OmgAs.CoerceExp(sp, e, e.type(), to,
                    org.uet.dse.ocl2cypher.core.CoreExpr.CoercionKind.CLASS_UPCAST);
        }
        if (e.type().isCollection() && to.isCollection()
                && e.type().kind() == to.kind()
                && ((e.type().elementType().equals(OclType.INTEGER)
                        && to.elementType().equals(OclType.REAL))
                    || (e.type().elementType().isClass()
                        && to.elementType().isClass()
                        && conforms(e.type().elementType(), to.elementType())))) {
            return new OmgAs.CoerceExp(sp, e, e.type(), to,
                    org.uet.dse.ocl2cypher.core.CoreExpr.CoercionKind.COLLECTION_ELEMENT_COERCION);
        }
        throw error("E_TYPE", "unsupported coercion from " + e.type() + " to " + to, sp);
    }

    private OmgAs.OclExpression[] inferEmptyCollectionPair(OmgAs.OclExpression left,
            OmgAs.OclExpression right, SourceSpan sp) {
        if (isUntypedEmptyCollection(left) && right.type() != null) {
            left = typeEmptyCollection(left, right.type(), sp);
        }
        if (isUntypedEmptyCollection(right) && left.type() != null) {
            right = typeEmptyCollection(right, left.type(), sp);
        }
        if (left.type() == null || right.type() == null) {
            throw error("E_UNINFERRED_EMPTY_COLLECTION_TYPE",
                    "empty collection literal requires an expected collection type", sp);
        }
        return new OmgAs.OclExpression[]{left, right};
    }

    private boolean isUntypedEmptyCollection(OmgAs.OclExpression expression) {
        return expression instanceof OmgAs.CollectionLiteralExp literal
                && literal.part.isEmpty() && literal.type() == null;
    }

    private OclType collectionTypeLike(OmgAs.OclExpression untypedEmpty, OclType elementType) {
        OmgAs.CollectionLiteralExp literal = (OmgAs.CollectionLiteralExp) untypedEmpty;
        return literal.kind == OmgAs.OmgCollectionKind.SET
                ? OclType.set(elementType) : OclType.bag(elementType);
    }

    private OmgAs.OclExpression typeEmptyCollection(OmgAs.OclExpression expression,
                                                     OclType expected, SourceSpan sp) {
        if (!isUntypedEmptyCollection(expression)) {
            return expression;
        }
        OmgAs.CollectionLiteralExp literal = (OmgAs.CollectionLiteralExp) expression;
        if (expected == null || !expected.isCollection()
                || (literal.kind == OmgAs.OmgCollectionKind.SET
                        && expected.kind() != OclType.Kind.SET)
                || (literal.kind == OmgAs.OmgCollectionKind.BAG
                        && expected.kind() != OclType.Kind.BAG)) {
            throw error("E_TYPE", "empty " + literal.kind
                    + " requires a matching expected collection type, found " + expected, sp);
        }
        return new OmgAs.CollectionLiteralExp(literal.span(), literal.kind, List.of(), expected);
    }

    private OclType joinType(OclType a, OclType b, SourceSpan sp) {
        if (a == null || b == null) {
            throw error("E_TYPE", "unadmitted operand in join", sp);
        }
        OclType j = OclType.join(a, b, sm::conforms);
        if (j == null) {
            throw error("E_TYPE", "no common type for " + a + " and " + b, sp);
        }
        return j;
    }

    private boolean conforms(OclType from, OclType to) {
        return from.equals(to)
                || (from.equals(OclType.INTEGER) && to.equals(OclType.REAL))
                || (from.isClass() && to.isClass() && sm.conforms(from.className(), to.className()));
    }

    private void requireNumeric(OclType t, SourceSpan sp) {
        if (t == null || !t.isNumeric()) {
            throw error("E_TYPE", "numeric operand required, found " + t, sp);
        }
    }

    private void requireBoolean(OclType t, SourceSpan sp) {
        if (t == null || !t.equals(OclType.BOOLEAN)) {
            throw error("E_TYPE", "Boolean operand required, found " + t, sp);
        }
    }

    private void requireReal(OclType t, SourceSpan sp) {
        if (t == null || !t.equals(OclType.REAL)) {
            throw error("E_TYPE", "Real operand required, found " + t, sp);
        }
    }

    private void requireInteger(OclType t, SourceSpan sp) {
        if (t == null || !t.equals(OclType.INTEGER)) {
            throw error("E_TYPE", "Integer operand required, found " + t, sp);
        }
    }

    // ---- token helpers ----------------------------------------------------

    private OclLexer.Tok peek() {
        return toks.get(idx);
    }

    private OclLexer.Tok advance() {
        OclLexer.Tok t = toks.get(idx);
        if (t.kind() != OclLexer.Kind.EOF) {
            idx++;
        }
        return t;
    }

    private void expectSymbol(String s) {
        if (!peek().isSymbol(s)) {
            throw error("E_PARSE", "expected '" + s + "', found '" + peek().text() + "'",
                    peek().span());
        }
        advance();
    }

    private void expectWord(String w) {
        if (!peek().isWord(w)) {
            throw error("E_PARSE", "expected '" + w + "', found '" + peek().text() + "'",
                    peek().span());
        }
        advance();
    }

    private String parseSimpleName() {
        OclLexer.Tok t = peek();
        if (t.kind() != OclLexer.Kind.WORD) {
            throw error("E_PARSE", "expected a name, found '" + t.text() + "'", t.span());
        }
        advance();
        return t.text();
    }

    private List<String> parseQualifiedNameSegments() {
        List<String> segs = new ArrayList<>();
        segs.add(parseSimpleName());
        while (peek().isSymbol("::")) {
            advance();
            segs.add(parseSimpleName());
        }
        return segs;
    }

    private String parsePathName() {
        return String.join("::", parseQualifiedNameSegments());
    }

    private OclType parseType() {
        OclLexer.Tok t = peek();
        SourceSpan sp = t.span();
        if (t.isWord("Boolean") || t.isWord("Integer") || t.isWord("Real") || t.isWord("String")) {
            advance();
            return switch (t.text()) {
                case "Boolean" -> OclType.BOOLEAN;
                case "Integer" -> OclType.INTEGER;
                case "Real" -> OclType.REAL;
                default -> OclType.STRING;
            };
        }
        if (t.isWord("Set") || t.isWord("Bag")) {
            boolean set = t.isWord("Set");
            advance();
            expectSymbol("(");
            OclType el = parseType();
            expectSymbol(")");
            return set ? OclType.set(el) : OclType.bag(el);
        }
        String path = parsePathName();
        if (sm.clazz(path) == null) {
            throw error("E_TYPE", "unknown type " + path, sp);
        }
        return OclType.clazz(path);
    }

    private FrontendError error(String code, String message, SourceSpan span) {
        return new FrontendError(Diagnostic.builder(code, Stage.E_SM, message)
                .span(span)
                .rule("E-F")
                .build());
    }

    /** Parser control-flow carrier; never a runtime value. */
    private static final class FrontendError extends RuntimeException {
        private final Diagnostic diagnostic;

        FrontendError(Diagnostic diagnostic) {
            super(diagnostic.toString(), null, false, false);
            this.diagnostic = diagnostic;
        }

        Diagnostic diagnostic() {
            return diagnostic;
        }
    }

    /** Lexical scope: declaration lookup is by name but each binder is fresh. */
    static final class Scope {
        private final java.util.Map<String, OmgAs.Variable> map =
                new java.util.LinkedHashMap<>();
        private final Scope parent;

        Scope() {
            this.parent = null;
        }

        private Scope(Scope parent) {
            this.parent = parent;
        }

        Scope child() {
            return new Scope(this);
        }

        void declare(OmgAs.Variable declaration) {
            map.put(declaration.name, declaration);
        }

        Optional<OmgAs.Variable> lookup(String name) {
            Scope s = this;
            while (s != null) {
                OmgAs.Variable declaration = s.map.get(name);
                if (declaration != null) {
                    return Optional.of(declaration);
                }
                s = s.parent;
            }
            return Optional.empty();
        }
    }
}
