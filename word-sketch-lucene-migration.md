# Word Sketch Lucene Migration Plan

## Overview

Port the word-sketch system from the standalone Python implementation (`/mnt/d/git/word-sketch`) to Lucene for faster pattern matching on the 74M sentence corpus.

## Current System Analysis

### Original word-sketch (`/mnt/d/git/word-sketch`)

**Architecture:**
- NLTK-based POS tagging (slow, English-only default)
- Linear corpus scan for each pattern
- Pickle-serialized GrammarGraph results
- CQL-based grammar patterns

**Performance:**
- O(W × C × P) complexity
- Minutes for moderate corpora
- Hours for 74M sentences (impractical)

**Key Components:**
```
Raw Text → tag_corpus() → Tagged Corpus → parse_corpus() → GrammarGraph
                                                              ↓
search_in_parsed_corpus() → logDice scores → Sorted collocations
```

### Current Flask app state

- Tables defined in `postgresql_connector.py` but never created
- `word_sketch_service.py` exists but unimplemented
- No POS-tagged corpus data exists

## Target Architecture

### Dual-Lucene Index Strategy

```
┌─────────────────────────────────────────────────────────────────┐
│                    Lucene Cluster                                │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌─────────────────────┐    ┌─────────────────────┐            │
│  │  Main Corpus Index  │    │  Word Sketch Index  │            │
│  │                     │    │                     │            │
│  │  - sentence_id      │    │  - sentence_id      │            │
│  │  - position         │    │  - position         │            │
│  │  - word             │    │  - word             │            │
│  │  - lemma            │    │  - lemma (indexed)  │            │
│  │  - (NO tag)         │    │  - tag (indexed)    │            │
│  │  - sentence         │    │  - sentence         │            │
│  │                     │    │  - pos_group        │            │
│  └─────────────────────┘    └─────────────────────┘            │
│          │                            │                          │
│  Fast KWIC/concordance        Fast pattern matching             │
│  ~5-20ms query               ~50-200ms query                    │
└─────────────────────────────────────────────────────────────────┘
```

### Rationale for Separate Indices

| Factor | Main Index | Word Sketch Index |
|--------|-----------|-------------------|
| Content | Raw corpus | POS-tagged corpus |
| Query type | Keyword search | Pattern matching |
| Tag storage | Not needed | Required |
| Index size | ~15GB | ~50GB |
| Retagging needed | No | Yes (independent) |

## Index Schema

### Word Sketch Index

```python
{
    "doc_id": int,              # sentence ID (for example retrieval)
    "position": int,            # word position in sentence (0-indexed)
    "word": str,                # raw word form (stored, for display)
    "lemma": str,               # lemmatized form (indexed, analyzed)
    "tag": str,                 # POS tag (indexed, keyword)
    "pos_group": str,           # broad category: noun, verb, adj, adv (fast filter)
    "sentence": str,            # full sentence (stored, for KWIC)
    "start_offset": int,        # char offset in sentence (for highlighting)
    "end_offset": int
}
```

### Field Configuration

```python
# indexer.py schema configuration
INDEX_FIELDS = {
    "doc_id": {"type": "stored", "numeric": True},
    "position": {"type": "stored", "numeric": True},
    "word": {"type": "stored"},
    "lemma": {"type": "indexed", "analyzed": True, "norms": True},
    "tag": {"type": "indexed", "keyword": True},
    "pos_group": {"type": "indexed", "keyword": True},
    "sentence": {"type": "stored"},
    "start_offset": {"type": "stored", "numeric": True},
    "end_offset": {"type": "stored", "numeric": True}
}
```

## CQL to Lucene Translation

### Pattern Syntax Mapping

| CQL Construct | Example | Lucene Equivalent |
|--------------|---------|-------------------|
| Labeled position | `1:"N.*"` | `SpanFirstQuery(TermQuery(tag="N.*"), 0)` |
| Constraint | `[tag="adj"]` | `TermQuery(tag=re.compile("adj.*"))` |
| Word match | `[word="the"]` | `TermQuery(word="the")` |
| Negation | `[tag!="N.*"]` | `BooleanQuery(MUST_NOT + TermQuery(...))` |
| Distance {min,max} | `{0,2}` | `SpanNearQuery(..., slop=max, inOrder=...)` |
| OR | `\|` | `BooleanQuery(should=[...])` |
| Sequence | A B C | `SpanNearQuery([A, B, C], slop=0, inOrder=true)` |

### Grammar Compilation Example

**Original Polish grammar (noun + adjective with case agreement):**
```
1:[tag="subst:.*:nom:.*"] 2:[tag="adj:.*:nom:.*"] & 1.case = 2.case
```

**Lucene translation:**
```python
# Position 0: noun in nominative
pos0 = SpanFirstQuery(
    TermQuery(tag=re.compile(r"subst:.*:nom:.*")),
    atMost=0
)

# Position 1: adjective in nominative
pos1 = SpanNearQuery(
    [TermQuery(tag=re.compile(r"adj:.*:nom:.*"))],
    slop=0,
    inOrder=true
)

# Combine with distance constraint
pattern = SpanNearQuery([pos0, pos1], slop=0, inOrder=true)

# Agreement rule (post-filter)
def check_case_agreement(spans):
    for span in spans:
        if span.label == "1":
            noun_case = extract_case(span.tag)
        elif span.label == "2":
            adj_case = extract_case(span.tag)
    return noun_case == adj_case
```

### Unsupported CQL Features

| Feature | Workaround |
|---------|------------|
| `& 1.tag = 2.tag` | Post-filter after SpanQuery |
| Complex cross-position rules | Custom filter function |
| Fuzzy matching | Not supported |

## POS Tagging Pipeline

### Recommended Taggers

| Tagger | Speed | Accuracy | Languages | Notes |
|--------|-------|----------|-----------|-------|
| UDPipe 2 | Fast | Good | 50+ | Best for production |
| Stanza | Medium | Better | 60+ | Stanford research |
| spaCy | Slow | Best | 70+ | Too slow for 74M |

### UDPipe 2 Integration

```python
from udapi.block.read.conllu import Conllu
from udapi.block.write.text import Text

# Train model on Polish (requires treebank)
# Or use pre-trained model: udpipe-ud-2.0-170801

# Process corpus
import subprocess

def tag_with_udpipe(input_file, output_file, model="polish-ud-2.0-170801"):
    cmd = [
        "udpipe",
        "--tokenize",
        "--tag",
        "--model", model,
        "--input", "generic",
        "--output", "conllu",
        input_file,
        output_file
    ]
    subprocess.run(cmd, check=True)

# Convert CoNLL-U to Lucene format
def convert_conllu_to_lucene(conllu_file, output_file):
    with open(conllu_file) as f, open(output_file, "w") as out:
        for line in f:
            if line.startswith("#") or line.strip() == "":
                continue
            parts = line.split("\t")
            if len(parts) >= 10:
                doc_id, position = track_position()
                word = parts[1]
                lemma = parts[2]
                tag = parts[3]
                pos_group = map_to_pos_group(tag)
                out.write(f"{doc_id}\t{position}\t{word}\t{lemma}\t{tag}\t{pos_group}\n")
```

## Frequency Collection

### SpanQuery Execution with Collection

```python
from collections import defaultdict, Counter
import lucene

class WordSketchCollector:
    """Collect lemma pairs during SpanQuery execution."""

    def __init__(self, headword: str, pattern_labels: dict):
        self.headword = headword
        self.pattern_labels = pattern_labels  # {label: position_in_pattern}
        self.collocates = defaultdict(Counter)  # (lemma2, pos2) → {(lemma1, pos1): count}
        self.headword_freq = Counter()           # (lemma, pos) → count
        self.collocate_total = Counter()         # (lemma, pos) → total
        self.examples = {}                       # (l1, l2) → KWIC strings

    def collect(self, spans: list):
        """Called for each pattern match."""
        labeled_spans = self._extract_labels(spans)
        if not labeled_spans:
            return

        # Get labeled words
        l1 = labeled_spans["1"]
        l2 = labeled_spans.get("2")

        if l2 is None:
            return

        # Apply post-filter rules
        if not self._check_agreement_rules(labeled_spans):
            return

        # Record frequencies
        key1 = (l1.lemma, l1.tag)
        key2 = (l2.lemma, l2.tag)

        self.collocates[key2][key1] += 1
        self.headword_freq[key1] += 1
        self.collocate_total[key2] += 1

        # Record example (truncate to ~100 chars)
        self._record_example(key1, key2, spans)

    def _extract_labels(self, spans: list) -> dict:
        """Extract labeled positions from span match."""
        result = {}
        for span in spans:
            label = self.pattern_labels.get(span.position)
            if label:
                result[label] = span
        return result

    def _check_agreement_rules(self, labeled_spans: dict) -> bool:
        """Apply grammar agreement rules (e.g., case, gender, number)."""
        # Override with grammar-specific rules
        return True

    def _record_example(self, key1, key2, spans):
        """Record KWIC example for lemma pair."""
        example_id = (key1[0], key2[0])
        if example_id not in self.examples:
            # Build KWIC string around the match
            self.examples[example_id] = self._build_kwic(spans)
```

### logDice Computation

```python
def compute_logdice(
    collocate_freq: int,
    headword_freq: int,
    collocate_total: int
) -> float:
    """
    Compute logDice association score.

    Formula:
    logDice = log2(2 * f(AB) / (f(A) + f(B))) + 14

    Where:
    - f(AB) = frequency of both words together
    - f(A) = frequency of headword as POS
    - f(B) = frequency of collocate in any collocation

    Returns:
        Score in range ~0-14 (14 = perfect association)
    """
    if headword_freq == 0 or collocate_total == 0:
        return 0.0

    dice = (2.0 * collocate_freq) / (headword_freq + collocate_total)
    logdice = math.log2(dice) + 14

    return max(0.0, logdice)  # Clamp to non-negative


def compute_all_logdice(collector: WordSketchCollector) -> dict:
    """Compute logDice for all collected collocations."""
    results = defaultdict(list)

    for collocate, headwords in collector.collocates.items():
        for headword, freq in headwords.items():
            logdice = compute_logdice(
                collocate_freq=freq,
                headword_freq=collector.headword_freq[headword],
                collocate_total=collector.collocate_total[collocate]
            )
            results[collocate].append({
                "lemma": headword[0],
                "pos": headword[1],
                "freq": freq,
                "logDice": round(logdice, 2)
            })

    # Sort by logDice descending
    for collocate in results:
        results[collocate].sort(key=lambda x: x["logDice"], reverse=True)

    return results
```

## Query API

### REST API Design

```
GET /sketch/{lemma}
GET /sketch/{lemma}?pos=noun,verb&min_logdice=5&limit=50

POST /sketch/query
{
    "lemma": "house",
    "patterns": [
        {"name": "modifiers", "cql": "1:noun 2:adj"},
        {"name": "subjects", "cql": "1:verb 2:noun[case=nom]"}
    ],
    "min_logdice": 5.0,
    "limit": 100
}
```

### Response Format

```json
{
    "status": "ok",
    "lemma": "house",
    "total_headword_freq": 12458,
    "patterns": {
        "modifiers": {
            "cql": "1:noun 2:adj",
            "total_matches": 8932,
            "collocations": [
                {
                    "lemma": "big",
                    "pos": "adj",
                    "logDice": 11.24,
                    "freq": 1247,
                    "relative_freq": 0.10,
                    "examples": [
                        "big house",
                        "the big house",
                        "a very big house"
                    ]
                },
                {
                    "lemma": "red",
                    "pos": "adj",
                    "logDice": 9.87,
                    "freq": 892,
                    "examples": [...]
                }
            ]
        },
        "subjects": {
            "cql": "1:verb 2:noun[case=nom]",
            "total_matches": 4521,
            "collocations": [...]
        }
    }
}
```

### Python Client

```python
from FlexTools.scripts.word_sketch_client import WordSketchClient

client = WordSketchClient(base_url="http://localhost:8083")

# Simple word sketch
sketch = client.word_sketch("house", pos_filter=["noun"], min_logdice=5.0)

# Access results
for pattern_name, pattern_data in sketch.patterns.items():
    print(f"\n=== {pattern_name} ===")
    for colloc in pattern_data["collocations"][:10]:
        print(f"  {colloc['lemma']}: logDice={colloc['logDice']}, freq={colloc['freq']}")

# Custom pattern
custom = client.query(
    lemma="run",
    patterns=[
        {"name": "objects", "cql": "1:verb 2:noun[case=acc]"},
        {"name": "subjects", "cql": "1:noun[case=nom] 2:verb"}
    ],
    min_logdice=7.0
)
```

## Implementation Plan

### Phase 1: Index Build (Week 1)

- [ ] Set up UDPipe 2 for Polish POS tagging
- [ ] Create Lucene indexer with `tag`, `lemma`, `pos_group` fields
- [ ] Process 74M sentences (~2-4 hours)
- [ ] Index size validation (~50GB)

### Phase 2: Pattern Engine (Week 2)

- [ ] Implement CQL → SpanQuery compiler
- [ ] Implement SpanQuery executor with collection
- [ ] Add post-filter support for agreement rules
- [ ] Test pattern matching accuracy

### Phase 3: Scoring (Week 3)

- [ ] Implement frequency collection during search
- [ ] Add logDice computation
- [ ] Optimize with pre-computed term statistics
- [ ] Benchmark: target <200ms per query

### Phase 4: API & Integration (Week 4)

- [ ] Create REST API endpoints
- [ ] Build Python client library
- [ ] Integrate with Flask app
- [ ] Write integration tests

### Phase 5: Grammar Porting (Ongoing)

- [ ] Port Polish grammar rules from `/mnt/d/git/word-sketch/grammars/`
- [ ] Add English grammar (UDPipe English model)
- [ ] Test against original implementation for accuracy

## Grammar Porting

### Polish Grammar Examples

From `/mnt/d/git/word-sketch/grammars/polish.txt`:

```cql
# Noun + Adjective (nom)
1:[tag="subst:.*:nom:.*"] 2:[tag="adj:.*:nom:.*"] & 1.case = 2.case

# Noun + Verb (subject)
1:[tag="subst:.*:nom:.*"] 2:[tag="fin:.*"] & 1.number = 2.number

# Verb + Object (accusative)
1:[tag="fin:.*"] 2:[tag="subst:.*:acc:.*"] & 1.number = 2.number

# Verb + Particle
1:[tag="fin:.*"] [tag="qub"] 2:[tag="fin:.*"]
```

### Porting Notes

1. **Case agreement**: Extract from tag, compare in post-filter
2. **Number agreement**: Extract `number` feature from tag
3. **Gender agreement**: Extract `gender` feature from tag
4. **Custom rules**: Define filter functions per grammar rule

## Performance Targets

| Metric | Target | Notes |
|--------|--------|-------|
| Index build time | <4 hours | With UDPipe, parallelized |
| Query latency (word sketch) | <200ms | For common lemmas |
| Query latency (custom pattern) | <500ms | Complex patterns |
| Index size | <60GB | With compression |
| Memory usage | <16GB | Query execution |

## Migration Checklist

- [x] Analyze original word-sketch codebase
- [x] Design dual-Lucene index strategy
- [x] Define index schema with POS tags
- [x] Design CQL → SpanQuery translation
- [x] Implement frequency collection class
- [x] Design logDice computation
- [x] Design REST API
- [x] Plan POS tagging pipeline (UDPipe 2)
- [ ] Write indexer implementation
- [ ] Write CQL compiler
- [ ] Write query executor
- [ ] Write API endpoints
- [ ] Port Polish grammar rules
- [ ] Test and benchmark

## Appendix: logDice Formula Derivation

The Dice coefficient measures association strength:

```
Dice = 2 * f(AB) / (f(A) + f(B))
```

Where:
- f(A) = frequency of word A in corpus
- f(B) = frequency of word B in corpus
- f(AB) = frequency of A and B occurring together

The logDice transformation:

```
logDice = log2(Dice) + 14
```

Benefits:
- Ranges from 0 to ~14 (14 = perfect association)
- Symmetric: logDice(A,B) = logDice(B,A)
- Resistant to frequency differences

## Appendix: Tagset Mapping

UDPipe uses Universal Dependencies (UD) tagset:

| UD Tag | Meaning | pos_group |
|--------|---------|-----------|
| NOUN | Noun | noun |
| VERB | Verb | verb |
| ADJ | Adjective | adj |
| ADV | Adverb | adv |
| ADP | Adposition | adp |
| PROPN | Proper noun | noun |
| DET | Determiner | det |

Polish-specific features encoded in `FEAT` column:
- Case: Nom, Acc, Gen, Dat, Inst, Loc
- Number: Sing, Plur
- Gender: Masc, Fem, Neut
- Person: 1, 2, 3

## References

- Original word-sketch: `/mnt/d/git/word-sketch`
- UDPipe 2: https://github.com/ufal/udpipe
- Universal Dependencies: https://universaldependencies.org/
- Lucene SpanQuery: https://lucene.apache.org/core/9_0_0/core/org/apache/lucene/search/spans/package-summary.html
