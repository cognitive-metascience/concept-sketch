# BlackLab 4.0.0 API Notes

Reference notes captured during development to replace deleted API-introspection
test stubs (`FindKwicTest`, `FindKwicTest2`).

---

## Concordance API (BlackLab 4.x)

### `Kwic` / `Kwics` are gone

In BlackLab 3.x there were `Kwic` and `Kwics` classes for keyword-in-context
retrieval. **Both are removed in BlackLab 4.0.0.** Use `Concordance` /
`Concordances` instead.

| BlackLab 3.x | BlackLab 4.x |
|---|---|
| `hits.kwics(contextSize)` | `hits.concordances(contextSize, ConcordanceType.FORWARD_INDEX)` |
| `Kwic kwic = kwics.get(hit)` | `Concordance conc = concordances.get(hit)` |
| `kwic.match(annotation)` → `List<String>` | `conc.parts()` → `String[3]` (XML strings) |
| `kwic.left(annotation)` | `conc.parts()[0]` |
| `kwic.right(annotation)` | `conc.parts()[2]` |

### `Concordance.parts()` format

```java
Concordances concordances = hits.concordances(
    ContextSize.get(60, 60, Integer.MAX_VALUE),
    ConcordanceType.FORWARD_INDEX
);

Concordance conc = concordances.get(hit);
String[] parts = conc.parts();
// parts[0] = left context XML
// parts[1] = match XML
// parts[2] = right context XML
```

Each part is an XML fragment with `<w>` elements containing per-token attributes,
e.g.:
```xml
<w lemma="theory" upos="NOUN" xpos="NN" deprel="nsubj">Theory</w>
```

Strip tags to get plain text: `xml.replaceAll("<[^>]+>", "")`.

---

## `ContextSize.get()` — Critical Overload Pitfall

BlackLab 4.x has **four** overloads. The two-argument form is a trap:

```java
// WRONG — looks like (left=60, right=60) but is (size=60, maxSnippet=60)
ContextSize.get(60, 60)
// → clips entire concordance to 60 tokens total; match + right are empty
```

**Always use the three-argument form:**

```java
// CORRECT
ContextSize.get(int before, int after, int maxSnippetLength)
ContextSize.get(60, 60, Integer.MAX_VALUE)
```

Full overload reference:

| Signature | Notes |
|---|---|
| `get(int size, int maxSnippetLength)` | before=after=size; avoid — looks like (left,right) |
| `get(int before, int after, int maxSnippetLength)` | **use this** |
| `get(int before, int after, boolean includeHit, int maxSnippetLength)` | explicit includeHit |
| `get(int before, int after, boolean includeHit, String tagName, int maxSnippetLength)` | full form |

---

## Relevant imports (BlackLab 4.0.0)

```java
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.Concordance;
import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.results.*;   // Hits, Hit, Concordances, ContextSize, HitGroups, …
import nl.inl.blacklab.searches.SearchHits;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.search.textpattern.CompleteQuery;
import nl.inl.blacklab.queryParser.contextql.ContextualQueryLanguageParser;  // CQL
import nl.inl.blacklab.queryParser.corpusql.CorpusQueryLanguageParser;       // BCQL
import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.indexmetadata.*;   // AnnotatedField, Annotation, …
import nl.inl.blacklab.resultproperty.*;          // HitProperty, HitPropertyHitText, …
import nl.inl.blacklab.search.TermFrequencyList;
```

---

## Parser choice: CQL vs BCQL

| Parser | Class | Use for |
|---|---|---|
| CQL (Contextual Query Language) | `ContextualQueryLanguageParser` | `/api/query` endpoint, simple keyword search |
| BCQL (Corpus Query Language) | `CorpusQueryLanguageParser` | `/api/bcql` endpoint, token-level patterns, dependency queries |

```java
// CQL
CompleteQuery cq = ContextualQueryLanguageParser.parse(blackLabIndex, cqlPattern);
TextPattern tp = cq.pattern();

// BCQL
TextPattern tp = CorpusQueryLanguageParser.parse(bcqlPattern, "lemma");
```

---

## Sentence boundary tags in the index

The corpus is indexed with `<s>` / `</s>` inline tags marking sentence spans
(see `conllu-sentences.blf.yaml`). These appear in the raw XML returned by
`concordance.parts()`:

```
left:  "… previous sentence.</s> <s>Start of current sentence"
right: "end of current sentence.</s> <s>Next sentence …"
```

Use this to trim concordances to a single sentence:
- **Left**: keep text after the **last** `<s>` open tag → `trimLeftXmlAtSentence()`
- **Right**: keep text before the **first** `</s>` close tag → `trimRightXmlAtSentence()`

Implementation: `BlackLabQueryExecutor.java`, methods `trimLeftXmlAtSentence` /
`trimRightXmlAtSentence` (using compiled patterns `XML_SENT_OPEN` / `XML_SENT_CLOSE`).
