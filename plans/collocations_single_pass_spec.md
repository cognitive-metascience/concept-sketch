# Single-pass Precomputed Collocations (Option A: lemma IDs)

Date: 2026-02-03

## Context
The existing precomputed collocations builder operates *per lemma* by running Lucene queries (e.g., `TermQuery(field="lemma", lemma)`) and decoding sentence tokens for every matched document. This repeats work across lemmas, makes overall complexity dominated by high-frequency headwords, and historically caused memory/time issues.

This document specifies a new build pipeline that is **single-pass over the corpus sentences** (i.e., one sequential traversal of sentence documents) and produces the same `collocations.bin` format used at query time.

Key constraint: **do not remove punctuation**. Punctuation is useful and may be referenced by CQL patterns.

## Goals
- Build `collocations.bin` with:
  - One sequential scan over sentence documents (no per-lemma Lucene searches)
  - Bounded memory via spill-to-disk
  - Crash safety and resumability
  - Output compatible with existing query-time reader/executor
- Reduce GC pressure compared to string-based token decoding by introducing **lemma IDs**.
- Keep punctuation as normal tokens/lemmas in the build.
- Keep the design extensible to future token attributes (e.g., dependency relations from UDPipe).

## Non-goals (for this iteration)
- Grammar-aware collocations (dependency-based chunks, typed relations) are explicitly deferred.
- Changing query-time algorithms, CQL behavior, or existing `tokens` storage is out of scope.

## High-level approach
Pipeline: **Scan → Spill (sorted runs) → Reduce (merge + topK) → Write**

1) **Scan stage (single pass)**
- Iterate all sentence documents sequentially.
- Decode a compact per-sentence integer array of lemma IDs.
- For each position `i`, update cooccurrence counts for lemma IDs in `[i-window, i+window]`.
- Accumulate counts in an in-memory primitive map keyed by `(headId, collId)`.
- When the map reaches a configured threshold, flush a **sorted run** to disk, sharded by headId.

2) **Reduce stage (no corpus rescan)**
- For each shard, multi-way merge its run files (already sorted by pair key).
- Aggregate counts per `(headId, collId)`.
- For each headId:
  - Apply `minCooccurrence`.
  - Compute logDice using frequencies from `stats.bin` / lexicon.
  - Keep only topK candidates (min-heap).
- Stream-write final entries to `collocations.bin`.

## Data model
### Lemma ID lexicon
We introduce a stable lemma ID mapping used during indexing and collocations build.

- Lemma IDs are `int` (0..N-1)
- Lemmas are stored lowercased (to match existing behavior)
- Punctuation tokens remain included as lemmas (e.g., ",", ".", etc.)

The lexicon provides:
- `int getId(String lemma)` (index-time)
- `String getLemma(int lemmaId)` (build-time/debug)
- `long getFrequency(int lemmaId)` (from stats)
- Optionally: `Map<String,Long> posDistribution(int lemmaId)` (if we want POS for collocates)

### Token attributes
Current hybrid index stores `tokens` as a string-rich binary DocValues field.

This spec adds one additional DocValues field:
- `lemma_ids` (BinaryDocValues): compact encoding of lemma IDs per token position.

**Extensibility (future):**
- Add additional parallel DocValues fields when needed, e.g.:
  - `tag_ids` (POS tag IDs)
  - `deprel_ids` (dependency relation IDs)
  - `dep_head_deltas` (relative head index per token)

The scan stage should be written so it can optionally consume these fields later, without changing the external aggregation infrastructure.

## File formats
### `lemma_ids` BinaryDocValues
Per document (sentence):
- `tokenCount` as varint
- For each token position 0..tokenCount-1:
  - `lemmaId` as varint

Notes:
- Using varints keeps common small IDs compact.
- This field is produced at indexing time.

### Pair key
We represent `(headId, collId)` as a single 64-bit key:

- `pairKey = ((long) headId << 32) | (collId & 0xffffffffL)`

Sorting by `pairKey` sorts by headId then collId.

### Run files (spill output)
Each run file contains sorted aggregated counts:

Header:
- magic `PAIR` (int)
- version (int)
- recordCount (int)

Records (repeated recordCount times):
- pairKey (long)
- count (int)

Files are written per shard:
- shard = `headId & (numShards - 1)` (numShards is power of 2)

### Manifest / checkpoint
Workdir contains `manifest.json` with:
- config: window, topK, minFreq, minCooc, numShards, spillThreshold
- scan progress: leaf ordinal + doc ordinal (or a monotonic doc counter)
- list of run files created per shard
- completed stage markers

Resuming:
- Scan stage resumes from the last checkpoint and continues appending new run files.
- Reduce stage can be re-run idempotently and overwrites the final output (or writes to a temp then renames).

## Algorithm details
### Scan stage details
Inputs:
- IndexReader leaves
- `BinaryDocValues lemma_ids`
- Statistics/lexicon to decide whether a token lemma can be a headword (minFreq)

Process:
- Decode lemmaId array for each sentence doc.
- For each position i:
  - `headId = ids[i]`
  - If headFreq < minFreq: continue
  - For j in window around i, j != i:
    - `collId = ids[j]`
    - increment `count(headId, collId)`

No punctuation filtering is applied.

Memory:
- Maintain a primitive map `pairKey -> count`.
- When size exceeds threshold, flush to run files and clear map.

### Reduce stage details
For each shard:
- Multi-way merge run files.
- Aggregate counts for identical pairKey.
- Stream through data grouped by headId.
- For each headId, keep a min-heap of size topK by logDice.
- Emit `CollocationEntry` when headId changes.

## Compatibility with current query-time code
- Output remains `collocations.bin` in the existing format.
- Query-time lookup stays unchanged.

Implementation note: query-time collocation entries currently include lemma strings and POS.
- Lemma string can be recovered from lexicon during reduce.
- POS can still come from existing `stats.bin` POS distribution (most frequent POS).

## Future expansion: dependency grammar
The pipeline is designed so the *scan stage* can be upgraded later to count only grammatically meaningful relations, without changing:
- spill file format
- reduce merge machinery
- output writer

Possible future modes:
- Window-based (current): count by distance window
- Dependency-based: count `(head lemma) --deprel--> (dependent lemma)`
- Chunk-based: precompute grammar chunks and count within chunk boundaries

This requires adding dependency attributes to the index (e.g., `deprel_ids`, `dep_head_deltas`).

## Implementation plan (detailed)
1) Add lexicon and lemma ID assignment during indexing
- Implement a `LemmaLexiconBuilder` used by the hybrid indexing pipeline.
- Persist `lexicon.bin` at index build time.
- Ensure stable IDs and lowercasing.

2) Add `lemma_ids` DocValues field to hybrid index
- Implement `LemmaIdsCodec` for varint encoding.
- In the hybrid indexer document creation, encode lemma IDs in token order.

3) Implement external aggregation primitives
- Primitive `LongIntHashMap` (open addressing)
- `RunWriter` for sorted spill files
- `Manifest` read/write and checkpointing

4) Implement scan stage
- Sequential scan over all leaves/docs
- Decode lemma IDs and update map
- Periodically spill and checkpoint

5) Implement reduce stage
- Multi-way merge per shard
- Compute logDice using stats frequencies
- Maintain topK heap per headword
- Stream-write `collocations.bin`

6) CLI and scripts
- New CLI entrypoint `CollocationsBuilderV2` (or extend existing builder)
- Optional flag on hybrid-index command to run collocation build after indexing

7) Tests and validation
- Tiny corpus end-to-end test that compares v1 vs v2 results on a fixed dataset
- Codec round-trip tests
- Merge correctness tests

## Risks and mitigations
- **Index size growth:** adding `lemma_ids` increases index size modestly; varint keeps it small.
- **String allocations:** scan uses integer arrays; strings only needed during reduce output.
- **Spill volume:** large corpora produce large run files; sharding + sorted runs keeps reduce feasible.
- **Resume correctness:** manifest must be written atomically (write temp then rename).
