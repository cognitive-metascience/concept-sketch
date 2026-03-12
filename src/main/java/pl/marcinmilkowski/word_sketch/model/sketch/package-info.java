/**
 * Word-sketch query result model types.
 *
 * <p>This sub-package is intentionally empty for now. The word-sketch result types
 * ({@link pl.marcinmilkowski.word_sketch.model.QueryResults.WordSketchResult},
 * {@link pl.marcinmilkowski.word_sketch.model.QueryResults.CollocateResult},
 * {@link pl.marcinmilkowski.word_sketch.model.QueryResults.SnippetResult}) currently live in
 * {@link pl.marcinmilkowski.word_sketch.model.QueryResults} as nested types rather than being
 * promoted to top-level classes in this sub-package.</p>
 *
 * <p>The asymmetry between this sketch/ sub-package and the neighbouring exploration/ sub-package
 * is deliberate: exploration types are numerous, independently-evolved records that benefit from
 * their own namespace, whereas the sketch types are a small, tightly-coupled set that are most
 * readable as co-located nested types inside {@code QueryResults}. If the sketch model grows
 * significantly, migrating the nested types here would be the natural next step.</p>
 */
package pl.marcinmilkowski.word_sketch.model.sketch;
