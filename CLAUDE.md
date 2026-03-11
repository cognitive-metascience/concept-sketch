# CLAUDE.md

This file documents the ConceptSketch project for future development.

## Current Status

✅ **v1.5 Functional Release**

### What Works
- Corpus indexing with CoNLL-U format (POS tagging via UDPipe)
- Fast precomputed collocation lookups (O(1), ~0-1ms)
- CQL pattern matching with SpanQueries
- logDice scoring for collocation ranking
- 4 grammatical relations: ADJ_PREDICATE, ADJ_MODIFIER, SUBJECT_OF, OBJECT_OF
- REST API with semantic field exploration
- Web UI with D3.js force-directed graphs
- **Single-seed and multi-seed semantic field exploration**

### Limitations (See README.md)
- ❌ No agreement rules (no noun-adjective gender/number matching)
- ❌ Only 4 grammatical relations
- ❌ Limited morphological analysis
- ⚠️ Depends on input corpus quality

---

## Build & Deployment

### Prerequisites
```bash
Java 21+
Maven 3.6+
Python 3 (for web server)
UDPipe 2 (optional, for corpus tagging)
```

### Build
```bash
mvn clean package
```

### Deploy (3 services)
```bash
# Terminal 1: API Server
java -jar target/concept-sketch-1.5.0-shaded.jar server --index d:\corpus_74m\index-hybrid --port 8080

# Terminal 2: Web UI (static server)
python -m http.server 3000 --directory webapp

# Terminal 3: Browser
# Open http://localhost:3000
```

### CLI Commands

See `src/main/java/pl/marcinmilkowski/word_sketch/Main.java`:

```bash
java -jar concept-sketch.jar blacklab-index --input corpus.conllu --output data/index/
java -jar concept-sketch.jar blacklab-query --index data/index/ --lemma house
java -jar concept-sketch.jar server --index data/index/ --port 8080
```

---

## API Endpoints

### REST API (Port 8080)

| Endpoint | Purpose |
|----------|---------|
| `GET /health` | Server health |
| `GET /api/sketch/{lemma}` | Full word sketch (all surface relations) |
| `GET /api/sketch/{lemma}/{relation}` | Specific surface grammatical relation |
| `GET /api/sketch/{lemma}/dep` | Full dependency sketch |
| `GET /api/sketch/{lemma}/dep/{deprel}` | Specific dependency relation |
| `GET /api/relations` | List available surface relations |
| `GET /api/relations/dep` | List available dependency relations |
| `GET /api/semantic-field/explore` | Single-seed semantic field exploration |
| `GET /api/semantic-field/explore-multi` | Multi-seed semantic field exploration |
| `GET /api/semantic-field` | Semantic field comparison across nouns |
| `GET /api/semantic-field/examples` | Concordance examples for adjective-noun pair |
| `GET /api/concordance/examples` | Concordance examples for a word pair |
| `POST /api/visual/radial` | Render radial plot (SVG) |
| `POST /api/bcql` | Execute arbitrary BCQL query |

### Multi-Seed Endpoint

```bash
curl "http://localhost:8080/api/semantic-field/explore-multi?seeds=theory,model,hypothesis&relation=adj_predicate&top=10"
```

Requires:
- `seeds`: Comma-separated nouns (min 2)
- `relation`: adj_predicate, adj_modifier, subject_of, object_of
- `top`, `min_logdice`, `min_shared`: Optional parameters

---

## Code Structure

```
src/main/java/pl/marcinmilkowski/word_sketch/
├── Main.java                           # CLI entry point
├── api/
│   ├── WordSketchApiServer.java        # REST API server (14 endpoints)
│   ├── ConcordanceHandlers.java        # Handlers for concordance/examples endpoints
│   ├── CorpusQueryHandlers.java        # Handler for BCQL corpus query endpoint
│   ├── ExplorationHandlers.java        # Handlers for semantic field exploration endpoints
│   ├── ExploreResponseAssembler.java   # Builds JSON response maps for exploration results
│   ├── GrammarConfigSerializer.java    # Serializes GrammarConfig/RelationConfig to JSON
│   ├── HttpApiUtils.java               # HTTP utilities: sendJsonResponse, readBodyWithSizeLimit, parseQueryParams
│   ├── RequestEntityTooLargeException.java  # RuntimeException for HTTP 413 responses
│   ├── SketchHandlers.java             # Handlers for word sketch endpoints
│   └── VisualizationHandlers.java      # Handler for radial plot endpoint
├── config/
│   ├── GrammarConfig.java              # Immutable grammar configuration (relations, version)
│   ├── GrammarConfigLoader.java        # Loads grammar config from JSON; throws IAE for invalid config
│   ├── RelationConfig.java             # Single relation: pattern, deprel derivation
│   ├── RelationPatternBuilder.java     # Builds CQL patterns for relations
│   └── RelationUtils.java              # Utility: relation type checks
├── exploration/
│   ├── CollocateProfileComparator.java # Compares adjective profiles across seed nouns
│   ├── MultiSeedExplorer.java          # Multi-seed semantic field exploration
│   └── SemanticFieldExplorer.java      # Coordination facade for SEF (single + multi seed)
├── indexer/
│   └── blacklab/
│       ├── BlackLabConllUIndexer.java  # CoNLL-U corpus indexer for BlackLab
│       └── ConlluConverter.java        # Converts CoNLL-U to WPL chunk format
├── model/
│   ├── AdjectiveProfile.java           # Adjective collocate profile for SEF comparison
│   ├── ComparisonResult.java           # Result DTO for compareCollocateProfiles()
│   ├── CoreCollocate.java              # High-coverage shared collocate
│   ├── DiscoveredNoun.java             # Noun discovered via shared adjectives
│   ├── Edge.java                       # Graph edge for D3.js visualization
│   ├── ExplorationOptions.java         # Base options for SEF exploration
│   ├── ExplorationResult.java          # Top-level result DTO for SEF exploration
│   ├── FetchExamplesOptions.java       # Options for fetchExamples
│   ├── PosGroup.java                   # POS group enum: NOUN, VERB, ADJ, ADV, OTHER
│   ├── QueryResults.java               # Result DTOs: WordSketchResult, ConcordanceResult
│   ├── RelationEdgeType.java           # Enum for edge types in exploration graphs
│   ├── RelationType.java               # Enum: SURFACE | DEP_GRAMMAR
│   ├── SharingCategory.java            # Enum: FULLY_SHARED, PARTIALLY_SHARED, SPECIFIC
│   ├── SingleSeedExplorationOptions.java  # Options for single-seed exploration
│   └── package-info.java               # Package documentation
├── query/
│   ├── BlackLabQueryExecutor.java      # BlackLab-backed query executor
│   ├── BlackLabSnippetParser.java      # Parses BlackLab XML snippets
│   ├── CollocateQueryHelper.java       # Low-level collocate frequency/example lookup
│   └── QueryExecutor.java             # Query executor interface
├── utils/
│   ├── CqlUtils.java                   # CQL parsing: splitCqlTokens, escapeForRegex
│   ├── LogDiceUtils.java               # logDice scoring
│   └── MathUtils.java                  # Math utilities: round2dp
└── viz/
    └── RadialPlot.java                 # Radial plot data builder
```

---

## Key Implementation Details

### Grammatical Relations

| Relation | Pattern | Example |
|----------|---------|---------|
| ADJ_PREDICATE | X is ADJ | "theory is correct" |
| ADJ_MODIFIER | ADJ X | "correct theory" |
| SUBJECT_OF | X VERBs | "theory suggests" |
| OBJECT_OF | VERB X | "develop theory" |

### Multi-Seed Exploration (SemanticFieldExplorer.java)

1. For each seed, find collocates using specified relation
2. Calculate intersection (common collocates)
3. Return all edges with sources/targets for visualization

### Precomputed Collocations

- Built during `mvn package` automatically
- File: `collocations.bin` (700MB for 74M sentences)
- Format: Spill-to-disk single-pass algorithm
- Performance: O(1) lookup, ~0-1ms per query

### logDice Scoring

```
logDice = log₂(2 * f(A,B) / (f(A) + f(B))) + 14
```

Range: 0-14 (14 = perfect association)

---

## Testing

```bash
# All tests
mvn test

# Specific test class
mvn test -Dtest=CQLParserTest

# With output
mvn test -X
```

Test coverage:
- CQL parsing (50+ patterns)
- Lucene query compilation
- logDice calculation
- API endpoints
- Multi-seed exploration

---

## Development Notes

### Common Debugging

**Index location:** `d:\corpus_74m\index-hybrid/`
- `segments.gen`, `segments_*` - Lucene index
- `stats.bin` - Frequency statistics
- `collocations.bin` - Precomputed collocations
- `lexicon.bin` - Lemma-to-ID mapping (fallback)

**Server logs:** Run with `--debug` or check console output

**API testing:** Use curl or Postman

### Performance Tuning

- Max heap: `-Xmx2g` in MAVEN_OPTS for large builds
- Worker threads: `NUM_WORKERS` in CollocationsBuilderV2

---

## For v2.0

Planned improvements:
- ✅ Multi-seed exploration
- ❌ Agreement rules (noun-adj matching)
- ❌ Additional relations (possessive, comparative)
- ❌ Morphological decomposition
- ❌ Word sense disambiguation

---

## Documentation

- **README.md** - User guide (indexing, querying, API, examples)
- **MULTI_SEED_EXPLORATION.md** - Multi-seed feature details
- **plans/concept-sketch-spec.md** - Technical specification (comprehensive architecture and implementation details)
Main Corpus Index          Word Sketch Index
- sentence_id              - sentence_id
- position                 - position
- word                     - word
- lemma                    - lemma (indexed)
- (NO tag)                 - tag (indexed)
- sentence                 - sentence
                           - pos_group
Fast KWIC/concordance      Fast pattern matching
```

The **Main Corpus Index** is for keyword search (5-20ms queries). The **Word Sketch Index** stores POS tags for pattern matching (50-200ms queries).

### Index Schema

| Field | Type | Purpose |
|-------|------|---------|
| `doc_id` | Numeric, stored | Sentence ID for example retrieval |
| `position` | Numeric, stored | Word position in sentence |
| `word` | Stored, not tokenized | Raw word form for display |
| `lemma` | Analyzed, indexed | Lemmatized form for search |
| `tag` | Keyword, indexed | POS tag (e.g., "NN", "VBD") |
| `pos_group` | Keyword, indexed | Broad category: noun, verb, adj, adv |
| `sentence` | Stored | Full sentence for KWIC display |
| `start_offset`, `end_offset` | Numeric, stored | Character offsets for highlighting |

### CQL to Lucene Translation

| CQL Construct | Example | Lucene Equivalent |
|--------------|---------|-------------------|
| Labeled position | `1:"N.*"` | `SpanFirstQuery(TermQuery(tag="N.*"), 0)` |
| Constraint | `[tag="adj"]` | `TermQuery(tag=re.compile("adj.*"))` |
| Negation | `[tag!="N.*"]` | `BooleanQuery(MUST_NOT + ...)` |
| Distance `{min,max}` | `{0,2}` | `SpanNearQuery(..., slop=max, inOrder=...)` |
| Sequence | `A B C` | `SpanNearQuery([A, B, C], slop=0, inOrder=true)` |

### logDice Scoring Formula

```
logDice = log2(2 * f(AB) / (f(A) + f(B))) + 14
```

Where `f(AB)` = collocate frequency, `f(A)` = headword frequency, `f(B)` = collocate total frequency.

## Key Components

- **[Main.java](src/main/java/pl/marcinmilkowski/word_sketch/Main.java)**: CLI entry point
- **[api/WordSketchApiServer.java](src/main/java/pl/marcinmilkowski/word_sketch/api/WordSketchApiServer.java)**: REST API server (14 endpoints)
- **[api/ExplorationHandlers.java](src/main/java/pl/marcinmilkowski/word_sketch/api/ExplorationHandlers.java)**: Semantic field exploration endpoints
- **[api/SketchHandlers.java](src/main/java/pl/marcinmilkowski/word_sketch/api/SketchHandlers.java)**: Word sketch, concordance, and radial endpoints
- **[config/GrammarConfigLoader.java](src/main/java/pl/marcinmilkowski/word_sketch/config/GrammarConfigLoader.java)**: Grammar config loading; throws IAE for invalid config
- **[exploration/SemanticFieldExplorer.java](src/main/java/pl/marcinmilkowski/word_sketch/exploration/SemanticFieldExplorer.java)**: Single-seed and multi-seed SEF coordination
- **[query/BlackLabQueryExecutor.java](src/main/java/pl/marcinmilkowski/word_sketch/query/BlackLabQueryExecutor.java)**: BlackLab-backed query executor with logDice scoring
- **[utils/LogDiceUtils.java](src/main/java/pl/marcinmilkowski/word_sketch/utils/LogDiceUtils.java)**: logDice scoring formula
- **[utils/CqlUtils.java](src/main/java/pl/marcinmilkowski/word_sketch/utils/CqlUtils.java)**: CQL token parsing and regex escaping
- **[grammars/](grammars/)**: Grammar definition files in JSON and m4 macro format

## POS Tagging Pipeline

**Recommended: UDPipe 2** - Fast, supports 50+ languages, outputs CoNLL-U format.

## Dependencies

- Java 21+ (Lucene 10.3.2 requires Java 21+)
- Apache Lucene 10.3.2
- Fastjson 2.0.25
- SLF4J 2.0.7
- JUnit 5.9.0
