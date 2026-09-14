package org.uet.dse.neo4jtgg.experiment;

public record PipelineStageTimings(long parseNs,
                                   long bindNs,
                                   long vaBuildNs,
                                   long normalizeNs,
                                   long planNs,
                                   long renderNs) {
    public long compileNs() {
        return parseNs + bindNs + vaBuildNs + normalizeNs + planNs + renderNs;
    }
}
