package pl.marcinmilkowski.word_sketch.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/** A single core-collocate entry in the {@code core_collocates} array. */
public record CoreCollocateEntry(
        String word,
        @JsonProperty("shared_by_count") int sharedByCount,
        @JsonProperty("total_nouns") int totalNouns,
        double coverage,
        @JsonProperty("seed_logdice") double seedLogDice) {}
