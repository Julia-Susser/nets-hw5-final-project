package data;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the finefoods.txt dataset into Review objects
 * reviews are seperated by blank lines, each field is "key: value"
 */
public class DataLoader {

    /**
     * loads reviews from a file, trying charsets in order until one works
     *
     * @param path  path to the finefoods dataset
     * @param limit max reviews to load, or 0 to load everything
     * @return list of parsed reviews
     */
    public static List<Review> loadReviews(Path path, int limit) throws IOException {
        // some versions of the file have non-utf8 chars so we try both
        Charset[] charsets = { StandardCharsets.UTF_8, StandardCharsets.ISO_8859_1 };
        for (Charset charset : charsets) {
            try {
                return loadReviewsWithCharset(path, limit, charset);
            } catch (MalformedInputException e) {
                // try next charset
            }
        }
        throw new IOException("Unable to read dataset with supported encodings.");
    }

    private static List<Review> loadReviewsWithCharset(Path path, int limit, Charset charset) throws IOException {
        // read the whole file at once then split on blank lines to get blocks
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = Files.newBufferedReader(path, charset)) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append('\n');
            }
        }

        String[] blocks = content.toString().split("\\n\\s*\\n");
        List<Review> reviews = new ArrayList<>();
        for (String block : blocks) {
            Review review = parseBlock(block);
            if (review != null) {
                reviews.add(review);
                if (limit > 0 && reviews.size() >= limit) break; // hit the cap
            }
        }
        return reviews;
    }

    /**
     * parses one blank-line-delimited block into a Review
     * returns null if the block is malformed or missing fields
     *
     * @param block raw text block for a single review
     * @return parsed Review, or null if we couldnt parse it
     */
    private static Review parseBlock(String block) {
        String[] rawLines = block.split("\\R");
        List<String> lines = new ArrayList<>();
        for (String line : rawLines) {
            if (!line.isBlank()) lines.add(line.trim());
        }

        if (lines.size() < 8) return null; // not enough fields, skip it

        try {
            String productId    = valueAfterColon(lines.get(0));
            String userId       = valueAfterColon(lines.get(1));
            String profileName  = valueAfterColon(lines.get(2));
            String helpfulness  = valueAfterColon(lines.get(3));
            double score        = Double.parseDouble(valueAfterColon(lines.get(4)));
            long timestamp      = Long.parseLong(valueAfterColon(lines.get(5)));
            String summary      = valueAfterColon(lines.get(6));
            String text         = valueAfterColon(lines.get(7));

            String[] parts    = helpfulness.split("/");
            int helpfulNum    = Integer.parseInt(parts[0].trim());
            int helpfulDen    = Integer.parseInt(parts[1].trim());

            return new Review(productId, userId, profileName,
                              helpfulNum, helpfulDen, score, timestamp, summary, text);
        } catch (Exception e) {
            return null; // malformed block, just skip
        }
    }

    /** grabs everything after the first colon on a line */
    private static String valueAfterColon(String line) {
        int idx = line.indexOf(':');
        if (idx < 0) return "";
        return line.substring(idx + 1).trim();
    }
}
