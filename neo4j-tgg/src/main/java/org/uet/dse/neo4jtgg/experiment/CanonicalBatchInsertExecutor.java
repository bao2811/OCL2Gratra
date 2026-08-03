package org.uet.dse.neo4jtgg.experiment;

import org.neo4j.driver.Session;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Executes deterministic dataset-generation Cypher in bounded auto-commit
 * transactions. Dataset preparation is intentionally kept separate from OCL
 * compilation and validation timing.
 */
public final class CanonicalBatchInsertExecutor {
    public static final int DEFAULT_BATCH_SIZE = 5_000;

    private final int batchSize;

    public CanonicalBatchInsertExecutor(int batchSize) {
        if (batchSize <= 0) throw new IllegalArgumentException("batchSize must be positive");
        this.batchSize = batchSize;
    }

    public int batchSize() {
        return batchSize;
    }

    public StageTiming ensureCanonicalIndexes(Session session) {
        long start = System.nanoTime();
        // These two indexes were part of the initial experiment, but can make
        // the planner start validation from all matching value nodes instead
        // of from the already-scoped object. They are not needed for loading.
        session.run("DROP INDEX research_value_model IF EXISTS").consume();
        session.run("DROP INDEX research_value_attribute IF EXISTS").consume();
        List<String> statements = List.of(
                "CREATE INDEX research_object_key IF NOT EXISTS FOR (n:Object) ON (n.objectKey)",
                "CREATE INDEX research_object_model IF NOT EXISTS FOR (n:Object) ON (n.modelKey)",
                "CREATE INDEX research_attribute_key IF NOT EXISTS FOR (n:Attribute) ON (n.attributeKey)"
        );
        for (String statement : statements) session.run(statement).consume();
        session.run("CALL db.awaitIndexes(300)").consume();
        return new StageTiming("canonical-indexes", 0, statements.size(), System.nanoTime() - start);
    }

    public StageTiming executeRange(Session session, String stageName, int first, int last,
                                    String cypher, Map<String, Object> commonParameters) {
        if (last < first) return new StageTiming(stageName, 0, 0, 0);
        long startNs = System.nanoTime();
        int batches = 0;
        for (int batchStart = first; batchStart <= last; batchStart += batchSize) {
            int batchEnd = Math.min(last, batchStart + batchSize - 1);
            Map<String, Object> parameters = new LinkedHashMap<>(commonParameters);
            parameters.put("start", batchStart);
            parameters.put("end", batchEnd);
            session.run(cypher, parameters).consume();
            batches++;
        }
        return new StageTiming(stageName, last - first + 1, batches, System.nanoTime() - startNs);
    }

    public StageTiming executeOnce(Session session, String stageName, long rows,
                                   String cypher, Map<String, Object> parameters) {
        long startNs = System.nanoTime();
        session.run(cypher, parameters).consume();
        return new StageTiming(stageName, rows, 1, System.nanoTime() - startNs);
    }

    public static long totalNs(List<StageTiming> timings) {
        return timings.stream().mapToLong(StageTiming::elapsedNs).sum();
    }

    public record StageTiming(String stage, long rows, int batches, long elapsedNs) {
    }

    public static List<StageTiming> mutableTimingList() {
        return new ArrayList<>();
    }
}
