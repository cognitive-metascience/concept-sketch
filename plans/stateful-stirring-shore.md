# Word Sketch - Sentence Display & BCQL Fixes Specification

## Context

User reported:
1. **Sentence Display Too Long**: Shows entire documents (~500 words) instead of single sentences
2. **BCQL Shows "Unknown"**: Query results display "unknown" instead of actual collocate lemmas

### BlackLab Documentation Insights

From deepwiki research:
- Sentence context via `context=s` requires `<s>` inline tags in indexed data (not available)
- Use `Kwic` class with `before()`, `match()`, `after()` methods
- Token spacing is implicit in XML structure: `</w> <w` = space between words

### User-Requested Approach: Poor-Man's Sentence Segmentation

Since BlackLab sentence tags aren't indexed, implement sentence boundary detection in Java:
1. Get context from BlackLab (already done)
2. In plain text, find last `.!?` to the LEFT of match
3. Find first `.!?` to the RIGHT of match
4. Extract only that sentence portion

## Requirements

### 1. Fix Sentence Display

In `BlackLabQueryExecutor.java`:
- After getting full context from BlackLab
- Find sentence boundaries using regex: `/[.!?]+/`
- Extract single sentence containing the match

### 2. Fix BCQL Collocate Extraction

The issue: `extractCollocateFromMatchText()` receives XML format:
```xml
<w lemma="theory">theory</w> <w lemma="be">is</w> <w lemma="correct">correct</w>
```

Fix: Parse XML to extract `lemma` attribute from the correct position (labeled "2:").

### 3. Add Raw XML Toggle to BCQL Tab

In `webapp/index.html`:
- Add checkbox in BCQL tab (around line 430)
- Toggle between plain text and raw XML display

### 4. E2E Test

Create test verifying:
- Single sentences returned (bounded by `.!?`)
- Collocate lemmas correctly extracted (not "unknown")

## Implementation

### Files to Modify

1. **`src/main/java/pl/marcinmilkowski/word_sketch/query/BlackLabQueryExecutor.java`**
   - Add `extractSingleSentence()` method - finds sentence boundaries in plain text
   - Fix `extractCollocateFromMatchText()` - parse XML to get lemma at position 2

2. **`webapp/index.html`**
   - Add raw XML toggle checkbox in BCQL tab

### Verification Steps

1. Rebuild and restart API server
2. Test: `GET /api/sketch/theory/noun_adj_predicates`
3. Verify: Single sentences (~10-50 words), not documents
4. Verify: Collocates show "irrelevant", "correct" not "unknown"
5. Test BCQL tab raw XML toggle
