# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Word Sketch Lucene is a computational linguistics tool that provides fast corpus-based collocation analysis using Apache Lucene. It ports the word-sketch concept (finding typical words that co-occur with a given word) to enable pattern matching on large corpora (up to 74M sentences).

**Key terminology:**
- **Word sketch**: A summary of typical collocations for a word (e.g., adjectives modifying "house", verbs associated with "house")
- **CQL (Corpus Query Language)**: Pattern syntax for defining grammatical relations
- **logDice**: Association score (0-14, where 14 = perfect association) for ranking collocations

## Build Commands

```bash
# NOTE: Requires Java 21+ (Lucene 10.3.2 requires Java 21+)
# On Windows with Java 22 installed at C:\Program Files\Java\jdk-22:
powershell -Command "Set-Item -Path Env:JAVA_HOME -Value 'C:\Program Files\Java\jdk-22'; Set-Item -Path Env:MAVEN_OPTS -Value '-Xmx512m'; Set-Location -Path 'd:\git\word-sketch-lucene'; mvn clean compile"

# Run tests
mvn test

# Run a single test class
mvn test -Dtest=ClassName

# Package as JAR
mvn package

# Run the application
java -jar target/word-sketch-lucene-1.0.0.jar <command>

# Run with Maven exec plugin
mvn exec:java -Dexec.mainClass="pl.marcinmilkowski.word_sketch.Main"
```

## CLI Commands

Available commands in [Main.java](src/main/java/pl/marcinmilkowski/word_sketch/Main.java):
- `index` - Index a corpus for word sketch analysis
- `query` - Query the word sketch index
- `server` - Start the REST API server
- `tag` - Tag a corpus with POS tags
- `help` - Show help message

## Architecture

### Dual-Lucene Index Strategy

```
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
- **[LuceneIndexer.java](src/main/java/pl/marcinmilkowski/word_sketch/indexer/LuceneIndexer.java)**: Index creation with 256MB RAM buffer
- **[grammar/CQLParser.java](src/main/java/pl/marcinmilkowski/word_sketch/grammar/CQLParser.java)**: CQL pattern parser
- **[query/CQLToLuceneCompiler.java](src/main/java/pl/marcinmilkowski/word_sketch/query/CQLToLuceneCompiler.java)**: Compiles CQL to Lucene SpanQueries
- **[query/WordSketchQueryExecutor.java](src/main/java/pl/marcinmilkowski/word_sketch/query/WordSketchQueryExecutor.java)**: Query executor with logDice scoring
- **[tagging/SimpleTagger.java](src/main/java/pl/marcinmilkowski/word_sketch/tagging/SimpleTagger.java)**: Rule-based POS tagger
- **[tagging/UDPipeTagger.java](src/main/java/pl/marcinmilkowski/word_sketch/tagging/UDPipeTagger.java)**: UDPipe 2 integration for POS tagging
- **[api/WordSketchApiServer.java](src/main/java/pl/marcinmilkowski/word_sketch/api/WordSketchApiServer.java)**: REST API endpoints
- **[sketchgrammar.wsdef.m4](sketchgrammar.wsdef.m4)**: English Penn Treebank 3.3 grammar in m4 macro format

## POS Tagging Pipeline

**Recommended: UDPipe 2** - Fast, supports 50+ languages, outputs CoNLL-U format. See [migration plan](word-sketch-lucene-migration.md) for integration details.

## Dependencies

- Java 21+ (Lucene 10.3.2 requires Java 21+)
- Apache Lucene 10.3.2
- Fastjson 2.0.25
- SLF4J 2.0.7
- JUnit 5.9.0
