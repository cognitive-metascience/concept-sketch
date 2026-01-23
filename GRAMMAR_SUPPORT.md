# Word Sketch Grammar Support Summary

## Test Results for sketchgrammar.wsdef.m4 Patterns

### Core POS Patterns (All Working)
| CQL Pattern | Description | Status |
|-------------|-------------|--------|
| `[tag="jj.*"]` | Adjectives | ✅ Working |
| `[tag="nn.*"]` | Nouns | ✅ Working |
| `[tag="vb.*"]` | Verbs (base form) | ✅ Working |
| `[tag="rb.*"]` | Adverbs | ✅ Working |
| `[tag="in"]` | Prepositions | ✅ Working |
| `[tag="dt"]` | Determiners | ✅ Working |
| `[tag="prp"]` | Pronouns | ✅ Working |
| `[tag="cc"]` | Conjunctions | ✅ Working |

### Distance Modifiers (All Working)
| CQL Pattern | Description | Status |
|-------------|-------------|--------|
| `[tag="jj.*"]~{0,3}` | Adj within 3 words | ✅ Working |
| `[tag="nn.*"]~{-5,0}` | Noun 1-5 words before | ✅ Working |
| `[tag="vb.*"]~{0,5}` | Verb within 5 words | ✅ Working |
| `[tag="rb.*"]~{0,2}` | Adverb within 2 words | ✅ Working |

### Constraint Types (Mixed Support)
| CQL Pattern | Description | Status |
|-------------|-------------|--------|
| `[tag="jj.*"]` | Basic constraint | ✅ Working |
| `[word="the"]` | Word constraint | ✅ Working |
| `[tag!="nn.*"]` | Negation | ✅ Working |
| `[tag="jj.*" \| tag="rb.*"]` | OR constraint | ⚠️ Partial* |
| `[tag="pp" & word!="I"]` | AND constraint | ✅ Working |

*OR constraint is parsed but only first pattern used for tag filtering

### Macro Support
| Macro | Expansion | Status |
|-------|-----------|--------|
| `NOUN` | `"N.*[^Z]"` | ✅ Works as tag pattern |
| `ADJECTIVE` | `"JJ.*"` | ✅ Works as tag pattern |
| `VERB` | `"V.*"` | ✅ Works as tag pattern |
| `VERB_BE` | `"VB.*"` | ✅ Works as tag pattern |
| `VERB_HAVE` | `"VH.*"` | ✅ Works as tag pattern |
| `ADVERB` | `"RB.*"` | ✅ Works as tag pattern |
| `NOT_NOUN` | `[tag!="N.*"]` | ✅ Working |
| `DETERMINER` | `[tag="DT"\|tag="PPZ"]` | ⚠️ Partial* |
| `MODIFIER` | `[tag="JJ.*"\|tag="RB.*"\|word=","]` | ⚠️ Partial* |
| `WHO_WHICH_THAT` | `[tag="WP"\|tag="IN/that"]` | ⚠️ Partial* |

### Unsupported / Partial Features
| Feature | CQL Example | Status | Notes |
|---------|-------------|--------|-------|
| Labeled positions | `1:"VB.*"` | ❌ Not supported | Would require headword reference |
| Agreement rules | `& 1.tag = 2.tag` | ❌ Not implemented | Semantic checking not done |
| Lemma substitution | `%(3.lemma)` | ❌ Not implemented | Would require context |
| Multi-alternative | `Pattern1 --- Pattern2` | ⚠️ Parsed | Not used in execution |
| Repetition | `"VB.*"{0,2}` | ⚠️ Parsed | Not applied to filtering |
| TRINARY/DUAL | (grammar directives) | ❌ Not applicable | Index-time feature |

### Pattern Translation Examples
From sketchgrammar.wsdef.m4 to Working CQL:

| Original Pattern | Working CQL |
|-----------------|-------------|
| `ADJECTIVE ~ {0,3}` | `[tag="jj.*"]~{0,3}` |
| `1:VERB ADVERB{0,2} DETERMINER{0,1} ... 2:NOUN NOT_NOUN` | `[tag="vb.*"]~{0,5} [tag="nn.*"]~{0,5}` |
| `2:NOUN ... 1:"V.[^N]?"` | `[tag="nn.*"]~{-5,0} [tag="vb.*"]` |
| `MODIFIER` | `[tag="jj.*"]` or `[tag="rb.*"]` |
| `WHO_WHICH_THAT` | `[tag="wp"]` |

## Summary
- **Basic POS patterns**: 8/8 working (100%)
- **Distance modifiers**: 4/4 working (100%)
- **Constraint types**: 4/5 working (80%)
- **Macros**: 5/10 fully working (50%)

### Key Limitations
1. Labeled positions require headword reference - not yet implemented
2. OR constraints only use first pattern for filtering
3. Complex multi-element patterns simplified to single constraint + distance

### Recommendations for Full Support
1. Implement headword-aware query builder for labeled positions
2. Extend OR constraint handling to filter on multiple patterns
3. Add agreement rule validation in post-processing
