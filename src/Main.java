import app.GraphViewer;
import data.DataLoader;
import data.Review;
import graph.Graph;
import recommendation.Recommender;
import recommendation.TFIDFEngine;

import javax.swing.SwingUtilities;
import java.nio.file.Paths;
import java.util.List;

/**
 * Run the code from this module
 * How to use:
 * java -cp out Main
 * java -cp out Main --data input/finefoods.txt
 */
public class Main {

    public static void main(String[] args) throws Exception {
        String dataPath = "input/finefoods.txt";
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals("--data")) dataPath = args[i + 1];
        }

        System.out.println("╔══════════════════════════════════════╗");
        System.out.println("║        Amazon Review Explorer        ║");
        System.out.println("╚══════════════════════════════════════╝");
        System.out.println();

        step("Loading all reviews from: " + dataPath);
        List<Review> reviews = DataLoader.loadReviews(Paths.get(dataPath), 0);
        System.out.println("  -> " + reviews.size() + " reviews loaded.");

        step("Building bipartite graph…");
        Graph graph = Graph.build(reviews);

        step("Computing TF-IDF vectors…");
        TFIDFEngine tfidf = new TFIDFEngine();
        tfidf.build(reviews);

        step("Initialising recommender…");
        Recommender recommender = new Recommender(graph, tfidf);

        step("Launching app…");
        GraphViewer viewer = new GraphViewer(reviews, graph, tfidf, recommender);
        SwingUtilities.invokeLater(() -> viewer.setVisible(true));
    }

    private static void step(String msg) {
        System.out.println("[*] " + msg);
    }
}
