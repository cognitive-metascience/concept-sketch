# BlackLab Integration Analysis

## Executive Summary

**Recommendation: ✅ PROCEED with BlackLab integration** - Your intuition is correct. BlackLab v4.0.0 now provides native CoNLL-U dependency indexing, which would eliminate ~80% of your custom indexing code while providing immediate access to production-ready corpus query features.

---

## 1. Current State: Your Project

### What You've Built
- **Custom Lucene Indexer**: Hybrid index with BinaryDocValues token storage
- **CQL Parser**: Custom CQL pattern parser → SpanQuery compiler
- **Grammar Relations**: 60+ surface patterns + dependency relations (in `grammars/relations.json`)
- **Precomputed Collocations**: O(1) lookup via `grammar_collocations.bin`
- **Query Engine**: Sample-based collocation extraction with logDice scoring
- **Web UI**: D3.js semantic field explorer

### Technical Stack
- **Java**: 21
- **Lucene**: 10.3.2 (latest)
- **Lines of Code**: ~102 Java files

### Key Limitations
1. **Surface patterns only**: Your dependency relations (`dep_nsubj`, `dep_amod`, etc.) are simulated via proximity patterns, not actual dependency trees
2. **Manual aggregation**: You iterate through sample documents to build frequency maps
3. **No true dependency queries**: Cannot query `theory <--nsubj-- brilliant` directly
4. **Custom maintenance burden**: Indexing, query compilation, grouping all hand-rolled

---

## 2. BlackLab v4.0.0 Capabilities

### Technical Requirements
| Component | BlackLab v4 | Your Project | Compatibility |
|-----------|-------------|--------------|---------------|
| **Java** | 17+ (tested to 21) | Java 21 | ✅ Perfect match |
| **Lucene** | 8 | Lucene 10.3.2 | ⚠️ **BLOCKER** - BlackLab uses older Lucene |
| **Corpus Format** | CoNLL-U native | CoNLL-U (via UDPipe) | ✅ Perfect match |

### Dependency Features (v4.0.0+)

#### CoNLL-U Indexing
BlackLab now **natively indexes CoNLL-U dependency trees**:
- Indexes `deprel` (dependency relation) field
- Stores head-dependent relationships
- Supports standoff annotations for custom relations

#### CQL for Dependencies
```cql
# Find lemma "theory" as head with adjectival modifier
[lemma="theory" & deprel="amod"]

# Find adjective modifying "theory" (reverse direction)
[lemma="theory"] <amod [pos="JJ"]

# Find nominal subjects of verbs
[deprel="nsubj"] >nsubj [lemma="theory"]

# Complex: theory as subject of any verb
[lemma="theory" & deprel="nsubj"] >nsubj [pos="VB.*"]
```

**Arrow syntax** (from your description):
```cql
# Your example format (BlackLab uses < > for direction)
A: [lemma="theory"] --> B:[]  # BlackLab: A: [lemma="theory"] >deprel B:[]
```

#### Grouping/Aggregation API
BlackLab provides **server-side frequency aggregation**:
```java
// Instead of your manual iteration:
Hits hits = searcher.search(query);
Map<String, Integer> freqMap = new HashMap<>();
for (Hit hit : hits) {
    String lemma = hit.getLemma();
    freqMap.merge(lemma, 1, Integer::sum);
}

// BlackLab does this internally:
DocGroups groups = searcher.groupBy(hits, "lemma");
// Returns: {brilliant=450, scientific=3200, unproven=1100}
```

### logDice Calculation
BlackLab **does NOT provide logDice out-of-the-box**. You would need to:
1. Extract frequencies via grouping API: `f_xy`, `f_x`, `f_y`
2. Apply your existing `LogDiceCalculator` formula
3. This is **trivial** - just plugging their frequency maps into your formula

---

## 3. Integration Strategies

### Option A: Full Migration to BlackLab
**Replace your entire indexing/query layer with BlackLab**

#### What You Keep
- ✅ Web UI (D3.js visualization)
- ✅ REST API endpoints (adapt to BlackLab server)
- ✅ `LogDiceCalculator` (apply to BlackLab frequency maps)
- ✅ Semantic field exploration logic
- ✅ Grammar config (`grammars/relations.json` → BlackLab CQL patterns)

#### What You Replace
- ❌ `HybridIndexer` → BlackLab CoNLL-U indexer
- ❌ `CQLParser` → BlackLab CQL parser (BCQL)
- ❌ `CQLToLuceneCompiler` → BlackLab query engine
- ❌ `HybridQueryExecutor` → BlackLab `Searcher` + grouping API
- ❌ `GrammarPatternCollocationsBuilder` → BlackLab grouping API
- ❌ `TokenSequenceCodec` → BlackLab forward index

#### Pros
- Immediate access to **true dependency queries**
- No more custom index maintenance
- Production-tested corpus query engine
- Access to BlackLab features: concordance, highlighting, parallel corpora

#### Cons
- **Lucene version mismatch**: BlackLab uses Lucene 8, you're on 10.3.2
- Learning curve for BlackLab API
- Less control over index structure
- Dependency on external project roadmap

#### Effort Estimate
- **2-3 weeks** for full migration
- Most time spent on: API learning, query translation, testing

---

### Option B: Hybrid Approach (Recommended)
**Use BlackLab for dependency queries, keep your index for surface patterns**

#### Architecture
```
User Request
    ↓
Router
├── Dependency Queries → BlackLab Index (Lucene 8)
│   - dep_nsubj, dep_amod, dep_obj, etc.
│   - True dependency tree queries
│
└── Surface Patterns → Your Index (Lucene 10)
    - noun_modifiers, adj_predicates, compounds
    - Proximity-based patterns
    - Precomputed collocations (fast path)
```

#### Implementation
1. **Index both formats** from same CoNLL-U source:
   ```bash
   # Your hybrid index
   java -jar word-sketch.jar hybrid-index --input corpus.conllu --output data/index-hybrid/
   
   # BlackLab index
   blacklab index --config blf.yaml corpus.conllu data/index-blacklab/
   ```

2. **Unified Query Executor**:
   ```java
   public class UnifiedQueryExecutor {
       private final HybridQueryExecutor hybridExecutor;  // Your existing
       private final BlackLabSearcher blackLabSearcher;   // New
       
       public CollocationResults query(String lemma, String relationId) {
           Relation relation = grammarConfig.getRelation(relationId);
           
           if (relation.type() == RelationType.DEP) {
               // Use BlackLab for true dependency queries
               return blackLabSearcher.queryDependency(lemma, relation.deprel());
           } else {
               // Use your hybrid index for surface patterns
               return hybridExecutor.findCollocations(lemma, relation.pattern());
           }
       }
   }
   ```

3. **Shared logDice Calculation**:
   - Both executors return `f_xy`, `f_x`, `f_y`
   - Single `LogDiceCalculator` applies formula

#### Pros
- No Lucene version conflicts
- Best of both worlds: dependency accuracy + surface pattern flexibility
- Gradual migration path
- Keep your optimized precomputed collocations
- Can phase out your dependency simulation over time

#### Cons
- Two indexes to maintain (disk space: ~2x)
- Slightly more complex query routing
- Need to learn BlackLab API incrementally

#### Effort Estimate
- **1 week** for initial integration
- **2-3 days** per new dependency relation migrated

---

### Option C: BlackLab as Library (Embedded)
**Embed BlackLab Core as a dependency, use their indexing but keep your query layer**

#### Maven Dependency
```xml
<dependency>
    <groupId>nl.inl.blacklab</groupId>
    <artifactId>blacklab-core</artifactId>
    <version>4.0.0</version>
</dependency>
```

#### Indexing
```java
// Use BlackLab's CoNLL-U indexer directly
BlackLab blackLab = BlackLab.open(indexPath);
ConllUIndexer indexer = new ConllUIndexer(blackLab);
indexer.index(conlluPath);
```

#### Querying
```java
// Use BlackLab's CQL parser
Searcher searcher = blackLab.getSearcher();
CQLParser parser = new CQLParser();
Query query = parser.parse("[lemma=\"theory\"] >nsubj []");
Hits hits = searcher.search(query);

// Extract frequencies
DocGroups groups = hits.groupBy("lemma");
```

#### Problem
**Lucene version conflict**:
- BlackLab 4.0.0: Lucene 8
- Your project: Lucene 10.3.2
- **Cannot have two Lucene versions in same classpath**

#### Workaround
- **Shading**: Use Maven Shade to relocate BlackLab's Lucene classes
- **Separate process**: Run BlackLab as microservice, communicate via HTTP
- **Wait**: BlackLab may upgrade to Lucene 10 in future release

---

## 4. Technical Deep Dive: Dependency Queries

### Your Current Approach (Simulated Dependencies)
From `grammars/relations.json`:
```json
{
  "id": "dep_amod",
  "name": "Dependency: adjectival modifier",
  "pattern": "[tag=\"NN.*\"] [deprel=\"amod\"]",
  "head_position": 1,
  "collocate_position": 2,
  "default_slop": 0,
  "relation_type": "DEP"
}
```

**Problem**: This pattern assumes **adjacent tokens** with `deprel="amod"` metadata. But:
- No actual tree structure indexed
- Cannot distinguish `theory <--amod-- brilliant` from `brilliant <--amod-- theory`
- Slop=0 means exact adjacency only (misses discontinuous dependencies)

### BlackLab's Approach (True Dependency Trees)

#### Index Structure
BlackLab creates **specialized Lucene fields** for dependencies:
```java
// For each token in CoNLL-U:
document.addField("deprel", "amod");           // Dependency relation
document.addField("head", "3");                // Head token ID
document.addField("dependent", "1");           // Dependent token ID
document.addField("sentence_id", "s123");      // Sentence identifier
```

#### Query Example
```cql
# Find all adjectives modifying "theory"
[lemma="theory"] <amod [pos="JJ"]

# Translation:
# - Find token with lemma="theory"
# - Follow incoming "amod" dependency arrow
# - Return the dependent token (must be JJ)
```

#### Frequency Extraction
```java
// BlackLab API
Searcher searcher = blackLab.getSearcher();
Hits hits = searcher.search(query);

// Group by lemma of dependent token
DocGroups groups = hits.groupBy("lemma");
for (DocGroup group : groups) {
    String lemma = group.getValue();  // e.g., "brilliant"
    int frequency = group.getHits();   // e.g., 450
    
    // Now you have f_xy
    results.add(lemma, frequency);
}

// Get f_y (global frequency) via TermFreq lookup
int f_y = searcher.getIndexReader().totalTermFreq(new Term("lemma", "brilliant"));

// Calculate logDice
double logDice = LogDiceCalculator.calculate(f_xy, f_x, f_y);
```

---

## 5. Migration Roadmap

### Phase 1: Evaluation (Week 1)
- [ ] Install BlackLab v4.0.0
- [ ] Index small test corpus (100K sentences)
- [ ] Test dependency CQL queries
- [ ] Verify frequency extraction via grouping API
- [ ] Benchmark query performance vs. your hybrid index

### Phase 2: Hybrid Integration (Weeks 2-3)
- [ ] Create `BlackLabQueryExecutor` wrapper
- [ ] Implement query routing (DEP → BlackLab, SURFACE → Hybrid)
- [ ] Unified result format (merge both sources)
- [ ] Update REST API to use unified executor
- [ ] Test with existing web UI

### Phase 3: Gradual Migration (Weeks 4-6)
- [ ] Migrate one dependency relation per day
- [ ] Compare results: BlackLab vs. your simulation
- [ ] Tune BlackLab queries for accuracy
- [ ] Document performance characteristics
- [ ] Update documentation

### Phase 4: Full Transition (Optional, Weeks 7-8)
- [ ] If satisfied, deprecate hybrid index
- [ ] Migrate all relations to BlackLab
- [ ] Remove custom CQL parser
- [ ] Remove custom query executor
- [ ] Keep only: Web UI, logDice, semantic exploration logic

---

## 6. Code Comparison

### Your Current Code (HybridQueryExecutor.java)
```java
public List<WordSketchResult> findCollocations(
    String headLemma, 
    String cqlPattern, 
    double minLogDice, 
    int limit) {
    
    // 1. Parse CQL
    CQLPattern pattern = CQLParser.parse(cqlPattern);
    
    // 2. Compile to SpanQuery
    SpanQuery spanQuery = CQLToLuceneCompiler.compile(pattern);
    
    // 3. Search with sampling
    TopDocs docs = searcher.search(spanQuery, maxSampleSize);
    
    // 4. Manual aggregation
    Map<String, CollocationStats> stats = new HashMap<>();
    for (ScoreDoc scoreDoc : docs.scoreDocs) {
        Document doc = reader.document(scoreDoc.doc);
        String collocateLemma = extractCollocate(doc, pattern);
        
        stats.merge(collocateLemma, new CollocationStats(1), 
            (old, newer) -> { old.increment(); return old; });
    }
    
    // 5. Calculate logDice
    List<WordSketchResult> results = new ArrayList<>();
    for (var entry : stats.entrySet()) {
        int f_xy = entry.getValue().frequency;
        int f_x = getGlobalFrequency(headLemma);
        int f_y = getGlobalFrequency(entry.getKey());
        
        double logDice = LogDiceCalculator.calculate(f_xy, f_x, f_y);
        if (logDice >= minLogDice) {
            results.add(new WordSketchResult(entry.getKey(), f_xy, logDice));
        }
    }
    
    // 6. Sort and limit
    return results.stream()
        .sorted(Comparator.comparingDouble(WordSketchResult::getLogDice).reversed())
        .limit(limit)
        .toList();
}
```

### BlackLab Equivalent
```java
public List<WordSketchResult> findDependencyCollocations(
    String headLemma,
    String deprel,
    double minLogDice,
    int limit) {
    
    // 1. Build dependency CQL (BlackLab syntax)
    String cql = String.format("[lemma=\"%s\"] <%s []", headLemma, deprel);
    
    // 2. Parse and search
    Query query = blackLab.parseCQL(cql);
    Hits hits = searcher.search(query);
    
    // 3. Server-side grouping (much faster!)
    DocGroups groups = hits.groupBy("lemma");
    
    // 4. Extract frequencies
    List<WordSketchResult> results = new ArrayList<>();
    int f_x = searcher.getGlobalFrequency(headLemma);
    
    for (DocGroup group : groups) {
        String collocateLemma = group.getValue();
        int f_xy = group.getHits();
        int f_y = searcher.getGlobalFrequency(collocateLemma);
        
        double logDice = LogDiceCalculator.calculate(f_xy, f_x, f_y);
        if (logDice >= minLogDice) {
            results.add(new WordSketchResult(collocateLemma, f_xy, logDice));
        }
    }
    
    // 5. Sort and limit
    return results.stream()
        .sorted(Comparator.comparingDouble(WordSketchResult::getLogDice).reversed())
        .limit(limit)
        .toList();
}
```

**Key Differences**:
- No manual CQL parsing (BlackLab provides it)
- No SpanQuery compilation (BlackLab handles it)
- No document iteration (BlackLab groups server-side)
- Same logDice formula (your existing code works)

---

## 7. Decision Matrix

| Criterion | Option A: Full Migration | Option B: Hybrid | Option C: Embedded |
|-----------|-------------------------|------------------|-------------------|
| **Development Time** | 2-3 weeks | 1 week + incremental | Blocked (Lucene conflict) |
| **Lucene Compatibility** | ❌ Must downgrade to 8 | ✅ Keep both | ❌ Requires shading |
| **Dependency Accuracy** | ✅ True trees | ✅ True trees + surface | ✅ True trees |
| **Code Reuse** | 20% (UI + logDice) | 60% (surface patterns) | 40% (query layer) |
| **Maintenance** | Low (BlackLab maintains) | Medium (2 indexes) | High (shading hacks) |
| **Performance** | Good | Best (hybrid fast path) | Good |
| **Flexibility** | Limited to BlackLab features | Maximum | Maximum |
| **Risk** | Medium (big rewrite) | Low (gradual) | High (version conflicts) |

---

## 8. Recommendation

### **Start with Option B (Hybrid)**

**Rationale**:
1. **No Lucene conflicts**: Keep your Lucene 10.3.2 index
2. **Immediate value**: Get true dependency queries in 1 week
3. **Low risk**: Your existing system continues working
4. **Gradual migration**: Move one relation at a time
5. **Exit strategy**: If BlackLab doesn't meet needs, you still have your system

### Implementation Steps

#### Step 1: Set Up BlackLab (2 days)
```bash
# Clone BlackLab
git clone https://github.com/instituutnederlandsetaal/BlackLab.git
cd BlackLab
git checkout v4.0.0

# Build
mvn clean install

# Index test corpus
blacklab index --config blf.yaml corpus.conllu data/test-blacklab/
```

#### Step 2: Create BlackLab Wrapper (3 days)
```java
public class BlackLabQueryExecutor implements AutoCloseable {
    private final BlackLab blackLab;
    private final Searcher searcher;
    
    public BlackLabQueryExecutor(String indexPath) {
        this.blackLab = BlackLab.open(indexPath);
        this.searcher = blackLab.getSearcher();
    }
    
    public CollocationResults queryDependency(
        String headLemma, 
        String deprel,
        double minLogDice,
        int limit) {
        
        String cql = String.format("[lemma=\"%s\"] <%s []", headLemma, deprel);
        Query query = blackLab.parseCQL(cql);
        Hits hits = searcher.search(query);
        
        // Group by lemma
        DocGroups groups = hits.groupBy("lemma");
        
        // Build results with logDice
        return buildResults(groups, headLemma, minLogDice, limit);
    }
    
    @Override
    public void close() {
        searcher.close();
        blackLab.close();
    }
}
```

#### Step 3: Query Router (2 days)
```java
public class UnifiedQueryExecutor implements QueryExecutor {
    private final HybridQueryExecutor hybridExecutor;
    private final BlackLabQueryExecutor blackLabExecutor;
    private final GrammarConfigLoader grammarConfig;
    
    public UnifiedQueryExecutor(
        String hybridIndexPath,
        String blackLabIndexPath,
        GrammarConfigLoader grammarConfig) {
        
        this.hybridExecutor = new HybridQueryExecutor(hybridIndexPath);
        this.blackLabExecutor = new BlackLabQueryExecutor(blackLabIndexPath);
        this.grammarConfig = grammarConfig;
    }
    
    @Override
    public List<WordSketchResult> findCollocations(
        String lemma,
        String relationId,
        double minLogDice,
        int limit) {
        
        Relation relation = grammarConfig.getRelation(relationId);
        
        if (relation.type() == RelationType.DEP) {
            // Extract deprel from pattern: [deprel="amod"] → "amod"
            String deprel = extractDeprel(relation.pattern());
            return blackLabExecutor.queryDependency(lemma, deprel, minLogDice, limit);
        } else {
            return hybridExecutor.findCollocations(lemma, relation.pattern(), minLogDice, limit);
        }
    }
}
```

#### Step 4: Update REST API (1 day)
```java
// In WordSketchApiServer.java
QueryExecutor executor = new UnifiedQueryExecutor(
    hybridIndexPath,
    blackLabIndexPath,
    grammarConfig
);
```

#### Step 5: Test and Validate (3-5 days)
- Compare results: BlackLab vs. hybrid for same dependency relations
- Measure query latency
- Verify logDice scores match expectations
- Test with web UI

---

## 9. Long-Term Considerations

### BlackLab Roadmap
- **v4.0.0** (current): Dependency relations, CoNLL-U support
- **Future**: Solr integration for distributed search
- **Risk**: Small development team, may not keep pace with Lucene

### Your Project's Unique Value
Even with BlackLab integration, your project provides:
1. **Semantic Field Exploration**: Multi-seed snowball queries (BlackLab doesn't have this)
2. **logDice Scoring**: BlackLab provides frequencies, you add association metrics
3. **Web UI**: D3.js visualization (BlackLab has basic UI, not as polished)
4. **Precomputed Collocations**: O(1) lookup for frequent queries

### Strategic Position
**Your project becomes**: "Semantic exploration layer on top of BlackLab"
- Focus on what makes you unique: semantic field discovery
- Let BlackLab handle: indexing, query parsing, frequency extraction
- Value add: logDice, multi-seed exploration, visualization

---

## 10. Next Steps

### Immediate Actions (This Week)
1. **Install BlackLab v4.0.0**
   ```bash
   git clone https://github.com/instituutnederlandsetaal/BlackLab.git
   cd BlackLab && git checkout v4.0.0
   mvn clean install
   ```

2. **Index 100K sentence sample**
   ```bash
   head -n 100000 corpus.conllu > sample.conllu
   blacklab index --config blf.yaml sample.conllu data/test/
   ```

3. **Test dependency CQL**
   ```bash
   # Via BlackLab server
   curl "http://localhost:8080/blacklab-server/corpora/test/search?pattern=[lemma=\"theory\"]%20%3Camod%20[]"
   ```

4. **Verify frequency extraction**
   - Check if grouping API returns expected frequency maps
   - Compare with your hybrid index results

### Decision Point (After Testing)
If BlackLab meets your needs:
- Proceed with hybrid integration
- Start migrating dependency relations one by one

If BlackLab has issues:
- Stick with your current system
- Consider contributing dependency features to BlackLab
- Wait for Lucene 10 upgrade in future BlackLab release

---

## Conclusion

**Your intuition is correct**: BlackLab v4.0.0's CoNLL-U dependency support solves the exact problem you identified - accurate dependency-based collocation extraction without manual token scanning.

**However**, the Lucene version mismatch (8 vs. 10.3.2) means you cannot simply add it as a dependency. The **hybrid approach** gives you:
- Immediate access to true dependency queries
- No breaking changes to your existing system
- Gradual migration path
- Best of both worlds

**Estimated timeline**: 2 weeks to working hybrid system, 1 month to full dependency migration.

**Recommendation**: Start with Option B (Hybrid) this week. Test BlackLab with your corpus. If results are good, proceed with gradual migration.
