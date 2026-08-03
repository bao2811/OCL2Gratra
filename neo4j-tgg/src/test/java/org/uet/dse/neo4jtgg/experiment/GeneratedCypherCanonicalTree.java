package org.uet.dse.neo4jtgg.experiment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Full token/group tree canonicalization for the generated Cypher fragment.
 * Only variable/alias identifiers are alpha-renamed. Schema names, property
 * keys, function names, literals, operators, and parameters remain exact.
 */
final class GeneratedCypherCanonicalTree {
    private static final Set<String> ITERATOR_FUNCTIONS = Set.of("any", "all", "none", "single");

    private GeneratedCypherCanonicalTree() {
    }

    static CanonicalTree parse(String cypher) {
        GeneratedCypherSyntaxTree.Document parsed = GeneratedCypherSyntaxTree.parse(cypher);
        AlphaState state = new AlphaState();
        GeneratedCypherSyntaxTree.Document canonical = new GeneratedCypherSyntaxTree.Document(
                canonicalize(parsed.nodes(), state, null));
        String text = GeneratedCypherSyntaxTree.render(canonical);
        Counts counts = count(canonical.nodes());
        return new CanonicalTree(canonical, text, counts.atoms(), counts.groups(), sha256(text));
    }

    private static List<GeneratedCypherSyntaxTree.Node> canonicalize(
            List<GeneratedCypherSyntaxTree.Node> nodes, AlphaState state, String container) {
        List<GeneratedCypherSyntaxTree.Node> result = new ArrayList<>(nodes.size());
        for (int index = 0; index < nodes.size(); index++) {
            GeneratedCypherSyntaxTree.Node node = nodes.get(index);
            if (node instanceof GeneratedCypherSyntaxTree.Group group) {
                String function = index > 0 && nodes.get(index - 1) instanceof GeneratedCypherSyntaxTree.Atom atom
                        && atom.kind() == GeneratedCypherSyntaxTree.AtomKind.IDENTIFIER ? atom.text() : null;
                result.add(canonicalizeGroup(group, state, function));
            } else {
                GeneratedCypherSyntaxTree.Atom atom = (GeneratedCypherSyntaxTree.Atom) node;
                result.add(canonicalizeAtom(nodes, index, atom, state, container));
            }
        }
        return List.copyOf(result);
    }

    private static GeneratedCypherSyntaxTree.Group canonicalizeGroup(
            GeneratedCypherSyntaxTree.Group group, AlphaState state, String function) {
        if (isIteratorGroup(group, function)) return canonicalizeIteratorGroup(group, state);
        if ("reduce".equalsIgnoreCase(function)) return canonicalizeReduceGroup(group, state);
        return new GeneratedCypherSyntaxTree.Group(group.open(), group.close(),
                canonicalize(group.nodes(), state, group.open()));
    }

    private static boolean isIteratorGroup(GeneratedCypherSyntaxTree.Group group, String function) {
        if (!("[".equals(group.open()) || ("(".equals(group.open())
                && function != null && ITERATOR_FUNCTIONS.contains(function.toLowerCase())))) return false;
        return group.nodes().size() >= 3
                && identifier(group.nodes().get(0))
                && keyword(group.nodes().get(1), "IN");
    }

    private static GeneratedCypherSyntaxTree.Group canonicalizeIteratorGroup(
            GeneratedCypherSyntaxTree.Group group, AlphaState state) {
        List<GeneratedCypherSyntaxTree.Node> nodes = group.nodes();
        int bodyStart = separator(nodes, 2, "WHERE", "|");
        String original = ((GeneratedCypherSyntaxTree.Atom) nodes.get(0)).text();
        String canonical = state.fresh();
        List<GeneratedCypherSyntaxTree.Node> result = new ArrayList<>(nodes.size());
        result.add(identifierAtom(canonical));
        result.add(nodes.get(1));
        result.addAll(canonicalize(nodes.subList(2, bodyStart < 0 ? nodes.size() : bodyStart), state, group.open()));
        if (bodyStart >= 0) {
            result.add(nodes.get(bodyStart));
            state.pushLocal(original, canonical);
            result.addAll(canonicalize(nodes.subList(bodyStart + 1, nodes.size()), state, group.open()));
            state.pop();
        }
        return new GeneratedCypherSyntaxTree.Group(group.open(), group.close(), List.copyOf(result));
    }

    private static GeneratedCypherSyntaxTree.Group canonicalizeReduceGroup(
            GeneratedCypherSyntaxTree.Group group, AlphaState state) {
        List<GeneratedCypherSyntaxTree.Node> nodes = group.nodes();
        int comma = symbol(nodes, 0, ",");
        int in = keyword(nodes, comma + 1, "IN");
        int pipe = symbol(nodes, in + 1, "|");
        if (comma < 0 || in < 0 || pipe < 0 || !identifier(nodes.get(0))
                || in == 0 || !identifier(nodes.get(in - 1))) {
            return new GeneratedCypherSyntaxTree.Group(group.open(), group.close(),
                    canonicalize(nodes, state, group.open()));
        }
        String accumulator = ((GeneratedCypherSyntaxTree.Atom) nodes.get(0)).text();
        String item = ((GeneratedCypherSyntaxTree.Atom) nodes.get(in - 1)).text();
        String canonicalAccumulator = state.fresh();
        String canonicalItem = state.fresh();
        List<GeneratedCypherSyntaxTree.Node> result = new ArrayList<>(nodes.size());
        result.add(identifierAtom(canonicalAccumulator));
        result.addAll(canonicalize(nodes.subList(1, comma + 1), state, group.open()));
        result.add(identifierAtom(canonicalItem));
        result.add(nodes.get(in));
        result.addAll(canonicalize(nodes.subList(in + 1, pipe + 1), state, group.open()));
        state.pushLocals(Map.of(accumulator, canonicalAccumulator, item, canonicalItem));
        result.addAll(canonicalize(nodes.subList(pipe + 1, nodes.size()), state, group.open()));
        state.pop();
        return new GeneratedCypherSyntaxTree.Group(group.open(), group.close(), List.copyOf(result));
    }

    private static GeneratedCypherSyntaxTree.Atom canonicalizeAtom(
            List<GeneratedCypherSyntaxTree.Node> siblings, int index,
            GeneratedCypherSyntaxTree.Atom atom, AlphaState state, String container) {
        if (atom.kind() != GeneratedCypherSyntaxTree.AtomKind.IDENTIFIER
                && atom.kind() != GeneratedCypherSyntaxTree.AtomKind.QUOTED_IDENTIFIER) return atom;
        if (isSchemaIdentifier(siblings, index, container)) return atom;
        return new GeneratedCypherSyntaxTree.Atom(atom.kind(), state.resolveOrBind(atom.text()));
    }

    private static boolean isSchemaIdentifier(List<GeneratedCypherSyntaxTree.Node> siblings,
                                              int index, String container) {
        if (symbolAt(siblings, index - 1, ".")) return true;
        if (index + 1 < siblings.size() && siblings.get(index + 1) instanceof GeneratedCypherSyntaxTree.Group group
                && "(".equals(group.open())) return true;
        if ("{".equals(container) && symbolAt(siblings, index + 1, ":")) return true;
        if ("(".equals(container) && symbolAt(siblings, index - 1, ":")) return true;
        if ("[".equals(container)
                && (symbolAt(siblings, index - 1, ":") || symbolAt(siblings, index - 1, "|"))) return true;
        return false;
    }

    private static int separator(List<GeneratedCypherSyntaxTree.Node> nodes, int start, String... values) {
        for (int index = start; index < nodes.size(); index++)
            for (String value : values) if (keyword(nodes.get(index), value) || symbolAt(nodes, index, value)) return index;
        return -1;
    }

    private static int symbol(List<GeneratedCypherSyntaxTree.Node> nodes, int start, String value) {
        for (int index = Math.max(0, start); index < nodes.size(); index++)
            if (symbolAt(nodes, index, value)) return index;
        return -1;
    }

    private static int keyword(List<GeneratedCypherSyntaxTree.Node> nodes, int start, String value) {
        for (int index = Math.max(0, start); index < nodes.size(); index++)
            if (keyword(nodes.get(index), value)) return index;
        return -1;
    }

    private static boolean identifier(GeneratedCypherSyntaxTree.Node node) {
        return node instanceof GeneratedCypherSyntaxTree.Atom atom
                && (atom.kind() == GeneratedCypherSyntaxTree.AtomKind.IDENTIFIER
                || atom.kind() == GeneratedCypherSyntaxTree.AtomKind.QUOTED_IDENTIFIER);
    }

    private static boolean keyword(GeneratedCypherSyntaxTree.Node node, String value) {
        return node instanceof GeneratedCypherSyntaxTree.Atom atom
                && atom.kind() == GeneratedCypherSyntaxTree.AtomKind.KEYWORD
                && value.equalsIgnoreCase(atom.text());
    }

    private static boolean symbolAt(List<GeneratedCypherSyntaxTree.Node> nodes, int index, String value) {
        return index >= 0 && index < nodes.size() && nodes.get(index) instanceof GeneratedCypherSyntaxTree.Atom atom
                && value.equals(atom.text());
    }

    private static GeneratedCypherSyntaxTree.Atom identifierAtom(String value) {
        return new GeneratedCypherSyntaxTree.Atom(GeneratedCypherSyntaxTree.AtomKind.IDENTIFIER, value);
    }

    private static Counts count(List<GeneratedCypherSyntaxTree.Node> nodes) {
        int atoms = 0;
        int groups = 0;
        for (GeneratedCypherSyntaxTree.Node node : nodes) {
            if (node instanceof GeneratedCypherSyntaxTree.Atom) atoms++;
            else {
                groups++;
                Counts nested = count(((GeneratedCypherSyntaxTree.Group) node).nodes());
                atoms += nested.atoms();
                groups += nested.groups();
            }
        }
        return new Counts(atoms, groups);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) result.append(String.format("%02x", item));
            return result.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    record CanonicalTree(GeneratedCypherSyntaxTree.Document document, String text,
                         int atomCount, int groupCount, String sha256) {
    }

    private record Counts(int atoms, int groups) {
    }

    private static final class AlphaState {
        private final Deque<Map<String, String>> scopes = new ArrayDeque<>();
        private int next;

        private AlphaState() {
            scopes.push(new LinkedHashMap<>());
        }

        private String fresh() {
            return "v" + (++next);
        }

        private String resolveOrBind(String name) {
            for (Map<String, String> scope : scopes) {
                String resolved = scope.get(name);
                if (resolved != null) return resolved;
            }
            String resolved = fresh();
            scopes.peek().put(name, resolved);
            return resolved;
        }

        private void pushLocal(String name, String canonical) {
            pushLocals(Map.of(name, canonical));
        }

        private void pushLocals(Map<String, String> names) {
            scopes.push(new LinkedHashMap<>(names));
        }

        private void pop() {
            scopes.pop();
        }
    }
}
