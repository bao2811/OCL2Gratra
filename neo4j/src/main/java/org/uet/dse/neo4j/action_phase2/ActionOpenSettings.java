package org.uet.dse.neo4j.action_phase2;

import org.tzi.use.runtime.gui.IPluginAction;
import org.tzi.use.runtime.gui.IPluginActionDelegate;
import org.uet.dse.neo4j.gui.SyncSettingsDialog;

public class ActionOpenSettings implements IPluginActionDelegate {
    @Override
    public void performAction(IPluginAction pluginAction) {
        SyncSettingsDialog dialog = new SyncSettingsDialog(pluginAction.getParent());
        dialog.setVisible(true);
    }
}