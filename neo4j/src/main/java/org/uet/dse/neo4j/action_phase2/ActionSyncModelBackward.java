package org.uet.dse.neo4j.action_phase2;

import org.tzi.use.gui.main.MainWindow;
import org.tzi.use.runtime.gui.IPluginAction;
import org.tzi.use.runtime.gui.IPluginActionDelegate;
import org.uet.dse.neo4j.sync.model.ModelSyncCoordinator;

public class ActionSyncModelBackward implements IPluginActionDelegate {
    @Override
    public void performAction(IPluginAction pluginAction) {
        MainWindow mainWindow = pluginAction.getParent();
        ModelSyncCoordinator coordinator = new ModelSyncCoordinator(pluginAction.getSession(), mainWindow);
        coordinator.syncBackward();
    }
}