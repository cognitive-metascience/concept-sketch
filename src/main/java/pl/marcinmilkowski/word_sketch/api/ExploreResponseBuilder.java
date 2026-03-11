package pl.marcinmilkowski.word_sketch.api;

import pl.marcinmilkowski.word_sketch.exploration.Edge;
import pl.marcinmilkowski.word_sketch.exploration.RelationEdgeType;
import pl.marcinmilkowski.word_sketch.model.AdjectiveProfile;
import pl.marcinmilkowski.word_sketch.model.ComparisonResult;
import pl.marcinmilkowski.word_sketch.model.DiscoveredNoun;
import pl.marcinmilkowski.word_sketch.model.ExplorationResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Converts model-layer result objects into graph {@link Edge} lists for API responses.
 *
 * <p>This class owns the model-to-graph translation so that {@link ExplorationResult}
 * and {@link ComparisonResult} stay free of presentation concerns and exploration-package
 * imports.
 */
public final class ExploreResponseBuilder {

    private ExploreResponseBuilder() {}

    /** Build edges for graph visualization from a single-seed exploration result. */
    public static List<Edge> buildEdges(ExplorationResult result) {
        List<Edge> edges = new ArrayList<>();
        for (Map.Entry<String, Double> colloc : result.getSeedCollocates().entrySet()) {
            edges.add(new Edge(result.getSeed(), colloc.getKey(), colloc.getValue(), RelationEdgeType.SEED_ADJ));
        }
        for (DiscoveredNoun noun : result.getDiscoveredNouns()) {
            for (Map.Entry<String, Double> colloc : noun.sharedCollocates().entrySet()) {
                edges.add(new Edge(noun.noun(), colloc.getKey(), colloc.getValue(), RelationEdgeType.DISCOVERED_ADJ));
            }
        }
        return edges;
    }

    /** Build edges for graph visualization from a comparison result. */
    public static List<Edge> buildEdges(ComparisonResult result) {
        List<Edge> edges = new ArrayList<>();
        for (AdjectiveProfile adj : result.getAllAdjectives()) {
            for (Map.Entry<String, Double> entry : adj.nounScores().entrySet()) {
                if (entry.getValue() > 0) {
                    edges.add(new Edge(adj.adjective(), entry.getKey(), entry.getValue(), RelationEdgeType.MODIFIER));
                }
            }
        }
        return edges;
    }
}
