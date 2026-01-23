package pl.marcinmilkowski.word_sketch.corpus;

import pl.marcinmilkowski.word_sketch.indexer.LuceneIndexer;
import pl.marcinmilkowski.word_sketch.tagging.PosTagger;
import pl.marcinmilkowski.word_sketch.tagging.SimpleTagger;

import java.sql.*;
import java.util.*;

/**
 * Utility to build a Lucene index from a PostgreSQL parallel corpus.
 * Samples sentences from the database, tokenizes them, and indexes for word sketch analysis.
 */
public class CorpusBuilder {

    private final String host;
    private final int port;
    private final String database;
    private final String user;
    private final String password;

    public CorpusBuilder(String host, int port, String database, String user, String password) {
        this.host = host;
        this.port = port;
        this.database = database;
        this.user = user;
        this.password = password;
    }

    /**
     * Build a word sketch index from the parallel corpus.
     *
     * @param tableName Source table (must have text column)
     * @param textColumn Column containing sentence text
     * @param targetTokens Target number of tokens to index
     * @param indexPath Path for Lucene index
     * @param tagger POS tagger to use
     * @return Statistics about the build
     */
    public BuildStats buildIndex(String tableName, String textColumn, int targetTokens,
                                  String indexPath, PosTagger tagger) throws Exception {
        System.out.println("Corpus Builder - Building Word Sketch Index");
        System.out.println("==========================================");
        System.out.println(String.format("Target: %,d tokens from %s.%s", targetTokens, tableName, textColumn));

        long startTime = System.currentTimeMillis();

        String url = String.format("jdbc:postgresql://%s:%d/%s", host, port, database);

        // Get total count
        long totalRows;
        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(String.format("SELECT COUNT(*) FROM %s", tableName))) {
                rs.next();
                totalRows = rs.getLong(1);
            }
        }
        System.out.println(String.format("Total rows in table: %,d", totalRows));

        // Create indexer
        LuceneIndexer indexer = new LuceneIndexer(indexPath);

        // Sample sentences and index them
        int docId = 0;
        int totalTokens = 0;
        int sentencesProcessed = 0;

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            // Calculate sample size - get enough sentences for target tokens
            int estimatedSentences = (int) Math.ceil(targetTokens / 10.0); // ~10 words per sentence
            int sampleSize = Math.min((int) totalRows, estimatedSentences * 3);

            String sql = String.format(
                "SELECT %s FROM %s ORDER BY RANDOM() LIMIT %d",
                textColumn, tableName, sampleSize
            );

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {

                while (rs.next() && totalTokens < targetTokens) {
                    String text = rs.getString(1);
                    if (text == null || text.isBlank() || text.split("\\s+").length < 3) {
                        continue;
                    }

                    try {
                        List<PosTagger.TaggedToken> tokens = tagger.tagSentence(text.trim());
                        if (tokens.isEmpty()) continue;

                        // Index this sentence
                        String sentence = text.trim();
                        for (int pos = 0; pos < tokens.size(); pos++) {
                            PosTagger.TaggedToken token = tokens.get(pos);
                            indexer.addWord(
                                docId,
                                pos,
                                token.getWord(),
                                token.getLemma(),
                                token.getTag(),
                                token.getPosGroup(),
                                sentence,
                                0, 0
                            );
                        }

                        docId++;
                        totalTokens += tokens.size();
                        sentencesProcessed++;

                        if (sentencesProcessed % 1000 == 0) {
                            System.out.println(String.format("  Progress: %,d sentences, %,d tokens",
                                sentencesProcessed, totalTokens));
                        }

                    } catch (Exception e) {
                        // Skip problematic sentences
                    }
                }
            }
        }

        indexer.commit();
        long buildTime = System.currentTimeMillis() - startTime;

        BuildStats stats = new BuildStats(
            sentencesProcessed, totalTokens, docId,
            indexer.getDocumentCount(), buildTime
        );
        indexer.close();

        System.out.println("\nBuild Complete:");
        System.out.println(String.format("  Sentences processed: %,d", stats.sentences));
        System.out.println(String.format("  Total tokens: %,d", stats.tokens));
        System.out.println(String.format("  Build time: %.1f seconds", stats.buildTimeMs / 1000.0));
        System.out.println(String.format("  Throughput: %.1f tokens/second",
            stats.tokens / (stats.buildTimeMs / 1000.0)));

        return stats;
    }

    public static class BuildStats {
        public final int sentences;
        public final int tokens;
        public final int documents;
        public final long indexedDocuments;
        public final long buildTimeMs;

        public BuildStats(int sentences, int tokens, int documents,
                         long indexedDocuments, long buildTimeMs) {
            this.sentences = sentences;
            this.tokens = tokens;
            this.documents = documents;
            this.indexedDocuments = indexedDocuments;
            this.buildTimeMs = buildTimeMs;
        }
    }

    public static void main(String[] args) throws Exception {
        // Configuration from environment
        String host = System.getenv().getOrDefault("POSTGRES_HOST", "localhost");
        int port = Integer.parseInt(System.getenv().getOrDefault("POSTGRES_PORT", "5432"));
        String database = System.getenv().getOrDefault("POSTGRES_DB", "dictionary_analytics");
        String user = System.getenv().getOrDefault("POSTGRES_USER", "dict_user");
        String password = System.getenv().getOrDefault("POSTGRES_PASSWORD", "dict_pass");
        String indexPath = args.length > 0 ? args[0] : "target/corpus-index";
        int targetTokens = args.length > 1 ? Integer.parseInt(args[1]) : 1_000_000;

        CorpusBuilder builder = new CorpusBuilder(host, port, database, user, password);
        PosTagger tagger = SimpleTagger.create();

        BuildStats stats = builder.buildIndex(
            "parallel_corpus", "source_text", targetTokens, indexPath, tagger
        );
    }
}
