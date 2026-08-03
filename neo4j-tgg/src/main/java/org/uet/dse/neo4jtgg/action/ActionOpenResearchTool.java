package org.uet.dse.neo4jtgg.action;

import org.tzi.use.runtime.gui.IPluginAction;
import org.tzi.use.runtime.gui.IPluginActionDelegate;
import org.uet.dse.neo4jtgg.ui.ResearchToolDialog;

/** Opens the single-model reproducible OCL-to-Cypher research workflow. */
public final class ActionOpenResearchTool implements IPluginActionDelegate {
    @Override
    public void performAction(IPluginAction pluginAction) {
        new ResearchToolDialog(pluginAction.getParent(), pluginAction.getSession()).setVisible(true);
    }
}
