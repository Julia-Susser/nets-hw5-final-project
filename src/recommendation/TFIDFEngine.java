package recommendation;

import data.Review;

import java.util.*;
import java.util.stream.Collectors;

/**
 * TF-IDF engine that builds one document per product from all its review text
 * supports free-text cosine similairty queries against those vectors
 * display name for each product is taken from the first non-blank review summary
 */
public class TFIDFEngine {

    // productId -> { term -> tfidf score }
    private final Map<String, Map<String, Double>> vectors = new HashMap<>();
    // productId -> display name (first non-blank review summary)
    private final Map<String, String> productNames = new HashMap<>();

    // common words that dont add any signal, filter these out before indexing
    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
        "the","a","an","and","or","but","in","on","at","to","for","of","with",
        "is","it","this","that","was","are","be","have","has","had","do","does",
        "did","will","would","could","should","may","might","i","you","he","she",
        "we","they","my","your","his","her","our","their","not","no","so","as",
        "if","by","from","up","out","about","into","through","before","after",
        "each","more","very","just","been","being","its","also","than","then",
        "when","where","who","which","what","how","all","both","few","some",
        "such","only","own","same","other","because","while","although","get",
        "got","like","one","can","use","am","ll","re","ve","don","didn","isn",
        "wasn","aren","won","hasn","nt","br","gt","lt"
    ));

    /**
     * builds TF-IDF vectors for all products from the review list
     * concatenates all review text for a product into one big document
     * then computes term freq and idf across the whole corpus
     *
     * @param reviews list of all loaded reviews
     */
    public void build(List<Review> reviews) {
        // step 1: merge all review text per product into one doc
        Map<String, StringBuilder> docs    = new HashMap<>();
        Map<String, String>        nameMap = new LinkedHashMap<>();

        for (Review r : reviews) {
            docs.computeIfAbsent(r.productId, k -> new StringBuilder())
                .append(' ').append(r.summary).append(' ').append(r.text);
            // grab the first usable summary as the display name
            if (!nameMap.containsKey(r.productId)
                    && r.summary != null && !r.summary.isBlank()) {
                nameMap.put(r.productId, r.summary.trim());
            }
        }
        // fall back to productId for products with no usable summary
        for (String pid : docs.keySet()) {
            productNames.put(pid, nameMap.getOrDefault(pid, pid));
        }

        // step 2: count how often each term appears in each product's doc
        Map<String, Map<String, Integer>> tfRaw   = new HashMap<>();
        Map<String, Integer>              docFreq = new HashMap<>();
        int numDocs = docs.size();

        for (Map.Entry<String, StringBuilder> e : docs.entrySet()) {
            String pid   = e.getKey();
            String[] tks = tokenize(e.getValue().toString());
            Map<String, Integer> tf = new HashMap<>();
            for (String t : tks) tf.merge(t, 1, Integer::sum);
            tfRaw.put(pid, tf);
            // track how many docs contain each term (for idf)
            for (String term : tf.keySet()) docFreq.merge(term, 1, Integer::sum);
        }

        // step 3: turn raw counts into TF-IDF weights
        for (Map.Entry<String, Map<String, Integer>> e : tfRaw.entrySet()) {
            String pid = e.getKey();
            Map<String, Integer> tf = e.getValue();
            int total = tf.values().stream().mapToInt(Integer::intValue).sum();
            if (total == 0) continue;

            Map<String, Double> vec = new HashMap<>();
            for (Map.Entry<String, Integer> te : tf.entrySet()) {
                String term  = te.getKey();
                double tfVal = (double) te.getValue() / total;
                double idf   = Math.log(1.0 + (double) numDocs
                                              / docFreq.getOrDefault(term, 1));
                vec.put(term, tfVal * idf);
            }
            vectors.put(pid, vec);
        }
    }

    /**
     * returns the top-K products by cosine similairty to the query text
     *
     * @param queryText free text query from the user
     * @param topK      how many results to return
     * @return list of results sorted by score descending
     */
    public List<Result> query(String queryText, int topK) {
        String[] tokens = tokenize(queryText);
        if (tokens.length == 0) return Collections.emptyList();

        // build a simple term-frequency vector for the query
        Map<String, Double> qVec = new HashMap<>();
        for (String t : tokens) {
            qVec.put(t, qVec.getOrDefault(t, 0.0) + 1.0);
        }

        // score every product and keep the ones that have any overlap
        List<Result> results = new ArrayList<>();
        for (Map.Entry<String, Map<String, Double>> e : vectors.entrySet()) {
            double score = cosine(qVec, e.getValue());
            if (score > 0) {
                results.add(new Result(e.getKey(),
                        productNames.getOrDefault(e.getKey(), e.getKey()), score));
            }
        }
        results.sort((a, b) -> Double.compare(b.score, a.score));
        return results.subList(0, Math.min(topK, results.size()));
    }

    /** looks up the display name we stored during build, falls back to the id */
    public String getProductName(String productId) {
        return productNames.getOrDefault(productId, productId);
    }

    /**
     * standard cosine similairty between two sparse term vectors
     *
     * @param q   query vector
     * @param doc document vector
     * @return cosine score between 0 and 1
     */
    private double cosine(Map<String, Double> q, Map<String, Double> doc) {
        double dot = 0, mq = 0, md = 0;
        for (Map.Entry<String, Double> e : q.entrySet()) {
            Double dv = doc.get(e.getKey());
            if (dv != null) dot += e.getValue() * dv; // only shared terms contribute
            mq += e.getValue() * e.getValue();
        }
        for (double v : doc.values()) md += v * v;
        if (mq == 0 || md == 0) return 0;
        return dot / (Math.sqrt(mq) * Math.sqrt(md));
    }

    /**
     * lowercases, strips punctuation, splits on whitespace, removes stop words
     * and drops tokens shorter than 3 chars since they're usually noise
     *
     * @param text raw input text
     * @return array of cleaned tokens
     */
    private String[] tokenize(String text) {
        if (text == null || text.isBlank()) return new String[0];
        return Arrays.stream(
                text.toLowerCase()
                    .replaceAll("[^a-z0-9\\s]", " ")
                    .split("\\s+"))
            .filter(t -> t.length() > 2 && !STOP_WORDS.contains(t))
            .toArray(String[]::new);
    }
}
