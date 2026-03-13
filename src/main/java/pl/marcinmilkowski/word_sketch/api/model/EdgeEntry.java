package pl.marcinmilkowski.word_sketch.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import pl.marcinmilkowski.word_sketch.model.exploration.RelationEdgeType;

/** A directed graph edge in semantic field exploration and comparison responses. */
public record EdgeEntry(
        String source,
        String target,
        @JsonProperty("log_dice") double logDice,
        RelationEdgeType type) {}
