package org.uet.dse.neo4j.action_phase2;

import org.tzi.use.gui.main.MainWindow;
import org.tzi.use.main.Session;
import org.tzi.use.runtime.gui.IPluginAction;
import org.tzi.use.runtime.gui.IPluginActionDelegate;
import org.uet.dse.neo4j.gui.Neo4jConnectionDialog;

public class ActionOpenNeo4j implements IPluginActionDelegate {

    @Override
    public void performAction(IPluginAction pluginAction) {
        Session session = pluginAction.getSession();
        MainWindow mainWindow = pluginAction.getParent();

        // Mở Dialog kết nối
        Neo4jConnectionDialog dialog = new Neo4jConnectionDialog(mainWindow, session);
        dialog.setVisible(true);
    }
}