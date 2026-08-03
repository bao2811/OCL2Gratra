package org.uet.dse.neo4jtgg.experiment;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Independent parser/normalizer for the closed concrete-Cypher fragment emitted
 * by {@code OclCypherRenderer}. It is deliberately test-only: production keeps
 * the direct plan-to-text path while this parser supplies an independent syntax
 * oracle for that text.
 */
final class GeneratedCypherSyntaxTree {
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

    private GeneratedCypherSyntaxTree() {
    }

    static Document parse(String source) {
        if (source == null) throw new IllegalArgumentException("Cypher query is null");
        if (PLACEHOLDER.matcher(source).find())
            throw new IllegalArgumentException("Unexpanded generated placeholder");
        List<Atom> tokens = tokenize(source);
        List<Node> root = new ArrayList<>();
        Deque<GroupBuilder> stack = new ArrayDeque<>();
        List<Node> current = root;
        for (Atom token : tokens) {
            if (CLOSING.containsKey(token.text())) {
                GroupBuilder builder = new GroupBuilder(token.text(), CLOSING.get(token.text()), current);
                stack.push(builder);
                current = new ArrayList<>();
            } else if (CLOSING.containsValue(token.text())) {
                if (stack.isEmpty() || !stack.peek().close().equals(token.text())) {
                    throw new IllegalArgumentException("Unbalanced Cypher delimiter: " + token.text());
                }
                GroupBuilder builder = stack.pop();
                Group group = new Group(builder.open(), token.text(), List.copyOf(current));
                current = builder.parent();
                current.add(group);
            } else {
                current.add(token);
            }
        }
        if (!stack.isEmpty()) {
            throw new IllegalArgumentException("Unclosed Cypher delimiter: " + stack.peek().open());
        }
        if (root.isEmpty()) throw new IllegalArgumentException("Cypher query is empty");
        Document document = new Document(List.copyOf(root));
        validateQuery(document.nodes(), "document");
        validateQueryGroups(document.nodes());
        return document;
    }

    static String render(Document document) {
        List<String> tokens = new ArrayList<>();
        append(document.nodes(), tokens);
        return String.join(" ", tokens);
    }

    private static void append(List<Node> nodes, List<String> target) {
        for (Node node : nodes) {
            if (node instanceof Atom atom) {
                target.add(atom.text());
            } else if (node instanceof Group group) {
                target.add(group.open());
                append(group.nodes(), target);
                target.add(group.close());
            }
        }
    }

    private static List<Atom> tokenize(String source) {
        List<Atom> tokens = new ArrayList<>();
        int index = 0;
        while (index < source.length()) {
            char current = source.charAt(index);
            if (Character.isWhitespace(current)) {
                index++;
                continue;
            }
            if (current == '\'' || current == '"' || current == '`') {
                int end = quotedEnd(source, index, current);
                tokens.add(new Atom(current == '`' ? AtomKind.QUOTED_IDENTIFIER : AtomKind.STRING,
                        source.substring(index, end)));
                index = end;
                continue;
            }
            if (current == '$') {
                int start = index++;
                if (index >= source.length() || !identifierStart(source.charAt(index)))
                    throw syntax("Invalid Cypher parameter", start);
                index++;
                while (index < source.length() && identifierPart(source.charAt(index))) index++;
                tokens.add(new Atom(AtomKind.PARAMETER, source.substring(start, index)));
                continue;
            }
            if (identifierStart(current)) {
                int end = index + 1;
                while (end < source.length() && identifierPart(source.charAt(end))) end++;
                String text = source.substring(index, end);
                tokens.add(new Atom(KEYWORDS.contains(text.toUpperCase(Locale.ROOT))
                        ? AtomKind.KEYWORD : AtomKind.IDENTIFIER, text));
                index = end;
                continue;
            }
            if (Character.isDigit(current)) {
                int end = index + 1;
                while (end < source.length() && Character.isDigit(source.charAt(end))) end++;
                if (end < source.length() && source.charAt(end) == '.') {
                    int fraction = end + 1;
                    if (fraction >= source.length() || !Character.isDigit(source.charAt(fraction)))
                        throw syntax("Invalid decimal literal", index);
                    end = fraction + 1;
                    while (end < source.length() && Character.isDigit(source.charAt(end))) end++;
                }
                tokens.add(new Atom(AtomKind.NUMBER, source.substring(index, end)));
                index = end;
                continue;
            }
            String pair = index + 1 < source.length() ? source.substring(index, index + 2) : "";
            if (pair.equals("//") || pair.equals("/*") || pair.equals("*/"))
                throw syntax("Comments are outside the generated Cypher fragment", index);
            if (TWO_CHARACTER_OPERATORS.contains(pair)) {
                tokens.add(new Atom(AtomKind.OPERATOR, pair));
                index += 2;
            } else if (SINGLE_CHARACTER_TOKENS.indexOf(current) >= 0) {
                tokens.add(new Atom("()[]{}.,:|".indexOf(current) >= 0
                        ? AtomKind.SYMBOL : AtomKind.OPERATOR, String.valueOf(current)));
                index++;
            } else {
                throw syntax("Character is outside the generated Cypher fragment: " + current, index);
            }
        }
        return tokens;
    }

    private static int quotedEnd(String source, int start, char quote) {
        int index = start + 1;
        while (index < source.length()) {
            if (source.charAt(index) == '\\' && index + 1 < source.length()) {
                index += 2;
            } else if (source.charAt(index) == quote) {
                if (index + 1 < source.length() && source.charAt(index + 1) == quote) {
                    index += 2;
                } else {
                    return index + 1;
                }
            } else {
                index++;
            }
        }
        throw new IllegalArgumentException("Unterminated Cypher quoted token");
    }

    private static void validateQueryGroups(List<Node> nodes) {
        for (int index = 0; index < nodes.size(); index++) {
            Node node = nodes.get(index);
            if (node instanceof Group group) {
                if ("{".equals(group.open()) && index > 0 && nodes.get(index - 1) instanceof Atom prefix
                        && prefix.kind() == AtomKind.KEYWORD
                        && QUERY_GROUP_PREFIXES.contains(prefix.text().toUpperCase(Locale.ROOT))) {
                    validateQuery(group.nodes(), prefix.text() + " subquery");
                }
                validateQueryGroups(group.nodes());
            }
        }
    }

    private static void validateQuery(List<Node> nodes, String location) {
        List<List<Node>> arms = splitUnionAll(nodes, location);
        for (int arm = 0; arm < arms.size(); arm++) validateArm(arms.get(arm), location + " arm " + (arm + 1));
    }

    private static List<List<Node>> splitUnionAll(List<Node> nodes, String location) {
        List<List<Node>> arms = new ArrayList<>();
        List<Node> current = new ArrayList<>();
        for (int index = 0; index < nodes.size(); index++) {
            if (keyword(nodes.get(index), "UNION")) {
                if (index + 1 >= nodes.size() || !keyword(nodes.get(index + 1), "ALL"))
                    throw new IllegalArgumentException(location + " uses unsupported bare UNION");
                if (current.isEmpty()) throw new IllegalArgumentException(location + " has an empty UNION arm");
                arms.add(List.copyOf(current));
                current.clear();
                index++;
            } else {
                current.add(nodes.get(index));
            }
        }
        if (current.isEmpty()) throw new IllegalArgumentException(location + " has an empty final UNION arm");
        arms.add(List.copyOf(current));
        return arms;
    }

    private static void validateArm(List<Node> nodes, String location) {
        List<Integer> clauses = new ArrayList<>();
        for (int index = 0; index < nodes.size(); index++) {
            if (clauseStart(nodes, index) != null) clauses.add(index);
        }
        if (clauses.isEmpty() || clauses.get(0) != 0)
            throw new IllegalArgumentException(location + " does not start with a generated Cypher clause");
        String first = clauseStart(nodes, clauses.get(0));
        if ("WHERE".equals(first)) throw new IllegalArgumentException(location + " starts with WHERE");
        for (int clause = 0; clause < clauses.size(); clause++) {
            int start = clauses.get(clause);
            int payloadStart = start + ("OPTIONAL MATCH".equals(clauseStart(nodes, start)) ? 2 : 1);
            int end = clause + 1 < clauses.size() ? clauses.get(clause + 1) : nodes.size();
            if (payloadStart >= end)
                throw new IllegalArgumentException(location + " has an empty " + clauseStart(nodes, start) + " clause");
            if (("MATCH".equals(clauseStart(nodes, start)) || "OPTIONAL MATCH".equals(clauseStart(nodes, start)))
                    && nodes.subList(payloadStart, end).stream().noneMatch(node -> node instanceof Group group
                    && "(".equals(group.open())))
                throw new IllegalArgumentException(location + " MATCH clause has no node pattern");
            if ("CALL".equals(clauseStart(nodes, start))
                    && !(nodes.get(payloadStart) instanceof Group group && "{".equals(group.open())))
                throw new IllegalArgumentException(location + " CALL clause has no query block");
        }
    }

    private static String clauseStart(List<Node> nodes, int index) {
        if (keyword(nodes.get(index), "OPTIONAL") && index + 1 < nodes.size() && keyword(nodes.get(index + 1), "MATCH"))
            return "OPTIONAL MATCH";
        if (keyword(nodes.get(index), "MATCH") && index > 0 && keyword(nodes.get(index - 1), "OPTIONAL")) return null;
        if (keyword(nodes.get(index), "WITH") && index > 0
                && (keyword(nodes.get(index - 1), "STARTS") || keyword(nodes.get(index - 1), "ENDS"))) return null;
        for (String clause : List.of("MATCH", "WHERE", "WITH", "UNWIND", "RETURN", "CALL"))
            if (keyword(nodes.get(index), clause)) return clause;
        return null;
    }

    private static boolean keyword(Node node, String expected) {
        return node instanceof Atom atom && atom.kind() == AtomKind.KEYWORD
                && expected.equals(atom.text().toUpperCase(Locale.ROOT));
    }

    private static boolean identifierStart(char value) {
        return Character.isLetter(value) || value == '_';
    }

    private static boolean identifierPart(char value) {
        return Character.isLetterOrDigit(value) || value == '_';
    }

    private static IllegalArgumentException syntax(String message, int index) {
        return new IllegalArgumentException(message + " at offset " + index);
    }

    sealed interface Node permits Atom, Group {
    }

    enum AtomKind { IDENTIFIER, QUOTED_IDENTIFIER, KEYWORD, PARAMETER, NUMBER, STRING, SYMBOL, OPERATOR }

    record Atom(AtomKind kind, String text) implements Node {
    }

    record Group(String open, String close, List<Node> nodes) implements Node {
    }

    record Document(List<Node> nodes) {
    }

    private record GroupBuilder(String open, String close, List<Node> parent) {
    }
}
