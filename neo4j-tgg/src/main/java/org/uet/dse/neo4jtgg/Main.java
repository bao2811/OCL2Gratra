package org.uet.dse.neo4jtgg;

import org.tzi.use.runtime.IPlugin;
import org.tzi.use.runtime.IPluginRuntime;

public class Main implements IPlugin {
    @Override
    public String getName() {
        return "Neo4jTggPlugin";
    }

    @Override
    public void run(IPluginRuntime pluginRuntime) {
        // Actions are registered declaratively via useplugin.xml.
    }
}
