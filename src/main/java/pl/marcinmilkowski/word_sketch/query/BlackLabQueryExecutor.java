package pl.marcinmilkowski.word_sketch.query;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.Concordance;
import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.results.*;
import nl.inl.blacklab.searches.SearchHits;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.search.textpattern.CompleteQuery;
import nl.inl.blacklab.queryParser.contextql.ContextualQueryLanguageParser;
import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.indexmetadata.*;
import nl.inl.blacklab.resultproperty.*;
import nl.inl.blacklab.search.TermFrequencyList;

import pl.marcinmilkowski.word_sketch.utils.LogDiceCalculator;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Query executor using BlackLab for CoNLL-U dependency tree indexing and querying.
 */
public class BlackLabQueryExecutor implements QueryExecutor {
    private static final Logger logger = LoggerFactory.getLogger(BlackLabQueryExecutor.class);

    private final BlackLabIndex blackLabIndex;
    private final String indexPath;

    public BlackLabQueryExecutor(String indexPath) throws IOException {
        this.indexPath = indexPath;
        try {
            this.blackLabIndex = BlackLab.open(new File(indexPath));
        } catch (Exception e) {
            throw new IOException("Failed to open index: " + e.getMessage(), e);
        }
    }

    @Override
    public List<QueryResults.WordSketchResult> findCollocations(
            String headword,
            String cqlPattern,
            double minLogDice,
            int maxResults) throws IOException {

        if (headword == null || headword.isEmpty()) {
            return Collections.emptyList();
        }

        // BCQL syntax: "word" [] for word followed by any token
        String bcql = String.format("\"%s\" []", headword.toLowerCase());

        try {
            TextPattern pattern = nl.inl.blacklab.queryParser.corpusql.CorpusQueryLanguageParser.parse(bcql, "lemma");
            BLSpanQuery query = pattern.toQuery(QueryInfo.create(blackLabIndex));
            Hits hits = blackLabIndex.find(query);

            long headwordFreq = getTotalFrequency(headword);

            // Group by word form - need to use Search API for grouping
            SearchHits searchHits = blackLabIndex.search().find(query);
            HitProperty groupBy = new HitPropertyHitText(blackLabIndex, MatchSensitivity.SENSITIVE);
            HitGroups groups = searchHits.groupWithStoredHits(groupBy, Results.NO_LIMIT).execute();

            List<QueryResults.WordSketchResult> results = new ArrayList<>();

            for (HitGroup group : groups) {
                PropertyValue identity = group.identity();
                String collocateLemma = identity.toString();
                long f_xy = group.size();
                long f_y = getTotalFrequency(collocateLemma);

                double logDice = LogDiceCalculator.compute(f_xy, headwordFreq, f_y);

                if (logDice >= minLogDice) {
                    double relFreq = LogDiceCalculator.relativeFrequency(f_xy, headwordFreq);
                    results.add(new QueryResults.WordSketchResult(
                        collocateLemma, "unknown", f_xy, logDice, relFreq, Collections.emptyList()));
                }
            }

            return results.stream()
                .sorted(Comparator.comparingDouble(QueryResults.WordSketchResult::getLogDice).reversed())
                .limit(maxResults)
                .toList();

        } catch (InvalidQuery e) {
            throw new IOException("CQL parse error: " + e.getMessage(), e);
        }
    }

    public List<QueryResults.WordSketchResult> findDependencyCollocations(
            String headword,
            String deprel,
            double minLogDice,
            int maxResults) throws IOException {

        if (headword == null || headword.isEmpty()) {
            return Collections.emptyList();
        }

        // BCQL syntax for dependency: "headword" -deprel-> _
        // This finds all words that have the specified dependency relation to headword
        String bcql = String.format("\"%s\" -%s-> _", headword.toLowerCase(), deprel);

        try {
            TextPattern pattern = nl.inl.blacklab.queryParser.corpusql.CorpusQueryLanguageParser.parse(bcql, "lemma");
            BLSpanQuery query = pattern.toQuery(QueryInfo.create(blackLabIndex));
            
            SearchHits searchHits = blackLabIndex.search().find(query);
            HitProperty groupBy = new HitPropertyHitText(blackLabIndex, MatchSensitivity.SENSITIVE);
            HitGroups groups = searchHits.groupWithStoredHits(groupBy, Results.NO_LIMIT).execute();

            long headwordFreq = getTotalFrequency(headword);
            List<QueryResults.WordSketchResult> results = new ArrayList<>();

            for (HitGroup group : groups) {
                String collocateLemma = group.identity().toString();
                long f_xy = group.size();
                long f_y = getTotalFrequency(collocateLemma);

                double logDice = LogDiceCalculator.compute(f_xy, headwordFreq, f_y);

                if (logDice >= minLogDice) {
                    double relFreq = LogDiceCalculator.relativeFrequency(f_xy, headwordFreq);
                    results.add(new QueryResults.WordSketchResult(
                        collocateLemma, "unknown", f_xy, logDice, relFreq, Collections.emptyList()));
                }
            }

            return results.stream()
                .sorted(Comparator.comparingDouble(QueryResults.WordSketchResult::getLogDice).reversed())
                .limit(maxResults)
                .toList();

        } catch (InvalidQuery e) {
            throw new IOException("CQL parse error: " + e.getMessage(), e);
        }
    }

    @Override
    public List<QueryResults.ConcordanceResult> executeQuery(String cqlPattern, int maxResults) throws IOException {
        try {
            CompleteQuery cq = ContextualQueryLanguageParser.parse(blackLabIndex, cqlPattern);
            TextPattern tp = cq.pattern();
            if (tp == null) {
                return Collections.emptyList();
            }
            BLSpanQuery query = tp.toQuery(QueryInfo.create(blackLabIndex));
            Hits hits = blackLabIndex.find(query);

            List<QueryResults.ConcordanceResult> results = new ArrayList<>();
            Concordances concordances = hits.concordances(ContextSize.get(5, 5, Integer.MAX_VALUE), ConcordanceType.FORWARD_INDEX);

            for (int i = 0; i < Math.min(hits.size(), maxResults); i++) {
                Hit hit = hits.get(i);
                Concordance conc = concordances.get(hit);
                String[] parts = conc.parts();
                String snippet = parts[0] + parts[1] + parts[2];

                results.add(new QueryResults.ConcordanceResult(
                    snippet, hit.start(), hit.end(), String.valueOf(hit.doc())));
            }

            return results;

        } catch (InvalidQuery e) {
            throw new IOException("CQL parse error: " + e.getMessage(), e);
        }
    }

    @Override
    public List<QueryResults.ConcordanceResult> executeBcqlQuery(String bcqlPattern, int maxResults) throws IOException {
        try {
            // Use CorpusQueryLanguageParser for BCQL (not ContextualQueryLanguageParser)
            TextPattern pattern = nl.inl.blacklab.queryParser.corpusql.CorpusQueryLanguageParser.parse(bcqlPattern, "lemma");
            BLSpanQuery query = pattern.toQuery(QueryInfo.create(blackLabIndex));

            // Use Hits API for concordances
            Hits hits = blackLabIndex.find(query);
            long totalHits = hits.size();

            // Extract headword for logDice calculation
            String headword = extractHeadword(bcqlPattern);
            long headwordFreq = headword != null ? getTotalFrequency(headword) : 0L;

            // Get concordances: 60 tokens each side captures most sentences; post-process to trim at boundaries.
            int collocatePos = findLabelPosition(bcqlPattern, 2);
            int sampleSize = (int) Math.min(totalHits, maxResults * 10L);

            // Single pass: collect per-hit data and build frequency map.
            record HitRecord(String xmlSnippet, String leftText, String matchText, String rightText, String collocateLemma, int docId, int start, int end) {}
            List<HitRecord> hitRecords = new ArrayList<>(sampleSize);
            Map<String, Long> collocateFreqMap = new HashMap<>();

            if (totalHits > 0) {
                Hits sample = hits.window(0L, (long) sampleSize);
                Concordances concordances = sample.concordances(ContextSize.get(60, 60, Integer.MAX_VALUE), ConcordanceType.FORWARD_INDEX);

                for (int idx = 0; idx < sampleSize; idx++) {
                    Hit hit = sample.get(idx);
                    Concordance conc = concordances.get(hit);
                    String leftText = "", matchText = "", rightText = "", xmlSnippet = "";
                    String collocateLemma = "unknown";

                    if (conc != null) {
                        String[] parts = conc.parts();
                        if (parts != null && parts.length >= 3) {
                            String leftXml  = parts[0] != null ? parts[0] : "";
                            String matchXml = parts[1] != null ? parts[1] : "";
                            String rightXml = parts[2] != null ? parts[2] : "";
                            xmlSnippet = leftXml + matchXml + rightXml;
                            leftText   = extractPlainTextFromXml(leftXml);
                            matchText  = extractPlainTextFromXml(matchXml);
                            rightText  = extractPlainTextFromXml(rightXml);

                            // Extract collocate lemma from match XML at the labeled position
                            if (collocatePos > 0) {
                                String extracted = extractCollocateFromXmlByPosition(matchXml, collocatePos);
                                if (extracted != null && !extracted.isEmpty()) {
                                    collocateLemma = extracted;
                                } else {
                                    // Fallback: last lemma in match XML
                                    extracted = extractCollocateFromSnippet(matchXml);
                                    if (extracted != null && !extracted.isEmpty()) {
                                        collocateLemma = extracted;
                                    }
                                }
                            }
                        }
                    }

                    if (!collocateLemma.equals("unknown") && !collocateLemma.isEmpty()) {
                        collocateFreqMap.merge(collocateLemma.toLowerCase(), 1L, Long::sum);
                    }
                    hitRecords.add(new HitRecord(xmlSnippet, leftText, matchText, rightText, collocateLemma, hit.doc(), hit.start(), hit.end()));
                }
            }

            // Second pass: compute logDice and build results using stored plain-text parts.
            List<QueryResults.ConcordanceResult> results = new ArrayList<>();
            int resultLimit = Math.min(hitRecords.size(), maxResults * 3);

            for (int i = 0; i < resultLimit; i++) {
                HitRecord rec = hitRecords.get(i);
                String collocateLemma = rec.collocateLemma();
                if (collocateLemma == null || collocateLemma.isEmpty()) collocateLemma = "unknown";

                long f_xy = collocateFreqMap.getOrDefault(collocateLemma.toLowerCase(), 1L);
                long f_y = getTotalFrequency(collocateLemma);

                double logDice = (headwordFreq > 0 && f_y > 0)
                    ? LogDiceCalculator.compute(f_xy, headwordFreq, f_y) : 0.0;

                String plainText = trimToSentence(rec.leftText(), rec.matchText(), rec.rightText());

                results.add(new QueryResults.ConcordanceResult(
                    plainText, rec.xmlSnippet(), rec.start(), rec.end(), String.valueOf(rec.docId()),
                    collocateLemma, f_xy, logDice));
            }

            // Sort by logDice and limit
            return results.stream()
                .sorted(Comparator.comparingDouble(QueryResults.ConcordanceResult::getLogDice).reversed())
                .limit(maxResults)
                .toList();

        } catch (InvalidQuery e) {
            throw new IOException("BCQL parse error: " + e.getMessage(), e);
        }
    }

    /**
     * Trim left/match/right plain-text parts to a single sentence.
     * Scans the left context backward for the last sentence boundary and
     * the right context forward for the first sentence boundary.
     */
    private String trimToSentence(String left, String match, String right) {
        // Trim left: keep only text after the last sentence-final punctuation
        String trimmedLeft = trimLeftAtSentenceBoundary(left);
        // Trim right: keep only text up to and including the first sentence-final punctuation
        String trimmedRight = trimRightAtSentenceBoundary(right);
        String assembled = (trimmedLeft.isEmpty() ? "" : trimmedLeft + " ") + match
                         + (trimmedRight.isEmpty() ? "" : " " + trimmedRight);
        return detokenize(assembled);
    }

    /** Keep only the portion of left-context text AFTER the last sentence boundary. */
    private String trimLeftAtSentenceBoundary(String text) {
        if (text == null || text.isEmpty()) return "";
        // Sentence boundaries: ". ", "! ", "? " (period/bang/question followed by whitespace)
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("[.!?]\\s+");
        java.util.regex.Matcher m = p.matcher(text);
        int lastEnd = 0;
        while (m.find()) lastEnd = m.end();
        return lastEnd > 0 ? text.substring(lastEnd).trim() : text.trim();
    }

    /** Keep only the portion of right-context text UP TO AND INCLUDING the first sentence boundary. */
    private String trimRightAtSentenceBoundary(String text) {
        if (text == null || text.isEmpty()) return "";
        // Look for a period/bang/question that ends a sentence:
        // must be followed by whitespace+uppercase or end-of-string.
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("[.!?](?=\\s+[A-Z]|\\s*$)");
        java.util.regex.Matcher m = p.matcher(text);
        if (m.find()) return text.substring(0, m.end()).trim();
        return text.trim();
    }

    /**
     * Extract plain text from XML snippet by stripping tags.
     */
    private String extractPlainTextFromXml(String xmlSnippet) {
        if (xmlSnippet == null || xmlSnippet.isEmpty()) {
            return "";
        }
        // Strip tags without injecting extra spaces — preserves whitespace already between elements.
        String text = xmlSnippet.replaceAll("<[^>]+>", "").replaceAll("\\s+", " ").trim();
        return detokenize(text);
    }

    /** Remove spurious spaces introduced by whitespace-separated tokenization (e.g. "word ," → "word,"). */
    private String detokenize(String text) {
        if (text == null || text.isEmpty()) return text;
        return text
            .replaceAll(" +([.,!?:;)\\]])", "$1")   // no space before closing punctuation
            .replaceAll("([({\\[]) +", "$1");         // no space after opening brackets
    }

    /**
     * Extract collocate lemma from XML by labeled position.
     * @param xmlSnippet The XML snippet containing the full sentence
     * @param position The 1-based position of the token to extract (from findLabelPosition)
     */
    private String extractCollocateFromXmlByPosition(String xmlSnippet, int position) {
        if (xmlSnippet == null || xmlSnippet.isEmpty() || position < 1) {
            return null;
        }
        // Find all lemma="xxx" patterns in order
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("lemma=\"([^\"]+)\"");
        java.util.regex.Matcher m = p.matcher(xmlSnippet);

        int count = 0;
        while (m.find()) {
            count++;
            if (count == position) {
                return m.group(1);
            }
        }
        return null;
    }

    /**
     * Extract lemma from match text (space-separated tokens from Kwic.match()).
     * @param matchText Space-separated tokens from the match
     * @param position 1-based position within the match tokens
     */
    private String extractCollocateFromMatchText(String matchText, int position) {
        if (matchText == null || matchText.isEmpty() || position < 1) {
            return null;
        }
        // matchText is plain text like "theory be relevant"
        // Tokenize by whitespace
        String[] tokens = matchText.trim().split("\\s+");
        if (position > tokens.length) {
            return null;
        }
        // Return the token at the specified position (it's already plain text)
        return tokens[position - 1].toLowerCase();
    }

    /**
     * Extract collocate lemma from the BCQL pattern for a given hit index.
     * Uses the position labeled "2:" in the pattern.
     */
    private String extractCollocateFromPattern(String bcqlPattern, int hitIndex) {
        // Find position 2: in the pattern
        int pos2Index = findLabelPosition(bcqlPattern, 2);
        if (pos2Index <= 0) {
            return "unknown";
        }
        // For now, return a placeholder - actual extraction would need hit-specific data
        return "unknown";
    }

    /**
     * Extract the headword lemma from a BCQL pattern.
     * Looks for patterns like lemma="word" or lemma='word'
     */
    private String extractHeadword(String bcqlPattern) {
        // Simple regex to find lemma="xxx" or lemma='xxx'
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("lemma=[\"']([^\"']+)[\"']", java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher m = p.matcher(bcqlPattern);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    /**
     * Extract the collocate lemma from a concordance snippet (XML format).
     * Finds the last lemma attribute in the snippet.
     */
    private String extractCollocateFromSnippet(String snippet) {
        // Look for lemma="xxx" patterns and get the last one (the collocate)
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("lemma=\"([^\"]+)\"");
        java.util.regex.Matcher m = p.matcher(snippet);
        String lastLemma = null;
        while (m.find()) {
            lastLemma = m.group(1);
        }
        return lastLemma != null ? lastLemma : "unknown";
    }

    @Override
    public long getTotalFrequency(String lemma) throws IOException {
        try {
            AnnotatedField field = blackLabIndex.mainAnnotatedField();
            Annotation annotation = field.annotation("lemma");
            if (annotation == null) {
                return 0L;
            }
            AnnotationSensitivity sensitivity = annotation.sensitivity(MatchSensitivity.INSENSITIVE);
            TermFrequencyList tfl = blackLabIndex.termFrequencies(sensitivity, null, Set.of(lemma.toLowerCase()));
            return tfl.frequency(lemma.toLowerCase());
        } catch (Exception e) {
            return 0L;
        }
    }

    /**
     * Execute a surface pattern query for word sketches.
     * Uses BCQL to find collocates matching the pattern.
     * Properly handles labeled capture groups (1: for head, 2: for collocate).
     */
    public List<QueryResults.WordSketchResult> executeSurfacePattern(
            String lemma, String bcqlPattern,
            int headPosition, int collocatePosition,
            double minLogDice, int maxResults) throws IOException {

        if (lemma == null || lemma.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            TextPattern pattern = nl.inl.blacklab.queryParser.corpusql.CorpusQueryLanguageParser.parse(bcqlPattern, "lemma");

            // Find the position of label "2:" in the pattern
            int collocateLabelPos = findLabelPosition(bcqlPattern, 2);
            BLSpanQuery query = pattern.toQuery(QueryInfo.create(blackLabIndex));

            // Get hits
            Hits hits = blackLabIndex.find(query);

            long headwordFreq = getTotalFrequency(lemma);

            // Build a map of collocate -> frequency by grouping hits
            Map<String, Long> freqMap = new HashMap<>();

            // Use hit groups - group by the matched text to get unique collocates
            SearchHits searchHits = blackLabIndex.search().find(query);
            HitProperty groupBy = new HitPropertyHitText(blackLabIndex, MatchSensitivity.INSENSITIVE);
            HitGroups groups = searchHits.group(groupBy, Results.NO_LIMIT).execute();

            for (HitGroup group : groups) {
                String identity = group.identity().toString();
                if (identity != null && !identity.isEmpty()) {
                    // The group identity is the matched text - extract the collocate from it
                    // Try XML format first, then plain text
                    String collocate = extractCollocateFromMatch(identity, collocateLabelPos);
                    if (collocate == null || collocate.isEmpty()) {
                        // Try plain text extraction - split by whitespace
                        collocate = extractCollocateFromPlainText(identity, collocateLabelPos);
                    }
                    if (collocate != null && !collocate.isEmpty()) {
                        freqMap.merge(collocate.toLowerCase(), group.size(), Long::sum);
                    }
                }
            }

            // Build results
            List<QueryResults.WordSketchResult> results = new ArrayList<>();
            for (Map.Entry<String, Long> entry : freqMap.entrySet()) {
                String collocateLemma = entry.getKey();
                long f_xy = entry.getValue();
                long f_y = getTotalFrequency(collocateLemma);

                double logDice = LogDiceCalculator.compute(f_xy, headwordFreq, f_y);

                if (logDice >= minLogDice) {
                    double relFreq = LogDiceCalculator.relativeFrequency(f_xy, headwordFreq);
                    results.add(new QueryResults.WordSketchResult(
                        collocateLemma, "unknown", f_xy, logDice, relFreq, Collections.emptyList()));
                }
            }

            return results.stream()
                .sorted(Comparator.comparingDouble(QueryResults.WordSketchResult::getLogDice).reversed())
                .limit(maxResults)
                .toList();

        } catch (InvalidQuery e) {
            throw new IOException("BCQL parse error: " + e.getMessage(), e);
        }
    }

    /**
     * Extract the collocate lemma from matched text using the labeled position.
     * @param matchOnly The matched text (parts[1] from concordance)
     * @param labelPos The 1-based position of the label (e.g., 3 for "2:" in pattern with 3 tokens)
     */
    private String extractCollocateFromMatch(String matchOnly, int labelPos) {
        if (matchOnly == null || matchOnly.isEmpty() || labelPos < 1) {
            return null;
        }

        // Find all lemma="xxx" patterns in order
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("lemma=\"([^\"]+)\"");
        java.util.regex.Matcher m = p.matcher(matchOnly);

        int count = 0;
        while (m.find()) {
            count++;
            if (count == labelPos) {
                return m.group(1);
            }
        }

        // If position not found, return null
        return null;
    }

    /**
     * Extract the collocate from plain text (whitespace-separated words).
     * @param text The matched text (e.g., "theory is irrelevant")
     * @param position The 1-based position of the word to extract
     */
    private String extractCollocateFromPlainText(String text, int position) {
        if (text == null || text.isEmpty() || position < 1) {
            return null;
        }

        // Split by whitespace
        String[] words = text.trim().split("\\s+");
        if (position > words.length) {
            return null;
        }

        return words[position - 1];
    }

    /**
     * Find the position of a labeled capture group (e.g., "2:") in a BCQL pattern.
     * Returns the 1-based position of that token in the pattern.
     *
     * Example: "1:[xpos="NN.*"] [lemma="be|..."] 2:[xpos="JJ.*"]"
     * - "1:" is at position 1
     * - "[lemma=...]" (unlabeled) is at position 2
     * - "2:" is at position 3
     */
    private int findLabelPosition(String pattern, int label) {
        if (pattern == null) {
            return -1;
        }

        // The pattern has labeled positions like "1:" and "2:" BEFORE brackets.
        // Example: "1:[xpos="NN.*"] [lemma="be|..."] 2:[xpos="JJ.*"]"
        // Token positions: 1 (labeled 1), 2 (unlabeled), 3 (labeled 2)

        // Strategy: Find "label:" and check if it's immediately followed by "["
        String labelStr = label + ":";
        int labelIndex = pattern.indexOf(labelStr);
        if (labelIndex < 0) {
            return -1;
        }

        // Check if label is followed by "["
        if (labelIndex + labelStr.length() < pattern.length() &&
            pattern.charAt(labelIndex + labelStr.length()) == '[') {
            // Count brackets up to and including this one
            int tokenPos = 0;
            for (int i = 0; i < pattern.length(); i++) {
                if (pattern.charAt(i) == '[') {
                    tokenPos++;
                    if (i == labelIndex + labelStr.length()) {
                        return tokenPos;
                    }
                }
            }
        }

        return -1;
    }

    /**
     * Extract a specific token from a concordance snippet.
     * Snippet format is like: "...[word1]...[word2]..." where words have lemma attributes.
     */
    private String extractTokenFromSnippet(String snippet, int position) {
        if (snippet == null || snippet.isEmpty()) {
            return null;
        }

        // Find all lemma="xxx" patterns and get the one at the specified position
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("lemma=\"([^\"]+)\"");
        java.util.regex.Matcher m = p.matcher(snippet);
        int count = 0;
        while (m.find()) {
            count++;
            if (count == position) {
                return m.group(1);
            }
        }
        // If position is beyond the count, return the last one
        if (count > 0 && position > count) {
            m.reset();
            String last = null;
            while (m.find()) {
                last = m.group(1);
            }
            return last;
        }
        return null;
    }

    public long getCorpusSize() throws IOException {
        return 0L;
    }

    @Override
    public String getExecutorType() {
        return "blacklab";
    }

    @Override
    public void close() throws IOException {
        blackLabIndex.close();
    }
}
