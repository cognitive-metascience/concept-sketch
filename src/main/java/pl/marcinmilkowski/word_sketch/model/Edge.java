package pl.marcinmilkowski.word_sketch.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Edge for graph visualization.
 */
public record Edge(String source, String target, double weight, RelationEdgeType type) {

    public Map<String, Object> toMap() {
        Map<String, Object> m = new HashMap<>();
        m.put("source", source);
        m.put("target", target);
        m.put("log_dice", Math.round(weight * 100.0) / 100.0);
        m.put("type", type.label());
        return m;
    }
}
