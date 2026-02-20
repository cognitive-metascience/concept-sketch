# Word Sketch Lucene - BlackLab Edition

This branch (`blacklab-sketches`) uses [BlackLab](https://github.com/instituutnederlandsetaal/BlackLab) as the indexing and query backend, providing native CoNLL-U dependency tree support.

## Key Differences from Main Branch

| Feature | Main Branch | BlackLab Branch |
|---------|-------------|-----------------|
| **Index** | Custom Lucene hybrid index | BlackLab CoNLL-U index |
| **Lucene** | 10.3.2 | 8.11.2 (via BlackLab) |
| **Java** | 21 | 17+ |
| **Dependency Queries** | Simulated via proximity | Native dependency trees |
| **CQL Parser** | Custom (buggy) | BlackLab CQL (production-ready) |
| **Tagging** | UDPipe | Stanza (recommended) |

## Quick Start

### 1. Build

```bash
mvn clean package
```

### 2. Tag Corpus with Stanza

```bash
# Install Stanza (Python)
pip install stanza

# Tag corpus (produces CoNLL-U with dependency parses)
python tag_with_stanza.py --input corpus.txt --output corpus.conllu --lang en
```

### 3. Index with BlackLab

```bash
java -jar target/word-sketch-lucene-1.0.1.jar blacklab-index \
  --input corpus.conllu \
  --output data/index/
```

### 4. Query

```bash
# Find adjectival modifiers of "theory"
java -jar target/word-sketch-lucene-1.0.1.jar blacklab-query \
  --index data/index/ \
  --lemma theory \
  --deprel amod

# Find nominal subjects of "explain"
java -jar target/word-sketch-lucene-1.0.1.jar blacklab-query \
  --index data/index/ \
  --lemma explain \
  --deprel nsubj
```

### 5. Start API Server

```bash
java -jar target/word-sketch-lucene-1.0.1.jar server \
  --index data/index/ \
  --port 8080
```

### 6. Query API

```bash
# Get all collocations for "theory"
curl "http://localhost:8080/api/sketch/theory"

# Get dependency collocations (amod)
curl "http://localhost:8080/api/sketch/theory?deprel=amod"

# Get dependency collocations (nsubj)
curl "http://localhost:8080/api/sketch/theory?deprel=nsubj"
```

### 7. Web Interface

```bash
# Terminal 2
python -m http.server 3000 --directory webapp
```

Open browser: **http://localhost:3000**

---

## Dependency Relations

BlackLab supports all Universal Dependencies relations:

| Relation | Description | Example |
|----------|-------------|---------|
| `amod` | Adjectival modifier | _big_ theory |
| `nsubj` | Nominal subject | theory _explains_ |
| `obj` | Direct object | _develop_ theory |
| `nmod` | Nominal modifier | government _policy_ |
| `advmod` | Adverbial modifier | _quickly_ run |
| `compound` | Compound | _coffee_ house |
| `case` | Case marker | theory _of_ |
| `det` | Determiner | _the_ theory |
| `cop` | Copula | theory _is_ |
| `aux` | Auxiliary | _will_ explain |

---

## CQL Syntax for Dependencies

BlackLab uses arrow notation for dependency relations:

```cql
# Find adjectives modifying "theory"
[lemma="theory"] <amod []

# Find words that "theory" is subject of
[lemma="theory"] >nsubj []

# Find verbs with "theory" as object
[] >obj [lemma="theory"]

# Complex: theory as subject of any verb
[lemma="theory" & deprel="nsubj"] >nsubj [pos="VB.*"]
```

**Arrow direction:**
- `<deprel` = incoming arrow (head receives relation)
- `>deprel` = outgoing arrow (head sends relation)

---

## Architecture

```
corpus.txt
    ↓
Stanza (Python)
    ↓
corpus.conllu (CoNLL-U with deprel)
    ↓
BlackLabConllUIndexer
    ↓
BlackLab Index (Lucene 8)
    ↓
BlackLabQueryExecutor
    ↓
logDice scoring (existing code)
    ↓
REST API + Web UI (existing)
```

---

## What's Preserved from Main Branch

- ✅ `LogDiceCalculator` - association scoring
- ✅ Web UI - D3.js visualizations
- ✅ REST API structure - same endpoints
- ✅ Semantic exploration logic - multi-seed, snowball
- ✅ Grammar config concept - `grammars/relations.json`

---

## What's Replaced

- ❌ Custom Lucene indexer → BlackLab indexer
- ❌ Custom CQL parser → BlackLab CQL
- ❌ Custom query executor → BlackLab Searcher
- ❌ Proximity-based dependency simulation → True dependency trees

---

## Configuration

### BlackLab Index Config (Optional)

Create `blf.yaml` for advanced indexing options:

```yaml
fileType: conllu
encoding: UTF-8
metadata:
  - name: corpus
    value: my-corpus
annotations:
  - word
  - lemma
  - pos
  - deprel
```

---

## Performance

| Task | Time |
|------|------|
| Index 1M sentences | ~5-10 min |
| Dependency query | 10-100 ms |
| API request | ~50 ms |

---

## Troubleshooting

### BlackLab not found in Maven

BlackLab is not in Maven Central. Add this to your `pom.xml`:

```xml
<repositories>
    <repository>
        <id>blacklab</id>
        <url>https://raw.githubusercontent.com/instituutnederlandsetaal/BlackLab/mvn-repo/</url>
    </repository>
</repositories>
```

### Stanza model not found

```bash
python -c "import stanza; stanza.download('en')"
```

### CQL parse error

Check BlackLab CQL syntax - it differs from the custom CQL in the main branch:
- Use `<deprel` and `>deprel` for dependency arrows
- Use `[lemma="X"]` not `[word="X"]` for lemmas
- Use `[pos="NN.*"]` not `[tag="NN.*"]`

---

## Migration from Main Branch

If you have existing code using the main branch:

1. **Update imports**:
   ```java
   // Old
   import pl.marcinmilkowski.word_sketch.query.HybridQueryExecutor;
   
   // New
   import pl.marcinmilkowski.word_sketch.query.BlackLabQueryExecutor;
   ```

2. **Update CQL patterns**:
   ```java
   // Old (proximity-based)
   "[tag=\"NN.*\"] [deprel=\"amod\"]"
   
   // New (dependency-based)
   "[lemma=\"theory\"] <amod []"
   ```

3. **Re-index corpus** with BlackLab instead of hybrid indexer

---

## Next Steps

1. **Test with your corpus**
2. **Compare results** vs. main branch (accuracy, speed)
3. **Migrate semantic exploration** features (snowball, multi-seed)
4. **Update documentation** based on real usage

---

## References

- [BlackLab Documentation](https://blacklab.ivdnt.org/)
- [BlackLab GitHub](https://github.com/instituutnederlandsetaal/BlackLab)
- [Universal Dependencies](https://universaldependencies.org/)
- [Stanza](https://stanfordnlp.github.io/stanza/)
