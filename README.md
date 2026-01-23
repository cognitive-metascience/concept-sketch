# Word Sketch Lucene

A high-performance corpus-based collocation analysis tool built on Apache Lucene. This project ports the word-sketch concept to enable fast pattern matching on large corpora (up to 74M sentences).

## Features

- **Fast Collocation Analysis**: Uses Apache Lucene SpanQueries for efficient pattern matching
- **CQL Support**: Full Corpus Query Language support with constraints, distance modifiers, and more
- **logDice Scoring**: Association strength metric (0-14 scale, where 14 = perfect association)
- **Multiple Input Formats**: Raw text (with POS tagging) or pre-tagged CoNLL-U files
- **REST API**: Built-in HTTP server for programmatic access
- **CLI Interface**: Easy command-line operations

## Quick Start

### Prerequisites

- Java 17 or higher (Java 21+ recommended for Lucene 10.x)
- Maven 3.6+
- UDPipe 2 (optional, for better POS tagging)

### Build

```bash
mvn clean package
```

### Index a Corpus

Use the provided indexing script for easy corpus indexing:

```bash
# Index line-segmented text with UDPipe (recommended)
./index_corpus.sh sentences.txt data/index --tagger udpipe --language en

# Index with simple tagger (no UDPipe needed)
./index_corpus.sh sentences.txt data/index --tagger simple

# Index pre-tagged CoNLL-U file (fastest)
./index_corpus.sh corpus.conllu data/index --format conllu
```

On Windows (PowerShell):
```powershell
.\index_corpus.ps1 sentences.txt data\index -Tagger udpipe -Language en
```

#### Script Options

| Option | Description |
|--------|-------------|
| `--tagger T` | Tagger: `udpipe` (default) or `simple` |
| `--language L` | Language code: `en`, `pl`, `de`, `es`, etc. |
| `--format F` | Input format: `text` (default) or `conllu` |
| `--batch N` | Batch size (default: 1000) |

### Usage

```bash
java -jar target/word-sketch-lucene-1.0.0.jar <command> [options]
```

## Commands

### Index a Pre-Tagged CoNLL-U File (Recommended)

For proper POS tagging, use an external tagger (UDPipe, spaCy, Stanza) and index the result:

```bash
# 1. Tag your corpus with UDPipe, output CoNLL-U
udpipe --tokenize --tag --lemma --output=conllu english-model.udpipe corpus.txt > corpus.conllu

# 2. Index the CoNLL-U file
java -jar word-sketch-lucene.jar conllu --input corpus.conllu --output data/index/

# 3. Query the word sketch
java -jar word-sketch-lucene.jar query --index data/index/ --lemma house --pattern "[tag=\"jj.*\"]"
```

### Index Raw Text (Simple Tagger)

```bash
java -jar word-sketch-lucene.jar index --corpus sentences.txt --output data/index/
```

Note: This uses a simple rule-based tagger with limited coverage. For accurate results, use CoNLL-U input.

### Query the Word Sketch

```bash
# Find adjectives modifying "house"
java -jar word-sketch-lucene.jar query --index data/index/ --lemma house --pattern "[tag=\"jj.*\"]"

# Find verbs associated with "time"
java -jar word-sketch-lucene.jar query --index data/index/ --lemma time --pattern "[tag=\"vb.*\"]"

# Custom CQL pattern
java -jar word-sketch-lucene.jar query --index data/index/ --lemma problem --pattern "[tag=\"jj.*\"] ~ {0,3}"
```

### Start REST API Server

```bash
java -jar word-sketch-lucene.jar server --index data/index/ --port 8080
```

## REST API Reference

### Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/health` | Health check |
| GET | `/api/relations` | List all grammatical relations |
| GET | `/api/sketch/{lemma}?pos=noun,verb,adj&limit=10` | Full word sketch |
| POST | `/api/sketch/query` | Custom CQL pattern query |

### Examples

```bash
# Health check
curl http://localhost:8080/health

# List available grammatical relations
curl http://localhost:8080/api/relations

# Get word sketch for "problem" (noun only, 10 results)
curl "http://localhost:8080/api/sketch/problem?pos=noun&limit=10"

# Get word sketch for all POS (default 10 results each)
curl "http://localhost:8080/api/sketch/house"

# Custom pattern query
curl -X POST http://localhost:8080/api/sketch/query \
  -H "Content-Type: application/json" \
  -d '{"lemma":"house","pattern":"[tag=jj.*]~{0,3}","limit":5}'
```

### Response Format

**Full Word Sketch Response:**
```json
{
  "status": "ok",
  "lemma": "problem",
  "patterns": {
    "noun_modifiers": {
      "name": "Adjectives modifying (modifiers)",
      "cql": "[tag=jj.*]~{0,3}",
      "pos_group": "noun",
      "total_matches": 299,
      "collocations": [
        {
          "lemma": "clinical",
          "pos": "jj",
          "frequency": 13,
          "logDice": 9.57,
          "relativeFrequency": 0.021,
          "examples": ["..."]
        }
      ]
    }
  }
}
```

**Custom Query Response:**
```json
{
  "status": "ok",
  "lemma": "house",
  "pattern": "[tag=jj.*]~{0,3}",
  "total_matches": 10,
  "collocations": [
    {
      "lemma": "big",
      "pos": "jj",
      "frequency": 1247,
      "logDice": 11.24,
      "relativeFrequency": 0.10,
      "examples": ["big house", "the big house"]
    }
  ]
}
```

## CQL Pattern Syntax

### Basic Patterns

| Pattern | Description |
|---------|-------------|
| `"NN"` | Match noun (lemma) |
| `"JJ.*"` | Match adjectives (wildcard) |
| `[tag="JJ"]` | Match by POS tag constraint |
| `[word="the"]` | Match by word form |
| `1:"NN"` | Labeled position (1 = first word relative to headword) |

### Constraints

```cql
[tag="JJ.*"]           # Adjectives
[tag="VB.*"]           # Verbs
[tag="NN.*"]           # Nouns
[tag!="NN.*"]          # Not nouns
[tag="JJ"|tag="RB"]    # Adjectives OR adverbs
```

### Distance Modifiers

```cql
[tag="JJ"]             # Adjacent
[tag="JJ"] ~ {0,3}     # Within 3 words
[tag="JJ"] ~ {1,5}     # 1-5 words apart
```

### Examples

```cql
# Adjectives modifying a noun
[tag="jj.*"]

# Verbs taking a noun object
[tag="vb.*"]

# Nouns modified by adjectives within 3 words
[tag="nn.*"] ~ {0,3}

# Preceding adjectives
[tag="jj.*"] ~ {1,3}
```

## API Usage

### Get Word Sketch

```bash
curl "http://localhost:8080/sketch/house?min_logdice=5&limit=20"
```

Response:
```json
{
  "status": "ok",
  "lemma": "house",
  "total_headword_freq": 12458,
  "collocationsations": [
    {
      "lemma": "big",
      "pos": "jj",
      "logDice": 11.24,
      "freq": 1247,
      "relative_freq": 0.10,
      "examples": [
        "big house",
        "the big house"
      ]
    }
  ]
}
```

### Custom Query

```bash
curl -X POST "http://localhost:8080/sketch/query" \
  -H "Content-Type: application/json" \
  -d '{
    "lemma": "time",
    "cql": "[tag=\"jj.*\"]",
    "min_logdice": 5,
    "limit": 50
  }'
```

## Architecture

### Dual-Lucene Index Strategy

```
Main Corpus Index          Word Sketch Index
- sentence_id              - sentence_id
- position                 - position
- word                     - word
- lemma (indexed)          - lemma (indexed)
- tag (indexed)            - tag (indexed)
- pos_group                - pos_group
- sentence                 - sentence
Fast KWIC/concordance      Fast pattern matching
```

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
| `start_offset`, `end_offset` | Numeric, stored | Character offsets |

### logDice Formula

```
logDice = log2(2 * f(AB) / (f(A) + f(B))) + 14
```

Where:
- `f(AB)` = frequency of collocate with headword
- `f(A)` = headword frequency
- `f(B)` = collocate total frequency

Score ranges from 0 to 14, where 14 indicates perfect association.

## Project Structure

```
word-sketch-lucene/
├── src/main/java/pl/marcinmilkowski/word_sketch/
│   ├── Main.java                    # CLI entry point
│   ├── api/
│   │   └── WordSketchApiServer.java # REST API
│   ├── corpus/
│   │   ├── CorpusBuilder.java       # Corpus processing
│   │   └── PostgresCorpusSampler.java
│   ├── grammar/
│   │   ├── CQLParser.java           # CQL parsing
│   │   ├── CQLPattern.java          # Pattern representation
│   │   ├── SketchGrammarParser.java # Sketch grammar parsing
│   │   └── SketchGrammarCompiler.java
│   ├── indexer/
│   │   └── LuceneIndexer.java       # Lucene index creation
│   ├── query/
│   │   ├── CQLToLuceneCompiler.java # CQL -> Lucene translation
│   │   └── WordSketchQueryExecutor.java
│   ├── tagging/
│   │   ├── PosTagger.java           # Tagger interface
│   │   ├── SimpleTagger.java        # Rule-based tagger
│   │   ├── UDPipeTagger.java        # UDPipe wrapper
│   │   └── ConllUProcessor.java     # CoNLL-U processing
│   └── utils/
│       └── LogDiceCalculator.java
├── plans/
│   └── word-sketch-lucene-spec.md   # Technical specification
└── pom.xml
```

## Dependencies

- Apache Lucene 9.8.0
- Fastjson 2.0.25
- SLF4J 2.0.7
- JUnit 5.9.0 (for testing)

## Performance

| Metric | Target | Notes |
|--------|--------|-------|
| Index build time | <4 hours | With UDPipe, parallelized |
| Query latency | <200ms | For common lemmas |
| Index size | <60GB | With compression |

## License

MIT License
