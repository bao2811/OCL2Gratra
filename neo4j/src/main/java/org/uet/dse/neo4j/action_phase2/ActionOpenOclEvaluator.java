package org.uet.dse.neo4j.action_phase2;

import org.tzi.use.gui.main.MainWindow;
import org.tzi.use.main.Session;
import org.tzi.use.runtime.gui.IPluginAction;
import org.tzi.use.runtime.gui.IPluginActionDelegate;
import org.uet.dse.neo4j.gui.OclEvaluatorDialog;
import org.uet.dse.neo4j.ocl.NEvaluator;

public class ActionOpenOclEvaluator implements IPluginActionDelegate {
    @Override
    public void performAction(IPluginAction pluginAction) {
        MainWindow mainWindow = pluginAction.getParent();
        Session session = pluginAction.getSession();

        NEvaluator neo4jEvaluator = new NEvaluator();
        OclEvaluatorDialog dialog = new OclEvaluatorDialog(mainWindow, session, neo4jEvaluator);
        dialog.setVisible(true);
    }
}
