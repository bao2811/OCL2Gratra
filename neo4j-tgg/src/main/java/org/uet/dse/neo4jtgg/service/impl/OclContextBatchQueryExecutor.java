package org.uet.dse.neo4jtgg.service.impl;

import org.neo4j.driver.Record;
import org.neo4j.driver.QueryRunner;
import org.uet.dse.neo4jtgg.model.CypherCompilationResult;
import org.uet.dse.neo4jtgg.model.OclRuleCompilationResult;
import org.uet.dse.neo4jtgg.model.OclRuleDescriptor;
import org.uet.dse.neo4jtgg.ocl.OclBottomToken;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

final class OclContextBatchQueryExecutor {
    static final int MAX_RULES_PER_QUERY = 8;
    static final int MAX_GENERATED_CHARS_PER_QUERY = 50_000;

    record BatchRule(int ruleIndex,
                     OclRuleDescriptor rule,
                     OclRuleCompilationResult compiledRule,
                     String violationMessage) {
    }

    record BatchQueryPlan(String cypher,
                          Map<String, Object> parameters,
                          Map<String, BatchRule> aliasToRule) {
    }

    record BatchExecutionResult(Map<Integer, Map<String, String>> violationsByRuleIndex,
                                long executionTimeMs) {
    }

    private OclContextBatchQueryExecutor() {
    }

    static BatchQueryPlan prepare(List<BatchRule> batchRules) {
        StringBuilder cypher = new StringBuilder();
        Map<String, Object> mergedParameters = new LinkedHashMap<>();
        Map<String, BatchRule> aliasToRule = new LinkedHashMap<>();
        int counter = 0;
        for (BatchRule batchRule : batchRules) {
            if (cypher.length() > 0) {
                cypher.append("\nUNION ALL\n");
            }
            String alias = "r" + counter++;
            aliasToRule.put(alias, batchRule);
            RewrittenQuery rewrittenQuery = rewriteParameters(
                    batchRule.compiledRule().getCompilation(),
                    alias);
            mergedParameters.putAll(rewrittenQuery.parameters());
            cypher.append("CALL {\n")
                    .append(rewrittenQuery.cypher())
                    .append("\n}\nRETURN '")
                    .append(alias)
                    .append("' AS __ruleAlias, useId");
        }
        return new BatchQueryPlan(cypher.toString(), Map.copyOf(mergedParameters), Map.copyOf(aliasToRule));
    }

    static BatchExecutionResult execute(QueryRunner session, List<BatchRule> batchRules) {
        if (batchRules.isEmpty()) {
            return new BatchExecutionResult(Map.of(), 0L);
        }
        long startedAt = System.nanoTime();
        Map<Integer, Map<String, String>> violationsByRuleIndex = new LinkedHashMap<>();
        for (BatchQueryPlan plan : prepareBatches(batchRules)) {
            OclBottomToken.requireWellFormedGeneratedParameters(plan.parameters());
            List<Record> records = session.run(plan.cypher(), plan.parameters()).list();
            for (Record record : records) {
                String alias = record.get("__ruleAlias").asString();
                String useId = record.get("useId").asString();
                BatchRule batchRule = plan.aliasToRule().get(alias);
                violationsByRuleIndex
                        .computeIfAbsent(batchRule.ruleIndex(), ignored -> new LinkedHashMap<>())
                        .put(useId, batchRule.violationMessage());
            }
        }
        return new BatchExecutionResult(Map.copyOf(violationsByRuleIndex), elapsedMillis(startedAt));
    }

    /**
     * Greedily bounds both UNION branches and generated text per Neo4j query.
     * One individually large rule is kept intact so diagnostics still identify
     * the actual compiler output instead of failing during batch construction.
     */
    static List<BatchQueryPlan> prepareBatches(List<BatchRule> batchRules) {
        if (batchRules == null || batchRules.isEmpty()) return List.of();
        List<BatchQueryPlan> plans = new ArrayList<>();
        List<BatchRule> current = new ArrayList<>();
        int currentChars = 0;
        for (BatchRule rule : batchRules) {
            int ruleChars = rule.compiledRule().getCompilation().getCypher().length() + 96;
            boolean exceedsRuleLimit = current.size() >= MAX_RULES_PER_QUERY;
            boolean exceedsTextLimit = !current.isEmpty()
                    && currentChars + ruleChars > MAX_GENERATED_CHARS_PER_QUERY;
            if (exceedsRuleLimit || exceedsTextLimit) {
                plans.add(prepare(current));
                current = new ArrayList<>();
                currentChars = 0;
            }
            current.add(rule);
            currentChars += ruleChars;
        }
        if (!current.isEmpty()) plans.add(prepare(current));
        return List.copyOf(plans);
    }

    private static RewrittenQuery rewriteParameters(CypherCompilationResult compilation, String alias) {
        String cypher = compilation.getCypher();
        Map<String, Object> rewrittenParameters = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : compilation.getParameters().entrySet()) {
            String originalName = entry.getKey();
            String rewrittenName = alias + "_" + originalName;
            cypher = cypher.replaceAll("\\$" + Pattern.quote(originalName) + "(?![A-Za-z0-9_])",
                    "\\$" + rewrittenName);
            rewrittenParameters.put(rewrittenName, entry.getValue());
        }
        return new RewrittenQuery(cypher, Map.copyOf(rewrittenParameters));
    }

    private static long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    private record RewrittenQuery(String cypher, Map<String, Object> parameters) {
    }
}
