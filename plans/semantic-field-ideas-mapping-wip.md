# WIP: Semantic-Field Ideas → Query Pipeline Mapping

**Status:** Work in progress. This document maps proposed semantic-field ideas onto the existing query pipeline in this repo. It is a design mapping only — no implementation claims.

## Current Pipeline Summary (as implemented)

**Core pipeline (query → scoring → response):**

```
User input
  → CQL pattern (if needed)
  → QueryExecutor (HybridQueryExecutor or WordSketchQueryExecutor)
  → logDice scoring (WordSketchQueryExecutor + LogDiceCalculator)
  → JSON response (WordSketchApiServer)
  → Web UI (webapp/index.html)
```

**Grammatical relations (QueryExecutor.RelationType):**

- `ADJ_PREDICATE`: X is ADJ
  - Simple: `[tag=jj.*]`
  - Full: `[lemma="%s"] [tag="VB.*"] [tag="JJ.*"]`
- `ADJ_MODIFIER`: ADJ X
  - Simple: `[tag=jj.*]`
  - Full: `[lemma="%s"] [tag="JJ.*"]`
- `SUBJECT_OF`: X VERBs
  - Simple: `[tag=vb.*]`
  - Full: `[lemma="%s"] [tag="VB.*"]`
- `OBJECT_OF`: VERB X
  - Simple: `[tag=vb.*]`
  - Full: `[tag="VB.*"] [lemma="%s"]`

**Semantic-field exploration:**

- Single-seed bootstrap: `SemanticFieldExplorer.exploreByRelation()`
- Multi-seed exploration (collocate union + intersection): `WordSketchApiServer.handleSemanticFieldExploreMulti()`
- Comparison mode (shared vs specific adjective profiles): `SemanticFieldExplorer.compare()`

**Snowball exploration (iterative graph growth):**

- `SnowballCollocations` explores adjective↔noun relations with CQL patterns such as:
  - Attributive: `[tag="JJ.*"]~{0,3}` / `[tag="NN.*"]~{0,3}`
  - Linking verbs: `[word="be|remain|seem|appear|feel|get|become|look|smell|taste"] [tag="JJ.*"]`
  - Hybrid fallback in code: `[word="be|is|are|was|were|been|being|remain|remains|seem|seems"] [tag="JJ.*"]`

**Relevant API and UI touchpoints:**

- API: `WordSketchApiServer` handlers
  - `/api/semantic-field/explore`
  - `/api/semantic-field/explore-multi`
  - `/api/semantic-field` (comparison)
  - `/api/semantic-field/examples` (adjective–noun examples)
  - `/api/concordance/examples` (word-pair concordance)
- Web UI: `webapp/index.html`
  - Modes: Explore / Multi-Seed Explore / Compare
  - Functions referenced in UI: `runExplore()`, `runMultiExplore()`, `runSemanticField()`

---

## Idea 1: FCA lattice over nouns × collocates across relations

**Data needed**
- Seed nouns and their collocates per relation (`ADJ_PREDICATE`, `ADJ_MODIFIER`, `SUBJECT_OF`, `OBJECT_OF`).
- Optional union of multiple relations (attribute set per noun = collocates + relation label).

**Algorithmic steps (mapping to current pipeline)**
1. For each noun, use `QueryExecutor.findGrammaticalRelation()` to retrieve collocates per relation.
2. Construct a formal context: objects = nouns, attributes = (relation, collocate) pairs.
3. Build FCA lattice over the context (outside current pipeline; proposed integration point).

**Code touchpoints**
- `QueryExecutor.RelationType` (relation definitions)
- `SemanticFieldExplorer.exploreByRelation()` (single seed) for data collection
- `WordSketchApiServer.handleSemanticFieldExplore()` (API wiring)
- `webapp/index.html` (add FCA view if UI needed)

**CQL patterns**
- Relation full patterns (see summary). If only precomputed simple patterns are used: `[tag=jj.*]`, `[tag=vb.*]`.

**Scoring hooks**
- logDice per (noun, collocate)
- Optional lattice weights = aggregate logDice across attributes

**Expected output artifacts**
- FCA concept lattice: nodes (concepts), edges (subsumption), node intents (attributes), extents (nouns)
- API payload with concept nodes + adjacency for visualization

---

## Idea 2: Opposition layer via contrastive patterns (X vs Y, not X but Y, either X or Y, rather than)

**Data needed**
- Concordance contexts where contrastive constructions occur
- Lemma pairs (X, Y) extracted from contexts

**Algorithmic steps (mapping to current pipeline)**
1. Execute CQL queries targeting contrastive patterns to retrieve concordances.
2. Extract X/Y lemma pairs from the matching windows (post-processing step).
3. Store contrast edges in a dedicated “opposition” layer for semantic-field graph.

**Code touchpoints**
- `QueryExecutor.executeQuery()` for concordance retrieval
- `ConcordanceExplorer` (via `/api/concordance/examples`)
- `WordSketchApiServer.handleConcordanceExamples()` for API access
- `webapp/index.html` (new layer toggles or overlays)

**CQL patterns (candidate shapes)**
```cql
[lemma="%s"] [word="vs|versus"] [lemma="%s"]
[word="not"] [lemma="%s"] [word="but"] [lemma="%s"]
[word="either"] [lemma="%s"] [word="or"] [lemma="%s"]
[word="rather"] [word="than"] [lemma="%s"]
```
*(Pattern shapes only; exact field constraints should match indexed fields.)*

**Scoring hooks**
- logDice (for frequency-based association of X–Y)
- Contrast strength = frequency of contrast patterns (from concordance counts)

**Expected output artifacts**
- Contrastive edge list: `{source: X, target: Y, type: "contrast"}`
- Optional per-pattern counts and example concordances

---

## Idea 3: Quality-dimension semantic axes from adjective pairs; conceptual spaces projection

**Data needed**
- Adjective pairs (e.g., {big, small}) and nouns that co-occur with each adjective
- Adjective–noun logDice scores

**Algorithmic steps (mapping to current pipeline)**
1. For each axis pair (A, B), get noun profiles via `findCollocations()` with `[tag="NN.*"]~{0,3}`.
2. Project nouns onto the axis by comparing logDice(A, noun) vs logDice(B, noun).
3. Aggregate across axes to create conceptual-space coordinates (outside current pipeline).

**Code touchpoints**
- `SnowballCollocations.findNounsForAdjectives()` (adjective→noun mapping)
- `WordSketchApiServer.handleQuery()` for ad-hoc pattern usage
- `SemanticFieldExplorer.compare()` if nouns are preselected
- `webapp/index.html` (axis visualization / projection view)

**CQL patterns**
- Adjective-to-noun (attributive): `[tag="NN.*"]~{0,3}`
- Noun-to-adjective (reverse): `[tag="JJ.*"]~{0,3}`

**Scoring hooks**
- logDice per (adjective, noun)
- Axis score: `logDice(A, noun) - logDice(B, noun)`

**Expected output artifacts**
- Axis projection table: noun → (axis scores)
- Optional 2D/3D coordinates for UI plotting

---

## Idea 4: Predicate-argument signature profiling using SUBJECT_OF / OBJECT_OF roles

**Data needed**
- Verb collocates for each noun via `SUBJECT_OF` and `OBJECT_OF`

**Algorithmic steps (mapping to current pipeline)**
1. For each noun, retrieve verbs where the noun is subject/object (`RelationType.SUBJECT_OF`, `RelationType.OBJECT_OF`).
2. Build two distributions: subject-verb profile and object-verb profile.
3. Compare or cluster nouns by their predicate-argument signatures.

**Code touchpoints**
- `QueryExecutor.RelationType.SUBJECT_OF` / `OBJECT_OF`
- `SemanticFieldExplorer.exploreByRelation()` (already relation-driven)
- `WordSketchApiServer.handleSemanticFieldExplore()` (relation parameter)

**CQL patterns**
- Subject: `[lemma="%s"] [tag="VB.*"]`
- Object: `[tag="VB.*"] [lemma="%s"]`

**Scoring hooks**
- logDice per verb role
- Role signature vector (subject vs object)

**Expected output artifacts**
- Role-based profile JSON: `{noun, subject_verbs[], object_verbs[]}`
- Signature similarity matrix or clustering result

---

## Idea 5: Sense-split fields via context clustering (WSD-lite) from concordance contexts

**Data needed**
- Concordance examples for (seed, collocate) pairs
- Token-level context windows from concordance retrieval

**Algorithmic steps (mapping to current pipeline)**
1. Use `/api/concordance/examples` or `SemanticFieldExplorer.fetchExamples()` to collect contexts.
2. Cluster contexts by similarity (outside current pipeline).
3. Split semantic field results by cluster (sense proxy).

**Code touchpoints**
- `ConcordanceExplorer` via `/api/concordance/examples`
- `SemanticFieldExplorer.fetchExamples()` (CQL-based example retrieval)
- `webapp/index.html` (examples panel + potential cluster UI)

**CQL patterns**
- Example query from `fetchExamples()`:
  ```cql
  [lemma="%s" & tag="JJ.*"] []{0,3} [lemma="%s" & tag="N.*"]
  ```

**Scoring hooks**
- logDice for collocation strength
- Cluster size / cohesion metrics (external to current pipeline)

**Expected output artifacts**
- Clustered context sets per collocate
- Sense-tagged semantic-field edges: `{edge, sense_cluster}`

---

## Idea 6: Knowledge-graph seeding with lexical resources (syn/ant/hypernym) and constrained expansion

**Data needed**
- External lexical resource mapping (synonyms/antonyms/hypernyms)
- Seeds + allowed expansion constraints

**Algorithmic steps (mapping to current pipeline)**
1. Accept a seed list from a lexical resource (outside pipeline).
2. For each seed, retrieve collocates using existing relations and thresholds.
3. Constrain expansion to only nodes in lexical resource or via whitelist/blacklist.

**Code touchpoints**
- `WordSketchApiServer.handleSemanticFieldExploreMulti()` for multi-seed inputs
- `SemanticFieldExplorer.exploreByRelation()` for expansion logic
- `SnowballCollocations` if recursive expansion is desired
- `webapp/index.html` (seed input + constraint options)

**CQL patterns**
- Same relation patterns as current semantic-field pipeline
- Optional custom CQL via `/api/sketch/query` to filter candidate collocates

**Scoring hooks**
- logDice per edge
- Optional lexical-resource rank or weight (external)

**Expected output artifacts**
- Constrained semantic-field graph (nodes from lexical resource)
- Expansion trace metadata (seed source, constraint rules)

---

## Idea 7: Bipartite concept–property graph + community detection

**Data needed**
- Concept nodes: nouns (seeds + discovered)
- Property nodes: collocates (adjectives/verbs) with relation labels

**Algorithmic steps (mapping to current pipeline)**
1. Use `exploreByRelation()` or multi-seed exploration to generate noun↔collocate edges.
2. Build bipartite graph with edge weights = logDice.
3. Run community detection externally; feed clusters back into UI.

**Code touchpoints**
- `SemanticFieldExplorer.getEdges()` (seed + discovered edges)
- `WordSketchApiServer.handleSemanticFieldExplore()` / `handleSemanticFieldExploreMulti()`
- `webapp/index.html` (graph visualization)

**CQL patterns**
- Relation-specific patterns (see summary)

**Scoring hooks**
- logDice as edge weight
- Similarity score used in `SemanticFieldExplorer` (sharedCount × avgLogDice)

**Expected output artifacts**
- Bipartite graph JSON (nodes + weighted edges)
- Community labels per node (post-processed)

---

## Idea 8: Contrastive multi-seed exploration (shared vs distinctive collocates)

**Data needed**
- Multiple seed nouns
- Per-seed collocate lists with logDice scores

**Algorithmic steps (mapping to current pipeline)**
1. Use `/api/semantic-field/explore-multi` to collect collocates per seed.
2. Compute intersection (shared) and seed-specific (distinctive) collocates.
3. Rank distinctive collocates by logDice or presence count.

**Code touchpoints**
- `WordSketchApiServer.handleSemanticFieldExploreMulti()` (current union + intersection)
- `SemanticFieldExplorer.compare()` for graded shared/specific analysis
- `webapp/index.html` (multi-seed mode + UI rendering)

**CQL patterns**
- Relation patterns as in `RelationType`

**Scoring hooks**
- logDice per seed–collocate
- Commonality / distinctiveness scores (as in `SemanticFieldExplorer.compare()`)

**Expected output artifacts**
- Shared collocates list
- Distinctive collocates per seed
- Edge list annotated with “shared” vs “distinctive”

---

## Idea 9: Semiotic operators / logic of terms (genus–differentia, part–whole, function–goal) via CQL patterns

**Data needed**
- Target relation types expressed as CQL patterns
- Seed nouns or noun pairs

**Algorithmic steps (mapping to current pipeline)**
1. Define CQL patterns representing semantic operators (e.g., part–whole constructions).
2. Execute with `/api/sketch/query` or `QueryExecutor.executeQuery()`.
3. Extract head–modifier or verb–object pairs as semantic operator edges.

**Code touchpoints**
- `WordSketchApiServer.handleQuery()` (custom CQL)
- `QueryExecutor.executeQuery()` (concordance)
- `CQLParser` / `CQLToLuceneCompiler` (pattern support)

**CQL patterns (candidate shapes)**
```cql
[lemma="%s"] [word="part|member|component"] [word="of"] [lemma="%s"]
[lemma="%s"] [word="kind|type|sort"] [word="of"] [lemma="%s"]
[lemma="%s"] [word="for|to"] [tag="VB.*"]
```
*(Pattern shapes only; exact forms should align with indexed fields.)*

**Scoring hooks**
- logDice for pair strength
- Frequency of operator pattern occurrences

**Expected output artifacts**
- Operator-typed edge list: `{source, target, operator_type}`
- Optional concordance examples per operator

---

## Idea 10: Coherence-gated snowballing using sense prototypes or logical constraints

**Data needed**
- Seed adjectives or nouns
- Prototype constraints (e.g., whitelist of collocates or relation filters)

**Algorithmic steps (mapping to current pipeline)**
1. Run `SnowballCollocations` iterations to discover adjective–noun edges.
2. Apply coherence gate after each iteration (e.g., keep nodes that meet a prototype constraint).
3. Continue expansion only for gated nodes.

**Code touchpoints**
- `SnowballCollocations.exploreAsPredicates()` / `exploreLinkingVerbPredicates()`
- `SemanticFieldExplorer` if gating uses relation-specific profiles
- `webapp/index.html` (snowball controls, if exposed)

**CQL patterns**
- Attributive: `[tag="JJ.*"]~{0,3}` and `[tag="NN.*"]~{0,3}`
- Linking verbs: `[word="be|remain|seem|appear|feel|get|become|look|smell|taste"] [tag="JJ.*"]`

**Scoring hooks**
- logDice (edge weight)
- Gate criteria = min logDice, min shared collocates, or custom logical constraints

**Expected output artifacts**
- Snowball graph with “kept” vs “filtered” nodes per iteration
- Iteration trace summary (nodes/edges added, removed)

---

## Notes / Boundaries (WIP)

- The mappings above only use code paths and patterns currently present in the repo.
- Any new lattice/community/clustering logic is **outside the current pipeline** and should be treated as an extension layer.
- CQL pattern shapes marked as “candidate” are placeholders for design discussion and require validation against the existing CQL parser/compiler.
