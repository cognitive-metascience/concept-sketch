# Hybrid Index Implementation Plan

## Overview

This document outlines the step-by-step implementation plan for migrating from the token-per-document index to the hybrid sentence-per-document architecture.

**Timeline**: 4 phases over ~2-3 weeks
**Risk Level**: Medium (mitigated by parallel operation and extensive testing)

---

## Phase 1: Foundation & Testing Infrastructure (Days 1-3)

### 1.1 Create Regression Test Suite

**Goal**: Establish a comprehensive test baseline before any changes.

#### Tasks:

- [ ] **1.1.1** Create `RegressionTestDataGenerator` to capture current results
  - Query 100 headwords across all relation patterns
  - Store results as JSON baseline files
  - Include edge cases: rare words, ambiguous POS, multi-word patterns

- [ ] **1.1.2** Create `ResultEquivalenceValidator`
  - Compare frequency counts (exact match)
  - Compare logDice scores (within 1e-6 tolerance)
  - Compare collocate sets (same lemmas)
  - Generate detailed diff reports

- [ ] **1.1.3** Create integration test harness
  - `HybridIndexIntegrationTest` - runs both implementations
  - Automated comparison for CI/CD
  - Performance benchmarking hooks

#### Files Created (COMPLETED):
```
src/test/java/pl/marcinmilkowski/word_sketch/
├── regression/
│   ├── RegressionTestDataGenerator.java     ✅ DONE
│   ├── ResultEquivalenceValidator.java      ✅ DONE
│   └── ResultEquivalenceValidatorTest.java  ✅ DONE (12 tests)
└── resources/
    └── baseline/
        ├── baseline.tsv (generated from index)
        └── metadata.txt
```

#### Acceptance Criteria:
- [ ] Baseline generated for 100+ headwords
- [x] Validator correctly detects intentional changes
- [ ] CI integration working

---

### 1.2 Refactor Current Code for Dual-Index Support

**Goal**: Prepare codebase for parallel implementations.

#### Tasks:

- [x] **1.2.1** Extract `QueryExecutor` interface ✅ DONE
  ```java
  public interface QueryExecutor extends Closeable {
      List<WordSketchResult> findCollocations(String headword, String pattern,
                                               double minLogDice, int maxResults);
      long getTotalFrequency(String lemma);
      default String getExecutorType();
      default boolean isReady();
  }
  ```

- [x] **1.2.2** Refactor current executor to implement interface ✅ DONE
  - `WordSketchQueryExecutor` now implements `QueryExecutor`
  - Added @Override annotations
  - All 113 tests pass

- [ ] **1.2.3** Create `QueryExecutorFactory`
  ```java
  public class QueryExecutorFactory {
      public static QueryExecutor create(String indexPath, IndexType type) {
          return switch (type) {
              case LEGACY -> new LegacyQueryExecutor(indexPath);
              case HYBRID -> new HybridQueryExecutor(indexPath);
              case DUAL -> new DualQueryExecutor(indexPath); // Verification mode
          };
      }
  }
  ```

#### Acceptance Criteria:
- [x] All existing tests pass with refactored code
- [x] No behavioral changes
- [x] Clean interface separation

---

## Phase 2: Hybrid Indexer Implementation (Days 4-7)

### 2.1 Create Sentence-Based Indexer

**Goal**: Build the new indexer that creates sentence-per-document indexes.

#### Tasks:

- [ ] **2.1.1** Create `SentenceDocument` record
  ```java
  public record SentenceDocument(
      int sentenceId,
      String text,
      List<Token> tokens
  ) {}
  
  public record Token(
      int position,
      String word,
      String lemma,
      String tag
  ) {}
  ```

- [ ] **2.1.2** Create `PositionalTokenStream`
  - Custom TokenStream that emits tokens with correct positions
  - Handles CoNLL-U input format
  - Preserves exact token positions

- [ ] **2.1.3** Create `HybridIndexer`
  ```java
  public class HybridIndexer {
      // Creates sentence-per-document index
      public void indexSentence(SentenceDocument sentence);
      
      // Creates term statistics index
      public void buildStatisticsIndex();
      
      // Finalizes and optimizes indexes
      public void commit();
  }
  ```

- [ ] **2.1.4** Implement DocValues encoding
  - `TokenSequenceCodec` for lemma/tag sequences
  - Efficient binary format for position→token lookup

#### Files to Create:
```
src/main/java/pl/marcinmilkowski/word_sketch/indexer/
├── hybrid/
│   ├── HybridIndexer.java
│   ├── SentenceDocument.java
│   ├── PositionalTokenStream.java
│   ├── TokenSequenceCodec.java
│   └── StatisticsIndexBuilder.java
```

#### Acceptance Criteria:
- [ ] Can index a small test corpus
- [ ] Index size ~15× smaller than legacy
- [ ] Position information correctly stored

---

### 2.2 Create Statistics Index

**Goal**: Pre-compute term frequencies for fast logDice calculation.

#### Tasks:

- [ ] **2.2.1** Create `TermStatistics` record
  ```java
  public record TermStatistics(
      String lemma,
      long totalFrequency,
      int documentFrequency,
      Map<String, Long> posDistribution
  ) {}
  ```

- [ ] **2.2.2** Create `StatisticsIndexBuilder`
  - Aggregates term frequencies during indexing
  - Builds separate statistics index
  - Supports incremental updates

- [ ] **2.2.3** Create `StatisticsReader`
  - O(1) frequency lookups
  - Cached in memory (small footprint)

#### Acceptance Criteria:
- [ ] Frequency lookups < 1ms
- [ ] Statistics match legacy `docFreq()` calls
- [ ] Memory usage < 100MB for statistics

---

## Phase 3: Hybrid Query Executor (Days 8-12)

### 3.1 SpanQuery Compiler

**Goal**: Translate CQL patterns to Lucene SpanQueries.

#### Tasks:

- [ ] **3.1.1** Create `CQLToSpanQueryCompiler`
  - Compiles CQL AST to SpanQuery tree
  - Handles distance modifiers (`~{0,3}`)
  - Supports regex patterns in constraints

- [ ] **3.1.2** Handle multi-field patterns
  - Primary field for SpanQuery
  - DocValues for secondary field checks
  - Position synchronization

- [ ] **3.1.3** Implement agreement rules
  - Cross-position constraints (e.g., `& 1.tag = 2.tag`)
  - Post-filter matches that don't satisfy agreement

#### Files to Create:
```
src/main/java/pl/marcinmilkowski/word_sketch/query/hybrid/
├── CQLToSpanQueryCompiler.java
├── SpanQueryBuilder.java
├── MultiFieldMatcher.java
└── AgreementFilter.java
```

#### Acceptance Criteria:
- [ ] All current CQL patterns compile correctly
- [ ] SpanQuery execution returns correct positions
- [ ] Multi-field patterns handled accurately

---

### 3.2 Hybrid Query Executor

**Goal**: Full query execution using the new index.

#### Tasks:

- [ ] **3.2.1** Create `HybridQueryExecutor`
  ```java
  public class HybridQueryExecutor implements QueryExecutor {
      private final IndexSearcher sentenceSearcher;
      private final StatisticsReader statsReader;
      private final CQLToSpanQueryCompiler compiler;
      
      @Override
      public List<WordSketchResult> findCollocations(...) {
          // 1. Compile pattern to SpanQuery
          // 2. Execute SpanQuery
          // 3. Extract collocates using DocValues
          // 4. Calculate logDice using statistics
          // 5. Fetch examples from stored fields
      }
  }
  ```

- [ ] **3.2.2** Implement span-based collocate extraction
  - Iterate spans efficiently
  - Use DocValues for position→lemma lookup
  - Aggregate without stored field access

- [ ] **3.2.3** Implement lazy example loading
  - Only load sentences for top N results
  - Batch loading for efficiency

#### Acceptance Criteria:
- [ ] Results match legacy executor exactly
- [ ] Query time < 2s for warm cache
- [ ] Memory usage stable during queries

---

### 3.3 Dual Executor for Verification

**Goal**: Run both implementations in parallel for validation.

#### Tasks:

- [ ] **3.3.1** Create `DualQueryExecutor`
  - Runs legacy and hybrid in parallel
  - Compares results automatically
  - Logs discrepancies with details

- [ ] **3.3.2** Add verification mode to server
  - `--verify` flag enables dual execution
  - Performance metrics for both paths
  - Discrepancy alerts

#### Acceptance Criteria:
- [ ] Both executors run concurrently
- [ ] Discrepancies logged with full context
- [ ] Easy toggle between modes

---

## Phase 4: Integration & Optimization (Days 13-17)

### 4.1 Server Integration

**Goal**: Integrate hybrid executor into the REST API server.

#### Tasks:

- [ ] **4.1.1** Update `WordSketchApiServer`
  - Support `--index-type=hybrid|legacy|dual` flag
  - Automatic detection based on index format
  - Graceful fallback on errors

- [ ] **4.1.2** Update CLI commands
  - `conllu` command creates hybrid index
  - `--legacy` flag for old format
  - Migration command for existing indexes

- [ ] **4.1.3** Add metrics endpoints
  - `/api/metrics` - query performance stats
  - `/api/health` - include index type info

#### Acceptance Criteria:
- [ ] Server works with both index types
- [ ] Zero downtime migration possible
- [ ] Metrics available for monitoring

---

### 4.2 Collocation Cache (Optional Optimization)

**Goal**: Pre-compute and cache common collocations.

#### Tasks:

- [ ] **4.2.1** Create `CollocationCache`
  - Key: (headword, relation)
  - Value: pre-computed collocations
  - LRU eviction for memory management

- [ ] **4.2.2** Cache warming
  - Pre-compute top 5000 frequent lemmas
  - Background refresh for staleness

- [ ] **4.2.3** Cache integration
  - Check cache before query execution
  - Populate cache on misses

#### Acceptance Criteria:
- [ ] Cache hits < 50ms
- [ ] Cache memory < 500MB
- [ ] 90% hit rate for common words

---

### 4.3 Performance Optimization

**Goal**: Achieve target query latencies.

#### Tasks:

- [ ] **4.3.1** Index optimization
  - Force merge to single segment
  - Optimize DocValues layout
  - Preload MMapDirectory

- [ ] **4.3.2** Query optimization
  - Early termination for top-N queries
  - Parallel span iteration
  - Result streaming

- [ ] **4.3.3** Memory optimization
  - DocValues caching strategy
  - Stored field compression
  - Object pooling for hot paths

#### Acceptance Criteria:
- [ ] Warm query < 500ms
- [ ] Cold query < 5s
- [ ] Memory stable under load

---

## Phase 5: Migration & Deployment (Days 18-21)

### 5.1 Corpus Re-indexing

**Goal**: Create production hybrid index from existing corpus.

#### Tasks:

- [ ] **5.1.1** Index the 74M sentence corpus
  - Use existing CoNLL-U files
  - Multi-threaded indexing
  - Progress monitoring

- [ ] **5.1.2** Build statistics index
  - Run statistics aggregation
  - Verify against legacy counts

- [ ] **5.1.3** Validate index
  - Run regression test suite
  - Compare results for 1000 random words
  - Performance benchmarking

#### Acceptance Criteria:
- [ ] New index < 8GB
- [ ] All regression tests pass
- [ ] Indexing completes in < 3 days

---

### 5.2 Production Deployment

**Goal**: Deploy hybrid index to production.

#### Tasks:

- [ ] **5.2.1** Deploy with dual-executor mode
  - Run both indexes in parallel
  - Monitor for discrepancies
  - Log performance comparison

- [ ] **5.2.2** Gradual traffic migration
  - Start with 10% traffic to hybrid
  - Increase as confidence builds
  - Full migration when stable

- [ ] **5.2.3** Legacy index retirement
  - Keep for 2 weeks post-migration
  - Archive and delete after validation

#### Acceptance Criteria:
- [ ] Zero discrepancies in production
- [ ] Query latency targets met
- [ ] Clean legacy removal

---

## Testing Strategy

### Unit Tests

| Component | Test Focus | Priority |
|-----------|------------|----------|
| `PositionalTokenStream` | Correct positions, edge cases | P0 |
| `TokenSequenceCodec` | Encode/decode roundtrip | P0 |
| `CQLToSpanQueryCompiler` | All pattern types | P0 |
| `HybridIndexer` | Field configuration | P1 |
| `StatisticsReader` | Frequency accuracy | P0 |

### Integration Tests

| Test | Description | Priority |
|------|-------------|----------|
| Result equivalence | Compare legacy vs hybrid | P0 |
| Full word sketch | All relations for test words | P0 |
| Edge cases | Rare words, empty results | P0 |
| Stress test | High-frequency words | P1 |
| Concurrent queries | Thread safety | P1 |

### Performance Tests

| Test | Metric | Target |
|------|--------|--------|
| Cold query | Time to first result | < 5s |
| Warm query | Average latency | < 500ms |
| Throughput | Queries/second | > 10 |
| Memory | Peak usage | < 2GB |

---

## Rollback Plan

### Triggers for Rollback

1. **Result discrepancies** > 1% of queries
2. **Query latency** > 3× regression
3. **Error rate** > 0.1%
4. **Memory issues** - OOM or > 4GB sustained

### Rollback Procedure

1. Set `--index-type=legacy` in server config
2. Restart server (< 30s downtime)
3. Legacy index remains available
4. Investigate and fix hybrid issues
5. Re-deploy with fixes

---

## Risk Assessment

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| Result accuracy regression | Medium | High | Extensive regression tests |
| SpanQuery limitations | Low | Medium | Multi-field fallback |
| Indexing time > expected | Medium | Low | Parallel indexing |
| Memory issues | Low | Medium | DocValues optimization |
| Performance regression | Low | High | Dual-executor monitoring |

---

## Dependencies

### External
- Apache Lucene 10.3.x (current)
- No new dependencies required

### Internal
- Existing CQL parser (reused)
- Existing logDice calculator (reused)
- Existing CoNLL-U parser (reused)

---

## Success Metrics

### Must Have
- [ ] 100% result accuracy vs legacy
- [ ] Index size < 10GB
- [ ] Query latency < 2s (warm)

### Should Have
- [ ] Query latency < 500ms (warm)
- [ ] Cache hit latency < 100ms
- [ ] Index size < 6GB

### Nice to Have
- [ ] Query latency < 100ms (cached)
- [ ] Zero-downtime migration
- [ ] Live index updates
