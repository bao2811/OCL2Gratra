package org.uet.dse.neo4jtgg.ocl.ir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Executable representation of the CQM-to-Raw-Cypher-AST contract catalog.
 * The catalog is deliberately structural: semantic preservation remains a
 * theorem obligation, while constructor coverage and bounded-expansion
 * metadata are checked at the implementation boundary.
 */
public final class CqmAstLoweringContract {
    private static final List<String> HEADER = List.of(
            "rule_id", "source_constructor", "required_ast_kinds", "forbidden_ast_kinds",
            "max_copies_per_complex_child", "observation_contract", "proof_status");

    private final List<ConstructorAstRule> rules;

    private CqmAstLoweringContract(List<ConstructorAstRule> rules) {
        this.rules = List.copyOf(rules);
    }

    public static CqmAstLoweringContract load(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path).stream()
                .filter(line -> !line.isBlank()).toList();
        if (lines.isEmpty() || !HEADER.equals(List.of(lines.get(0).split(",", -1)))) {
            throw new IllegalArgumentException("Malformed CQM AST lowering catalog header: " + path);
        }
        List<ConstructorAstRule> rules = lines.subList(1, lines.size()).stream().map(line -> {
            String[] values = line.split(",", -1);
            if (values.length != HEADER.size()) {
                throw new IllegalArgumentException("Malformed CQM AST lowering rule: " + line);
            }
            return new ConstructorAstRule(values[0], values[1], split(values[2]), split(values[3]),
                    Integer.parseInt(values[4]), values[5], values[6]);
        }).toList();
        return new CqmAstLoweringContract(rules);
    }

    public List<ConstructorAstRule> rules() {
        return rules;
    }

    public Map<String, ConstructorAstRule> requireComplete(Set<String> constructors) {
        Map<String, ConstructorAstRule> byConstructor = new LinkedHashMap<>();
        for (ConstructorAstRule rule : rules) {
            if (rule.ruleId().isBlank() || rule.sourceConstructor().isBlank()
                    || rule.requiredAstKinds().isEmpty() || rule.maxCopiesPerComplexChild() < 1
                    || rule.observationContract().isBlank()) {
                throw new IllegalArgumentException("Invalid CQM AST lowering rule: " + rule.ruleId());
            }
            if (byConstructor.put(rule.sourceConstructor(), rule) != null) {
                throw new IllegalArgumentException("Duplicate CQM AST lowering constructor: "
                        + rule.sourceConstructor());
            }
        }
        Set<String> missing = new LinkedHashSet<>(constructors);
        missing.removeAll(byConstructor.keySet());
        Set<String> extra = new LinkedHashSet<>(byConstructor.keySet());
        extra.removeAll(constructors);
        if (!missing.isEmpty() || !extra.isEmpty()) {
            throw new IllegalArgumentException("CQM AST lowering catalog mismatch; missing="
                    + missing + ", extra=" + extra);
        }
        return Map.copyOf(byConstructor);
    }

    private static List<String> split(String value) {
        if (value == null || value.isBlank()) return List.of();
        return List.of(value.split("\\|", -1));
    }

    public record ConstructorAstRule(String ruleId, String sourceConstructor,
                                     List<String> requiredAstKinds,
                                     List<String> forbiddenAstKinds,
                                     int maxCopiesPerComplexChild,
                                     String observationContract,
                                     String proofStatus) {
        public ConstructorAstRule {
            requiredAstKinds = List.copyOf(requiredAstKinds);
            forbiddenAstKinds = List.copyOf(forbiddenAstKinds);
        }
    }
}
