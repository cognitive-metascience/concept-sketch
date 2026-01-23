# Full Sketch Grammar Support: Design Doc

Date: 2026-01-20

## Goals
- Full CQL sketch grammar support: labeled positions, agreement rules, lemma substitution, alternation, repetition, AND/OR/NOT, distance ranges (including negative).
- Keep query execution fast on large corpora.
- Preserve existing index as much as possible; allow optional index upgrade for higher fidelity and speed.
- Provide clear separation between candidate retrieval (Lucene) and exact matching (verifier).

## Non-Goals
- Semantic role labeling or dependency parsing.
- Cross-document matching.
- UI/CLI changes beyond minimal wiring for new query path.

## Current Gaps (Observed)
- Labeled positions incorrectly compiled as absolute positions.
- Distance ranges collapsed into a single slop; negative distances ignored.
- Repetition `{m,n}` ignored (uses only `n`).
- OR/AND constraints not enforced at same token; cross-field constraints downgraded.
- Negation not token-scoped.
- Agreement rules parsed but not enforced.
- Lemma substitution parsed but not enforced.
- Alternatives parsed but not executed in the main query path.

## Proposed Architecture

### 1) Two-Phase Execution (Hybrid)
**Phase A: Candidate Retrieval (Lucene)**
- Compile a permissive SpanQuery that guarantees a superset of true matches.
- Use max distances, drop agreement and lemma-substitution constraints.
- Keep field-specific constraints as filter hints only (e.g., tag/lemma/word terms).

**Phase B: Exact Verification (Verifier)**
- Run a fast token-level matcher over candidate windows.
- Enforce:
  - Labeled positions
  - Min/max distances (including negative order)
  - Repetition `{m,n}`
  - AND/OR/NOT at the same token position
  - Agreement rules
  - Lemma substitution
  - Alternatives

### 2) Token Data Access
Two options; the design supports both:

**A. Use stored fields + sentence window**
- Read token attributes (`lemma`, `word`, `tag`, `pos_group`, `position`, `sentence`).
- Build sentence token arrays on demand.

**B. Term vectors / payloads (optional upgrade)**
- Store term vectors with positions + payloads for token attributes.
- Faster window extraction and less stored-field I/O.

### 3) Pattern Compilation
- Parse CQL to an AST (existing `CQLParser` + minor fixes).
- Compile to an internal matcher representation:
  - Sequence of `TokenPredicate`s with distance constraints.
  - Capturing groups for labeled positions.
  - Constraints (AND/OR/NOT) tied to the same token index.
  - Agreement rules referencing captures.
  - Lemma substitution referencing captures.
  - Alternation as disjunction of compiled sequences.

### 4) Verifier Engine
- NFA-like matching over a window of tokens with backtracking bounded by max distances and repetition limits.
- Capture map for labeled positions.
- Early pruning using per-token predicate checks.
- Optional reuse of Morfologik:
  - Not required. A custom matcher is likely simpler and faster for this use case.
  - If desired, abstract Morfologik’s FSA traversal to operate over token predicates instead of characters.

### 5) Lucene Candidate Query Strategy
- For each pattern element, build a SpanQuery over the most selective field (tag/lemma/word).
- Use SpanNearQuery with slop = sum(maxDistance) and inOrder based on directionality.
- For alternation, use SpanOrQuery of per-alternative candidate queries.
- If no selective constraint exists, fallback to a match-all token query with a warning.

### 6) Correctness & Performance
- Correctness ensured by verifier.
- Performance ensured by:
  - Efficient candidate query (high selectivity).
  - Window-bounded verifier and early pruning.
  - Caching token windows per sentence.

## Data Flow
1. Parse CQL -> AST (ParsedCQL).
2. Build candidate SpanQuery (superset).
3. Lucene search -> candidate hits with doc IDs / positions.
4. Reconstruct window around hit.
5. Run verifier; discard non-matches.
6. Return concordances / collocates.

## Risk & Mitigation
- **Risk:** Candidate query too broad => slow verification.
  - Mitigation: prefer tag/lemma constraints; consider index upgrade for stacked field or payloads.
- **Risk:** Complex repetition/distance causes exponential backtracking.
  - Mitigation: bound with max distances; implement iterative deepening and pruning.
- **Risk:** Cross-field same-position constraints hard in Lucene.
  - Mitigation: enforce in verifier; candidate query only uses strongest field.

## Compatibility
- Existing query behavior preserved for basic patterns.
- Full support enabled via a new “exact” execution path.

## Key Files (for implementation)
- [src/main/java/pl/marcinmilkowski/word_sketch/grammar/CQLParser.java](../src/main/java/pl/marcinmilkowski/word_sketch/grammar/CQLParser.java)
- [src/main/java/pl/marcinmilkowski/word_sketch/grammar/CQLPattern.java](../src/main/java/pl/marcinmilkowski/word_sketch/grammar/CQLPattern.java)
- [src/main/java/pl/marcinmilkowski/word_sketch/query/CQLToLuceneCompiler.java](../src/main/java/pl/marcinmilkowski/word_sketch/query/CQLToLuceneCompiler.java)
- [src/main/java/pl/marcinmilkowski/word_sketch/query/WordSketchQueryExecutor.java](../src/main/java/pl/marcinmilkowski/word_sketch/query/WordSketchQueryExecutor.java)
