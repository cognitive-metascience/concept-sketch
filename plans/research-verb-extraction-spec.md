# Research Goal Verb Extraction & Hierarchy Building

**Date:** 2026-02-04
**Status:** Brainstorming / Light Spec

## Vision

Extract verbs used by researchers in scientific papers (`[word="we"] [tag="RB"]? [tag="VB.*"]`) and build a hierarchical structure of research aims/goals. The goal is to discover, from the corpus itself, what researchers declare as their goals through verb analysis.

## Two Complementary Approaches

### Approach A: GLiNER Zero-Shot Labeling (Simpler)

1. **Extract concordances** using CQL: `[word="we"] [tag="RB"]? [tag="VB.*"]`
2. **Run GLiNER** on context windows with labels: `["research_aim", "research_method", "research_claim", "research_result"]`
3. **Aggregate** by verb lemma → category counts
4. **Build hierarchy** from label structure + embedding clusters within labels

**Dependencies:** GLiNER, Lucene for concordance retrieval
**Output:** Labeled verb instances → hierarchical tree

### Approach B: Argument-Structure Profiling with logDice Thesaurus (More Reproducible)

Focus on extracting semantic relationships between verbs using corpus evidence directly.

#### Step 1: Extract Candidate Verbs

Query pattern:
```cql
[word="we"] [tag="RB"]? [tag="VB.*"]
```

This captures:
- `we aim to`, `we propose`, `we demonstrate` (aims)
- `we use`, `we employ`, `we apply` (methods)
- `we argue`, `we suggest`, `we claim` (claims)

#### Step 2: Build Argument Profiles per Verb

For each verb, extract its typical argument patterns using CQL:

| Pattern Type | CQL Template | Meaning |
|--------------|---------------|---------|
| Object | `[word="we"] [tag="RB"]? [lemma="%s"] [tag="NN.*"]` | What do they V? |
| Prepositional | `[word="we"] [tag="RB"]? [lemma="%s"] [tag="IN"] [tag="NN.*"]` | V with what? |
| Complement | `[word="we"] [tag="RB"]? [lemma="%s"] [tag="TO"]` | V to do what? |
| Adverbial | `[word="we"] [tag="RB"]? [lemma="%s"] [tag="RB"]` | How do they V? |

For each verb `V`:
1. Execute CQL patterns to get collocates
2. Score collocates with logDice: `log2(2 * f(V, collocate) / (f(V) + f(collocate))) + 14`
3. Store top-N collocates as the verb's "argument signature"

#### Step 3: Automatic Thesaurus via logDice Substitution

**Key insight:** If verbs A and B share similar argument profiles (same collocates with similar logDice), they are semantically similar and potentially substitutable.

Algorithm:
```
For each verb V:
  profile(V) = sorted list of (collocate, logDice) pairs

Similarity(V1, V2) = cosine_similarity(profile(V1), profile(V2))

Cluster verbs by similarity → semantic groups
```

This is exactly how Sketch Engine's "automatic thesaurus" works - verbs that appear in the same grammatical contexts with similar association scores are semantically related.

#### Step 4: Build Hierarchy from Clusters

```
Level 1: Broad categories (aim, method, claim, result) - via pattern matching
Level 2: Semantic clusters from logDice similarity
Level 3: Individual verbs within clusters
```

**To bootstrap Level 1 without manual labels:**
1. Start with small seed sets per category
2. Snowball: find verbs with similar logDice profiles to seeds
3. Grow iteratively, filtering by similarity threshold

## Implementation Mapping to This Codebase

### Lucene/CQL Components

| Component | File | Use For |
|----------|------|---------|
| CQL Parser | `grammar/CQLParser.java` | Parse verb patterns |
| CQL → Lucene | `query/CQLToLuceneCompiler.java` | Compile to SpanQueries |
| Query Executor | `query/HybridQueryExecutor.java` | Execute pattern queries |
| Concordance Explorer | `query/ConcordanceExplorer.java` | Get full contexts |
| logDice Calculator | `utils/LogDiceCalculator.java` | Score collocate associations |

### API Endpoints (New)

```
GET /api/research-verbs/extract
  ?pattern=[cql]
  &limit=1000
  → Returns verb lemmas with frequencies

GET /api/research-verbs/profile
  ?verb=demonstrate
  &pattern=[cql]
  → Returns argument profile with logDice scores

GET /api/research-verbs/similarity
  ?verb1=demonstrate&verb2=show
  → Returns similarity score

GET /api/research-verbs/thesaurus
  ?verb=demonstrate
  &top=20
  → Returns ranked list of similar verbs

GET /api/research-verbs/hierarchy
  ?seeds=aim,use,argue
  → Returns hierarchical structure
```

### Code Touchpoints

1. **New service:** `ResearchVerbExtractor.java`
   - Methods: `extractVerbs(pattern)`, `buildProfiles(verbs)`, `computeSimilarity(v1, v2)`

2. **New query mode:** Support for "we V" patterns
   - Extend `RelationType` or add custom pattern support

3. **Profile storage:** Reuse existing collocation data structures
   - `CollocationsReader` / `CollocationsBuilderV2` for fast lookups
   - Or compute on-the-fly with `HybridQueryExecutor`

## logDice Substitution Test (Concrete Example)

From a scientific corpus, you might find:

| Verb | Top Collocates (logDice) |
|------|--------------------------|
| **demonstrate** | that(12.1), we(11.8), how(9.3), effect(8.7), mechanism(8.2) |
| **show** | that(11.9), we(11.5), how(9.1), effect(8.4), mechanism(7.9) |
| **illustrate** | how(10.2), effect(7.8), mechanism(7.2), that(6.9), clearly(6.5) |

**Interpretation:** `demonstrate`, `show`, and `illustrate` have nearly identical profiles - they are semantically equivalent in scientific writing.

Contrast with:

| Verb | Top Collocates |
|------|----------------|
| **propose** | that(10.8), we(10.5), framework(9.2), model(8.9), approach(8.7) |
| **suggest** | that(11.2), we(9.8), may(9.1), could(8.6), explanation(7.4) |

`propose` and `suggest` cluster together (framework/model vs may/could) - slightly different epistemic stance.

## Challenges & Open Questions

1. **Polysemy:** "show" can be aim (`we show that X`) or method (`we show the method`)
   - Solution: Filter by pattern context, or cluster by position

2. **Low-frequency verbs:** Many research verbs are rare
   - Solution: Use precomputed collocations for common verbs, on-the-fly for rare

3. **Hierarchical depth:** How many levels?
   - Start shallow (3 levels), refine as data warrants

4. **GLiNER vs logDice:**
   - GLiNER: Faster to start, but depends on label design
   - logDice: More reproducible, corpus-native, but requires pattern engineering

## Next Steps

1. Prototype Step 1: Extract top-100 verbs with `[word="we"] [tag="VB.*"]`
2. Prototype Step 2: Build argument profiles for top-20 verbs
3. Prototype Step 3: Compute pairwise similarity, cluster
4. Compare: Does the logDice cluster make linguistic sense?

## Files to Modify/Create

```
src/main/java/pl/marcinmilkowski/word_sketch/
├── query/
│   └── ResearchVerbExtractor.java    [NEW]
├── api/
│   └── WordSketchApiServer.java      [MODIFY - add endpoints]
└── utils/
    └── LogDiceCalculator.java        [REUSE]
```

## References

- Sketch Engine automatic thesaurus: based on collocation substitution
- logDice formula: `log2(2 * f(A,B) / (f(A) + f(B))) + 14`
- GLiNER: bidirectional transformer for zero-shot entity labeling
