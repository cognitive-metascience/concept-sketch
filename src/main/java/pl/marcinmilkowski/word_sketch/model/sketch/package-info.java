/**
 * Word-sketch query result types.
 *
 * <p>Contains the result records returned by word sketch and collocation queries:
 * {@link pl.marcinmilkowski.word_sketch.model.sketch.WordSketchResult},
 * {@link pl.marcinmilkowski.word_sketch.model.sketch.ConcordanceResult},
 * {@link pl.marcinmilkowski.word_sketch.model.sketch.ConcordanceHit}, and
 * {@link pl.marcinmilkowski.word_sketch.model.sketch.CollocateResult}.
 *
 * <p>This sub-package mirrors the structure of {@code model/exploration/} for
 * exploration result types, keeping sketch-specific result types symmetrically
 * isolated. No persistence, I/O, or HTTP concerns belong here.
 */
package pl.marcinmilkowski.word_sketch.model.sketch;
