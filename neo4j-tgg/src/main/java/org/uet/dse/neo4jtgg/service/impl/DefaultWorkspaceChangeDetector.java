package org.uet.dse.neo4jtgg.service.impl;

import org.uet.dse.neo4j.model.FullObjectSnapshot;
import org.uet.dse.neo4jtgg.model.CdcChangeEvent;
import org.uet.dse.neo4jtgg.model.LinkChange;
import org.uet.dse.neo4jtgg.model.ModelDelta;
import org.uet.dse.neo4jtgg.model.NormalizedChangeSet;
import org.uet.dse.neo4jtgg.model.ObjectChange;
import org.uet.dse.neo4jtgg.model.TggWorkspaceContext;
import org.uet.dse.neo4jtgg.model.WorkspaceSide;
import org.uet.dse.neo4jtgg.service.WorkspaceChangeDetector;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DefaultWorkspaceChangeDetector implements WorkspaceChangeDetector {

    @Override
    public NormalizedChangeSet detectSourceChanges(TggWorkspaceContext context,
                                                   FullObjectSnapshot previous,
                                                   FullObjectSnapshot current) {
        return detect(context, WorkspaceSide.SOURCE, previous, current);
    }

    @Override
    public NormalizedChangeSet detectTargetChanges(TggWorkspaceContext context,
                                                   FullObjectSnapshot previous,
                                                   FullObjectSnapshot current) {
        return detect(context, WorkspaceSide.TARGET, previous, current);
    }

    private NormalizedChangeSet detect(TggWorkspaceContext context,
                                       WorkspaceSide side,
                                       FullObjectSnapshot previous,
                                       FullObjectSnapshot current) {
        ModelDelta delta = SnapshotDeltaComputer.compute(side, previous, current);
        long token = System.currentTimeMillis();
        List<CdcChangeEvent> events = new ArrayList<>();
        for (ObjectChange change : delta.addedObjects()) {
            events.add(objectEvent(token, side, CdcChangeEvent.Operation.CREATE, change.objectId(), change.className(),
                    Map.of(), change.currentAttributes()));
        }
        for (ObjectChange change : delta.modifiedObjects()) {
            for (String attribute : changedAttributes(change)) {
                events.add(new CdcChangeEvent(
                        token,
                        token,
                        side,
                        CdcChangeEvent.EntityKind.ATTRIBUTE,
                        CdcChangeEvent.Operation.UPDATE,
                        change.objectId() + "." + attribute,
                        change.className(),
                        Map.of(attribute, change.previousAttributes().get(attribute)),
                        Map.of(attribute, change.currentAttributes().get(attribute)),
                        CdcChangeEvent.ChangeSource.SNAPSHOT_DIFF));
            }
        }
        for (ObjectChange change : delta.deletedObjects()) {
            events.add(objectEvent(token, side, CdcChangeEvent.Operation.DELETE, change.objectId(), change.className(),
                    change.previousAttributes(), Map.of()));
        }
        for (LinkChange change : delta.addedLinks()) {
            events.add(linkEvent(token, side, CdcChangeEvent.Operation.CREATE, change));
        }
        for (LinkChange change : delta.deletedLinks()) {
            events.add(linkEvent(token, side, CdcChangeEvent.Operation.DELETE, change));
        }
        context.setLastProcessedChangeToken(token);
        return new NormalizedChangeSet(side, NormalizedChangeSet.ChangeSourceKind.SNAPSHOT_DIFF, delta, events, token);
    }

    private CdcChangeEvent objectEvent(long token,
                                       WorkspaceSide side,
                                       CdcChangeEvent.Operation operation,
                                       String objectId,
                                       String className,
                                       Map<String, Object> before,
                                       Map<String, Object> after) {
        return new CdcChangeEvent(token, token, side, CdcChangeEvent.EntityKind.OBJECT, operation, objectId, className,
                before, after, CdcChangeEvent.ChangeSource.SNAPSHOT_DIFF);
    }

    private CdcChangeEvent linkEvent(long token,
                                     WorkspaceSide side,
                                     CdcChangeEvent.Operation operation,
                                     LinkChange change) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("participants", change.participants());
        return new CdcChangeEvent(token, token, side, CdcChangeEvent.EntityKind.LINK, operation,
                change.identity(), change.associationName(), operation == CdcChangeEvent.Operation.DELETE ? payload : Map.of(),
                operation == CdcChangeEvent.Operation.CREATE ? payload : Map.of(),
                CdcChangeEvent.ChangeSource.SNAPSHOT_DIFF);
    }

    private List<String> changedAttributes(ObjectChange change) {
        List<String> result = new ArrayList<>();
        for (String key : change.previousAttributes().keySet()) {
            if (!java.util.Objects.equals(change.previousAttributes().get(key), change.currentAttributes().get(key))) {
                result.add(key);
            }
        }
        for (String key : change.currentAttributes().keySet()) {
            if (!change.previousAttributes().containsKey(key)) {
                result.add(key);
            }
        }
        return result;
    }
}
