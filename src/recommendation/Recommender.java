package recommendation;

import graph.Graph;

import java.util.*;

/**
 * PPR algorithm with restart recommender
 * seeds are products the user rated 4 or 5 stars
 * walks through the graph weighted by those same ratings
 */
public class Recommender {

    private static final double ALPHA     = 0.15;  // restart/teleport prob (damping = 1 - alpha = 0.85)
    private static final int    MAX_ITER  = 40;   
    private static final double THRESHOLD = 1e-6;  // stop early if scores barely move
    private static final int    MAX_NODES = 4000;  // BFS cap so we dont explode on huge graphs

    private final Graph       graph;
    private final TFIDFEngine tfidf;

    public Recommender(Graph graph, TFIDFEngine tfidf) {
        this.graph = graph;
        this.tfidf = tfidf;
    }

    /**
     * Runs personalized PageRank from a user's highly-rated products
     * and returns the top unseen products by score.
     *
     * @param userId the user to generate reccomendations for
     * @param topK   how many reccomendations to return
     * @return list of up to topK products the user hasnt reviewed yet, sorted by PPR score
     */
    public List<Result> recommend(String userId, int topK) {
        // stop early if this id isnt even a user node
        if (graph.getType(userId) != Graph.NodeType.USER) return Collections.emptyList();

        List<Graph.Edge> userEdges = graph.getNeighbors(userId);
        if (userEdges.isEmpty()) return Collections.emptyList();

        // seed on 4/5-star products; fall back to everything if none exist
        // (some users only give low ratings so we need the fallback)
        Map<String, Double> seed = new LinkedHashMap<>();
        double seedSum = 0;
        for (Graph.Edge e : userEdges) {
            if (e.weight >= 4.0) { seed.put(e.to, e.weight); seedSum += e.weight; }
        }
        if (seed.isEmpty()) {
            // user never gave a high rating, just use all their products
            for (Graph.Edge e : userEdges) { seed.put(e.to, e.weight); seedSum += e.weight; }
        }
        if (seedSum == 0) return Collections.emptyList();
        final double fs = seedSum;
        seed.replaceAll((k, v) -> v / fs); // normalize so it sums to 1

        // BFS out from seed nodes to build a local subgraph we can actually run PPR on
        // dont want to run on the full graph, way too slow
        // subgraph is list of nodes in linked hashset whcih iterates in the same order each time
        Set<String> subgraph = new LinkedHashSet<>();
        subgraph.add(userId);
        subgraph.addAll(seed.keySet());
        Queue<String> queue = new ArrayDeque<>(seed.keySet());

        while (!queue.isEmpty() && subgraph.size() < MAX_NODES) {
            String node = queue.poll();
            for (Graph.Edge e : graph.getNeighbors(node)) {
                if (subgraph.add(e.to)) queue.add(e.to); // only visit new nodes
            }
        }

        // power iteration — spread score through edges
        // walk with (1-alpha) damping, teleport back to seed with prob alpha each round
        // start uniform across all nodes in the subgraph
        Map<String, Double> scores = new HashMap<>();
        double init = 1.0 / subgraph.size();
        for (String n : subgraph) scores.put(n, init);

        for (int iter = 0; iter < MAX_ITER; iter++) {
            Map<String, Double> next = new HashMap<>();
            for (String n : subgraph) next.put(n, 0.0);

            for (String n : subgraph) {
                List<Graph.Edge> nbrs = graph.getNeighbors(n);
                // only count edges that stay inside our subgraph
                double wsum = 0;
                for (Graph.Edge e : nbrs) if (subgraph.contains(e.to)) wsum += e.weight;
                if (wsum == 0) continue; // dangling node, skip

                double ns = scores.get(n);
                for (Graph.Edge e : nbrs) {
                    if (subgraph.contains(e.to)) {
                        // distribute score proporionally by edge weight
                        next.merge(e.to, (1 - ALPHA) * ns * (e.weight / wsum), Double::sum);
                    }
                }
            }

            // alpha restart: teleport back to seed with prob alpha
            for (Map.Entry<String, Double> e : seed.entrySet()) {
                if (next.containsKey(e.getKey())) {
                    next.merge(e.getKey(), ALPHA * e.getValue(), Double::sum);
                }
            }

            // renormalize so scores still sum to 1
            double sum = next.values().stream().mapToDouble(d -> d).sum();
            if (sum > 0) next.replaceAll((k, v) -> v / sum);

            // check if we converged (L1 diff between rounds)
            double diff = 0;
            for (String n : subgraph) {
                diff += Math.abs(next.getOrDefault(n, 0.0) - scores.getOrDefault(n, 0.0));
            }
            scores = next;
            if (diff < THRESHOLD) break;
        }

        // filter to products the user hasnt touched yet and grab the top K
        Set<String> alreadyRated = new HashSet<>();
        for (Graph.Edge e : userEdges) alreadyRated.add(e.to);

        List<Result> results = new ArrayList<>();
        for (Map.Entry<String, Double> e : scores.entrySet()) {
            if (graph.getType(e.getKey()) == Graph.NodeType.PRODUCT
                    && !alreadyRated.contains(e.getKey())) {
                String name = tfidf.getProductName(e.getKey());
                results.add(new Result(e.getKey(), name, e.getValue()));
            }
        }
        results.sort((a, b) -> Double.compare(b.score, a.score));
        return results.subList(0, Math.min(topK, results.size()));
    }
}
