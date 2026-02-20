package pl.marcinmilkowski.word_sketch.config;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Loads and provides access to grammar configuration from JSON.
 *
 * Expected JSON structure:
 * {
 *   "version": "1.0",
 *   "copulas": ["be", "seem", "become", ...],
 *   "relations": [
 *     {
 *       "id": "noun_adj_predicates",
 *       "name": "...",
 *       "head_pos": "noun",
 *       "collocate_pos": "adj",
 *       "cql_pattern": "[tag=jj.*]",
 *       "uses_copula": true,
 *       "default_slop": 8
 *     },
 *     ...
 *   ]
 * }
 */
public class GrammarConfigLoader {
    private static final Logger logger = LoggerFactory.getLogger(GrammarConfigLoader.class);

    private final List<String> copulas;
    private final Set<String> copulaSet;
    private final List<RelationConfig> relations;
    private final Map<String, RelationConfig> relationsById;
    private final String version;
    private final Path configPath;

    /**
     * Load grammar configuration from the specified path.
     *
     * @param configPath Path to the relations.json file
     * @throws IOException if the file cannot be read
     * @throws IllegalArgumentException if the file is invalid
     */
    public GrammarConfigLoader(Path configPath) throws IOException {
        this.configPath = configPath;

        // Load and parse the config
        if (!Files.exists(configPath)) {
            throw new IOException("Grammar config file not found: " + configPath);
        }

        String content = Files.readString(configPath);
        JSONObject root = JSON.parseObject(content);

        // Validate version
        String parsedVersion = root.getString("version");
        if (parsedVersion == null || parsedVersion.isBlank()) {
            throw new IllegalArgumentException("Missing 'version' field in grammar config");
        }
        this.version = parsedVersion;

        // VALIDATION: Reject 'copulas' key - must be embedded in CQL patterns
        if (root.containsKey("copulas")) {
            throw new IllegalArgumentException("Grammar config must NOT contain 'copulas' key. " +
                "Copulas must be embedded in CQL patterns using [lemma=\"be|appear|seem|...\"]. " +
                "See noun_adj_predicates for example.");
        }

        // Copulas are now derived from CQL patterns - no separate array needed
        this.copulas = Collections.emptyList();
        this.copulaSet = Collections.emptySet();

        // Load relations
        JSONArray relationsArray = root.getJSONArray("relations");
        if (relationsArray == null || relationsArray.isEmpty()) {
            throw new IllegalArgumentException("Missing or empty 'relations' array in grammar config");
        }
        List<RelationConfig> loadedRelations = new ArrayList<>();
        Map<String, RelationConfig> loadedRelationsById = new HashMap<>();

        for (int i = 0; i < relationsArray.size(); i++) {
            JSONObject relObj = relationsArray.getJSONObject(i);
            if (relObj == null) {
                throw new IllegalArgumentException("Invalid relation at index " + i);
            }

            String id = relObj.getString("id");
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("Missing 'id' field for relation at index " + i);
            }

            // Support both new format (pattern, head_position, dual) and legacy format (cql_pattern)
            String pattern = relObj.containsKey("pattern") ? relObj.getString("pattern") :
                           (relObj.containsKey("cql_pattern") ? relObj.getString("cql_pattern") : "");

            // VALIDATION: Reject legacy {head} format - throw exception
            if (pattern.contains("{head}")) {
                throw new IllegalArgumentException("Relation '" + id + "' uses deprecated {head} format. Use [lemma=\"...\"] instead.");
            }

            int headPosition = relObj.containsKey("head_position") ? relObj.getIntValue("head_position") :
                              (relObj.containsKey("head_pos") ? 1 : 1); // default to 1
            int collocatePosition = relObj.containsKey("collocate_position") ? relObj.getIntValue("collocate_position") :
                                   (relObj.containsKey("collocate_pos") ? 2 : 2);

            // VALIDATION: Require pattern - throw exception if missing
            if (pattern == null || pattern.isBlank()) {
                throw new IllegalArgumentException("Relation '" + id + "' has no pattern - every relation must have a CQL pattern");
            }

            // VALIDATION: Check positions match pattern token count - throw exception
            int tokenCount = countPatternTokens(pattern);
            if (headPosition < 1 || headPosition > tokenCount || collocatePosition < 1 || collocatePosition > tokenCount) {
                throw new IllegalArgumentException("Relation '" + id + "': positions (" + headPosition + "," + collocatePosition
                    + ") must be between 1 and " + tokenCount + " (pattern has " + tokenCount + " tokens: " + pattern + ")");
            }

            boolean dual = relObj.containsKey("dual") && relObj.getBoolean("dual");

            RelationConfig config = new RelationConfig(
                id,
                relObj.getString("name"),
                relObj.getString("description"),
                pattern,
                headPosition,
                collocatePosition,
                dual,
                relObj.getIntValue("default_slop", 10),
                relObj.getString("relation_type"),
                relObj.getBoolean("exploration_enabled")
            );

            if (loadedRelationsById.containsKey(id)) {
                throw new IllegalArgumentException("Duplicate relation id: " + id);
            }

            loadedRelations.add(config);
            loadedRelationsById.put(id, config);
        }
        this.relations = Collections.unmodifiableList(loadedRelations);
        this.relationsById = Collections.unmodifiableMap(loadedRelationsById);

        logger.info("Loaded grammar config version {}: {} copulas, {} relations from {}",
            version, copulas.size(), relations.size(), configPath);
    }

    /**
     * Create a default English grammar config with standard copulas and relations.
     * This is explicit configuration, not a fallback.
     */
    public static GrammarConfigLoader createDefaultEnglish() {
        try {
            return new GrammarConfigLoader(Path.of("grammars/relations.json"));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load default grammar config: grammars/relations.json", e);
        }
    }

    /**
     * Count the number of tokens in a CQL pattern.
     * Each [constraint] is one token.
     */
    private static int countPatternTokens(String pattern) {
        if (pattern == null || pattern.isBlank()) {
            return 0;
        }
        int count = 0;
        int i = 0;
        while (i < pattern.length()) {
            if (pattern.charAt(i) == '[') {
                // Find matching ]
                int end = pattern.indexOf(']', i);
                if (end > i) {
                    count++;
                    i = end + 1;
                } else {
                    i++;
                }
            } else {
                i++;
            }
        }
        return count;
    }

    /**
     * Get all configured copula lemmas.
     */
    public List<String> getCopulas() {
        return copulas;
    }

    /**
     * Check if a lemma is a copular verb.
     */
    public boolean isCopularVerb(String lemma) {
        if (lemma == null) return false;
        return copulaSet.contains(lemma.toLowerCase(Locale.ROOT));
    }

    /**
     * Get all configured relations.
     */
    public List<RelationConfig> getRelations() {
        return relations;
    }

    /**
     * Get a relation by ID.
     */
    public Optional<RelationConfig> getRelation(String id) {
        return Optional.ofNullable(relationsById.get(id));
    }

    /**
     * Get relations for a specific head POS group.
     */
    public List<RelationConfig> getRelationsForHeadPos(String headPos) {
        // Filter by collocate POS group derived from pattern
        return relations.stream()
            .filter(r -> headPos.equalsIgnoreCase(r.collocatePosGroup()))
            .collect(Collectors.toList());
    }

    /**
     * Find the relation ID for a given relationType (e.g., "ADJ_PREDICATE").
     * Returns the first matching relation's ID, or null if not found.
     */
    public String findRelationIdByType(String relationType) {
        if (relationType == null) return null;
        return relations.stream()
            .filter(r -> relationType.equalsIgnoreCase(r.relationType()))
            .map(RelationConfig::id)
            .findFirst()
            .orElse(null);
    }

    /**
     * Get the configuration version.
     */
    public String getVersion() {
        return version;
    }

    /**
     * Get the config file path.
     */
    public Path getConfigPath() {
        return configPath;
    }

    /**
     * Export the loaded config as a JSONObject for API responses.
     */
    public JSONObject toJson() {
        JSONObject root = new JSONObject();
        root.put("version", version);
        root.put("config_path", configPath.toString());
        root.put("copulas", new JSONArray(copulas));

        JSONArray relationsArray = new JSONArray();
        for (RelationConfig rel : relations) {
            relationsArray.add(rel.toJson());
        }
        root.put("relations", relationsArray);
        return root;
    }

    /**
     * Relation configuration record.
     */
    public record RelationConfig(
        String id,
        String name,
        String description,
        String pattern,
        int headPosition,
        int collocatePosition,
        boolean dual,
        int defaultSlop,
        String relationType,
        Boolean explorationEnabled
    ) {
        public JSONObject toJson() {
            JSONObject obj = new JSONObject();
            obj.put("id", id);
            if (name != null) obj.put("name", name);
            if (description != null) obj.put("description", description);
            if (pattern != null) obj.put("pattern", pattern);
            obj.put("head_position", headPosition);
            obj.put("collocate_position", collocatePosition);
            obj.put("dual", dual);
            obj.put("default_slop", defaultSlop);
            if (relationType != null) obj.put("relation_type", relationType);
            if (explorationEnabled != null) obj.put("exploration_enabled", explorationEnabled);
            return obj;
        }

        /**
         * Get the full CQL pattern with headword substituted at headPosition.
         */
        public String getFullPattern(String headword) {
            if (pattern == null || headword == null) return null;

            // Check for legacy format: pattern doesn't contain {head} placeholder and is a single constraint
            if (isLegacyFormat()) {
                return null; // Signal to use fallback
            }

            // Split pattern into elements and replace the headPosition element with lemma constraint
            String[] elements = pattern.split("\\s+");
            if (headPosition < 1 || headPosition > elements.length) {
                return pattern; // Return as-is if position is invalid
            }
            // Replace the element at headPosition (1-based) with lemma constraint
            elements[headPosition - 1] = "[lemma=\"" + headword.toLowerCase() + "\"]";
            return String.join(" ", elements);
        }

        /**
         * Check if this relation uses legacy format (single constraint without position).
         * Legacy: cql_pattern like "[tag=NN.*]" without {head} or multiple elements.
         */
        public boolean isLegacyFormat() {
            if (pattern == null || pattern.isEmpty()) return true;
            // Legacy format is a single constraint without {head} placeholder
            // and doesn't look like a multi-element pattern
            String trimmed = pattern.trim();
            // If it starts with [ and contains no space, it's a single constraint (legacy)
            return !trimmed.contains("{head}") && !trimmed.matches(".*\\[.*\\].*\\[.*\\].*");
        }

        /**
         * Derive the collocate POS group from the pattern.
         */
        public String collocatePosGroup() {
            if (pattern == null) return "other";
            String pat = pattern.toLowerCase(Locale.ROOT);
            // Check with quotes (CQL format: tag="JJ.*") and without quotes (simple format: tag=JJ.*)
            if (pat.contains("tag=\"in") || pat.contains("tag=in")) return "prep";
            if (pat.contains("tag=\"rp") || pat.contains("tag=rp") || pat.contains("tag=\"to") || pat.contains("tag=to")) return "part";
            if (pat.contains("tag=\"jj") || pat.contains("tag=jj")) return "adj";
            if (pat.contains("tag=\"vb") || pat.contains("tag=vb")) return "verb";
            if (pat.contains("tag=\"nn") || pat.contains("tag=nn") || pat.contains("tag=\"pos") || pat.contains("tag=pos")) return "noun";
            if (pat.contains("tag=\"rb") || pat.contains("tag=rb")) return "adv";
            return "other";  // collocatePos derived from pattern only
        }

    }
}
