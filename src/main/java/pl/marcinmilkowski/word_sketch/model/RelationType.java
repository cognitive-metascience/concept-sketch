package pl.marcinmilkowski.word_sketch.model;

/** Relation matching strategy: surface token sequence vs. dependency annotation. */
public enum RelationType {
    SURFACE, DEP,
    ADJ_PREDICATE, ADJ_MODIFIER,
    SUBJECT_OF, OBJECT_OF
}
