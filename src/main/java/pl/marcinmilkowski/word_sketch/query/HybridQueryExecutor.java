package pl.marcinmilkowski.word_sketch.query;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queries.spans.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.MMapDirectory;
import org.apache.lucene.util.BytesRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.marcinmilkowski.word_sketch.grammar.CQLParser;
import pl.marcinmilkowski.word_sketch.grammar.CQLPattern;
import pl.marcinmilkowski.word_sketch.indexer.hybrid.SentenceDocument;
import pl.marcinmilkowski.word_sketch.indexer.hybrid.StatisticsReader;
import pl.marcinmilkowski.word_sketch.indexer.hybrid.TokenSequenceCodec;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Query executor for hybrid sentence-per-document index.
 * 
 * Key differences from legacy token-per-document approach:
 * - One Lucene document per sentence (not per token)
 * - Tokens stored in BinaryDocValues for O(1) position access
 * - Uses SpanQueries for positional matching within sentences
 * - Pre-computed term statistics for fast frequency lookups
 * 
 * Performance benefits:
 * - 10-100x fewer Lucene docs to search
 * - No need for batch sentence loading - tokens are in DocValues
 * - SpanNear/SpanOr for native positional matching
 */
public class HybridQueryExecutor implements QueryExecutor {

    private static final Logger logger = LoggerFactory.getLogger(HybridQueryExecutor.class);

    private final IndexReader reader;
    private final IndexSearcher searcher;
    private final CQLToLuceneCompiler compiler;
    private final StatisticsReader statsReader;
    
    private int maxSampleSize = 10_000;

    /**
     * Create a HybridQueryExecutor for a hybrid index.
     * 
     * @param indexPath Path to the hybrid index directory
     * @throws IOException if index cannot be opened
     */
    public HybridQueryExecutor(String indexPath) throws IOException {
        this(indexPath, indexPath + "/stats.bin");
    }

    /**
     * Create a HybridQueryExecutor with explicit statistics file path.
     * 
     * @param indexPath Path to the hybrid index directory
     * @param statsPath Path to the statistics file (.bin or .tsv)
     * @throws IOException if index or stats cannot be opened
     */
    public HybridQueryExecutor(String indexPath, String statsPath) throws IOException {
        Path path = Paths.get(indexPath);
        this.reader = DirectoryReader.open(MMapDirectory.open(path));
        this.searcher = new IndexSearcher(reader);
        this.compiler = new CQLToLuceneCompiler();
        
        // Try to load statistics
        StatisticsReader tempStats = null;
        try {
            tempStats = new StatisticsReader(statsPath);
            logger.info("Loaded term statistics from: {}", statsPath);
        } catch (IOException e) {
            logger.warn("No statistics file found at {}. Will use index for frequency lookups.", statsPath);
        }
        this.statsReader = tempStats;
        
        logger.info("HybridQueryExecutor initialized with {} sentences", reader.numDocs());
    }

    @Override
    public String getExecutorType() {
        return "hybrid";
    }

    public void setMaxSampleSize(int maxSampleSize) {
        this.maxSampleSize = maxSampleSize;
    }

    public int getMaxSampleSize() {
        return maxSampleSize;
    }

    @Override
    public List<WordSketchQueryExecutor.WordSketchResult> findCollocations(
            String headword, String cqlPattern, double minLogDice, int maxResults) throws IOException {

        if (headword == null || headword.isEmpty()) {
            return Collections.emptyList();
        }

        long startTime = System.currentTimeMillis();

        // Get headword frequency from statistics or index
        long headwordFreq = getTotalFrequency(headword);
        if (headwordFreq == 0) {
            logger.debug("Headword '{}' not found in corpus", headword);
            return Collections.emptyList();
        }

        // Parse CQL pattern
        CQLPattern collocatePattern = new CQLParser().parse(cqlPattern);
        List<CQLPattern.Constraint> collocateConstraints = extractConstraints(collocatePattern);
        int maxDist = extractMaxDistance(collocatePattern);
        if (maxDist <= 0) maxDist = 3;

        // Find sentences containing the headword
        Query headwordQuery = new TermQuery(new Term("lemma", headword.toLowerCase()));
        
        // Count total hits first
        TotalHitCountCollector countCollector = new TotalHitCountCollector();
        searcher.search(headwordQuery, countCollector);
        int totalSentences = countCollector.getTotalHits();

        if (totalSentences == 0) {
            logger.debug("No sentences found containing '{}'", headword);
            return Collections.emptyList();
        }

        // Determine sample size
        int sampleSize = (maxSampleSize == 0 || totalSentences <= maxSampleSize) 
            ? totalSentences : maxSampleSize;
        double scaleFactor = (double) totalSentences / sampleSize;

        logger.info("'{}': found {} sentences, processing {} (scale: {:.1f}x)",
            headword, totalSentences, sampleSize, scaleFactor);

        // Get sample of sentences
        TopDocs topDocs = searcher.search(headwordQuery, sampleSize);

        // Process sentences and collect collocations
        Map<String, Long> lemmaFreqs = new HashMap<>();
        Map<String, String> lemmaPos = new HashMap<>();
        Map<String, List<String>> examples = new HashMap<>();
        long collocateCount = 0;

        // Get DocValues reader for tokens
        var storedFields = searcher.storedFields();

        for (ScoreDoc hit : topDocs.scoreDocs) {
            int docId = hit.doc;
            
            // Load tokens from DocValues
            List<SentenceDocument.Token> tokens = loadTokensFromDoc(docId);
            if (tokens.isEmpty()) continue;

            // Find headword positions in this sentence
            List<Integer> headwordPositions = new ArrayList<>();
            for (SentenceDocument.Token token : tokens) {
                if (headword.equalsIgnoreCase(token.lemma())) {
                    headwordPositions.add(token.position());
                }
            }

            // Find collocates within distance of each headword position
            for (int hwPos : headwordPositions) {
                for (SentenceDocument.Token token : tokens) {
                    int dist = Math.abs(token.position() - hwPos);
                    if (dist == 0 || dist > maxDist) continue;

                    String lemma = token.lemma();
                    String tag = token.tag();

                    if (lemma == null || lemma.equalsIgnoreCase(headword)) continue;

                    // Check CQL constraints
                    if (!matchesConstraints(lemma, tag, collocateConstraints)) continue;

                    collocateCount++;
                    String key = lemma.toLowerCase();

                    lemmaFreqs.merge(key, 1L, Long::sum);
                    lemmaPos.putIfAbsent(key, tag != null ? tag.toUpperCase() : "");

                    // Collect examples
                    if (!examples.containsKey(key) || examples.get(key).size() < 3) {
                        try {
                            Document doc = storedFields.document(docId, Set.of("text"));
                            String text = doc.get("text");
                            if (text != null) {
                                examples.computeIfAbsent(key, k -> new ArrayList<>());
                                if (examples.get(key).size() < 3) {
                                    examples.get(key).add(text);
                                }
                            }
                        } catch (IOException e) {
                            // Ignore example loading errors
                        }
                    }
                }
            }
        }

        logger.debug("Processed {} collocations, found {} unique collocates",
            collocateCount, lemmaFreqs.size());

        // Calculate logDice scores
        List<WordSketchQueryExecutor.WordSketchResult> results = new ArrayList<>();
        long totalMatches = Math.max(1, collocateCount);

        for (Map.Entry<String, Long> entry : lemmaFreqs.entrySet()) {
            String lemma = entry.getKey();
            long sampleFreq = entry.getValue();
            long estimatedFreq = Math.round(sampleFreq * scaleFactor);

            long collocateTotalFreq = getTotalFrequency(lemma);
            if (collocateTotalFreq == 0) collocateTotalFreq = 1;

            double logDice = calculateLogDice(estimatedFreq, headwordFreq, collocateTotalFreq);

            if (logDice >= minLogDice || minLogDice == 0) {
                double relFreq = (double) sampleFreq / totalMatches;
                results.add(new WordSketchQueryExecutor.WordSketchResult(
                    lemma,
                    lemmaPos.getOrDefault(lemma, ""),
                    estimatedFreq,
                    logDice,
                    relFreq,
                    examples.getOrDefault(lemma, Collections.emptyList())
                ));
            }
        }

        results.sort((a, b) -> Double.compare(b.getLogDice(), a.getLogDice()));

        double elapsed = (System.currentTimeMillis() - startTime) / 1000.0;
        logger.info("Query completed in {:.2f}s, returned {} results", elapsed, results.size());

        return results.subList(0, Math.min(maxResults, results.size()));
    }

    /**
     * Load tokens from DocValues for a document.
     */
    private List<SentenceDocument.Token> loadTokensFromDoc(int docId) throws IOException {
        var leafReaders = reader.leaves();
        
        for (var leafContext : leafReaders) {
            int localDocId = docId - leafContext.docBase;
            if (localDocId >= 0 && localDocId < leafContext.reader().maxDoc()) {
                var binaryDocValues = leafContext.reader().getBinaryDocValues("tokens");
                if (binaryDocValues != null && binaryDocValues.advanceExact(localDocId)) {
                    BytesRef bytesRef = binaryDocValues.binaryValue();
                    return TokenSequenceCodec.decode(bytesRef);
                }
            }
        }
        
        return Collections.emptyList();
    }

    @Override
    public long getTotalFrequency(String lemma) throws IOException {
        if (lemma == null) return 0;
        
        String normalized = lemma.toLowerCase();
        
        // Use pre-computed statistics if available
        if (statsReader != null) {
            return statsReader.getFrequency(normalized);
        }

        // Fall back to index lookup
        Term term = new Term("lemma", normalized);
        return reader.totalTermFreq(term);
    }

    /**
     * Calculate logDice association score.
     */
    private double calculateLogDice(long cooccurrence, long freq1, long freq2) {
        if (cooccurrence <= 0 || freq1 <= 0 || freq2 <= 0) {
            return 0.0;
        }
        double dice = (2.0 * cooccurrence) / (freq1 + freq2);
        double logDice = Math.log(dice) / Math.log(2) + 14;
        return Math.max(0, Math.min(14, logDice));
    }

    /**
     * Extract max distance from CQL pattern.
     */
    private int extractMaxDistance(CQLPattern pattern) {
        for (CQLPattern.PatternElement elem : pattern.getElements()) {
            if (elem.getMaxDistance() > 0) {
                return elem.getMaxDistance();
            }
        }
        return 0;
    }

    /**
     * Extract constraints from CQL pattern elements.
     */
    private List<CQLPattern.Constraint> extractConstraints(CQLPattern pattern) {
        List<CQLPattern.Constraint> constraints = new ArrayList<>();
        for (CQLPattern.PatternElement elem : pattern.getElements()) {
            if (elem.getConstraint() != null) {
                constraints.add(elem.getConstraint());
            }
        }
        return constraints;
    }

    /**
     * Check if a token matches all constraints.
     */
    private boolean matchesConstraints(String lemma, String tag, List<CQLPattern.Constraint> constraints) {
        for (CQLPattern.Constraint constraint : constraints) {
            if (!matchesConstraint(lemma, tag, constraint)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if a token matches a single constraint.
     */
    private boolean matchesConstraint(String lemma, String tag, CQLPattern.Constraint constraint) {
        String field = constraint.getField();
        String pattern = constraint.getPattern();

        // Handle OR constraints
        if (constraint.isOr()) {
            if (constraint.getOrConstraints().isEmpty()) {
                String[] parts = pattern.split("\\|");
                for (String part : parts) {
                    if (matchesField(tag != null ? tag.toLowerCase() : "", field, part.trim())) {
                        return !constraint.isNegated();
                    }
                }
                return constraint.isNegated();
            } else {
                for (CQLPattern.Constraint orConstraint : constraint.getOrConstraints()) {
                    if (matchesConstraint(lemma, tag, orConstraint)) {
                        return !constraint.isNegated();
                    }
                }
                return constraint.isNegated();
            }
        }

        // Handle AND constraints
        if (constraint.isAnd() && !constraint.getAndConstraints().isEmpty()) {
            for (CQLPattern.Constraint andConstraint : constraint.getAndConstraints()) {
                if (!matchesConstraint(lemma, tag, andConstraint)) {
                    return constraint.isNegated();
                }
            }
            return !constraint.isNegated();
        }

        // Simple constraint
        String value = getFieldValue(lemma, tag, field);
        boolean matches = matchesField(value, field, pattern);
        return constraint.isNegated() ? !matches : matches;
    }

    private String getFieldValue(String lemma, String tag, String field) {
        return switch (field.toLowerCase()) {
            case "lemma" -> lemma != null ? lemma.toLowerCase() : "";
            case "tag", "pos" -> tag != null ? tag.toLowerCase() : "";
            case "pos_group" -> tag != null ? getPosGroup(tag) : "";
            default -> "";
        };
    }

    private String getPosGroup(String tag) {
        if (tag == null || tag.isEmpty()) return "";
        String upper = tag.toUpperCase();
        if (upper.startsWith("NN") || upper.equals("NOUN")) return "noun";
        if (upper.startsWith("VB") || upper.equals("VERB")) return "verb";
        if (upper.startsWith("JJ") || upper.equals("ADJ")) return "adj";
        if (upper.startsWith("RB") || upper.equals("ADV")) return "adv";
        if (upper.startsWith("IN") || upper.equals("ADP")) return "prep";
        if (upper.startsWith("DT") || upper.equals("DET")) return "det";
        if (upper.startsWith("PR") || upper.equals("PRON")) return "pron";
        if (upper.startsWith("CC") || upper.equals("CCONJ") || upper.equals("SCONJ")) return "conj";
        return "";
    }

    private boolean matchesField(String value, String field, String pattern) {
        if (value == null) value = "";
        pattern = pattern.replace("\"", "").toLowerCase().trim();
        value = value.toLowerCase();

        // Regex/wildcard patterns
        if (pattern.contains(".*") || pattern.contains("*") || pattern.contains("?")) {
            String regex = pattern.replace(".*", ".*").replace("*", ".*").replace("?", ".");
            return value.matches(regex);
        }

        // Exact match
        return value.equals(pattern);
    }

    @Override
    public void close() throws IOException {
        reader.close();
        logger.info("HybridQueryExecutor closed");
    }
}
