package pl.marcinmilkowski.word_sketch.query;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class SnowballCollocationsTest {

    @Test
    void testEdgeAndResultContainers() {
        SnowballCollocations.Edge e1 = new SnowballCollocations.Edge("big", "problem", 9.5, "attributive");
        SnowballCollocations.Edge e2 = new SnowballCollocations.Edge("serious", "problem", 8.1, "attributive");

        Set<String> adjs = new LinkedHashSet<>(Arrays.asList("big", "serious"));
        Set<String> nouns = new LinkedHashSet<>(Collections.singletonList("problem"));
        List<SnowballCollocations.Edge> edges = Arrays.asList(e1, e2);

        SnowballCollocations.SnowballResult r = new SnowballCollocations.SnowballResult(adjs, nouns, edges);

        assertEquals(2, r.getAllAdjectives().size());
        assertEquals(1, r.getAllNouns().size());
        assertEquals(2, r.getEdges().size());

        Map<String, Set<String>> adj2n = r.getAdjectiveToNounsMap();
        assertEquals(Set.of("problem"), adj2n.get("big"));
        assertTrue(adj2n.containsKey("serious"));

        Map<String, Set<String>> n2adj = r.getNounToAdjectivesMap();
        assertEquals(Set.of("big", "serious"), n2adj.get("problem"));

        String s = r.toString();
        assertTrue(s.contains("adjectives"));
    }
}
