package org.uet.dse.neo4jtgg.ocl.ir;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Strict parser for the closed concrete-Cypher fragment emitted by the
 * production CQM renderer. It produces a typed {@link RawCypherAst} boundary
 * tree and rejects placeholders, comments, unknown characters, unbalanced
 * groups, bare UNION, empty query arms, and malformed top-level clauses.
 */
public final class RawCypherParser {
    private static final Map<String, String> CLOSING = Map.of("(", ")", "[", "]", "{", "}");
    private static final Pattern PLACEHOLDER = Pattern.compile("<[A-Za-z_][A-Za-z0-9_]*>");
    private static final Set<String> KEYWORDS = Set.of(
            "ALL", "AND", "AS", "ASC", "BY", "CALL", "CASE", "COLLECT", "CONTAINS", "COUNT",
            "DESC", "DISTINCT", "ELSE", "END", "ENDS", "EXISTS", "FALSE", "IN", "IS", "LIMIT",
            "MATCH", "NONE", "NOT", "NULL", "OPTIONAL", "OR", "ORDER", "RETURN", "SINGLE", "SKIP",
            "STARTS", "THEN", "TRUE", "UNION", "UNWIND", "WHEN", "WHERE", "WITH", "XOR");
    private static final Set<String> QUERY_GROUP_PREFIXES = Set.of("CALL", "COLLECT", "COUNT", "EXISTS");
    private static final Set<String> TWO_CHARACTER_OPERATORS = Set.of(
            "<=", ">=", "<>", "!=", "=~", "->", "<-");
    private static final String SINGLE_CHARACTER_TOKENS = "()[]{}.,:|+-*/%=<>";

    public RawCypherAst.ProductionQuery parse(String source) {
        if (source == null) throw new IllegalArgumentException("Cypher query is null");
        if (PLACEHOLDER.matcher(source).find())
            throw new IllegalArgumentException("Unexpanded generated placeholder");
        Tokenization tokenization = tokenize(source);
        List<RawCypherAst.RawNode> root = new ArrayList<>();
        Deque<GroupBuilder> stack = new ArrayDeque<>();
        List<RawCypherAst.RawNode> current = root;
        for (RawCypherAst.RawAtom token : tokenization.tokens()) {
            if (CLOSING.containsKey(token.text())) {
                stack.push(new GroupBuilder(token, current));
                current = new ArrayList<>();
            } else if (CLOSING.containsValue(token.text())) {
                if (stack.isEmpty() || !CLOSING.get(stack.peek().open().text()).equals(token.text())) {
                    throw new IllegalArgumentException("Unbalanced Cypher delimiter: " + token.text());
                }
                GroupBuilder builder = stack.pop();
                RawCypherAst.RawGroup group = new RawCypherAst.RawGroup(
                        builder.open(), List.copyOf(current), token);
                current = builder.parent();
                current.add(group);
            } else {
                current.add(token);
            }
        }
        if (!stack.isEmpty())
            throw new IllegalArgumentException("Unclosed Cypher delimiter: " + stack.peek().open().text());
        if (root.isEmpty()) throw new IllegalArgumentException("Cypher query is empty");
        validateQuery(root, "document");
        validateQueryGroups(root);
        return new RawCypherAst.ProductionQuery(root, tokenization.trailingTrivia());
    }

    private Tokenization tokenize(String source) {
        List<RawCypherAst.RawAtom> tokens = new ArrayList<>();
        int index = 0;
        while (index < source.length()) {
            int triviaStart = index;
            while (index < source.length() && Character.isWhitespace(source.charAt(index))) index++;
            String trivia = source.substring(triviaStart, index);
            if (index >= source.length()) return new Tokenization(List.copyOf(tokens), trivia);
            int start = index;
            char current = source.charAt(index);
            if (current == '\'' || current == '"' || current == '`') {
                int end = quotedEnd(source, index, current);
                tokens.add(atom(current == '`' ? RawCypherAst.RawAtomKind.QUOTED_IDENTIFIER
                        : RawCypherAst.RawAtomKind.STRING, trivia, source.substring(index, end)));
                index = end;
                continue;
            }
            if (current == '$') {
                index++;
                if (index >= source.length() || !identifierStart(source.charAt(index)))
                    throw syntax("Invalid Cypher parameter", start);
                index++;
                while (index < source.length() && identifierPart(source.charAt(index))) index++;
                tokens.add(atom(RawCypherAst.RawAtomKind.PARAMETER, trivia, source.substring(start, index)));
                continue;
            }
            if (identifierStart(current)) {
                index++;
                while (index < source.length() && identifierPart(source.charAt(index))) index++;
                String text = source.substring(start, index);
                RawCypherAst.RawAtomKind kind = KEYWORDS.contains(text.toUpperCase(Locale.ROOT))
                        ? RawCypherAst.RawAtomKind.KEYWORD : RawCypherAst.RawAtomKind.IDENTIFIER;
                tokens.add(atom(kind, trivia, text));
                continue;
            }
            if (Character.isDigit(current)) {
                index++;
                while (index < source.length() && Character.isDigit(source.charAt(index))) index++;
                if (index + 1 < source.length() && source.charAt(index) == '.'
                        && Character.isDigit(source.charAt(index + 1))) {
                    index++;
                    while (index < source.length() && Character.isDigit(source.charAt(index))) index++;
                }
                tokens.add(atom(RawCypherAst.RawAtomKind.NUMBER, trivia, source.substring(start, index)));
                continue;
            }
            String pair = index + 1 < source.length() ? source.substring(index, index + 2) : "";
            if (pair.equals("//") || pair.equals("/*") || pair.equals("*/"))
                throw syntax("Comments are outside the generated Cypher fragment", index);
            if (TWO_CHARACTER_OPERATORS.contains(pair)) {
                tokens.add(atom(RawCypherAst.RawAtomKind.OPERATOR, trivia, pair));
                index += 2;
            } else if (SINGLE_CHARACTER_TOKENS.indexOf(current) >= 0) {
                RawCypherAst.RawAtomKind kind = "()[]{}.,:|".indexOf(current) >= 0
                        ? RawCypherAst.RawAtomKind.SYMBOL : RawCypherAst.RawAtomKind.OPERATOR;
                tokens.add(atom(kind, trivia, String.valueOf(current)));
                index++;
            } else {
                throw syntax("Character is outside the generated Cypher fragment: " + current, index);
            }
        }
        return new Tokenization(List.copyOf(tokens), "");
    }

    private int quotedEnd(String source, int start, char quote) {
        int index = start + 1;
        while (index < source.length()) {
            if (source.charAt(index) == '\\' && index + 1 < source.length()) {
                index += 2;
            } else if (source.charAt(index) == quote) {
                if (index + 1 < source.length() && source.charAt(index + 1) == quote) index += 2;
                else return index + 1;
            } else index++;
        }
        throw new IllegalArgumentException("Unterminated Cypher quoted token");
    }

    private void validateQueryGroups(List<RawCypherAst.RawNode> nodes) {
        for (int index = 0; index < nodes.size(); index++) {
            RawCypherAst.RawNode node = nodes.get(index);
            if (node instanceof RawCypherAst.RawGroup group) {
                if ("{".equals(group.open().text()) && index > 0 && atom(nodes.get(index - 1), null)
                        && keyword(nodes.get(index - 1), QUERY_GROUP_PREFIXES)) {
                    validateQuery(group.nodes(), text(nodes.get(index - 1)) + " subquery");
                }
                validateQueryGroups(group.nodes());
            }
        }
    }

    private void validateQuery(List<RawCypherAst.RawNode> nodes, String location) {
        List<List<RawCypherAst.RawNode>> arms = splitUnionAll(nodes, location);
        for (int arm = 0; arm < arms.size(); arm++) validateArm(arms.get(arm), location + " arm " + (arm + 1));
    }

    private List<List<RawCypherAst.RawNode>> splitUnionAll(List<RawCypherAst.RawNode> nodes, String location) {
        List<List<RawCypherAst.RawNode>> arms = new ArrayList<>();
        List<RawCypherAst.RawNode> current = new ArrayList<>();
        for (int index = 0; index < nodes.size(); index++) {
            if (keyword(nodes.get(index), Set.of("UNION"))) {
                if (index + 1 >= nodes.size() || !keyword(nodes.get(index + 1), Set.of("ALL")))
                    throw new IllegalArgumentException(location + " uses unsupported bare UNION");
                if (current.isEmpty()) throw new IllegalArgumentException(location + " has an empty UNION arm");
                arms.add(List.copyOf(current));
                current.clear();
                index++;
            } else current.add(nodes.get(index));
        }
        if (current.isEmpty()) throw new IllegalArgumentException(location + " has an empty final UNION arm");
        arms.add(List.copyOf(current));
        return arms;
    }

    private void validateArm(List<RawCypherAst.RawNode> nodes, String location) {
        List<Integer> clauses = new ArrayList<>();
        for (int index = 0; index < nodes.size(); index++)
            if (clauseStart(nodes, index) != null) clauses.add(index);
        if (clauses.isEmpty() || clauses.get(0) != 0) {
            String first = nodes.isEmpty() ? "<empty>" : describeNode(nodes.get(0));
            throw new IllegalArgumentException(location
                    + " does not start with a generated Cypher clause; first node=" + first);
        }
        if ("WHERE".equals(clauseStart(nodes, 0)))
            throw new IllegalArgumentException(location + " starts with WHERE");
        for (int clause = 0; clause < clauses.size(); clause++) {
            int start = clauses.get(clause);
            String name = clauseStart(nodes, start);
            int payloadStart = start + ("OPTIONAL MATCH".equals(name) ? 2 : 1);
            int end = clause + 1 < clauses.size() ? clauses.get(clause + 1) : nodes.size();
            if (payloadStart >= end) throw new IllegalArgumentException(location + " has an empty " + name + " clause");
            if (("MATCH".equals(name) || "OPTIONAL MATCH".equals(name))
                    && nodes.subList(payloadStart, end).stream().noneMatch(node ->
                    node instanceof RawCypherAst.RawGroup group && "(".equals(group.open().text())))
                throw new IllegalArgumentException(location + " MATCH clause has no node pattern");
            if ("CALL".equals(name) && !(nodes.get(payloadStart) instanceof RawCypherAst.RawGroup group
                    && "{".equals(group.open().text())))
                throw new IllegalArgumentException(location + " CALL clause has no query block");
        }
    }

    private String clauseStart(List<RawCypherAst.RawNode> nodes, int index) {
        if (keyword(nodes.get(index), Set.of("OPTIONAL")) && index + 1 < nodes.size()
                && keyword(nodes.get(index + 1), Set.of("MATCH"))) return "OPTIONAL MATCH";
        if (keyword(nodes.get(index), Set.of("MATCH")) && index > 0
                && keyword(nodes.get(index - 1), Set.of("OPTIONAL"))) return null;
        if (keyword(nodes.get(index), Set.of("WITH")) && index > 0
                && keyword(nodes.get(index - 1), Set.of("STARTS", "ENDS"))) return null;
        for (String clause : List.of("MATCH", "WHERE", "WITH", "UNWIND", "RETURN", "CALL"))
            if (keyword(nodes.get(index), Set.of(clause))) return clause;
        return null;
    }

    private boolean keyword(RawCypherAst.RawNode node, Set<String> expected) {
        return node instanceof RawCypherAst.RawAtom value
                && value.kind() == RawCypherAst.RawAtomKind.KEYWORD
                && expected.contains(value.text().toUpperCase(Locale.ROOT));
    }

    private boolean atom(RawCypherAst.RawNode node, RawCypherAst.RawAtomKind kind) {
        return node instanceof RawCypherAst.RawAtom value && (kind == null || value.kind() == kind);
    }

    private String text(RawCypherAst.RawNode node) {
        return ((RawCypherAst.RawAtom) node).text();
    }

    private String describeNode(RawCypherAst.RawNode node) {
        if (node instanceof RawCypherAst.RawAtom atom) {
            return atom.kind() + "(" + atom.text() + ")";
        }
        if (node instanceof RawCypherAst.RawGroup group) {
            return "GROUP(" + group.open().text() + group.close().text() + ")";
        }
        return node.getClass().getSimpleName();
    }

    private RawCypherAst.RawAtom atom(RawCypherAst.RawAtomKind kind, String trivia, String text) {
        return new RawCypherAst.RawAtom(kind, trivia, text);
    }

    private boolean identifierStart(char value) { return Character.isLetter(value) || value == '_'; }
    private boolean identifierPart(char value) { return Character.isLetterOrDigit(value) || value == '_'; }
    private IllegalArgumentException syntax(String message, int index) {
        return new IllegalArgumentException(message + " at offset " + index);
    }

    private record Tokenization(List<RawCypherAst.RawAtom> tokens, String trailingTrivia) { }
    private record GroupBuilder(RawCypherAst.RawAtom open, List<RawCypherAst.RawNode> parent) { }
}
