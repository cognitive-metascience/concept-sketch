# Code Review: Query Architecture Overhaul

**Date:** 2026-02-19
**Reviewer:** Claude Code (oh-my-claudecode:code-review)
**Project:** word-sketch-lucene
**Focus:** Grammar-pattern precomputation and CQL-to-Lucene query encoding

---

## Executive Summary

The precomputation (`precompute-grammar`) is silently failing because **the query architecture has fundamental bugs that prevent proper matching between CQL patterns and the Lucene index**. The code executes without errors but produces **zero results** because:

1. Headword is never included in the CQL pattern
2. Multi-field patterns silently drop constraints
3. Index fields don't match query assumptions

**Overall Assessment:** ❌ **CRITICAL ISSUES FOUND** - Precomputation produces incorrect/empty results

---

## Critical Issues (MUST FIX)

### 1. HEADWORD NOT INCLUDED IN CQL PATTERN
**Severity:** CRITICAL
**File:** `GrammarPatternCollocationsBuilder.java:441-447`

```java
private String buildCqlPattern(String cqlPattern, String lemma) {
    // DO NOT prepend lemma - the builder already searches for sentences containing
    // the headword separately. Prepending the lemma changes the pattern semantics
    // incorrectly...
    return cqlPattern;  // <-- BUG: lemma is completely ignored!
}
```

**Problem:** The method receives the lemma but **returns the raw pattern unchanged**. This means:
- Pattern `[tag="JJ.*"] [tag="NN.*"]` is passed as-is
- There's **no constraint for the headword itself**
- The search finds ALL nouns and adjectives, not just those related to the target lemma

**Impact:** The precomputation counts collocations for ANY nouns, not specific headwords.

---

### 2. MULTI-FIELD PATTERNS SILENTLY DROP CONSTRAINTS
**Severity:** CRITICAL
**File:** `CQLToLuceneCompiler.java:76-93`

```java
// For different fields, we can't use SpanNearQuery (requires same field)
// Return the query from the first field - application code handles post-filtering
String firstField = queriesByField.keySet().iterator().next();
List<SpanQuery> firstFieldQueries = queriesByField.get(firstField);
// ...
return new SpanNearQuery(firstFieldQueries.toArray(new SpanQuery[0]), totalSlop, true);
```

**Problem:** When a pattern spans multiple fields (e.g., `lemma=problem [tag=JJ.*]`), the compiler:
1. Groups queries by field
2. **Keeps only the first field's queries**
3. **Silently discards all other field constraints**

**Impact:** Most grammar patterns in `relations.json` use both `lemma` and `tag` fields but only the tag constraints are executed.

---

### 3. DUAL RELATIONS PROCESSED INCORRECTLY
**Severity:** HIGH
**File:** `GrammarPatternCollocationsBuilder.java:312-317`

```java
for (var rel : relations) {
    GrammarCollocationsEntry entry = buildEntryForRelation(lemma, rel, headwordFreq, corpusUuid);
    if (entry != null && !entry.collocations().isEmpty()) {
        entries.add(entry);
    }
}
```

**Problem:** Relations marked as `"dual": true` (e.g., `verb_nouns`) require **bidirectional search** (headword→collocate AND collocate→headword), but the code only searches in one direction.

---

### 4. PATTERN EXECUTION FALLBACK WRONG
**Severity:** HIGH
**File:** `GrammarPatternCollocationsBuilder.java:480-483`

```java
if (!(spanQuery instanceof org.apache.lucene.queries.spans.SpanNearQuery)) {
    countCollocationsWithSimpleFallback(headwordLower, relation, collocateCounts);
    return;
}
```

**Problem:** The code checks if query is `SpanNearQuery` and falls back to simple counting. But:
- Complex multi-field patterns return only the first field's queries (bug #2)
- The "fallback" actually has the CORRECT offset calculation but is treated as inferior

---

## Medium Issues

### 5. DEPREL FIELD MATCHING
**Severity:** MEDIUM

The `deprel` field is indexed but **dependency relations in relations.json use `deprel` in patterns**, which may not match the indexed values (Universal Dependencies uses different labels than the patterns expect).

---

### 6. NO E2E TEST FOR PRECOMPUTATION
**Severity:** HIGH

The test `GrammarPatternRetrievalTest.java` only tests in-memory token matching, NOT the actual Lucene query execution path used during precomputation.

---

## Grammar Configuration Reference

### relations.json Structure
```json
{
  "id": "adj_modifier",
  "name": "Modifiers (adjectives)",
  "pattern": "[tag=\"JJ.*\"] [tag=\"NN.*\"]",
  "head_position": 2,
  "collocate_position": 1,
  "relation_type": "SURFACE"
}
```

### Pattern Types
- **SURFACE**: Based on POS tag sequences (e.g., JJ NN = adjective before noun)
- **DEP**: Based on dependency relations (e.g., nsubj, obj, amod)

---

## Verification Steps

```bash
# 1. Create small test index
java -jar target/word-sketch-lucene.jar conllu \
  --input src/test/resources/test-corpus.conllu \
  --output /tmp/test-index

# 2. Run precompute-grammar
java -jar target/word-sketch-lucene.jar precompute-grammar \
  --index /tmp/test-index \
  --output /tmp/grammar.bin \
  --type surface

# 3. Check output has entries
java -cp target/word-sketch-lucene-1.0.1.jar \
  pl.marcinmilkowski.word_sketch.indexer.hybrid.GrammarPatternCollocationsReader \
  /tmp/grammar.bin info
```

---

## Summary

| Severity | Count |
|----------|-------|
| CRITICAL | 2 |
| HIGH | 3 |
| MEDIUM | 3 |

The precomputation produces empty results because the CQL patterns are not properly translated to queries that match the index. The core architectural problem is that `buildCqlPattern()` was intentionally left as a no-op with a misleading comment, and the CQL compiler silently drops multi-field constraints.
