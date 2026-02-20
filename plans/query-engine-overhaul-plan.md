# Word Sketch Query Engine - Overhaul Plan

## Goals
1. **Accuracy**: Every collocate MUST have examples (no zero-example collocates)
2. **Speed**: Precomputed relations with O(1) lookup
3. **Expressiveness**: Full CQL pattern support in grammar JSON
4. **Completeness**: Process full index (sampling optional)

---

## Issues Identified

### Issue 1: Adjective Predicate Pattern is Wrong
- **Current**: `[head] [tag=VB.*] [tag=JJ.*]` matches ANY verb
- **Should be**: `[head] [lemma="be|appear|seem|become|..."] [tag=JJ.*]`
- **Root cause**: Copula verbs are hardcoded in code, not in grammar pattern

### Issue 2: Zero Examples for Non-Zero Frequency
- **Problem**: Mathematical impossibility - if collocate has frequency 9, concordance must find ≥9 examples
- **Root cause**: Pattern mismatch between collocation finder and concordance explorer
- **Test required**: Assert `frequency > 0 implies examples.size() > 0`

### Issue 3: Precomputation Disabled
- I disabled ALL precomputation as a workaround
- Need to RE-ENABLE with CORRECT full CQL patterns

### Issue 4: Sampling
- Current: `maxSampleSize = 10,000` limits to partial index
- Need: Optional sampling (default: full index)

---

## Phase 1: Grammar JSON - Full Expressiveness

### 1.1 Grammar Format (Simplified)
Based on m4 format - position is implicit by order in pattern:

```json
{
  "id": "noun_adj_predicates",
  "name": "Adjectives (predicative)",
  "pattern": "[tag=\"NN.*\"] [lemma=\"be|appear|seem|become|look|sound|feel|smell|taste|remain|prove\"] [tag=\"JJ.*\"]",
  "head_position": 1,
  "collocate_position": 3,
  "dual": false
}
```

**Where**:
- `pattern`: CQL pattern - position is implicit by order (1st, 2nd, 3rd...)
- `head_position`: 1-based index (which element is head)
- `collocate_position`: which element is collocate to extract
- `dual`: boolean, compute both directions

### 1.2 Support for DUAL Relations
```json
{
  "id": "object_of",
  "name": "Objects (direct)",
  "pattern": "[tag=\"VB.*\"] [tag=\"NN.*\"]",
  "head_position": 2,
  "collocate_position": 1,
  "dual": true
}
```
`dual: true` means compute both (head,collocate) and (collocate,head).

### 1.3 Backward Compatibility
Keep old format but deprecate:
- Old: `"cql_pattern": "[tag=JJ.*] [{head}]"`
- New: `"pattern": "[tag=\"JJ.*\"] [tag=\"NN.*\"]"` with `head_position: 2`

---

## Phase 2: CQL Parser Enhancements

### 2.1 Extend CQLParser
Add support for:
- Regex patterns in quotes: `[lemma="be|seem"]`
- Logical operators: `[tag="JJ.*" & lemma=".*able"]`
- Multiple constraints on same token

### 2.2 Pattern Compilation
When compiling pattern with `{head}`:
1. Replace `{head}` with `[lemma="headword"]`
2. Parse remaining as CQL
3. Handle regex in constraints

---

## Phase 3: Precomputation with Correct Patterns

### 3.1 Re-enable Relation Precomputation
In `CollocationsBuilder.java`:
- Remove the "disabled" fix
- Use FULL pattern from grammar (not just POS tag)
- Apply all constraints (lemma regex, word regex, etc.)

### 3.2 Build Process
For each (lemma, relation) pair:
1. Get full CQL pattern from grammar: `[tag=JJ.*] [{head}]`
2. Replace `{head}` with lemma
3. Execute CQL query on index
4. Extract collocates matching pattern
5. Store with frequency and logDice

### 3.3 Output Format
Same binary format, but with CORRECT data from full patterns.

---

## Phase 4: Query Execution

### 4.1 Precomputed Path
- O(1) lookup from binary file
- Return collocates with frequency, logDice

### 4.2 Concordance Path
- Use EXACT same pattern as collocation finder
- Must find examples for every collocate
- Full index search (no sampling by default)

### 4.3 Sampling Control
- Add `--sample-size <N>` CLI flag
- Default: 0 (process all, no sampling)
- For backward compatibility

---

## Phase 5: TDD Tests

### 5.1 Integration Tests
Create `QueryAccuracyTest.java`:

```java
@Test
void collocateWithFrequencyMustHaveExamples() {
    // For every collocate with frequency > 0:
    // examples.size() MUST be > 0
    assertTrue(result.getExamples().size() > 0,
        "Mathematical impossibility: frequency=" + result.getFrequency()
        + " but examples=0");
}

@Test
void examplesCannotExceedFrequency() {
    // examples.size() <= frequency
    assertTrue(result.getExamples().size() <= result.getFrequency());
}

@Test
void precomputedQueryIsFast() {
    // O(1) lookup should be < 10ms
    long start = System.nanoTime();
    var results = executor.findGrammaticalRelation("theory", ADJ_MODIFIER, 5.0, 20);
    long elapsed = (System.nanoTime() - start) / 1_000_000;
    assertTrue(elapsed < 10, "Precomputed query took " + elapsed + "ms");
}

@Test
void fullIndexQueryIsComplete() {
    // With sampling disabled, total matches >= sample size
    assertTrue(totalMatches >= maxSampleSize);
}
```

### 5.2 Regression Tests
- Test each relation type returns valid patterns
- Test concordance matches collocation finder patterns
- Test logDice calculation correctness

---

## Implementation Order

1. **Fix noun_adj_predicates pattern** in JSON (add copula lemmas)
2. **Extend CQLParser** to handle regex in constraints
3. **Re-enable precomputation** with full patterns
4. **Remove sampling** (default to full index)
5. **Add TDD tests** for accuracy invariants
6. **Test end-to-end** with framework example

---

## Files to Modify

| File | Changes |
|------|---------|
| `grammars/relations.json` | Full patterns with `{head}`, remove `uses_copula` |
| `CQLParser.java` | Handle regex in quotes, logical operators |
| `CollocationsBuilder.java` | Re-enable precomputation with full patterns |
| `HybridQueryExecutor.java` | Use grammar patterns, add sample-size flag |
| `QueryAccuracyTest.java` | NEW - TDD tests for accuracy |
| `GrammarConfigLoader.java` | Remove uses_copula references |

---

## Success Criteria

- [ ] `frequency > 0` implies `examples.size() > 0` (100% of cases)
- [ ] Precomputed queries < 10ms
- [ ] All relations from grammar are precomputed
- [ ] Full index processed by default
- [ ] TDD tests pass for all accuracy invariants
