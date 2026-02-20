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
