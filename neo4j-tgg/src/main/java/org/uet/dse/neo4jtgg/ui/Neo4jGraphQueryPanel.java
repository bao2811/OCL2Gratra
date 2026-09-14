package org.uet.dse.neo4jtgg.ui;

import org.neo4j.driver.AccessMode;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Value;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Path;
import org.neo4j.driver.types.Relationship;
import org.uet.dse.neo4j.manager.Neo4jDriverManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Read-only Cypher console and lightweight graph visualization. */
@SuppressWarnings("serial")
public final class Neo4jGraphQueryPanel extends JPanel {
    private static final int MAX_RECORDS = 500;
    private final JTextArea query = new JTextArea(4, 80);
    private final JTextArea details = new JTextArea();
    private final JLabel summary = new JLabel("No query executed.");
    private final GraphCanvas canvas = new GraphCanvas();
    private final JButton runButton = new JButton("Run");
    private final JButton modelGraphButton = new JButton("Model Graph");

    public Neo4jGraphQueryPanel() {
        super(new BorderLayout(8, 8));
        setBorder(new EmptyBorder(8, 8, 8, 8));
        query.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        query.setText("MATCH (n) OPTIONAL MATCH (n)-[r]-(m) RETURN n, r, m LIMIT 200");
        details.setEditable(false);
        details.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        details.setText("Select a node or relationship to inspect its properties.");
        canvas.setSelectionListener(details::setText);
        add(buildQueryPanel(), BorderLayout.NORTH);
        add(buildGraphArea(), BorderLayout.CENTER);
    }

    private JComponent buildQueryPanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(BorderFactory.createTitledBorder("Read-only Cypher Query"));
        panel.add(new JScrollPane(query), BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        JButton reset = new JButton("Reset");
        runButton.setToolTipText("Run the read-only Cypher query and visualize its graph result.");
        modelGraphButton.setToolTipText("Load the canonical M2/M1 model graph.");
        reset.setToolTipText("Reset graph zoom and position.");
        runButton.addActionListener(event -> runQuery());
        modelGraphButton.addActionListener(event -> {
            query.setText("MATCH (n) WHERE n.modelKey IS NOT NULL OR n.modelName IS NOT NULL "
                    + "OPTIONAL MATCH (n)-[r]-(m) RETURN n, r, m LIMIT 300");
            runQuery();
        });
        reset.addActionListener(event -> canvas.resetView());
        actions.add(runButton);
        actions.add(modelGraphButton);
        actions.add(reset);
        actions.add(summary);
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    private JComponent buildGraphArea() {
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                titled(new JScrollPane(canvas), "Graph"),
                titled(new JScrollPane(details), "Selection / Scalar Results"));
        split.setResizeWeight(0.78);
        split.setDividerLocation(820);
        return split;
    }

    private void runQuery() {
        String cypher = query.getText().trim();
        if (cypher.isBlank()) return;
        if (!isReadOnly(cypher)) {
            JOptionPane.showMessageDialog(this,
                    "Graph viewer accepts read-only MATCH, OPTIONAL MATCH, WITH, UNWIND, RETURN, SHOW or EXPLAIN queries.",
                    "Read-only Query Required", JOptionPane.WARNING_MESSAGE);
            return;
        }
        Neo4jDriverManager manager = Neo4jDriverManager.getInstance();
        if (manager == null || !manager.isConnected()) {
            JOptionPane.showMessageDialog(this, "Connect Neo4j before running a graph query.",
                    "Neo4j Connection Required", JOptionPane.WARNING_MESSAGE);
            return;
        }
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        runButton.setEnabled(false);
        modelGraphButton.setEnabled(false);
        summary.setText("Running query...");
        new SwingWorker<QueryView, Void>() {
            @Override protected QueryView doInBackground() {
                try (Session session = manager.getDriver().session(SessionConfig.builder()
                        .withDatabase(manager.getActiveDatabase())
                        .withDefaultAccessMode(AccessMode.READ).build())) {
                    Result result = session.run(cypher);
                    GraphData graph = new GraphData();
                    StringBuilder scalars = new StringBuilder();
                    int recordCount = 0;
                    while (result.hasNext() && recordCount < MAX_RECORDS) {
                        Record record = result.next();
                        recordCount++;
                        for (String key : record.keys()) collect(record.get(key), graph, scalars, key);
                    }
                    return new QueryView(graph, scalars.toString(), recordCount);
                }
            }

            @Override protected void done() {
                setCursor(Cursor.getDefaultCursor());
                runButton.setEnabled(true);
                modelGraphButton.setEnabled(true);
                try {
                    QueryView view = get();
                    canvas.setGraph(view.graph());
                    details.setText(view.scalars().isEmpty()
                            ? "Select a node or relationship to inspect its properties."
                            : view.scalars());
                    summary.setText("records=" + view.recordCount()
                            + ", nodes=" + view.graph().nodes.size()
                            + ", relationships=" + view.graph().edges.size()
                            + (view.recordCount() == MAX_RECORDS ? " (record limit reached)" : ""));
                } catch (Exception exception) {
                    Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                    summary.setText("Query failed");
                    details.setText(cause.getMessage());
                    JOptionPane.showMessageDialog(Neo4jGraphQueryPanel.this, cause.getMessage(),
                            "Cypher Query Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private record QueryView(GraphData graph, String scalars, int recordCount) { }

    private void collect(Value value, GraphData graph, StringBuilder scalars, String key) {
        if (value == null || value.isNull()) return;
        collectObject(value.asObject(), graph, scalars, key);
    }

    private void collectObject(Object object, GraphData graph, StringBuilder scalars, String key) {
        if (object instanceof Node node) {
            graph.add(node);
        } else if (object instanceof Relationship relationship) {
            graph.add(relationship);
        } else if (object instanceof Path path) {
            path.nodes().forEach(graph::add);
            path.relationships().forEach(graph::add);
        } else if (object instanceof Map<?, ?> map) {
            map.forEach((childKey, child) -> collectObject(child, graph, scalars, String.valueOf(childKey)));
        } else if (object instanceof Collection<?> collection) {
            for (Object child : collection) collectObject(child, graph, scalars, key);
        } else {
            scalars.append(key).append(" = ").append(object).append('\n');
        }
    }

    private boolean isReadOnly(String cypher) {
        String normalized = cypher.stripLeading().toUpperCase(Locale.ROOT);
        if (normalized.contains(";") || normalized.matches("(?s).*\\b(CREATE|MERGE|DELETE|DETACH|SET|REMOVE|DROP|LOAD CSV|FOREACH)\\b.*")) {
            return false;
        }
        return List.of("MATCH", "OPTIONAL MATCH", "WITH", "UNWIND", "RETURN", "SHOW", "EXPLAIN")
                .stream().anyMatch(normalized::startsWith);
    }

    private static JComponent titled(JComponent component, String title) {
        component.setBorder(BorderFactory.createTitledBorder(title));
        return component;
    }

    private interface SelectionListener {
        void selected(String details);
    }

    private static final class GraphData {
        private final Map<String, VisualNode> nodes = new LinkedHashMap<>();
        private final Map<String, VisualEdge> edges = new LinkedHashMap<>();

        void add(Node node) {
            String id = node.elementId();
            Set<String> labels = new LinkedHashSet<>();
            node.labels().forEach(labels::add);
            nodes.put(id, new VisualNode(id, labels, node.asMap(), false));
        }

        void add(Relationship relationship) {
            String source = relationship.startNodeElementId();
            String target = relationship.endNodeElementId();
            nodes.putIfAbsent(source, VisualNode.placeholder(source));
            nodes.putIfAbsent(target, VisualNode.placeholder(target));
            edges.put(relationship.elementId(), new VisualEdge(relationship.elementId(), source, target,
                    relationship.type(), relationship.asMap()));
        }
    }

    private record VisualNode(String id, Set<String> labels, Map<String, Object> properties, boolean placeholder) {
        static VisualNode placeholder(String id) {
            return new VisualNode(id, Set.of("endpoint"), Map.of(), true);
        }

        String caption() {
            for (String key : List.of("use_id", "name", "classKey", "modelKey")) {
                Object value = properties.get(key);
                if (value != null) return String.valueOf(value);
            }
            return labels.isEmpty() ? id : labels.iterator().next();
        }

        String describe() {
            return "NODE\nid=" + id + "\nlabels=" + labels + "\nproperties=" + properties;
        }
    }

    private record VisualEdge(String id, String source, String target, String type, Map<String, Object> properties) {
        String describe() {
            return "RELATIONSHIP\nid=" + id + "\ntype=" + type + "\nsource=" + source
                    + "\ntarget=" + target + "\nproperties=" + properties;
        }
    }

    private static final class GraphCanvas extends JPanel {
        private GraphData graph = new GraphData();
        private final Map<String, Point.Double> positions = new LinkedHashMap<>();
        private double zoom = 1.0;
        private double panX;
        private double panY;
        private Point dragStart;
        private String draggedNodeId;
        private SelectionListener selectionListener = ignored -> { };

        GraphCanvas() {
            setPreferredSize(new Dimension(1200, 800));
            setBackground(Color.WHITE);
            setToolTipText("Drag a node to reposition it; drag empty space to pan; use the mouse wheel to zoom.");
            MouseAdapter mouse = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent event) {
                    dragStart = event.getPoint();
                    draggedNodeId = nodeAt(event.getPoint());
                    select(event.getPoint());
                    setCursor(Cursor.getPredefinedCursor(
                            draggedNodeId == null ? Cursor.MOVE_CURSOR : Cursor.HAND_CURSOR));
                }
                @Override public void mouseDragged(MouseEvent event) {
                    if (dragStart == null) return;
                    double dx = event.getX() - dragStart.x;
                    double dy = event.getY() - dragStart.y;
                    if (draggedNodeId == null) {
                        panX += dx;
                        panY += dy;
                    } else {
                        Point.Double position = positions.get(draggedNodeId);
                        if (position != null) {
                            position.x += dx / zoom;
                            position.y += dy / zoom;
                        }
                    }
                    dragStart = event.getPoint();
                    repaint();
                }
                @Override public void mouseReleased(MouseEvent event) {
                    dragStart = null;
                    draggedNodeId = null;
                    setCursor(Cursor.getDefaultCursor());
                }
                @Override public void mouseMoved(MouseEvent event) {
                    setCursor(Cursor.getPredefinedCursor(
                            nodeAt(event.getPoint()) == null ? Cursor.DEFAULT_CURSOR : Cursor.HAND_CURSOR));
                }
                @Override public void mouseWheelMoved(MouseWheelEvent event) {
                    zoom = Math.max(0.25, Math.min(3.5, zoom * (event.getWheelRotation() < 0 ? 1.12 : 0.89)));
                    repaint();
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
            addMouseWheelListener(mouse);
        }

        void setSelectionListener(SelectionListener listener) { this.selectionListener = listener; }

        void setGraph(GraphData graph) {
            this.graph = graph;
            layoutGraph();
            resetView();
        }

        void resetView() { zoom = 1.0; panX = 0; panY = 0; repaint(); }

        private void layoutGraph() {
            positions.clear();
            int count = Math.max(1, graph.nodes.size());
            double radius = Math.max(170, Math.min(520, count * 14.0));
            int index = 0;
            for (String id : graph.nodes.keySet()) {
                double angle = 2 * Math.PI * index++ / count;
                positions.put(id, new Point.Double(600 + radius * Math.cos(angle), 400 + radius * Math.sin(angle)));
            }
            setPreferredSize(new Dimension((int) Math.max(1200, radius * 2 + 300),
                    (int) Math.max(800, radius * 2 + 300)));
            revalidate();
        }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.transform(new AffineTransform(zoom, 0, 0, zoom, panX, panY));
            g.setStroke(new BasicStroke(1.2f));
            for (VisualEdge edge : graph.edges.values()) drawEdge(g, edge);
            for (VisualNode node : graph.nodes.values()) drawNode(g, node);
            g.dispose();
        }

        private void drawEdge(Graphics2D g, VisualEdge edge) {
            Point.Double from = positions.get(edge.source());
            Point.Double to = positions.get(edge.target());
            if (from == null || to == null) return;
            g.setColor(new Color(125, 125, 125));
            g.drawLine((int) from.x, (int) from.y, (int) to.x, (int) to.y);
            double middleX = (from.x + to.x) / 2;
            double middleY = (from.y + to.y) / 2;
            g.setColor(new Color(70, 70, 70));
            g.drawString(edge.type(), (int) middleX + 4, (int) middleY - 4);
        }

        private void drawNode(Graphics2D g, VisualNode node) {
            Point.Double point = positions.get(node.id());
            if (point == null) return;
            double width = 120;
            double height = 48;
            Shape shape = new Ellipse2D.Double(point.x - width / 2, point.y - height / 2, width, height);
            g.setColor(color(node));
            g.fill(shape);
            g.setColor(new Color(45, 45, 45));
            g.draw(shape);
            String caption = node.caption();
            if (caption.length() > 18) caption = caption.substring(0, 17) + "…";
            FontMetrics metrics = g.getFontMetrics();
            g.drawString(caption, (int) (point.x - metrics.stringWidth(caption) / 2.0), (int) point.y + 4);
        }

        private Color color(VisualNode node) {
            if (node.placeholder()) return new Color(230, 230, 230);
            if (node.labels().contains("Object")) return new Color(157, 211, 255);
            if (node.labels().stream().anyMatch(label -> label.contains("Class"))) return new Color(174, 231, 183);
            if (node.labels().stream().anyMatch(label -> label.contains("Attribute"))) return new Color(255, 220, 158);
            return new Color(210, 190, 242);
        }

        private void select(Point screenPoint) {
            String selectedNodeId = nodeAt(screenPoint);
            if (selectedNodeId != null) {
                selectionListener.selected(graph.nodes.get(selectedNodeId).describe());
            }
        }

        private String nodeAt(Point screenPoint) {
            double x = (screenPoint.x - panX) / zoom;
            double y = (screenPoint.y - panY) / zoom;
            for (VisualNode node : graph.nodes.values()) {
                Point.Double point = positions.get(node.id());
                if (point != null && Math.abs(x - point.x) <= 60 && Math.abs(y - point.y) <= 24) {
                    return node.id();
                }
            }
            return null;
        }
    }
}
