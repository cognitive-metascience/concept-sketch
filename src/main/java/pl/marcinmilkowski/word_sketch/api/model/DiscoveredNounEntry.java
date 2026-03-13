package pl.marcinmilkowski.word_sketch.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** A single discovered-noun entry in the {@code discovered_nouns} or {@code source_seeds} array. */
public record DiscoveredNounEntry(
        String word,
        @JsonProperty("shared_count") int sharedCount,
        @JsonProperty("similarity_score") double similarityScore,
        @JsonProperty("avg_logdice") double avgLogDice,
        @JsonProperty("shared_collocates") List<String> sharedCollocates) {}
