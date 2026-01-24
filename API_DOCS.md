# Word Sketch Lucene API Documentation

## Overview

The Word Sketch API provides corpus-based collocation analysis for lexical exploration. It identifies typical words that co-occur with a given word in specific grammatical relations.

**Base URL:** `http://localhost:8080` (when running locally)

---

## Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/health` | Health check |
| GET | `/api/relations` | List available grammatical relations |
| GET | `/api/sketch/{lemma}` | Get full word sketch for a lemma |
| POST | `/api/sketch/query` | Execute custom CQL pattern query |
| GET | `/sketch` | Legacy endpoint (backward compatible) |
| POST | `/sketch/query` | Legacy endpoint (backward compatible) |

---

## 1. Health Check

### Request

```http
GET /health
```

### Response

```json
{
  "status": "ok",
  "service": "word-sketch-lucene",
  "port": 8080
}
```

---

## 2. List Available Relations

### Request

```http
GET /api/relations
```

### Response

```json
{
  "status": "ok",
  "relations": {
    "noun": [
      {
        "id": "noun_modifiers",
        "name": "Adjectives modifying (modifiers)",
        "pattern": "[tag=jj.*]~{0,3}",
        "pos_group": "noun"
      },
      {
        "id": "noun_objects",
        "name": "Verbs with as object",
        "pattern": "[tag=vb.*]~{0,5} [tag=nn.*]",
        "pos_group": "noun"
      }
    ],
    "verb": [...],
    "adj": [...]
  }
}
```

---

## 3. Get Word Sketch

### Request

```http
GET /api/sketch/{lemma}?pos=noun,verb,adj&limit=10&min_logdice=0
```

**Query Parameters:**
| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `pos` | string | `"noun,verb,adj"` | Comma-separated POS groups to include |
| `limit` | int | `10` | Maximum collocates per relation |
| `min_logdice` | double | `0` | Minimum logDice score (0-14 scale) |

### Response

```json
{
  "status": "ok",
  "lemma": "house",
  "patterns": {
    "noun_modifiers": {
      "name": "Adjectives modifying (modifiers)",
      "cql": "[tag=jj.*]~{0,3}",
      "pos_group": "noun",
      "total_matches": 5,
      "collocations": [
        {
          "lemma": "beautiful",
          "pos": "JJ",
          "frequency": 15,
          "logDice": 8.75,
          "relativeFrequency": 0.023,
          "examples": [
            "I see a big beautiful house near the river",
            "A beautiful house stands on the hill"
          ]
        }
      ]
    },
    "noun_objects": {
      "name": "Verbs with as object",
      "cql": "[tag=vb.*]~{0,5} [tag=nn.*]",
      "pos_group": "noun",
      "total_matches": 3,
      "collocations": [...]
    }
  }
}
```

---

## 4. Custom CQL Query

### Request

```http
POST /api/sketch/query
Content-Type: application/json

{
  "lemma": "house",
  "pattern": "[tag=jj.*]~{0,3}",
  "min_logdice": 0,
  "limit": 50
}
```

### Response

```json
{
  "status": "ok",
  "lemma": "house",
  "pattern": "[tag=jj.*]~{0,3}",
  "total_matches": 5,
  "collocations": [
    {
      "lemma": "beautiful",
      "pos": "JJ",
      "frequency": 15,
      "logDice": 8.75,
      "relativeFrequency": 0.023,
      "examples": [
        "I see a big beautiful house near the river",
        "A beautiful house stands on the hill"
      ]
    }
  ]
}
```

---

## JSON Schema

### Full Sketch Response

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "properties": {
    "status": { "type": "string", "enum": ["ok", "error"] },
    "lemma": { "type": "string" },
    "patterns": {
      "type": "object",
      "additionalProperties": {
        "type": "object",
        "properties": {
          "name": { "type": "string" },
          "cql": { "type": "string" },
          "pos_group": { "type": "string" },
          "total_matches": { "type": "integer" },
          "collocations": {
            "type": "array",
            "items": {
              "type": "object",
              "properties": {
                "lemma": { "type": "string" },
                "pos": { "type": "string" },
                "frequency": { "type": "integer" },
                "logDice": { "type": "number" },
                "relativeFrequency": { "type": "number" },
                "examples": { "type": "array", "items": { "type": "string" } }
              },
              "required": ["lemma", "pos", "frequency", "logDice"]
            }
          }
        }
      }
    }
  },
  "required": ["status", "lemma", "patterns"]
}
```

### Collocation Item

```json
{
  "type": "object",
  "properties": {
    "lemma": { "type": "string", "description": "Collocate word in lemma form" },
    "pos": { "type": "string", "description": "Part-of-speech tag (Penn Treebank)" },
    "frequency": { "type": "integer", "description": "Number of co-occurrences in corpus" },
    "logDice": { "type": "number", "description": "Association strength (0-14, higher = stronger)" },
    "relativeFrequency": { "type": "number", "description": "Frequency relative to all collocations" },
    "examples": {
      "type": "array",
      "items": { "type": "string" },
      "description": "Example sentences from corpus"
    }
  },
  "required": ["lemma", "pos", "frequency", "logDice"]
}
```

---

## Grammatical Relations

### Noun Relations (headword = noun)

| Relation ID | Name | CQL Pattern | Description |
|-------------|------|-------------|-------------|
| `noun_modifiers` | Adjectives modifying | `[tag=jj.*]~{0,3}` | Adjectives within 3 words before noun |
| `noun_objects` | Verbs with as object | `[tag=vb.*]~{0,5} [tag=nn.*]` | Verbs taking noun as object |
| `noun_subjects` | Verbs as subject | `[tag=nn.*]~{-5,0} [tag=vb.*]` | Verbs before noun as subject |
| `noun_compound` | Nouns in compound | `[tag=nn.*]~{1,2} [tag=nn.*]` | Noun+noun compounds |
| `noun_adverbs` | Adverbs modifying | `[tag=rb.*]~{0,3}` | Adverbs modifying the noun |
| `noun_determiners` | Determiners | `[tag=dt]~{0,1}` | Articles and determiners |
| `noun_prepositions` | Prepositions | `[word=of]~{0,3}` | Prepositions like "of", "for" |

### Verb Relations (headword = verb)

| Relation ID | Name | CQL Pattern | Description |
|-------------|------|-------------|-------------|
| `verb_objects` | Direct objects | `[tag=vb.*]~{0,5} [tag=nn.*]` | What is VERBed |
| `verb_subjects` | Subjects | `[tag=nn.*]~{-5,0} [tag=vb.*]` | Who VERBs |
| `verb_particles` | Particles | `[tag=vb.*]~{0,2} [tag=rp]` | Verb+particle constructions |
| `verb_infinitive` | Infinitive 'to' | `[tag=vb.*]~{0,3} [word=to]~{0,2}` | Verb + to + infinitive |
| `verb_gerunds` | Gerunds (-ing) | `[tag=vb.*]~{0,3} [tag=vbg]` | Verb + gerund |
| `verb_passive` | Passive 'by' agent | `[tag=vbn]~{0,3} [word=by]~{0,2}` | Passive voice with 'by' |

### Adjective Relations (headword = adjective)

| Relation ID | Name | CQL Pattern | Description |
|-------------|------|-------------|-------------|
| `adj_nouns` | Nouns modified | `[tag=nn.*]~{-3,0} [tag=jj.*]` | Nouns before adjective |
| `adj_verbs` | Verbs with complement | `[tag=vb.*]~{0,5} [tag=jj.*]` | Verbs with adjective complement |
| `adj_adverbs` | Adverbs modifying | `[tag=rb.*]~{0,2} [tag=jj.*]` | Adverbs before adjective |
| `adj_postnominal` | After noun | `[tag=nn.*]~{0,3} [tag=jj.*]` | Adjective after noun |
| `adj_intensifiers` | With 'very'/'too' | `[word=very]~{0,1} [tag=jj.*]` | Intensifiers |

---

## CQL Pattern Reference

### Syntax

```
[constraint]~{min,max}
```

### Constraints

| Syntax | Meaning |
|--------|---------|
| `[tag=JJ]` | Match specific POS tag |
| `[tag=JJ.*]` | Regex match on POS tag |
| `[word=of]` | Match specific word |
| `[word!="stop"]` | Negation - exclude word |
| `{min,max}` | Distance window (words between) |

### Examples

| Pattern | Meaning |
|---------|---------|
| `[tag=jj.*]~{0,3}` | Adjective within 3 words |
| `[tag=nn.*]~{-5,0} [tag=vb.*]` | Noun before verb (5 words back) |
| `[tag=rb.*]~{0,2}` | Adverb within 2 words |
| `[word=very]~{0,1}` | "very" immediately before |

---

## Scoring: logDice

**logDice** measures association strength between words:

- **Range:** 0 to 14
- **Formula:** `log2(2 * f(AB) / (f(A) + f(B))) + 14`
- **Interpretation:**
  - 0-4: Weak association
  - 4-8: Moderate association
  - 8-12: Strong association
  - 12-14: Very strong (near-collocation)

Higher logDice means the collocate is more distinctive for that headword.

---

## Example Usage

### Python

```python
import requests

BASE_URL = "http://localhost:8080"

# Get word sketch
response = requests.get(f"{BASE_URL}/api/sketch/house")
data = response.json()

# Access collocations
for rel_id, pattern_data in data["patterns"].items():
    print(f"\n{pattern_data['name']}:")
    for colloc in pattern_data["collocations"][:5]:
        print(f"  {colloc['lemma']} ({colloc['pos']}): logDice={colloc['logDice']}")
```

### JavaScript

```javascript
const BASE_URL = 'http://localhost:8080';

// Get word sketch
const response = await fetch(`${BASE_URL}/api/sketch/house?limit=20`);
const data = await response.json();

// Display collocations
for (const [relId, pattern] of Object.entries(data.patterns)) {
    console.log(`\n${pattern.name}:`);
    pattern.collocations.slice(0, 5).forEach(c => {
        console.log(`  ${c.lemma} (${c.pos}): logDice=${c.logDice}`);
    });
}

// Custom query
const query = await fetch(`${BASE_URL}/api/sketch/query`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
        lemma: 'house',
        pattern: '[tag=jj.*]~{0,3}',
        limit: 10
    })
});
```

### cURL

```bash
# Health check
curl http://localhost:8080/health

# Get sketch for "house"
curl "http://localhost:8080/api/sketch/house?limit=20"

# Custom query
curl -X POST http://localhost:8080/api/sketch/query \
  -H "Content-Type: application/json" \
  -d '{"lemma":"house","pattern":"[tag=jj.*]~{0,3}","limit":10}'
```

---

## Error Responses

```json
{
  "status": "error",
  "message": "Headword 'xyz' not found in corpus",
  "code": 500
}
```

---

## CORS Support

The API supports CORS for cross-origin requests. Preflight OPTIONS requests are handled at `/api/sketch/options`.

Headers included:
- `Access-Control-Allow-Origin: *`
- `Access-Control-Allow-Methods: GET, POST, OPTIONS`
- `Access-Control-Allow-Headers: Content-Type, Authorization`

---

## Starting the Server

```bash
# Build first
mvn package -DskipTests

# Run with Java
java -jar target/word-sketch-lucene-1.0.0.jar server --index /path/to/index --port 8080
```

Or use the Maven exec plugin:
```bash
mvn exec:java -Dexec.mainClass="pl.marcinmilkowski.word_sketch.Main" \
  -Dexec.args="server --index /path/to/index --port 8080"
```
