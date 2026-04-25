package analysis;

import graph.Graph;
import recommendation.TFIDFEngine;

import java.util.*;

/**
 * computes summary stats for the bipartite review graph
 * counts nodes/edges and finds the higest degree users and products
 * called once when the stats tab loads
 */
public class NetworkStats {

    private final Graph       graph;
    private final TFIDFEngine tfidf;

    public NetworkStats(Graph graph, TFIDFEngine tfidf) {
        this.graph = graph;
        this.tfidf = tfidf;
    }

    /**
     * builds the full stats report as a formatted string
     * computes degree distributions for users and products then picks the top 10 of each
     *
     * @return multi-line report ready to dump into a text area
     */
    public String report() {
        long userCount    = graph.getNodes().values().stream()
                                 .filter(t -> t == Graph.NodeType.USER).count();
        long productCount = graph.getNodes().values().stream()
                                 .filter(t -> t == Graph.NodeType.PRODUCT).count();

        // collect degrees seperately for users and products
        List<Integer> uDeg    = new ArrayList<>();
        List<Integer> pDeg    = new ArrayList<>();
        List<Map.Entry<String, Integer>> prodList = new ArrayList<>();
        List<Map.Entry<String, Integer>> userList = new ArrayList<>();

        for (Map.Entry<String, Graph.NodeType> e : graph.getNodes().entrySet()) {
            int d = graph.getNeighbors(e.getKey()).size();
            if (e.getValue() == Graph.NodeType.USER) {
                uDeg.add(d);
                userList.add(new AbstractMap.SimpleEntry<>(e.getKey(), d));
            } else {
                pDeg.add(d);
                prodList.add(new AbstractMap.SimpleEntry<>(e.getKey(), d));
            }
        }

        // sort descending so index 0 = max and size/2 = rough median
        uDeg.sort(Collections.reverseOrder());
        pDeg.sort(Collections.reverseOrder());
        prodList.sort((a, b) -> b.getValue() - a.getValue());
        userList.sort((a, b) -> b.getValue() - a.getValue());

        double avgU = uDeg.stream().mapToInt(Integer::intValue).average().orElse(0);
        double avgP = pDeg.stream().mapToInt(Integer::intValue).average().orElse(0);
        int maxU    = uDeg.isEmpty() ? 0 : uDeg.get(0);
        int maxP    = pDeg.isEmpty() ? 0 : pDeg.get(0);
        int medU    = uDeg.isEmpty() ? 0 : uDeg.get(uDeg.size() / 2);
        int medP    = pDeg.isEmpty() ? 0 : pDeg.get(pDeg.size() / 2);

        StringBuilder sb = new StringBuilder();
        sb.append("============================================================\n");
        sb.append("  NETWORK STATISTICS\n");
        sb.append("============================================================\n\n");
        sb.append(String.format("  Total nodes      : %,d\n",   graph.nodeCount()));
        sb.append(String.format("  Users            : %,d\n",   userCount));
        sb.append(String.format("  Products         : %,d\n",   productCount));
        sb.append(String.format("  Edges (reviews)  : %,d\n\n", graph.edgeCount()));

        sb.append("  --- User degree (reviews written) ---\n");
        sb.append(String.format("  Average : %.2f\n", avgU));
        sb.append(String.format("  Median  : %d\n",   medU));
        sb.append(String.format("  Maximum : %d\n\n", maxU));

        sb.append("  --- Product degree (reviews recieved) ---\n");
        sb.append(String.format("  Average : %.2f\n", avgP));
        sb.append(String.format("  Median  : %d\n",   medP));
        sb.append(String.format("  Maximum : %d\n\n", maxP));

        sb.append("  TOP 10 MOST-REVIEWED PRODUCTS\n");
        sb.append("  ------------------------------------------------------------\n");
        for (int i = 0; i < Math.min(10, prodList.size()); i++) {
            Map.Entry<String, Integer> e = prodList.get(i);
            sb.append(String.format("  %2d. %-50s\n",
                    (i + 1), truncate(tfidf.getProductName(e.getKey()), 48)));
            sb.append(String.format("      ID: %-20s  (%,d reviews)\n",
                    e.getKey(), e.getValue()));
        }

        sb.append("\n  TOP 10 MOST ACTIVE REVIEWERS\n");
        sb.append("  ------------------------------------------------------------\n");
        for (int i = 0; i < Math.min(10, userList.size()); i++) {
            Map.Entry<String, Integer> e = userList.get(i);
            sb.append(String.format("  %2d. %-30s  (%,d reviews)\n",
                    (i + 1), e.getKey(), e.getValue()));
        }
        return sb.toString();
    }

    /** truncates a string with an ellipsis if its too long for the column */
    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
