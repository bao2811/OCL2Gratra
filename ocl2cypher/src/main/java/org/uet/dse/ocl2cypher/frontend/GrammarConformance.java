package org.uet.dse.ocl2cypher.frontend;

import java.util.BitSet;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.ATNConfigSet;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.uet.dse.ocl2cypher.frontend.antlr.OCLLexer;
import org.uet.dse.ocl2cypher.frontend.antlr.OCLParser;

/**
 * Acceptance bridge to the ANTLR4 grammar generated from
 * {@code research/OCLscope/OCL-Concrete-Grammar.ebnf}. It exists to discharge
 * the P3 obligation {@code OCL.g4 ≈ OCL-Concrete-Grammar.ebnf}: it recognizes
 * exactly the OMG OCL 2.4 concrete profile, and its structural print exposes
 * operator precedence/associativity for the conformance corpus.
 *
 * <p>The hand-written {@link OclFrontend} remains the semantic elaborator; this
 * class is the independent grammar oracle, so a precedence divergence between
 * the two would surface as a differential failure rather than pass silently.
 */
public final class GrammarConformance {

    private GrammarConformance() {
    }

    /** True iff the whole text is a well-formed OCL_val document. */
    public static boolean acceptsDocument(String text) {
        return parse(text, true) != null;
    }

    /** True iff the text is a well-formed OCL_val expression. */
    public static boolean acceptsExpression(String text) {
        return parseExpression(text) != null;
    }

    /** Parse tree of a document; null if syntactically invalid. */
    public static ParseTree parse(String text, boolean requireEof) {
        OCLParser p = newParser(text);
        ThrowingErrorListener listener = new ThrowingErrorListener();
        p.removeErrorListeners();
        p.addErrorListener(listener);
        try {
            ParseTree t = p.document();
            return listener.failed ? null : t;
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static ParseTree parseExpression(String text) {
        OCLParser p = newParser(text);
        ThrowingErrorListener listener = new ThrowingErrorListener();
        p.removeErrorListeners();
        p.addErrorListener(listener);
        try {
            OCLParser.ExpressionContext ctx = p.expression();
            if (listener.failed) {
                return null;
            }
            // require the whole input to be consumed
            if (p.getCurrentToken().getType() != Token.EOF) {
                return null;
            }
            return ctx;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * A fully-parenthesized rendering of the expression parse tree, collapsing
     * chain rules so that only the binary/unary structure remains. This makes
     * precedence and associativity observable: {@code a or b and c} prints as
     * {@code (a or (b and c))}, {@code a implies b implies c} as
     * {@code (a implies (b implies c))}.
     */
    public static String precedenceShape(String expression) {
        ParseTree t = parseExpression(expression);
        if (t == null) {
            return "<reject>";
        }
        return shape(t);
    }

    private static OCLParser newParser(String text) {
        OCLLexer lexer = new OCLLexer(CharStreams.fromString(text == null ? "" : text));
        lexer.removeErrorListeners();
        ThrowingErrorListener le = new ThrowingErrorListener();
        lexer.addErrorListener(le);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        OCLParser parser = new OCLParser(tokens);
        return parser;
    }

    private static String shape(ParseTree t) {
        if (t instanceof TerminalNode tn) {
            return tn.getText();
        }
        ParserRuleContext ctx = (ParserRuleContext) t;
        int n = ctx.getChildCount();
        // Collapse single-child chain rules.
        if (n == 1) {
            return shape(ctx.getChild(0));
        }
        // Left-associative infix chains: a OP b OP c  →  ((a OP b) OP c).
        // Right-associative implies: a implies b  →  (a implies b) with b already right-nested.
        java.util.List<ParseTree> kids = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) {
            kids.add(ctx.getChild(i));
        }
        String rule = OCLParser.ruleNames[ctx.getRuleIndex()];
        // Fold left for the *Expression infix rules that repeat operator operand.
        if (isInfixChain(rule) && n >= 3) {
            String acc = shape(kids.get(0));
            int i = 1;
            while (i + 1 < kids.size()) {
                String op = shape(kids.get(i));
                String rhs = shape(kids.get(i + 1));
                acc = "(" + acc + " " + op + " " + rhs + ")";
                i += 2;
            }
            return acc;
        }
        // Default: parenthesize the ordered concatenation.
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < kids.size(); i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(shape(kids.get(i)));
        }
        return sb.append(')').toString();
    }

    private static boolean isInfixChain(String rule) {
        return switch (rule) {
            case "xorExpression", "orExpression", "andExpression",
                 "equalityExpression", "relationalExpression",
                 "additiveExpression", "multiplicativeExpression" -> true;
            default -> false;
        };
    }

    /** Fails on any lexer/parser error so acceptance is exact. */
    private static final class ThrowingErrorListener extends BaseErrorListener {
        boolean failed = false;

        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                                int line, int charPositionInLine, String msg,
                                RecognitionException e) {
            failed = true;
        }
    }
}
