package org.uet.dse.neo4jtgg.service;

import org.uet.dse.neo4j.model.FullObjectSnapshot;
import org.uet.dse.neo4jtgg.model.NormalizedChangeSet;
import org.uet.dse.neo4jtgg.model.TggWorkspaceContext;

public interface WorkspaceChangeDetector {
    NormalizedChangeSet detectSourceChanges(TggWorkspaceContext context,
                                            FullObjectSnapshot previous,
                                            FullObjectSnapshot current);

    NormalizedChangeSet detectTargetChanges(TggWorkspaceContext context,
                                            FullObjectSnapshot previous,
                                            FullObjectSnapshot current);
}
