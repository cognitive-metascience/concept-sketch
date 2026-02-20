# Action Plan: Query Architecture Fix

**Date:** 2026-02-19
**Based On:** `plans/query-architecture-review.md`

## Objective

Fix the query architecture to ensure `precompute-grammar` correctly identifies grammatical patterns by properly encoding CQL patterns as Lucene queries that match the index.

---

## Root Causes

1. **Headword not in CQL pattern** - `buildCqlPattern()` returns pattern unchanged
2. **Multi-field patterns drop constraints** - CQLToLuceneCompiler keeps only first field
3. **No E2E verification** - No test validates precomputation output

---

## Action Items

### Phase 1: Core Fixes (P0 - Critical)

#### 1.1 Fix buildCqlPattern to Include Headword
- **File:** `GrammarPatternCollocationsBuilder.java`
- **Method:** `buildCqlPattern(String cqlPattern, String lemma)`
- **Change:** Prepend headword constraint to pattern
- **Approach:** Determine direction from head_position vs collocate_position
  - If head_position < collocate_position: headword at position X, collocate at X + offset
  - If head_position > collocate_position: headword at position X, collocate at X - offset
- **Test:** Verify "theory" with adj_modifier finds adjectives BEFORE "theory"

#### 1.2 Fix Multi-Field Pattern Compilation
- **File:** `CQLToLuceneCompiler.java`
- **Method:** `compile(CQLPattern pattern)`
- **Change:** Use BooleanQuery to combine multi-field constraints instead of dropping
- **Alternative:** Return all field queries with post-filter flag
- **Test:** Pattern `[lemma="problem"] [tag="JJ.*"]` matches "problem" + adjacent JJ

#### 1.3 Add Precomputation E2E Test
- **New File:** `src/test/java/pl/marcinmilkowski/word_sketch/e2e/PrecomputationE2ETest.java`
- **Steps:**
  1. Create index with known sentences (from GrammarPatternRetrievalTest)
  2. Run GrammarPatternCollocationsBuilder programmatically
  3. Verify binary output contains expected collocations
  4. Validate with zero false positives/negatives

---

### Phase 2: Supporting Fixes (P1 - High Priority)

#### 2.1 Handle Dual Relations Bidirectionally
- **File:** `GrammarPatternCollocationsBuilder.java`
- **Method:** `buildEntriesForLemma()`
- **Change:** For `dual: true` relations, create two entries:
  - Forward: headword → collocate
  - Reverse: collocate → headword

#### 2.2 Fix Fallback Detection Logic
- **File:** `GrammarPatternCollocationsBuilder.java`
- **Method:** `countCollocationsWithSpanQuery()`
- **Change:** Remove instanceof SpanNearQuery check; always use position extraction

#### 2.3 Add Diagnostic Logging
- **Files:** GrammarPatternCollocationsBuilder.java, CQLToLuceneCompiler.java
- **Change:** Log when:
  - Falling back to simple counting
  - Pattern has multiple fields
  - No results found for (lemma, relation)

---

### Phase 3: Verification & Polish (P2)

#### 3.1 Validate Against Real Corpus
- Run `precompute-grammar` on fpsyg_index
- Verify output has entries for known headwords

#### 3.2 Performance Optimization (After Correctness)
- Profile query execution time
- Optimize batch processing
- Consider caching compiled patterns

#### 3.3 Document Edge Cases
- Update relations.json schema docs
- Note limitations of current pattern support

---

## Implementation Order

```
Week 1:
├── Day 1-2: Fix buildCqlPattern (1.1)
├── Day 3-4: Fix CQLToLuceneCompiler (1.2)
└── Day 5: Write E2E test (1.3)

Week 2:
├── Day 1-2: Handle dual relations (2.1)
├── Day 3: Fix fallback logic (2.2)
└── Day 4-5: Add logging (2.3)

Week 3:
├── Day 1-2: Validate on real corpus (3.1)
└── Day 3-5: Polish & document (3.2-3.3)
```

---

## Success Criteria

- [ ] E2E test passes with zero false positives/negatives
- [ ] precompute-grammar produces non-empty output on fpsyg_index
- [ ] Known collocations found: "theory" + adj_modifier → "correct", "valid", etc.
- [ ] All grammar patterns in relations.json produce results

---

## Dependencies

- **Blockers:** None
- **Prerequisites:**
  - Test corpus exists at `src/test/resources/test-corpus.conllu`
  - GrammarConfigLoader parses relations.json correctly

---

## Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|-------------|
| Pattern compilation changes break existing queries | Medium | High | Add regression tests |
| Performance regression with BooleanQuery | Low | Medium | Profile after fix |
| Edge cases in position calculation | Medium | Medium | Extensive E2E testing |
