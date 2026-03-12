package pl.marcinmilkowski.word_sketch.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Typed response record for a single collocate in a word sketch relation.
 *
 * <p>Replaces the raw {@code Map<String,Object>} previously returned by
 * {@code SketchResponseAssembler.formatWordSketchResult}. Jackson serialises records
 * directly via their component accessors.</p>
 */
public record CollocateEntry(
        String lemma,
        long frequency,
        @JsonProperty("log_dice") double logDice,
        String pos) {}
