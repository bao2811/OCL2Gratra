package org.uet.dse.neo4jtgg.service;

import org.uet.dse.neo4j.model.FullObjectSnapshot;
import org.uet.dse.neo4jtgg.model.ImpactAnalysisResult;
import org.uet.dse.neo4jtgg.model.NormalizedChangeSet;
import org.uet.dse.neo4jtgg.model.TggWorkspaceContext;

public interface IncrementalImpactAnalysisService {
    ImpactAnalysisResult analyze(TggWorkspaceContext context,
                                 NormalizedChangeSet changeSet,
                                 FullObjectSnapshot sourceSnapshot,
                                 FullObjectSnapshot targetSnapshot,
                                 FullObjectSnapshot corrSnapshot);
}
