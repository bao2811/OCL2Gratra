package org.uet.dse.neo4j.action_phase2;

import org.neo4j.driver.Driver;
import org.tzi.use.api.UseSystemApi;
import org.tzi.use.gui.main.MainWindow;
import org.tzi.use.main.Session;
import org.tzi.use.runtime.gui.IPluginAction;
import org.tzi.use.runtime.gui.IPluginActionDelegate;
import org.uet.dse.neo4j.gui.OclEvaluatorDialog;
import org.uet.dse.neo4j.manager.Neo4jDriverManager;
import org.uet.dse.neo4j.ocl.NEvaluator;
import org.uet.dse.neo4j.tgg.engine.NRuleCollectionManager;
import org.uet.dse.rtlplus.Main;

public class ActionOpenOclEvaluator implements IPluginActionDelegate {
    @Override
    public void performAction(IPluginAction pluginAction) {
        MainWindow mainWindow = pluginAction.getParent();
        Session session = pluginAction.getSession();

        NEvaluator neo4jEvaluator = new NEvaluator();

        System.out.println(Main.getTggRuleCollection().toString());
//        NRuleCollectionManager st = new NRuleCollectionManager(UseSystemApi.create(pluginAction.getSession().system(), true));
//        st.iterateThroughMRuleCollection();
        OclEvaluatorDialog dialog = new OclEvaluatorDialog(mainWindow, session, neo4jEvaluator);
        dialog.setVisible(true);
    }
}