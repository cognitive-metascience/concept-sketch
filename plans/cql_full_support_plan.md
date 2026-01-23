# Full Sketch Grammar Support: Implementation Plan

Date: 2026-01-20

## Phase 0 — Baseline Fixes (Quick Wins)
1. **Use `parseFull`/`compileFull` in executor**
   - Update query execution to use alternatives and agreement rules.
   - Files: [src/main/java/pl/marcinmilkowski/word_sketch/query/WordSketchQueryExecutor.java](../src/main/java/pl/marcinmilkowski/word_sketch/query/WordSketchQueryExecutor.java)

2. **Correct labeled positions semantics**
   - Stop using `SpanFirstQuery` for labeled positions.
   - Treat labels as captures for verifier only.
   - Files: [src/main/java/pl/marcinmilkowski/word_sketch/query/CQLToLuceneCompiler.java](../src/main/java/pl/marcinmilkowski/word_sketch/query/CQLToLuceneCompiler.java)

3. **Fix repetition parsing and storage**
   - Ensure `{m,n}` is represented in `PatternElement` consistently.
   - Files: [src/main/java/pl/marcinmilkowski/word_sketch/grammar/CQLParser.java](../src/main/java/pl/marcinmilkowski/word_sketch/grammar/CQLParser.java)

## Phase 1 — Candidate Query Compiler
4. **Create `CandidateQueryBuilder`**
   - Build permissive SpanQuery for the pattern.
   - Use only max distances; ignore agreement and lemma substitution.
   - Use the most selective constraint per element (tag/lemma/word).
   - Files: new `src/main/java/pl/marcinmilkowski/word_sketch/query/CandidateQueryBuilder.java`

5. **Handle alternation**
   - Build SpanOrQuery over candidate queries from each alternative.
   - Files: `CandidateQueryBuilder`, `CQLToLuceneCompiler` or new compilation path.

## Phase 2 — Verifier Engine
6. **Introduce token model**
   - `Token` with fields: `lemma`, `word`, `tag`, `posGroup`, `position`, `sentenceId`.
   - `TokenWindow` for sentence-level arrays.
   - Files: new `src/main/java/pl/marcinmilkowski/word_sketch/query/Token.java` and `TokenWindow.java`.

7. **Implement `TokenPredicate`**
   - AND/OR/NOT logic at single token position.
   - Regex/pattern matching.
   - Files: new `TokenPredicate` classes.

8. **Compile CQL to internal matcher**
   - Convert `PatternElement` to `TokenStep` with distance and repetition.
   - Capture labeled positions.
   - Files: new `CQLMatcherCompiler.java`.

9. **Implement verifier (NFA-style)**
   - Iterative matching with backtracking bounded by distance and repetition.
   - Capture map for labels.
   - Apply agreement rules and lemma substitution.
   - Files: new `CQLVerifier.java`.

10. **Integrate in executor**
   - For each candidate hit, load sentence window and verify.
   - Emit only exact matches.
   - Files: [src/main/java/pl/marcinmilkowski/word_sketch/query/WordSketchQueryExecutor.java](../src/main/java/pl/marcinmilkowski/word_sketch/query/WordSketchQueryExecutor.java)

## Phase 3 — Performance & Optional Index Upgrade
11. **Token window cache**
   - LRU cache by sentence ID to avoid repeated stored-field reads.
   - Files: new `TokenWindowCache.java`.

12. **Optional: term vectors / payloads**
   - Add payloads for token attributes to reduce stored-field access.
   - If adopted, implement a `TokenWindowProvider` that reads from term vectors.

## Phase 4 — Tests & Validation
13. **Unit tests for parser + verifier**
   - Labeled positions, agreements, lemma substitution, alternation, repetition.
   - Files: new tests under [src/test/java](../src/test/java).

14. **End-to-end tests with sketchgrammar.wsdef.m4 patterns**
   - Ensure parity with expected outputs.

## Phase 5 — Documentation
15. **Update support matrix**
   - Reflect full support or explicit gaps.
   - Files: [GRAMMAR_SUPPORT.md](../GRAMMAR_SUPPORT.md)

## Optional: Morfologik Integration
- If desired, implement a small abstraction for FSA traversal over token predicates.
- Keep the verifier interface unchanged; swap in an FSA-backed matcher.

## Deliverables
- Candidate query builder + verifier.
- Full CQL feature support (labels, agreement, substitution, repetition, alternation).
- Tests and docs updates.
