package org.uet.dse.neo4j.action_phase2;

import org.tzi.use.api.UseSystemApi;
import org.tzi.use.gui.main.MainWindow;
import org.tzi.use.runtime.gui.IPluginAction;
import org.tzi.use.runtime.gui.IPluginActionDelegate;
import org.uet.dse.neo4j.gui.UseFileBrowserDialog;

public class ActionOpenIncrementalUpdate implements IPluginActionDelegate {
    @Override
    public void performAction(IPluginAction pluginAction) {
        MainWindow mainWindow = pluginAction.getParent();
        UseFileBrowserDialog dialog = new UseFileBrowserDialog(mainWindow, mainWindow.logWriter(), pluginAction.getSession());
        dialog.setVisible(true);
    }
}