# Word Sketch CQL Grammar Support

This document describes the current status of CQL (Corpus Query Language) grammar support in word-sketch-lucene.

## Supported Features

| Feature | Syntax | Status | Notes |
|---------|--------|--------|-------|
| Basic unlabeled positions | `A B C` | ✅ Supported | Elements separated by whitespace |
| Tag constraints | `[tag="JJ"]` | ✅ Supported | Match specific POS tags |
| Field constraints | `[lemma="dog"]` | ✅ Supported | Match lemmas directly |
| Wildcards | `*` (any), `?` (single char) | ✅ Supported | In patterns like `[tag="JJ.*"]` |
| OR constraints | `[tag="JJ"\|tag="RB"]` | ✅ Supported | Match multiple tag values |
| Negation | `[tag!="NN"]` | ✅ Supported | Exclude matching tokens |
| Distance modifiers | `{min,max}` | ✅ Supported | Control word distance |
| Labeled positions | `1:NOUN 2:.*` | ⚠️ Partial | Position labels parsed but not enforced |
| Repetition | `{0,3}` | ⚠️ Parsed | Used for distance, not actual repetition |
| AND constraints | `[tag="JJ" & word!="the"]` | ⚠️ Partial | Requires post-filtering for negation |
| Agreement rules | `& 1.tag = 2.tag` | ❌ Not yet | Would need special handling |
| Lemma substitution | `%(1.lemma)` | ❌ Not yet | Would need runtime substitution |

## Pattern Syntax

### Tag Constraints
```
[tag="JJ"]          - Match adjectives
[tag="NN.*"]        - Match any noun (wildcard)
[tag!="VB.*"]       - Exclude verbs
[tag="JJ" | tag="RB"] - Match adjectives OR adverbs
```

### Field Constraints
```
[lemma="dog"]       - Match lemma exactly
[word="running"]    - Match word form
[pos_group="noun"]  - Match broad POS category
```

### Distance Modifiers
```
~{0,3}              - Within 3 words (default)
~{1,5}              - Between 1 and 5 words
```

### Examples

```bash
# Adjectives modifying 'fox'
mvn exec:java -Dexec.mainClass=pl.marcinmilkowski.word_sketch.Main -Dexec.args="query --index <index> --lemma fox --pattern '[tag=JJ]' --limit 10"

# Verbs near 'problem'
mvn exec:java -Dexec.mainClass=pl.marcinmilkowski.word_sketch.Main -Dexec.args="query --index <index> --lemma problem --pattern '[tag=VB.*]~{0,5}' --limit 10"

# Adjectives OR adverbs
mvn exec:java -Dexec.mainClass=pl.marcinmilkowski.word_sketch.Main -Dexec.args="query --index <index> --lemma time --pattern '[tag=\"JJ\"|tag=\"RB\"]' --limit 10"
```

## Known Limitations

1. **Labeled positions** are parsed but the current implementation doesn't enforce position constraints between different pattern elements. The headword is always at position 0.

2. **Repetition modifiers** like `{2,4}` are parsed but the current algorithm doesn't enforce exact repetition counts.

3. **AND constraints** with negation (like `word!="the"`) require post-filtering which isn't implemented yet.

4. **Agreement rules** require comparing features across matched spans, which needs more sophisticated span handling.

5. **Lemma substitution** requires dynamic query modification based on matched content.

## Implementation Notes

The current implementation uses a hybrid approach:
1. Find headword occurrences via lemma search
2. Retrieve all tokens in the same sentence
3. Apply CQL constraints as post-filters
4. Aggregate collocate frequencies and compute logDice scores

This works well for most word sketch patterns but may be slower for very large corpora compared to a pure Lucene SpanQuery approach.

## Test Results

### Core POS Patterns (All Working)
| CQL Pattern | Description | Status |
|-------------|-------------|--------|
| `[tag="jj.*"]` | Adjectives | ✅ Working |
| `[tag="nn.*"]` | Nouns | ✅ Working |
| `[tag="vb.*"]` | Verbs (base form) | ✅ Working |
| `[tag="rb.*"]` | Adverbs | ✅ Working |

### Distance Modifiers (All Working)
| CQL Pattern | Description | Status |
|-------------|-------------|--------|
| `[tag="jj.*"]~{0,3}` | Adj within 3 words | ✅ Working |
| `[tag="nn.*"]~{-5,0}` | Noun 1-5 words before | ✅ Working |
| `[tag="vb.*"]~{0,5}` | Verb within 5 words | ✅ Working |

### Constraint Types (All Working)
| CQL Pattern | Description | Status |
|-------------|-------------|--------|
| `[tag="jj.*"]` | Basic constraint | ✅ Working |
| `[word="the"]` | Word constraint | ✅ Working |
| `[tag!="nn.*"]` | Negation | ✅ Working |
| `[tag="jj.*" \| tag="rb.*"]` | OR constraint | ✅ Working |
