package pl.marcinmilkowski.word_sketch.api.model;

import java.util.List;

/**
 * Typed top-level response record for the grammar-relations catalogue endpoints
 * ({@code GET /api/sketch/surface-relations} and {@code GET /api/sketch/dep-relations}).
 *
 * <p>Replaces the raw {@code Map<String,Object>} previously assembled inline in
 * {@link pl.marcinmilkowski.word_sketch.api.SketchHandlers#handleRelationsForType}.</p>
 */
public record RelationListResponse(String status, List<RelationListEntry> relations) {}
