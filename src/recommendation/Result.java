package recommendation;

/**
 * holds a single search or recommendation result
 * score is cosine sim for TF-IDF search, PPR score for reccomendations
 */
public class Result {
    public final String productId;
    public final String name;
    public final double score;

    public Result(String productId, String name, double score) {
        this.productId = productId;
        this.name      = name;
        this.score     = score;
    }
}
