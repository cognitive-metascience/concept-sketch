package pl.marcinmilkowski.word_sketch.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Converts {@link GrammarConfig} and {@link RelationConfig} value objects to typed records
 * for API responses and diagnostic output.
 *
 * <p>Serialization lives here rather than on the value objects so that
 * {@link GrammarConfig} and {@link RelationConfig} remain pure data carriers
 * with no dependency on the JSON library.</p>
 */
public final class GrammarConfigSerializer {

    private GrammarConfigSerializer() {}

    /** Typed record for a single relation entry in a grammar-config response. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RelationConfigEntry(
            String id,
            @Nullable String name,
            @Nullable String description,
            @Nullable String pattern,
            @JsonProperty("head_position") int headPosition,
            @JsonProperty("collocate_position") int collocatePosition,
            boolean dual,
            @JsonProperty("default_slop") int defaultSlop,
            @JsonProperty("relation_type") @Nullable String relationType) {}

    /** Typed record for the full grammar-config response (version, config path, relations). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record GrammarConfigResponse(
            String version,
            @JsonProperty("config_path") @Nullable String configPath,
            List<RelationConfigEntry> relations) {}

    /**
     * Builds a {@link GrammarConfigResponse} for the given config.
     *
     * @param config the grammar config to convert; must not be null
     * @return a typed record suitable for Jackson serialization
     */
    public static GrammarConfigResponse toJson(@NonNull GrammarConfig config) {
        List<RelationConfigEntry> entries = config.relations().stream()
                .map(GrammarConfigSerializer::toJson)
                .toList();
        String configPath = config.configPath() != null ? config.configPath().toString() : null;
        return new GrammarConfigResponse(config.version(), configPath, entries);
    }

    /**
     * Builds a {@link RelationConfigEntry} for the given relation config.
     *
     * @param rel the relation config to convert; must not be null
     * @return a typed record with all non-null fields populated
     */
    public static RelationConfigEntry toJson(@NonNull RelationConfig rel) {
        String relationType = rel.relationType().map(rt -> rt.name()).orElse(null);
        return new RelationConfigEntry(
                rel.id(),
                rel.name(),
                rel.description(),
                rel.pattern(),
                rel.headPosition(),
                rel.collocatePosition(),
                rel.dual(),
                rel.defaultSlop(),
                relationType);
    }
}
