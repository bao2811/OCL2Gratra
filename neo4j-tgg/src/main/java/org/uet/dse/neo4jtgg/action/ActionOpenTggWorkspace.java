package org.uet.dse.neo4jtgg.action;

import org.tzi.use.gui.main.MainWindow;
import org.tzi.use.runtime.gui.IPluginAction;
import org.tzi.use.runtime.gui.IPluginActionDelegate;
import org.uet.dse.neo4jtgg.ui.TggWorkspaceDialog;

public class ActionOpenTggWorkspace implements IPluginActionDelegate {
    @Override
    public void performAction(IPluginAction pluginAction) {
        MainWindow mainWindow = pluginAction.getParent();
        TggWorkspaceDialog dialog = new TggWorkspaceDialog(mainWindow, pluginAction.getSession());
        dialog.setVisible(true);
    }
}
