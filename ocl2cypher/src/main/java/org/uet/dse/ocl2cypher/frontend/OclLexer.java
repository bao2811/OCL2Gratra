package org.uet.dse.ocl2cypher.frontend;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.uet.dse.ocl2cypher.diagnostics.SourceSpan;

/**
 * Lexer for the OCL_val surface determined by
 * {@code research/OCLscope/OCL-Concrete-Grammar.ebnf}.
 *
 * <p>Design decisions taken directly from the EBNF:
 * <ul>
 *   <li>Words are returned as {@link Kind#WORD}; the parser classifies
 *       contextual keywords ({@code div}, {@code mod}, iterator names) by
 *       position, never globally — "the spellings div, mod and the iterator
 *       names are matched contextually by the parser and remain ordinary
 *       identifiers in all other positions."</li>
 *   <li>Numeric tokens are lexed exactly: an integer keeps its whole
 *       {@link BigInteger}, a real keeps its whole {@link BigDecimal}
 *       (maximal munch; INTEGER is chosen instead of REAL when no fraction
 *       or exponent is present).</li>
 *   <li>Longest-match order is mandatory: {@code :: -> <= >= <> ..} before
 *       their one-character prefixes.</li>
 *   <li>Surface {@code null} and {@code invalid} are recognized as words;
 *       admission later rejects them without producing a typed bottom.</li>
 * </ul>
 */
final class OclLexer {

    enum Kind {
        WORD,
        INTEGER,
        REAL,
        STRING,
        SYMBOL,
        EOF
    }

    /** One lexical token with its exact numeric value where applicable. */
    record Tok(Kind kind, String text, BigInteger intValue, BigDecimal realValue,
               SourceSpan span) {

        boolean isWord(String w) {
            return kind == Kind.WORD && text.equals(w);
        }

        boolean isSymbol(String s) {
            return kind == Kind.SYMBOL && text.equals(s);
        }

        boolean isEnd() {
            return kind == Kind.EOF;
        }
    }

    private final String src;
    private int pos;
    private int line = 1;
    private int lineStart;

    OclLexer(String src) {
        this.src = src == null ? "" : src;
    }

    List<Tok> tokenize() {
        List<Tok> out = new ArrayList<>();
        while (true) {
            Tok t = next();
            out.add(t);
            if (t.kind() == Kind.EOF) {
                return out;
            }
        }
    }

    private Tok next() {
        skipTrivia();
        int start = pos;
        if (pos >= src.length()) {
            return new Tok(Kind.EOF, "", null, null, span(start, pos));
        }
        char c = src.charAt(pos);

        // Quoted string literal, multi-fragment with escapes.
        if (c == '\'') {
            return readString(start);
        }
        // Escaped identifier _'...' behaves as a plain word.
        if (c == '_' && pos + 1 < src.length() && src.charAt(pos + 1) == '\'') {
            pos += 2;
            StringBuilder sb = new StringBuilder();
            while (pos < src.length() && src.charAt(pos) != '\'') {
                sb.append(src.charAt(pos++));
            }
            if (pos < src.length()) {
                pos++; // closing quote
            }
            return new Tok(Kind.WORD, sb.toString(), null, null, span(start, pos));
        }
        // Name start.
        if (isNameStart(c)) {
            while (pos < src.length() && isNameContinue(src.charAt(pos))) {
                pos++;
            }
            return new Tok(Kind.WORD, src.substring(start, pos), null, null, span(start, pos));
        }
        // Numeric literal with maximal munch.
        if (Character.isDigit(c)) {
            return readNumber(start);
        }
        // Longest-match symbols first.
        for (String s : new String[]{"::", "->", "<=", ">=", "<>", ".."}) {
            if (src.startsWith(s, pos)) {
                pos += s.length();
                return new Tok(Kind.SYMBOL, s, null, null, span(start, pos));
            }
        }
        pos++;
        return new Tok(Kind.SYMBOL, src.substring(start, pos), null, null, span(start, pos));
    }

    private Tok readString(int start) {
        StringBuilder sb = new StringBuilder();
        // STRING_LITERAL may concatenate several quoted fragments.
        while (true) {
            if (pos >= src.length() || src.charAt(pos) != '\'') {
                break;
            }
            pos++; // opening quote
            while (pos < src.length() && src.charAt(pos) != '\'') {
                char ch = src.charAt(pos);
                if (ch == '\\' && pos + 1 < src.length()) {
                    char esc = src.charAt(pos + 1);
                    switch (esc) {
                        case 'n' -> sb.append('\n');
                        case 't' -> sb.append('\t');
                        case 'r' -> sb.append('\r');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case '0' -> sb.append('\0');
                        case '\'' -> sb.append('\'');
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        default -> sb.append(esc);
                    }
                    pos += 2;
                } else {
                    sb.append(ch);
                    pos++;
                }
            }
            if (pos < src.length()) {
                pos++; // closing quote
            }
            // Look ahead across whitespace for another fragment.
            int save = pos;
            int ws = pos;
            while (ws < src.length() && Character.isWhitespace(src.charAt(ws))) {
                ws++;
            }
            if (ws < src.length() && src.charAt(ws) == '\'') {
                pos = ws;
            } else {
                pos = save;
                break;
            }
        }
        // text carries the decoded literal value for STRING tokens.
        return new Tok(Kind.STRING, sb.toString(), null, null, span(start, pos));
    }

    private Tok readNumber(int start) {
        int j = pos;
        while (j < src.length() && Character.isDigit(src.charAt(j))) {
            j++;
        }
        boolean isReal = false;
        // Fraction: '.' must be present; '..' is a range delimiter, not a fraction.
        if (j < src.length() && src.charAt(j) == '.'
                && j + 1 < src.length() && Character.isDigit(src.charAt(j + 1))) {
            isReal = true;
            j++;
            while (j < src.length() && Character.isDigit(src.charAt(j))) {
                j++;
            }
        }
        // Exponent.
        if (j < src.length() && (src.charAt(j) == 'e' || src.charAt(j) == 'E')) {
            int k = j + 1;
            if (k < src.length() && (src.charAt(k) == '+' || src.charAt(k) == '-')) {
                k++;
            }
            if (k < src.length() && Character.isDigit(src.charAt(k))) {
                isReal = true;
                while (k < src.length() && Character.isDigit(src.charAt(k))) {
                    k++;
                }
                j = k;
            }
        }
        String raw = src.substring(start, j);
        pos = j;
        SourceSpan sp = span(start, j);
        if (isReal) {
            return new Tok(Kind.REAL, raw, null, new BigDecimal(raw), sp);
        }
        return new Tok(Kind.INTEGER, raw, new BigInteger(raw), null, sp);
    }

    private void skipTrivia() {
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == '\n') {
                pos++;
                line++;
                lineStart = pos;
            } else if (Character.isWhitespace(c)) {
                pos++;
            } else if (c == '-' && pos + 1 < src.length() && src.charAt(pos + 1) == '-') {
                while (pos < src.length() && src.charAt(pos) != '\n') {
                    pos++;
                }
            } else {
                return;
            }
        }
    }

    private static boolean isNameStart(char c) {
        return c == '_' || c == '$' || Character.isLetter(c);
    }

    private static boolean isNameContinue(char c) {
        return isNameStart(c) || Character.isDigit(c);
    }

    private SourceSpan span(int from, int to) {
        int col = from - lineStart + 1;
        return new SourceSpan(from, to, line, col);
    }
}
