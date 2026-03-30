package org.uet.dse.neo4j.sync.feedback;

import org.uet.dse.neo4j.sync.object.ObjectDiff;
import reactor.util.annotation.NonNull;

@FunctionalInterface
public interface SyncPreviewProvider {
    boolean showSyncPreview(ObjectDiff diff, @NonNull String direction);
}