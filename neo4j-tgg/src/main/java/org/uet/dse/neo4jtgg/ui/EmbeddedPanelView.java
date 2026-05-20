package org.uet.dse.neo4jtgg.ui;

import org.tzi.use.gui.views.View;

import javax.swing.*;
import java.awt.*;

@SuppressWarnings("serial")
public class EmbeddedPanelView extends JPanel implements View {
    public EmbeddedPanelView(JComponent delegate) {
        super(new BorderLayout());
        add(delegate, BorderLayout.CENTER);
    }

    @Override
    public void detachModel() {
        // Nothing extra to detach for embedded utility panels.
    }
}
