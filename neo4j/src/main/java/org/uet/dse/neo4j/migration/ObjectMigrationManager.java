package org.uet.dse.neo4j.migration;

import org.tzi.use.api.UseSystemApi;
import org.tzi.use.uml.sys.MSystem;
import org.uet.dse.neo4j.model.FullObjectSnapshot;
import org.uet.dse.neo4j.model.ObjectState;
import org.uet.dse.neo4j.sync.object.ObjectSyncCoordinator;

public class ObjectMigrationManager {
    private FullObjectSnapshot backup;

    public void restoreState(MSystem newSystem) {
        if (backup == null || backup.objects.isEmpty()) return;

        UseSystemApi api = UseSystemApi.create(newSystem, true);
        int restoredCount = 0;

        for (ObjectState os : backup.objects.values()) {
            if (newSystem.model().getClass(os.className) != null) {
                try {
                    api.createObject(os.className, os.name);
                    restoreAttributes(api, os, newSystem);
                    restoredCount++;
                } catch (Exception e) {
                    System.err.println("Migration: Could not restore object " + os.name);
                }
            }
        }
        System.out.println("Migration: Successfully restored " + restoredCount + " objects.");
    }

    private void restoreAttributes(UseSystemApi api, ObjectState os, MSystem system) {
        // Logic tương tự applyChangesToUSEState nhưng có thêm bước check attr tồn tại trong Model M1 mới
    }
}