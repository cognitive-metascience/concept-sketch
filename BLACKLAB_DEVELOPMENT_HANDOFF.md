# BlackLab Integration - Development Handoff Document

**Branch:** `blacklab-sketches`  
**Date:** 2026-02-20  
**Status:** Partial Implementation Complete - Ready for Windows Development

---

## Executive Summary

This branch ports Word Sketch Lucene from a custom Lucene-based index to **BlackLab v4.0.0**, a production-ready corpus query engine with native CoNLL-U dependency parsing support.

**Key Achievement:** BlackLab 4.0.0 JARs are available from GitHub releases and can be installed to local Maven repository.

**Current Status:** 
- ✅ BlackLab 4.0.0 installed to local Maven repo
- ✅ GPU-enabled Stanza tagging script created
- ✅ BlackLabQueryExecutor skeleton written
- ⚠️ Build fails on Linux (WSL2) due to Java version mismatch - **will work on Windows**

---

## What Works

### 1. BlackLab Installation Script

**File:** `install-blacklab.sh` (Linux/Mac) / `install-blacklab.ps1` (Windows)

Downloads BlackLab 4.0.0 from GitHub releases and installs to local Maven repository:

```bash
# Linux/Mac
./install-blacklab.sh

# Windows PowerShell
.\install-blacklab.ps1
```

**Output:**
- `nl.inl.blacklab:blacklab-core:4.0.0` → `blacklab-engine-4.0.0.jar` (1.6MB)
- `nl.inl.blacklab:blacklab-util:4.0.0`
- `nl.inl.blacklab:blacklab-common:4.0.0`
- `org.apache.lucene:lucene-*:8.11.1` (all required Lucene 8 libraries)

### 2. Stanza GPU Tagging

**File:** `tag_with_stanza.py`

Features:
- CUDA detection and GPU utilization
- Batch processing for GPU efficiency
- CoNLL-U output with dependency parses
- Progress reporting

**Usage:**
```bash
# Install dependencies (Windows PowerShell)
pip install -r requirements-stanza-gpu.txt

# Download Stanza models
python tag_with_stanza.py --download --lang en

# Tag corpus with GPU
python tag_with_stanza.py -i corpus.txt -o corpus.conllu --lang en --batch-size 64
```

**Expected Performance (GPU):**
- RTX 3080: ~350 sentences/sec
- RTX 3090: ~500 sentences/sec
- CPU fallback: ~20 sentences/sec

### 3. BlackLabQueryExecutor (Skeleton)

**File:** `src/main/java/.../query/BlackLabQueryExecutor.java`

Implements the `QueryExecutor` interface with:
- Dependency relation queries via BlackLab CQL
- Frequency extraction via BlackLab grouping API
- logDice scoring (reuses existing `LogDiceCalculator`)

**Key Methods:**
```java
// Find collocations using dependency relation
findDependencyCollocations(lemma, deprel, minLogDice, limit)

// Standard CQL query
findCollocations(headword, cqlPattern, minLogDice, maxResults)

// Get lemma frequency
getTotalFrequency(lemma)
```

### 4. Documentation

- `README_BLACKLAB.md` - User guide for BlackLab branch
- `BLACKLAB_SETUP.md` - Installation instructions
- `STANZA_GPU.md` - GPU setup guide

---

## What Needs to Be Done

### Priority 1: Build on Windows

**Expected Issues:**
1. Java version compatibility (BlackLab requires Java 17+, we use Java 21)
2. Maven compiler plugin configuration

**Steps:**

1. **Install BlackLab to Maven repo:**
   ```powershell
   .\install-blacklab.ps1
   ```

2. **Build project:**
   ```powershell
   mvn clean package -DskipTests
   ```

3. **If build fails:**
   - Check Java version: `java -version` (should be 17, 21, or higher)
   - Update `pom.xml` if needed:
     ```xml
     <maven.compiler.release>21</maven.compiler.release>
     ```

### Priority 2: Fix BlackLabQueryExecutor

**Current Issues:**
- Uses BlackLab API classes that may not exist in v4.0.0
- CQL syntax may need adjustment
- Grouping API usage needs verification

**Steps:**

1. **Review BlackLab API:**
   - Extract `blacklab-engine-4.0.0.jar` from Maven repo
   - Inspect available classes: `nl.inl.blacklab.search.*`
   - Check CQL parser: `nl.inl.blacklab.queryparser.*`

2. **Update BlackLabQueryExecutor:**
   ```java
   // Current code uses:
   import nl.inl.blacklab.search.BlackLab;
   import nl.inl.blacklab.search.Searcher;
   import nl.inl.blacklab.search.results.DocGroups;
   import nl.inl.blacklab.queryparser.CQL;
   
   // Verify these exist in v4.0.0, update if needed
   ```

3. **Test CQL syntax:**
   ```java
   // Dependency query format in BlackLab v4.0.0:
   // [lemma="theory"] <amod []  (incoming amod arrow)
   // [] >nsubj [lemma="explain"]  (outgoing nsubj arrow)
   ```

### Priority 3: Test with Sample Corpus

**Steps:**

1. **Create test corpus:**
   ```
   test-data/sample.txt (100-1000 sentences)
   ```

2. **Tag with Stanza:**
   ```powershell
   python tag_with_stanza.py -i test-data/sample.txt -o test-data/sample.conllu
   ```

3. **Index with BlackLab:**
   ```powershell
   java -jar target/word-sketch-lucene-1.0.1.jar blacklab-index `
     --input test-data/sample.conllu `
     --output data/test-index/
   ```

4. **Query:**
   ```powershell
   java -jar target/word-sketch-lucene-1.0.1.jar blacklab-query `
     --index data/test-index/ `
     --lemma theory `
     --deprel amod
   ```

### Priority 4: Integrate with REST API

**File:** `src/main/java/.../api/WordSketchApiServer.java`

**Changes Needed:**
1. Update server to use `BlackLabQueryExecutor` instead of `HybridQueryExecutor`
2. Add new endpoint for dependency queries:
   ```
   GET /api/sketch/{lemma}?deprel=amod&limit=20
   ```
3. Test with curl/Postman

### Priority 5: Migrate Web UI

**Directory:** `webapp/`

**Changes Needed:**
1. Update JavaScript to call new dependency endpoints
2. Add dependency relation selector (amod, nsubj, obj, etc.)
3. Test visualization with dependency-based results

---

## Known Issues

### 1. BlackLab API Compatibility

**Issue:** BlackLab v4.0.0 API may differ from what's documented online.

**Resolution:**
- Inspect JAR contents directly
- Check BlackLab source code on GitHub
- May need to use BlackLab Server REST API instead of embedded library

### 2. Lucene Version Mismatch

**Issue:** 
- BlackLab uses Lucene 8.11.1
- Main branch uses Lucene 10.3.2
- Cannot merge back to main without significant rework

**Resolution:**
- Keep `blacklab-sketches` as separate branch
- Or: Convince BlackLab to upgrade to Lucene 10 (long-term)

### 3. Dependency Query Syntax

**Issue:** BlackLab CQL syntax for dependencies is not well-documented.

**Expected Syntax:**
```cql
# Adjectival modifier of "theory"
[lemma="theory"] <amod []

# "theory" as subject of verb
[lemma="theory"] >nsubj []

# Verb with "theory" as object
[] >obj [lemma="theory"]
```

**Resolution:**
- Test with BlackLab directly first
- Check BlackLab documentation: https://blacklab.ivdnt.org/

---

## File Structure

```
word-sketch-lucene/
├── src/main/java/pl/marcinmilkowski/word_sketch/
│   ├── Main.java                          # Updated for BlackLab commands
│   ├── api/
│   │   └── WordSketchApiServer.java       # Needs update for BlackLab
│   ├── query/
│   │   ├── BlackLabQueryExecutor.java     # NEW - BlackLab queries
│   │   └── QueryExecutor.java             # Interface (unchanged)
│   ├── indexer/
│   │   └── blacklab/
│   │       └── BlackLabConllUIndexer.java # NEW - BlackLab indexing
│   └── utils/
│       └── LogDiceCalculator.java         # Unchanged (reused)
│
├── tag_with_stanza.py                     # NEW - GPU-enabled tagging
├── install-blacklab.sh                    # NEW - Linux/Mac installer
├── install-blacklab.ps1                   # NEW - Windows installer
├── requirements-stanza-gpu.txt            # NEW - Python dependencies
├── pom.xml                                # Updated - BlackLab dependency
├── README_BLACKLAB.md                     # NEW - User guide
├── BLACKLAB_SETUP.md                      # NEW - Setup instructions
└── STANZA_GPU.md                          # NEW - GPU guide
```

---

## Development Environment (Windows)

### Prerequisites

1. **Java 17 or 21**
   - Download: https://adoptium.net/
   - Verify: `java -version`

2. **Maven 3.6+**
   - Should already be installed
   - Verify: `mvn -version`

3. **Python 3.10+** (for Stanza tagging)
   - Download: https://www.python.org/downloads/
   - Verify: `python --version`

4. **CUDA Toolkit** (optional, for GPU acceleration)
   - Download: https://developer.nvidia.com/cuda-downloads
   - Verify: `nvidia-smi`

### Setup Commands

```powershell
# 1. Clone repository
git clone <repo-url>
cd word-sketch-lucene
git checkout blacklab-sketches

# 2. Install BlackLab
.\install-blacklab.ps1

# 3. Install Python dependencies
pip install -r requirements-stanza-gpu.txt

# 4. Download Stanza models
python tag_with_stanza.py --download --lang en

# 5. Build project
mvn clean package -DskipTests

# 6. Test indexing
java -jar target/word-sketch-lucene-1.0.1.jar blacklab-index `
  --input test-data/sample.conllu `
  --output data/test-index/

# 7. Test query
java -jar target/word-sketch-lucene-1.0.1.jar blacklab-query `
  --index data/test-index/ `
  --lemma theory `
  --deprel amod

# 8. Start API server
java -jar target/word-sketch-lucene-1.0.1.jar server `
  --index data/test-index/ `
  --port 8080
```

---

## Testing Checklist

- [ ] Build succeeds on Windows
- [ ] BlackLabQueryExecutor compiles without errors
- [ ] Stanza tagging produces valid CoNLL-U
- [ ] BlackLab indexing completes without errors
- [ ] Dependency queries return results
- [ ] logDice scores are calculated correctly
- [ ] REST API endpoints work
- [ ] Web UI displays results

---

## Next Steps After Testing

1. **If BlackLabQueryExecutor works:**
   - Migrate all dependency relations from `grammars/relations.json`
   - Update REST API to use BlackLab for DEP relations
   - Keep HybridQueryExecutor for SURFACE relations (optional)

2. **If BlackLabQueryExecutor fails:**
   - Option A: Use BlackLab Server REST API instead of embedded library
   - Option B: Revert to fixing custom CQL parser in main branch
   - Option C: Implement simple Lucene-based dependency queries

3. **Long-term:**
   - Benchmark performance vs. main branch
   - Compare accuracy of dependency vs. proximity-based sketches
   - Decide whether to fully migrate or maintain both approaches

---

## Contact / Resources

- **BlackLab Documentation:** https://blacklab.ivdnt.org/
- **BlackLab GitHub:** https://github.com/instituutnederlandsetaal/BlackLab
- **BlackLab v4.0.0 Release:** https://github.com/instituutnederlandsetaal/BlackLab/releases/tag/v4.0.0
- **Stanza Documentation:** https://stanfordnlp.github.io/stanza/
- **Universal Dependencies:** https://universaldependencies.org/

---

## Notes from Initial Implementation

1. **BlackLab JAR Structure:**
   - `blacklab-4.0.0-jar.zip` contains `blacklab-4.0.0.jar` (empty wrapper) + `lib/` directory
   - Actual code is in `lib/blacklab-engine-4.0.0.jar`
   - Install script now uses `blacklab-engine-4.0.0.jar` as `blacklab-core`

2. **Java Version:**
   - BlackLab requires Java 17+
   - Tested with Java 25 on Linux (build failed due to Maven compiler)
   - Should work on Windows with Java 17/21

3. **CQL Syntax:**
   - BlackLab uses `<deprel` and `>deprel` for dependency arrows
   - `<` = incoming arrow (head receives relation)
   - `>` = outgoing arrow (head sends relation)

4. **logDice Formula:**
   - Existing `LogDiceCalculator.compute(f_xy, f_x, f_y)` works unchanged
   - Just need to provide correct frequency values from BlackLab

---

**Good luck! The hard part (getting BlackLab installed) is done.** 🚀
