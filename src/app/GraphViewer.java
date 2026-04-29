package app;

import analysis.NetworkStats;
import data.Review;
import graph.Graph;
import recommendation.Recommender;
import recommendation.Result;
import recommendation.TFIDFEngine;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

/**
 * main application window with three tabs
 * tab 1: TF-IDF free-text product search
 * tab 2: personalized PPR reccomendations for a user
 * tab 3: network stats (degrees, top products/users)
 */
public class GraphViewer extends JFrame {

    private final Graph        graph;
    private final TFIDFEngine  tfidf;
    private final Recommender  recommender;
    private final List<Review> reviews;

    private JLabel statusLabel; // bottom bar showing current action or result count

    public GraphViewer(List<Review> reviews, Graph graph,
                       TFIDFEngine tfidf, Recommender recommender) {
        super("The Reviewer Network for Amazon Fine Foods");
        this.reviews     = reviews;
        this.graph       = graph;
        this.tfidf       = tfidf;
        this.recommender = recommender;

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setPreferredSize(new Dimension(1200, 820));
        buildUI();
        pack();
        setLocationRelativeTo(null);
    }

    private void buildUI() {
        setLayout(new BorderLayout());

        // status bar at the bottom shows node/edge counts on startup
        statusLabel = new JLabel("  " + String.format("%,d", graph.nodeCount())
                + " nodes  |  " + String.format("%,d", graph.edgeCount())
                + " edges  |  " + String.format("%,d", reviews.size()) + " reviews loaded");
        statusLabel.setFont(statusLabel.getFont().deriveFont(12f));
        statusLabel.setBorder(BorderFactory.createEtchedBorder());
        add(statusLabel, BorderLayout.SOUTH);

        JTabbedPane tabs = new JTabbedPane(JTabbedPane.TOP);
        tabs.setFont(tabs.getFont().deriveFont(13f));
        tabs.addTab("TF-IDF Search", createSearchTab());
        tabs.addTab("Recommender",   createRecommenderTab());
        tabs.addTab("Network Stats", createStatsTab());
        add(tabs, BorderLayout.CENTER);
    }

    // =========================================================================
    // Tab 1 – TF-IDF Search
    // =========================================================================

    // built lazily on first search so startup isnt slow
    private Map<String, List<Review>> productReviewMap;

    private Map<String, List<Review>> getProductReviewMap() {
        if (productReviewMap == null) {
            productReviewMap = new HashMap<>();
            for (Review r : reviews) {
                productReviewMap.computeIfAbsent(r.productId, k -> new ArrayList<>()).add(r);
            }
        }
        return productReviewMap;
    }

    private JPanel createSearchTab() {
        JPanel panel = new JPanel(new BorderLayout(5, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JTextField queryField = new JTextField();
        queryField.setFont(queryField.getFont().deriveFont(14f));
        queryField.setToolTipText("e.g. \"organic coffee\", \"dog food chicken\", \"gluten free snack\"");
        JButton searchBtn = new JButton("Search");

        JPanel top = new JPanel(new BorderLayout(8, 0));
        top.add(new JLabel("Query: "), BorderLayout.WEST);
        top.add(queryField, BorderLayout.CENTER);
        top.add(searchBtn,  BorderLayout.EAST);
        panel.add(top, BorderLayout.NORTH);

        String[] cols = {"#", "Product ID", "Review Summary", "TF-IDF Score"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable table = styledTable(model);
        table.getColumnModel().getColumn(0).setMaxWidth(40);
        table.getColumnModel().getColumn(1).setPreferredWidth(140);
        table.getColumnModel().getColumn(2).setPreferredWidth(480);
        table.getColumnModel().getColumn(3).setPreferredWidth(110);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JTextArea detailArea = new JTextArea("Click a product row to read its reviews.");
        detailArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        detailArea.setLineWrap(true);
        detailArea.setWrapStyleWord(true);
        detailArea.setEditable(false);
        detailArea.setMargin(new Insets(6, 8, 6, 8));
        JScrollPane detailScroll = new JScrollPane(detailArea);
        detailScroll.setBorder(BorderFactory.createTitledBorder("Reviews for selected product"));

        // split: results table on top, review text on the bottom
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(table), detailScroll);
        split.setResizeWeight(0.55);
        split.setBorder(null);
        panel.add(split, BorderLayout.CENTER);

        // when a row is clicked, load all reviews for that product into the detail pane
        table.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int row = table.getSelectedRow();
            if (row < 0) return;
            String pid = (String) model.getValueAt(row, 1);
            List<Review> productReviews = getProductReviewMap().getOrDefault(pid, Collections.emptyList());
            if (productReviews.isEmpty()) {
                detailArea.setText("No reviews found for " + pid);
                return;
            }
            StringBuilder sb = new StringBuilder();
            sb.append("Product: ").append(pid)
              .append("  (").append(productReviews.size()).append(" reviews)\n");
            sb.append("=".repeat(60)).append("\n\n");
            for (int i = 0; i < productReviews.size(); i++) {
                Review r = productReviews.get(i);
                sb.append("Review ").append(i + 1).append("  |  ")
                  .append(String.format("%.0f", r.score)).append(" stars");
                if (r.profileName != null && !r.profileName.isBlank())
                    sb.append("  |  ").append(r.profileName);
                sb.append("\n");
                if (r.summary != null && !r.summary.isBlank())
                    sb.append(r.summary).append("\n");
                if (r.text != null && !r.text.isBlank())
                    sb.append(r.text).append("\n");
                sb.append("\n");
            }
            detailArea.setText(sb.toString());
            detailArea.setCaretPosition(0); // scroll back to top
        });

        // run the query on a background thread so the UI doesnt freeze
        ActionListener doSearch = e -> {
            String q = queryField.getText().trim();
            if (q.isEmpty()) return;
            setStatus("Searching: " + q + " …");
            new SwingWorker<List<Result>, Void>() {
                @Override protected List<Result> doInBackground() {
                    return tfidf.query(q, 20);
                }
                @Override protected void done() {
                    try {
                        List<Result> res = get();
                        model.setRowCount(0);
                        detailArea.setText("Click a product row to read its reviews.");
                        int rank = 1;
                        for (Result r : res) {
                            model.addRow(new Object[]{
                                rank++, r.productId, r.name,
                                String.format("%.4f", r.score)
                            });
                        }
                        setStatus("Found " + res.size() + " results for: \"" + q + "\"");
                    } catch (Exception ex) {
                        setStatus("Search error: " + ex.getMessage());
                    }
                }
            }.execute();
        };
        searchBtn.addActionListener(doSearch);
        queryField.addActionListener(doSearch); // also fires on enter key

        return panel;
    }

    // =========================================================================
    // Tab 2 – Recommender
    // =========================================================================

    private JPanel createRecommenderTab() {
        JPanel panel = new JPanel(new BorderLayout(5, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JTextField userField = new JTextField();
        userField.setFont(userField.getFont().deriveFont(14f));
        userField.setToolTipText("Enter a userId from the dataset, e.g. A3SGXH7AUHU8GW");
        JButton recBtn  = new JButton("Recommend");
        JButton randBtn = new JButton("Random user");

        JPanel inputRow = new JPanel(new BorderLayout(8, 0));
        inputRow.add(new JLabel("User ID: "), BorderLayout.WEST);
        inputRow.add(userField, BorderLayout.CENTER);
        inputRow.add(recBtn,    BorderLayout.EAST);

        JPanel helpRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 2));
        helpRow.add(new JLabel("Don't have a user ID? "));
        helpRow.add(randBtn);

        JPanel top = new JPanel(new BorderLayout(0, 4));
        top.add(inputRow, BorderLayout.NORTH);
        top.add(helpRow,  BorderLayout.SOUTH);
        panel.add(top, BorderLayout.NORTH);

        String[] cols = {"#", "Product ID", "Review Summary", "PPR Score"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable table = styledTable(model);
        table.getColumnModel().getColumn(0).setMaxWidth(40);
        table.getColumnModel().getColumn(1).setPreferredWidth(140);
        table.getColumnModel().getColumn(2).setPreferredWidth(560);
        table.getColumnModel().getColumn(3).setPreferredWidth(110);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);

        // PPR can take a moment on large subgraphs, run it off the EDT
        ActionListener doRec = e -> {
            String uid = userField.getText().trim();
            if (uid.isEmpty()) return;
            setStatus("Computing reccomendations for: " + uid + " …");
            new SwingWorker<List<Result>, Void>() {
                @Override protected List<Result> doInBackground() {
                    return recommender.recommend(uid, 20);
                }
                @Override protected void done() {
                    try {
                        List<Result> res = get();
                        model.setRowCount(0);
                        if (res.isEmpty()) {
                            setStatus("No reccomendations found for: " + uid
                                    + "  (user not in graph, or no unrated products reachable)");
                            return;
                        }
                        int rank = 1;
                        for (Result r : res) {
                            model.addRow(new Object[]{
                                rank++, r.productId, r.name,
                                String.format("%.6f", r.score)
                            });
                        }
                        setStatus(res.size() + " reccomendations for user: " + uid);
                    } catch (Exception ex) {
                        setStatus("Recommender error: " + ex.getMessage());
                    }
                }
            }.execute();
        };
        recBtn.addActionListener(doRec);
        userField.addActionListener(doRec);

        // pick a random user who has at least 3 reviews so PPR has something to work with
        randBtn.addActionListener(e -> {
            List<String> candidates = new ArrayList<>();
            for (Map.Entry<String, Graph.NodeType> entry : graph.getNodes().entrySet()) {
                if (entry.getValue() == Graph.NodeType.USER
                        && graph.getNeighbors(entry.getKey()).size() >= 3) {
                    candidates.add(entry.getKey());
                    if (candidates.size() >= 2000) break; // dont scan the whole graph
                }
            }
            if (!candidates.isEmpty()) {
                String picked = candidates.get(new Random().nextInt(candidates.size()));
                userField.setText(picked);
                doRec.actionPerformed(null);
            }
        });

        return panel;
    }

    // =========================================================================
    // Tab 3 – Network Stats
    // =========================================================================

    private JPanel createStatsTab() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JTextArea area = new JTextArea("Computing statistics…");
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        area.setEditable(false);
        panel.add(new JScrollPane(area), BorderLayout.CENTER);

        // compute stats off the EDT since it scans every node
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() {
                return new NetworkStats(graph, tfidf).report();
            }
            @Override protected void done() {
                try { area.setText(get()); area.setCaretPosition(0); }
                catch (Exception ex) { area.setText("Error: " + ex.getMessage()); }
            }
        }.execute();

        return panel;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /** shared table style so all three tabs look consistent */
    private static JTable styledTable(DefaultTableModel model) {
        JTable t = new JTable(model);
        t.setFont(t.getFont().deriveFont(13f));
        t.setRowHeight(22);
        t.getTableHeader().setFont(t.getFont().deriveFont(Font.BOLD, 13f));
        return t;
    }

    /** always update the status bar on the EDT even if called from a worker thread */
    private void setStatus(String msg) {
        SwingUtilities.invokeLater(() -> statusLabel.setText("  " + msg));
    }
}
