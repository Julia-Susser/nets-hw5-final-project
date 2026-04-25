package graph;

import data.Review;
import java.util.*;

/**
 * weighted undirected bipartite graph
 * two node types: USER and PRODUCT
 * edge weight = star rating so higher-rated reviews are stronger connections
 */
public class Graph {

    public enum NodeType { USER, PRODUCT }

    private final Map<String, NodeType> nodes = new LinkedHashMap<>();
    private final Map<String, List<Edge>> adj  = new HashMap<>();

    // edge just holds the destination and the weight
    public static class Edge {
        public final String to;
        public final double weight;

        public Edge(String to, double weight) {
            this.to     = to;
            this.weight = weight;
        }
    }

    /**
     * adds a node if it doesnt exist yet, ignores duplicates
     *
     * @param id   node identifier (userId or productId)
     * @param type USER or PRODUCT
     */
    public void addNode(String id, NodeType type) {
        nodes.putIfAbsent(id, type);
        adj.putIfAbsent(id, new ArrayList<>());
    }

    /**
     * adds an undirected edge by inserting into both adjacency lists
     *
     * @param from   source node id
     * @param to     dest node id
     * @param weight edge weight (star rating)
     */
    public void addEdge(String from, String to, double weight) {
        adj.computeIfAbsent(from, k -> new ArrayList<>()).add(new Edge(to,   weight));
        adj.computeIfAbsent(to,   k -> new ArrayList<>()).add(new Edge(from, weight));
    }

    public Map<String, NodeType> getNodes() {
        return Collections.unmodifiableMap(nodes);
    }

    public Set<String> getNodeIds() {
        return nodes.keySet();
    }

    /** returns empty list instead of null for nodes with no edges */
    public List<Edge> getNeighbors(String id) {
        return adj.getOrDefault(id, Collections.emptyList());
    }

    public NodeType getType(String id) {
        return nodes.get(id);
    }

    public int nodeCount() {
        return nodes.size();
    }

    /**
     * each undirected edge is stored twice (once per direction) so divide by 2
     *
     * @return number of undirected edges in the graph
     */
    public int edgeCount() {
        int total = 0;
        for (List<Edge> list : adj.values()) total += list.size();
        return total / 2;
    }

    /**
     * builds a weighted bipartite user-product graph from a list of reviews
     * edge weight = star rating so higher-rated reviews are stronger connections
     *
     * @param reviews list of parsed reviews from DataLoader
     * @return fully built Graph ready for PPR and stats
     */
    public static Graph build(List<Review> reviews) {
        Graph graph = new Graph();
        for (Review r : reviews) {
            graph.addNode(r.userId,    NodeType.USER);
            graph.addNode(r.productId, NodeType.PRODUCT);
            graph.addEdge(r.userId, r.productId, r.score); // score becomes edge weight
        }
        return graph;
    }
}
