package org.uet.dse.neo4j.action_phase2;

import org.tzi.use.gui.main.MainWindow;
import org.tzi.use.main.Session;
import org.tzi.use.runtime.gui.IPluginAction;
import org.tzi.use.runtime.gui.IPluginActionDelegate;
import org.uet.dse.neo4j.gui.Neo4jDashboardView;
import org.uet.dse.neo4j.manager.Neo4jDriverManager;
import javax.swing.*;

public class ActionOpenDashboard implements IPluginActionDelegate {
    @Override
    public void performAction(IPluginAction pluginAction) {
        if (Neo4jDriverManager.getInstance() == null || !Neo4jDriverManager.getInstance().isConnected()) {
            JOptionPane.showMessageDialog(pluginAction.getParent(),
                    "Please connect to Neo4j database first!", "Not Connected", JOptionPane.WARNING_MESSAGE);
            return;
        }

        MainWindow mainWindow = pluginAction.getParent();
        Session session = pluginAction.getSession();

        Neo4jDashboardView dashboard = Neo4jDashboardView.getInstance(mainWindow, session);
        dashboard.setVisible(true);
        dashboard.toFront(); // Đưa lên phía trước
    }
}