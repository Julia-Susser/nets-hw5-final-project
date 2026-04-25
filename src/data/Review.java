package data;

/**
 * simple data class holding one parsed review from the dataset
 * all fields are final since we never mutate a review after loading
 * helpfulness is stored as num/den so we dont lose the raw counts
 */
public class Review {
    public final String productId;
    public final String userId;
    public final String profileName;
    public final int helpfulnessNumerator;
    public final int helpfulnessDenominator;
    public final double score;       // 1.0 - 5.0 stars
    public final long timestamp;
    public final String summary;
    public final String text;

    public Review(String productId,
                  String userId,
                  String profileName,
                  int helpfulnessNumerator,
                  int helpfulnessDenominator,
                  double score,
                  long timestamp,
                  String summary,
                  String text) {
        this.productId             = productId;
        this.userId                = userId;
        this.profileName           = profileName;
        this.helpfulnessNumerator   = helpfulnessNumerator;
        this.helpfulnessDenominator = helpfulnessDenominator;
        this.score                 = score;
        this.timestamp             = timestamp;
        this.summary               = summary;
        this.text                  = text;
    }

    /**
     * ratio of helpful votes, or null if no one voted on it yet
     *
     * @return helpful votes / total votes, or null if denominator is 0
     */
    public Double helpfulnessRatio() {
        if (helpfulnessDenominator == 0) return null; // no votes yet
        return (double) helpfulnessNumerator / helpfulnessDenominator;
    }
}
