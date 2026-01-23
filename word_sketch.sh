#!/bin/bash
# Word Sketch Generator - Generates complete word sketches for nouns, verbs, and adjectives
# Based on sketchgrammar.wsdef.m4 patterns

INDEX_PATH="target/corpus-udpipe"
LEMMA="${1:-problem}"
LIMIT="${2:-10}"

# Colors (if terminal supports)
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Query function
query_pattern() {
    local name="$1"
    local pattern="$2"

    echo -e "  ${YELLOW}${name}${NC}" >&2
    echo -e "  ----------------------------------------" >&2

    local output
    output=$(mvn exec:java -Dexec.mainClass=pl.marcinmilkowski.word_sketch.Main \
        -Dexec.args="query --index $INDEX_PATH --lemma $LEMMA --pattern \"$pattern\" --limit $LIMIT" 2>&1)

    echo "$output" | grep -E "^\s+[a-zA-Z]" | head -10
    echo "" >&2
}

# Header
echo ""
echo -e "${CYAN}========================================${NC}"
echo -e "${CYAN}  WORD SKETCH: $LEMMA${NC}"
echo -e "${CYAN}========================================${NC}"
echo ""

echo -e "${CYAN}NOUN RELATIONS${NC}"
echo "================"

query_pattern "Adjectives modifying (modifiers)" "[tag=jj.*]~{0,3}"
query_pattern "Verbs with as object" "[tag=vb.*]~{0,5}"
query_pattern "Verbs as subject" "[tag=nn.*]~{-5,0}"
query_pattern "Nouns in compound" "[tag=nn.*]~{1,2}"
query_pattern "Adverbs" "[tag=rb.*]~{0,3}"
query_pattern "Determiners" "[tag=dt]~{0,1}"
query_pattern "Prepositions (of, for, etc.)" "[word=of]~{0,3}"

echo -e "${CYAN}VERB RELATIONS${NC}"
echo "================="

query_pattern "Direct objects" "[tag=vb.*]~{0,5} [tag=nn.*]"
query_pattern "Subjects" "[tag=nn.*]~{-5,0} [tag=vb.*]"
query_pattern "Particles (verb+particle)" "[tag=vb.*]~{0,2} [tag=rp]"
query_pattern "Infinitive 'to'" "[tag=vb.*]~{0,3} [word=to]~{0,2}"
query_pattern "Gerunds (-ing)" "[tag=vb.*]~{0,3} [tag=vbg]"
query_pattern "Passive 'by' agent" "[tag=vbn]~{0,3} [word=by]~{0,2}"

echo -e "${CYAN}ADJECTIVE RELATIONS${NC}"
echo "===================="

query_pattern "Nouns modified (predicates)" "[tag=nn.*]~{-3,0} [tag=jj.*]"
query_pattern "Verbs with adjective complement" "[tag=vb.*]~{0,5} [tag=jj.*]"
query_pattern "Adverbs modifying" "[tag=rb.*]~{0,2} [tag=jj.*]"
query_pattern "After noun (postnominal)" "[tag=nn.*]~{0,3} [tag=jj.*]"
query_pattern "With 'very' or 'too'" "[word=very|word=too]~{0,1} [tag=jj.*]"

echo ""
echo -e "${GREEN}Word sketch complete.${NC}"
echo ""
