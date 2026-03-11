package pl.marcinmilkowski.word_sketch.config;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable grammar configuration value object holding the parsed relation data.
 *
 * <p>Instances are produced by {@link GrammarConfigLoader}. Separating the loaded data
 * from the loading mechanism lets callers depend only on the data they need, without
 * carrying a reference to the file-IO machinery.</p>
 */
public final class GrammarConfig {

    private final List<RelationConfig> relations;
    private final Map<String, RelationConfig> relationsById;
    private final String version;
    private final Path configPath;

    GrammarConfig(List<RelationConfig> relations, Map<String, RelationConfig> relationsById,
                  String version, Path configPath) {
        this.relations = Collections.unmodifiableList(relations);
        this.relationsById = Collections.unmodifiableMap(relationsById);
        this.version = version;
        this.configPath = configPath;
    }

    public List<RelationConfig> getRelations() {
        return relations;
    }

    public Optional<RelationConfig> getRelation(String id) {
        return Optional.ofNullable(relationsById.get(id));
    }

    public String getVersion() {
        return version;
    }

    public @org.jspecify.annotations.Nullable Path getConfigPath() {
        return configPath;
    }

    /** Export the config as a JSONObject for API responses. */
    public JSONObject toJson() {
        JSONObject root = new JSONObject();
        root.put("version", version);
        root.put("config_path", configPath != null ? configPath.toString() : null);
        JSONArray relationsArray = new JSONArray();
        for (RelationConfig rel : relations) {
            relationsArray.add(rel.toJson());
        }
        root.put("relations", relationsArray);
        return root;
    }
}
