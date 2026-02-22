package pl.marcinmilkowski.word_sketch.query;

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
            Concordances concordances = hits.concordances(ContextSize.get(5, 5), ConcordanceType.FORWARD_INDEX);

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

            // Get hits for concordances
            Hits hits = blackLabIndex.find(query);
            long totalHits = hits.size();

            // Get concordances
            Concordances concordances = hits.concordances(ContextSize.get(5, 5), ConcordanceType.FORWARD_INDEX);

            // Extract headword for logDice calculation
            String headword = extractHeadword(bcqlPattern);
            long headwordFreq = headword != null ? getTotalFrequency(headword) : 0L;

            // Get grouped results for collocation stats - iterate groups not hits
            SearchHits searchHits = blackLabIndex.search().find(query);
            HitProperty groupBy = new HitPropertyHitText(blackLabIndex, MatchSensitivity.SENSITIVE);
            HitGroups groups = searchHits.group(groupBy, Results.NO_LIMIT).execute();

            List<QueryResults.ConcordanceResult> results = new ArrayList<>();

            for (HitGroup group : groups) {
                String collocateLemma = group.identity().toString();
                long f_xy = group.size();
                long f_y = getTotalFrequency(collocateLemma);

                double logDice = 0.0;
                if (headwordFreq > 0 && f_y > 0) {
                    logDice = LogDiceCalculator.compute(f_xy, headwordFreq, f_y);
                }

                // Try to find a matching hit for this collocate
                String snippet = "";
                for (int i = 0; i < Math.min(totalHits, 100); i++) {
                    Hit hit = hits.get(i);
                    String hitStr = hit.toString();
                    if (hitStr.contains(collocateLemma)) {
                        Concordance conc = concordances.get(hit);
                        if (conc != null) {
                            String[] parts = conc.parts();
                            snippet = parts[0] + parts[1] + parts[2];
                            break;
                        }
                    }
                }

                results.add(new QueryResults.ConcordanceResult(
                    snippet, 0, 0, null,
                    collocateLemma, f_xy, logDice));
            }

            return results.stream()
                .sorted(Comparator.comparingDouble(QueryResults.ConcordanceResult::getLogDice).reversed())
                .limit(maxResults)
                .toList();

        } catch (InvalidQuery e) {
            throw new IOException("BCQL parse error: " + e.getMessage(), e);
        }
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
            BLSpanQuery query = pattern.toQuery(QueryInfo.create(blackLabIndex));

            // Get hits and concordances
            Hits hits = blackLabIndex.find(query);
            Concordances concordances = hits.concordances(ContextSize.get(0, 0), ConcordanceType.FORWARD_INDEX);

            long headwordFreq = getTotalFrequency(lemma);

            // Build a map of collocate -> frequency by iterating through hits
            Map<String, Long> freqMap = new HashMap<>();
            for (int i = 0; i < hits.size(); i++) {
                Hit hit = hits.get(i);
                Concordance conc = concordances.get(hit);
                if (conc != null) {
                    String[] parts = conc.parts();
                    String snippet = parts[0] + parts[1] + parts[2];
                    // Extract the collocate at collocatePosition from the snippet
                    String collocate = extractTokenFromSnippet(snippet, collocatePosition);
                    if (collocate != null) {
                        freqMap.merge(collocate, 1L, Long::sum);
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
