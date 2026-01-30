# Hybrid Word Sketch Index - Detailed Specification

## Executive Summary

This document specifies a redesigned index architecture for the Word Sketch system that replaces the current token-per-document model with a hybrid sentence-per-document approach. The goal is to achieve 10-100× query speedup while maintaining identical result accuracy.

---

## 1. Current Architecture Problems

### 1.1 Token-per-Document Model
```
Current: Each token = 1 Lucene document
├── Document 1: {doc_id=1, pos=0, lemma="the", tag="DT", sentence="The cat sat..."}
├── Document 2: {doc_id=1, pos=1, lemma="cat", tag="NN", sentence="The cat sat..."}
├── Document 3: {doc_id=1, pos=2, lemma="sit", tag="VBD", sentence="The cat sat..."}
└── ... (~1.5 billion documents for 74M sentences)
```

### 1.2 Quantified Problems

| Problem | Impact | Root Cause |
|---------|--------|------------|
| **63GB stored fields** | 87% of index is duplicated sentence text | Same sentence stored 20× (once per token) |
| **No native span queries** | O(n) post-filtering required | SpanNearQuery requires same document |
| **Slow sentence reconstruction** | 8-12s per query | Must search by doc_id to find sentence tokens |
| **Memory pressure** | High heap usage | Loading millions of stored field documents |

---

## 2. Hybrid Architecture Specification

### 2.1 High-Level Design

```
┌─────────────────────────────────────────────────────────────────┐
│                    HYBRID INDEX ARCHITECTURE                     │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────────────┐  ┌──────────────────┐  ┌───────────────┐ │
│  │  SENTENCE INDEX  │  │  STATISTICS IDX  │  │ COLLOC CACHE  │ │
│  │  (Primary)       │  │  (DocValues)     │  │ (Optional)    │ │
│  ├──────────────────┤  ├──────────────────┤  ├───────────────┤ │
│  │ • 1 doc/sentence │  │ • Term freqs     │  │ • Top 5K lemmas│ │
│  │ • Positional idx │  │ • Doc counts     │  │ • Pre-computed │ │
│  │ • Term vectors   │  │ • Corpus stats   │  │ • Key-value    │ │
│  │ • Stored text    │  │                  │  │               │ │
│  └──────────────────┘  └──────────────────┘  └───────────────┘ │
│           │                     │                    │          │
│           └─────────────────────┼────────────────────┘          │
│                                 ▼                               │
│                    ┌─────────────────────┐                      │
│                    │   QUERY EXECUTOR    │                      │
│                    │   (SpanQuery-based) │                      │
│                    └─────────────────────┘                      │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 Sentence Index Schema

Each sentence is stored as **one Lucene document**:

```java
public class SentenceDocument {
    // Identity
    int sentenceId;              // Unique sentence identifier
    
    // Stored fields (for display)
    String text;                 // Full sentence text
    String source;               // Optional: source file/document
    
    // Positional indexed fields (for SpanQuery)
    // These use custom analysis to preserve token positions
    String[] lemmas;             // ["the", "cat", "sit", "on", "the", "mat"]
    String[] tags;               // ["DT", "NN", "VBD", "IN", "DT", "NN"]
    String[] words;              // ["The", "cat", "sat", "on", "the", "mat"]
    
    // Parallel arrays as DocValues (for fast iteration without stored fields)
    byte[] lemmaPositions;       // Encoded: pos→lemma mapping
    byte[] tagPositions;         // Encoded: pos→tag mapping
}
```

#### Field Configuration

| Field | Type | Indexed | Stored | DocValues | TermVector | Positions |
|-------|------|---------|--------|-----------|------------|-----------|
| `sentence_id` | NumericDocValues | ✓ | ✓ | ✓ | - | - |
| `text` | StoredField | - | ✓ | - | - | - |
| `lemma` | TextField | ✓ | - | - | ✓ | ✓ |
| `tag` | TextField | ✓ | - | - | ✓ | ✓ |
| `word` | TextField | ✓ | - | - | ✓ | ✓ |
| `lemma_seq` | BinaryDocValues | - | - | ✓ | - | - |
| `tag_seq` | BinaryDocValues | - | - | ✓ | - | - |

### 2.3 Custom Analyzer for Positional Indexing

```java
/**
 * Analyzer that produces tokens at specific positions from CoNLL-U input.
 * Preserves the exact position of each token in the sentence.
 */
public class PositionalCoNLLUAnalyzer extends Analyzer {
    
    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
        // Tokenizer reads pre-tokenized input (tab-separated)
        Tokenizer tokenizer = new PreTokenizedTokenizer();
        
        // Token filter that sets correct position increments
        TokenStream filter = new PositionPreservingFilter(tokenizer);
        
        // Lowercase for case-insensitive matching
        filter = new LowerCaseFilter(filter);
        
        return new TokenStreamComponents(tokenizer, filter);
    }
}
```

### 2.4 Statistics Index Schema

Separate lightweight index for corpus-wide statistics:

```java
public class TermStatisticsDocument {
    String lemma;                // The lemma/term
    long totalFrequency;         // Total occurrences in corpus
    int documentFrequency;       // Number of sentences containing this lemma
    String posDistribution;      // JSON: {"NN": 45000, "VB": 12000, ...}
}
```

This enables O(1) lookup for logDice denominator calculations.

### 2.5 Collocation Cache Schema (Optional Phase 3)

Pre-computed collocations for top N frequent lemmas:

```java
public class CachedCollocation {
    String headword;
    String relation;             // "adj_modifier", "noun_compound", etc.
    String collocate;
    long cooccurrenceFreq;
    double logDice;
    String[] exampleSentenceIds; // Top 3 example sentence IDs
}
```

---

## 3. Query Execution Specification

### 3.1 SpanQuery-Based Pattern Matching

The key advantage is native Lucene SpanQuery support:

```java
// CQL Pattern: [tag="JJ.*"]~{0,2} [lemma="cat"]
// Translates to:

SpanQuery adjQuery = new SpanMultiTermQueryWrapper<>(
    new RegexpQuery(new Term("tag", "jj.*"))
);
SpanQuery headwordQuery = new SpanTermQuery(new Term("lemma", "cat"));

SpanNearQuery pattern = SpanNearQuery.newOrderedNearQuery("lemma")
    .addClause(adjQuery)
    .addGap(0, 2)  // 0-2 tokens gap
    .addClause(headwordQuery)
    .build();
```

### 3.2 Query Execution Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                      QUERY EXECUTION FLOW                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  1. CACHE CHECK                                                  │
│     ├─ Check collocation cache for headword                     │
│     └─ If hit: return immediately (< 10ms)                      │
│                                                                  │
│  2. COMPILE CQL TO SPANQUERY                                     │
│     ├─ Parse CQL pattern                                        │
│     ├─ Build SpanNearQuery with field alignment                 │
│     └─ Handle multi-field patterns with position sync           │
│                                                                  │
│  3. EXECUTE SPANQUERY                                            │
│     ├─ Lucene returns matching spans with positions             │
│     ├─ Each span = (docId, startPos, endPos)                    │
│     └─ Native positional matching - no post-filter!             │
│                                                                  │
│  4. AGGREGATE COLLOCATES                                         │
│     ├─ For each span, extract collocate at target position      │
│     ├─ Use DocValues (lemma_seq) for fast access                │
│     └─ Aggregate frequencies in HashMap                         │
│                                                                  │
│  5. CALCULATE SCORES                                             │
│     ├─ Lookup total frequencies from Statistics Index           │
│     ├─ Calculate logDice for each collocate                     │
│     └─ Sort by score, return top N                              │
│                                                                  │
│  6. FETCH EXAMPLES                                               │
│     ├─ For top N collocates, get example sentence IDs           │
│     ├─ Load sentences from stored fields (only N × 3)           │
│     └─ Return complete results                                  │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### 3.3 Multi-Field Pattern Handling

CQL patterns can span multiple fields (e.g., `[tag="JJ" & lemma!="good"]`). 

**Challenge**: Lucene SpanQuery requires all clauses on the same field.

**Solution**: Position-synchronized iteration with DocValues:

```java
/**
 * For patterns requiring multiple field constraints at the same position,
 * we use the primary field for SpanQuery, then filter using DocValues.
 */
public List<SpanMatch> executeMultiFieldPattern(SpanQuery primaryQuery, 
                                                  MultiFieldConstraint constraint) {
    Spans spans = primaryQuery.getSpans(leafReader, SpanWeight.Postings.POSITIONS);
    
    List<SpanMatch> matches = new ArrayList<>();
    while (spans.nextDoc() != Spans.NO_MORE_DOCS) {
        // Get DocValues for secondary field checks
        BinaryDocValues tagSeq = leafReader.getBinaryDocValues("tag_seq");
        BinaryDocValues lemmaSeq = leafReader.getBinaryDocValues("lemma_seq");
        
        while (spans.nextStartPosition() != Spans.NO_MORE_POSITIONS) {
            int pos = spans.startPosition();
            
            // Check secondary constraints at this position
            if (constraint.matches(pos, tagSeq, lemmaSeq)) {
                matches.add(new SpanMatch(spans.docID(), pos, spans.endPosition()));
            }
        }
    }
    return matches;
}
```

---

## 4. Data Format Specifications

### 4.1 DocValues Encoding for Token Sequences

To enable fast position→token lookup without stored fields:

```java
/**
 * Binary encoding for token sequences in DocValues.
 * Format: [count:4bytes][pos1:2bytes][len1:2bytes][token1:variable]...
 * 
 * Example for ["the", "cat", "sat"]:
 * [3][0][3][the][1][3][cat][2][3][sat]
 */
public class TokenSequenceCodec {
    
    public static byte[] encode(List<Token> tokens) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        
        dos.writeInt(tokens.size());
        for (int i = 0; i < tokens.size(); i++) {
            dos.writeShort(i);  // position
            byte[] bytes = tokens.get(i).lemma().getBytes(UTF_8);
            dos.writeShort(bytes.length);
            dos.write(bytes);
        }
        return baos.toByteArray();
    }
    
    public static String getTokenAtPosition(byte[] encoded, int position) {
        // Binary search or sequential scan based on position
        // Returns the token at the specified position
    }
}
```

### 4.2 Index Directory Structure

```
index/
├── sentence/                    # Primary sentence index
│   ├── _0.cfs                  # Compound file (or individual files)
│   ├── segments_N
│   └── write.lock
├── statistics/                  # Term statistics index
│   ├── _0.cfs
│   └── segments_N
└── cache/                       # Optional collocation cache
    ├── collocations.db         # Key-value store (RocksDB or similar)
    └── metadata.json
```

---

## 5. Accuracy Guarantees

### 5.1 Result Equivalence

The new index MUST produce **identical results** to the current implementation:

| Metric | Requirement |
|--------|-------------|
| Collocate set | Identical lemmas returned |
| Frequency counts | Exact match (±0) |
| LogDice scores | Match to 6 decimal places |
| Example sentences | Valid examples (may differ in selection) |
| Ranking order | Identical for same scores |

### 5.2 Verification Strategy

```java
public class ResultEquivalenceTest {
    
    @Test
    void compareResults() {
        String[] testWords = {"cat", "dog", "house", "love", "work"};
        String[] testPatterns = {
            "[tag=\"JJ.*\"]~{0,3}",           // Adjective modifiers
            "[tag=\"NN.*\"]~{1,2} [tag=\"NN.*\"]", // Noun compounds
            "[tag=\"VB.*\"]~{0,5} [tag=\"NN.*\"]"  // Verb objects
        };
        
        for (String word : testWords) {
            for (String pattern : testPatterns) {
                List<Collocation> oldResults = oldExecutor.query(word, pattern);
                List<Collocation> newResults = newExecutor.query(word, pattern);
                
                assertResultsEquivalent(oldResults, newResults);
            }
        }
    }
    
    void assertResultsEquivalent(List<Collocation> old, List<Collocation> new_) {
        assertEquals(old.size(), new_.size(), "Result count mismatch");
        
        Map<String, Collocation> oldMap = toMap(old);
        Map<String, Collocation> newMap = toMap(new_);
        
        for (String lemma : oldMap.keySet()) {
            assertTrue(newMap.containsKey(lemma), "Missing collocate: " + lemma);
            assertEquals(oldMap.get(lemma).frequency(), 
                        newMap.get(lemma).frequency(),
                        "Frequency mismatch for: " + lemma);
            assertEquals(oldMap.get(lemma).logDice(), 
                        newMap.get(lemma).logDice(), 
                        0.000001,
                        "LogDice mismatch for: " + lemma);
        }
    }
}
```

---

## 6. Performance Targets

### 6.1 Query Latency

| Query Type | Current | Target | Improvement |
|------------|---------|--------|-------------|
| Cold (uncached) | 80s | < 5s | 16× |
| Warm (OS cache) | 8s | < 0.5s | 16× |
| Hot (app cache) | N/A | < 50ms | New |

### 6.2 Index Metrics

| Metric | Current | Target | Improvement |
|--------|---------|--------|-------------|
| Index size | 73 GB | < 8 GB | 9× smaller |
| Documents | ~1.5B | 74M | 20× fewer |
| Indexing time | ~5 days | < 3 days | 40% faster |

### 6.3 Memory Usage

| Scenario | Current | Target |
|----------|---------|--------|
| Idle server | ~2 GB | < 500 MB |
| During query | ~4 GB peak | < 1 GB |
| With cache | N/A | +200 MB |

---

## 7. Migration Strategy

### 7.1 Parallel Operation

During migration, both indexes will be available:

```java
public class DualIndexQueryExecutor implements QueryExecutor {
    private final LegacyExecutor legacyExecutor;  // Current implementation
    private final HybridExecutor hybridExecutor;  // New implementation
    private final boolean verifyMode;
    
    public List<Collocation> query(String headword, String pattern) {
        List<Collocation> newResults = hybridExecutor.query(headword, pattern);
        
        if (verifyMode) {
            List<Collocation> oldResults = legacyExecutor.query(headword, pattern);
            verifyEquivalence(oldResults, newResults);
        }
        
        return newResults;
    }
}
```

### 7.2 Rollback Plan

If issues are discovered:
1. Switch `--use-legacy-index` flag
2. All queries fall back to current implementation
3. No data loss - both indexes remain available

---

## 8. API Compatibility

### 8.1 REST API (Unchanged)

The REST API remains identical:

```
GET  /api/sketch/{lemma}          # Full word sketch
POST /api/sketch/query            # Custom CQL query
GET  /api/relations               # List relations
GET  /health                      # Health check
```

### 8.2 Response Format (Unchanged)

```json
{
  "lemma": "cat",
  "status": "ok",
  "patterns": {
    "adj_modifiers": {
      "name": "Adjective modifiers",
      "cql": "[tag=\"JJ.*\"]~{0,3}",
      "total_matches": 150,
      "collocations": [
        {
          "lemma": "black",
          "pos": "JJ",
          "frequency": 1234,
          "logDice": 8.45,
          "relativeFrequency": 0.082,
          "examples": ["The black cat...", ...]
        }
      ]
    }
  }
}
```

---

## 9. Appendix: Glossary

| Term | Definition |
|------|------------|
| **SpanQuery** | Lucene query type that matches terms with position information |
| **DocValues** | Column-oriented storage for fast field value access |
| **TermVector** | Per-document term frequency and position information |
| **logDice** | Association measure: `14 + log2(2*f(AB) / (f(A)+f(B)))` |
| **CQL** | Corpus Query Language - pattern syntax for corpus searches |
