package pl.marcinmilkowski.word_sketch.exploration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import pl.marcinmilkowski.word_sketch.config.RelationConfig;
import pl.marcinmilkowski.word_sketch.model.CoreCollocate;
import pl.marcinmilkowski.word_sketch.model.DiscoveredNoun;
import pl.marcinmilkowski.word_sketch.model.ExplorationResult;
import pl.marcinmilkowski.word_sketch.model.QueryResults;
import pl.marcinmilkowski.word_sketch.query.QueryExecutor;

/**
 * Computes multi-seed semantic field exploration: for each seed, fetches collocates using the
 * given relation, then intersects them across seeds to find shared and distinctive patterns.
 *
 * <p>Extracted from {@link SemanticFieldExplorer} so that the single-seed and multi-seed
 * algorithms each have a focused owner. {@link SemanticFieldExplorer} is now a thin facade
 * delegating to both this class and {@link CollocateProfileComparator}.</p>
 */
class MultiSeedExplorer {

    private final QueryExecutor executor;

    MultiSeedExplorer(QueryExecutor executor) {
        this.executor = executor;
    }

    /**
     * Fetches collocates for each seed using the given relation and maps the results into an
     * {@link ExplorationResult}. Seeds become the {@code discoveredNouns} (each carrying their
     * common collocates as shared-collocate set); the collocate intersection becomes
     * {@code coreCollocates}; and the aggregate collocate map becomes {@code seedCollocates}.
     *
     * @param seeds          ordered seed words (at least 2)
     * @param relationConfig grammar relation to use for collocate lookup
     * @param minLogDice     minimum logDice threshold for inclusion
     * @param topCollocates  maximum collocates to fetch per seed
     * @param minShared      minimum number of seeds a collocate must appear in to be
     *                       included in the core set
     * @return ExplorationResult mapping multi-seed data into the shared exploration model
     */
    ExplorationResult explore(
            Set<String> seeds,
            RelationConfig relationConfig,
            double minLogDice,
            int topCollocates,
            int minShared) throws IOException {
        Map<String, List<QueryResults.WordSketchResult>> seedCollocateMap = new LinkedHashMap<>();
        Map<String, Integer> collocateSharedCount = new HashMap<>();

        for (String seed : seeds) {
            String bcqlPattern = relationConfig.buildFullPattern(seed);
            List<QueryResults.WordSketchResult> collocates = executor.executeSurfacePattern(
                seed, bcqlPattern, minLogDice, topCollocates);
            seedCollocateMap.put(seed, collocates);

            for (QueryResults.WordSketchResult wsr : collocates) {
                collocateSharedCount.merge(wsr.lemma(), 1, Integer::sum);
            }
        }

        int threshold = Math.min(minShared, seeds.size());
        Set<String> commonCollocates = new HashSet<>();
        for (Map.Entry<String, Integer> entry : collocateSharedCount.entrySet()) {
            if (entry.getValue() >= threshold) {
                commonCollocates.add(entry.getKey());
            }
        }

        Map<String, Double> seedCollocScores = new LinkedHashMap<>();
        Map<String, Long> seedCollocFreqs = new LinkedHashMap<>();
        for (List<QueryResults.WordSketchResult> collocs : seedCollocateMap.values()) {
            for (QueryResults.WordSketchResult wsr : collocs) {
                seedCollocScores.merge(wsr.lemma(), wsr.logDice(), Math::max);
                seedCollocFreqs.merge(wsr.lemma(), wsr.frequency(), Long::sum);
            }
        }

        int numSeeds = seeds.size();
        List<DiscoveredNoun> discoveredNounsList = new ArrayList<>();
        for (String seed : seeds) {
            List<QueryResults.WordSketchResult> collocs = seedCollocateMap.getOrDefault(seed, List.of());
            Map<String, Double> sharedCollocs = new LinkedHashMap<>();
            for (QueryResults.WordSketchResult wsr : collocs) {
                if (commonCollocates.contains(wsr.lemma())) {
                    sharedCollocs.put(wsr.lemma(), wsr.logDice());
                }
            }
            int count = sharedCollocs.size();
            double avg = sharedCollocs.isEmpty() ? 0.0
                : sharedCollocs.values().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double sum = sharedCollocs.values().stream().mapToDouble(Double::doubleValue).sum();
            discoveredNounsList.add(new DiscoveredNoun(seed, sharedCollocs, count, sum, avg));
        }

        List<CoreCollocate> coreCollocatesList = new ArrayList<>();
        for (String c : commonCollocates) {
            int sharedBy = collocateSharedCount.getOrDefault(c, 0);
            double avgLd = seedCollocateMap.values().stream()
                .flatMap(List::stream)
                .filter(wsr -> c.equals(wsr.lemma()))
                .mapToDouble(QueryResults.WordSketchResult::logDice)
                .average().orElse(0.0);
            double seedLd = seedCollocScores.getOrDefault(c, 0.0);
            coreCollocatesList.add(new CoreCollocate(c, sharedBy, numSeeds, seedLd, avgLd));
        }

        return new ExplorationResult(
            String.join(",", seeds),
            seedCollocScores, seedCollocFreqs,
            discoveredNounsList, coreCollocatesList);
    }
}
