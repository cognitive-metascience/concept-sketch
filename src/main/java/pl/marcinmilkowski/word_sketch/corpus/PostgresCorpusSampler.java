package pl.marcinmilkowski.word_sketch.corpus;

import pl.marcinmilkowski.word_sketch.tagging.PosTagger;
import pl.marcinmilkowski.word_sketch.tagging.SimpleTagger;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Sampler that fetches tokenized sentences from PostgreSQL database.
 * Used for building test corpora from production data.
 */
public class PostgresCorpusSampler {

    private static final String JDBC_URL = "jdbc:postgresql://%s:%d/%s";

    private final String host;
    private final int port;
    private final String database;
    private final String user;
    private final String password;

    public PostgresCorpusSampler(String host, int port, String database, String user, String password) {
        this.host = host;
        this.port = port;
        this.database = database;
        this.user = user;
        this.password = password;
    }

    /**
     * Sample tokens from the parallel corpus table.
     *
     * @param tableName The table containing parallel corpus data
     * @param limit Maximum number of tokens to sample
     * @param randomSeed Random seed for reproducibility
     * @return List of sampled tokens with word, lemma, and tag
     */
    public List<PosTagger.TaggedToken> sampleTokens(String tableName, int limit, long randomSeed) throws SQLException {
        String url = String.format(JDBC_URL, host, port, database);

        List<PosTagger.TaggedToken> tokens = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            // First, get total count for sampling
            String countSql = String.format("SELECT COUNT(*) FROM %s", tableName);
            long totalCount;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(countSql)) {
                rs.next();
                totalCount = rs.getLong(1);
            }

            System.out.println(String.format("Total rows in %s: %,d", tableName, totalCount));

            // Use ORDER BY RANDOM() LIMIT for sampling (compatible with all PostgreSQL versions)
            // For very large tables, TABLESAMPLE would be more efficient
            String sampleSql = String.format(
                "SELECT word, lemma, tag FROM %s ORDER BY RANDOM(42) LIMIT %d",
                tableName, limit * 2 // Get extra to account for filtering
            );

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sampleSql)) {
                int count = 0;
                int position = 0;
                while (rs.next() && count < limit) {
                    String word = rs.getString("word");
                    String lemma = rs.getString("lemma");
                    String tag = rs.getString("tag");

                    if (word != null && !word.isBlank()) {
                        tokens.add(new PosTagger.TaggedToken(
                            word, lemma != null ? lemma : word,
                            tag != null ? tag : "X",
                            position++
                        ));
                        count++;
                    }
                }
            }
        }

        System.out.println(String.format("Sampled %,d tokens", tokens.size()));
        return tokens;
    }

    /**
     * Get sentences from the corpus by joining tokens.
     */
    public List<List<PosTagger.TaggedToken>> sampleSentences(String tokenTable, String sentenceIdCol,
                                              int targetTokenCount, long randomSeed) throws SQLException {
        String url = String.format(JDBC_URL, host, port, database);

        List<List<PosTagger.TaggedToken>> sentences = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            // Get unique sentences with enough tokens using random sampling
            String sql = String.format(
                "SELECT DISTINCT %s FROM %s ORDER BY RANDOM(42) LIMIT %d",
                sentenceIdCol, tokenTable, targetTokenCount / 5
            );

            Set<String> sentenceIds = new HashSet<>();
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    sentenceIds.add(rs.getString(1));
                }
            }

            // Fetch tokens for each sentence
            String tokensSql = String.format(
                "SELECT word, lemma, tag FROM %s WHERE %s = ? ORDER BY position",
                tokenTable, sentenceIdCol
            );

            try (PreparedStatement pstmt = conn.prepareStatement(tokensSql)) {
                for (String sentenceId : sentenceIds) {
                    pstmt.setString(1, sentenceId);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        List<PosTagger.TaggedToken> sentence = new ArrayList<>();
                        int position = 0;
                        while (rs.next()) {
                            String word = rs.getString("word");
                            String lemma = rs.getString("lemma");
                            String tag = rs.getString("tag");
                            sentence.add(new PosTagger.TaggedToken(
                                word, lemma != null ? lemma : word,
                                tag != null ? tag : "X",
                                position++
                            ));
                        }
                        if (!sentence.isEmpty()) {
                            sentences.add(sentence);
                        }
                    }
                    if (sentences.size() * 10 >= targetTokenCount) break;
                }
            }
        }

        int totalTokens = sentences.stream().mapToInt(List::size).sum();
        System.out.println(String.format("Collected %,d sentences with %,d total tokens",
            sentences.size(), totalTokens));
        return sentences;
    }

    /**
     * Sample sentences from a sentence table that already has combined text.
     */
    public List<String> sampleSentenceTexts(String tableName, String textColumn,
                                             int targetSentences, long randomSeed) throws SQLException {
        String url = String.format(JDBC_URL, host, port, database);

        List<String> sentences = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            // Count total
            String countSql = String.format("SELECT COUNT(*) FROM %s", tableName);
            long totalCount;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(countSql)) {
                rs.next();
                totalCount = rs.getLong(1);
            }

            // Use random sampling for sentence selection
            String sql = String.format(
                "SELECT %s FROM %s ORDER BY RANDOM(42) LIMIT %d",
                textColumn, tableName, targetSentences * 2
            );

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next() && sentences.size() < targetSentences) {
                    String text = rs.getString(textColumn);
                    if (text != null && !text.isBlank() && text.split("\\s+").length >= 3) {
                        sentences.add(text.trim());
                    }
                }
            }
        }

        System.out.println(String.format("Sampled %,d sentences", sentences.size()));
        return sentences;
    }

    /**
     * Export sampled tokens to a simple text format for indexing.
     */
    public String exportToText(List<PosTagger.TaggedToken> tokens) {
        return tokens.stream()
            .map(t -> t.getWord())
            .collect(Collectors.joining(" "));
    }

    /**
     * Discover table structure - useful for exploring unknown tables.
     */
    public void discoverTable(String tableName) throws SQLException {
        String url = String.format(JDBC_URL, host, port, database);

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            // Get column info
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet columns = meta.getColumns(null, null, tableName, null)) {
                System.out.println(String.format("\nColumns in table '%s':", tableName));
                while (columns.next()) {
                    String name = columns.getString("COLUMN_NAME");
                    String type = columns.getString("TYPE_NAME");
                    System.out.println(String.format("  %s (%s)", name, type));
                }
            }

            // Sample a few rows
            String sql = String.format("SELECT * FROM %s LIMIT 5", tableName);
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                ResultSetMetaData metaData = rs.getMetaData();
                int colCount = metaData.getColumnCount();
                System.out.println(String.format("\nSample rows from '%s':", tableName));
                while (rs.next()) {
                    StringBuilder row = new StringBuilder("  ");
                    for (int i = 1; i <= colCount; i++) {
                        if (i > 1) row.append(" | ");
                        row.append(metaData.getColumnName(i)).append("=").append(rs.getString(i));
                    }
                    System.out.println(row);
                }
            }
        }
    }

    /**
     * Sample tokens with flexible column name detection.
     */
    public List<PosTagger.TaggedToken> sampleTokensAuto(String tableName, int limit,
                                                         String wordCol, String lemmaCol, String tagCol,
                                                         long randomSeed) throws SQLException {
        String url = String.format(JDBC_URL, host, port, database);

        List<PosTagger.TaggedToken> tokens = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            // First, get total count
            String countSql = String.format("SELECT COUNT(*) FROM %s", tableName);
            long totalCount;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(countSql)) {
                rs.next();
                totalCount = rs.getLong(1);
            }
            System.out.println(String.format("Total rows in %s: %,d", tableName, totalCount));

            // Sample using random order
            String sampleSql = String.format(
                "SELECT %s, %s, %s FROM %s ORDER BY RANDOM() LIMIT %d",
                wordCol, lemmaCol, tagCol, tableName, limit * 2
            );

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sampleSql)) {
                int count = 0;
                int position = 0;
                while (rs.next() && count < limit) {
                    String word = rs.getString(1);
                    String lemma = rs.getString(2);
                    String tag = rs.getString(3);

                    if (word != null && !word.isBlank()) {
                        tokens.add(new PosTagger.TaggedToken(
                            word, lemma != null ? lemma : word,
                            tag != null ? tag : "X",
                            position++
                        ));
                        count++;
                    }
                }
            }
        }

        System.out.println(String.format("Sampled %,d tokens", tokens.size()));
        return tokens;
    }

    /**
     * Sample sentences and tokenize them using the built-in tagger.
     * For sentence-level tables (like parallel_corpus with source_text/target_text).
     */
    public List<List<PosTagger.TaggedToken>> sampleAndTokenizeSentences(
            String tableName, String textColumn, int targetSentences, PosTagger tagger) throws SQLException {
        String url = String.format(JDBC_URL, host, port, database);

        List<List<PosTagger.TaggedToken>> sentences = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            // Get total count
            String countSql = String.format("SELECT COUNT(*) FROM %s", tableName);
            long totalCount;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(countSql)) {
                rs.next();
                totalCount = rs.getLong(1);
            }
            System.out.println(String.format("Total rows in %s: %,d", tableName, totalCount));

            // Sample sentences
            String sql = String.format(
                "SELECT %s FROM %s ORDER BY RANDOM() LIMIT %d",
                textColumn, tableName, targetSentences * 2
            );

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next() && sentences.size() < targetSentences) {
                    String text = rs.getString(1);
                    if (text != null && !text.isBlank() && text.split("\\s+").length >= 3) {
                        try {
                            List<PosTagger.TaggedToken> tagged = tagger.tagSentence(text.trim());
                            if (!tagged.isEmpty()) {
                                sentences.add(tagged);
                            }
                        } catch (Exception e) {
                            // Skip sentences that fail to tokenize
                        }
                    }
                }
            }
        }

        int totalTokens = sentences.stream().mapToInt(List::size).sum();
        System.out.println(String.format("Collected %,d sentences with %,d total tokens",
            sentences.size(), totalTokens));
        return sentences;
    }

    public static void main(String[] args) throws SQLException {
        // Example usage with environment variables
        String host = System.getenv().getOrDefault("POSTGRES_HOST", "localhost");
        int port = Integer.parseInt(System.getenv().getOrDefault("POSTGRES_PORT", "5432"));
        String database = System.getenv().getOrDefault("POSTGRES_DB", "dictionary_analytics");
        String user = System.getenv().getOrDefault("POSTGRES_USER", "dict_user");
        String password = System.getenv().getOrDefault("POSTGRES_PASSWORD", "dict_pass");

        PostgresCorpusSampler sampler = new PostgresCorpusSampler(host, port, database, user, password);

        System.out.println("PostgreSQL Corpus Sampler");
        System.out.println("=========================");
        System.out.println(String.format("Connected to %s:%d/%s", host, port, database));

        // Discover table structure
        String tableName = args.length > 0 ? args[0] : "parallel_corpus";
        System.out.println(String.format("\nDiscovering table '%s'...", tableName));
        sampler.discoverTable(tableName);

        // Sample sentences and tokenize them
        System.out.println("\nSampling 100,000 tokens from sentences...");
        PosTagger tagger = SimpleTagger.create();
        List<List<PosTagger.TaggedToken>> sentences = sampler.sampleAndTokenizeSentences(
            tableName, "source_text", 10000, tagger); // ~10k sentences for 100k tokens

        System.out.println("\nFirst 5 sentences:");
        int count = 0;
        for (List<PosTagger.TaggedToken> sentence : sentences) {
            if (count >= 5) break;
            System.out.println(String.format("  %d: %s", count + 1,
                sentence.stream().map(PosTagger.TaggedToken::getWord).collect(Collectors.joining(" "))));
            count++;
        }
    }
}
