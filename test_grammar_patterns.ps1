# Grammar Pattern Expansion and Testing Script
# Expand macros from sketchgrammar.wsdef.m4

$PROJECT_ROOT = "D:\git\word-sketch-lucene"
$INDEX_PATH = "$PROJECT_ROOT\target\corpus-udpipe"

# Function to run test
function Run-Test {
    param(
        [string]$Name,
        [string]$Pattern,
        [string]$Lemma,
        [int]$Limit = 3
    )

    Write-Host "Testing: $Name" -ForegroundColor Yellow
    Write-Host "  Pattern: $Pattern" -ForegroundColor Gray

    # Use proper escaping for mvn exec
    $escapedPattern = $Pattern.Replace('"', '\"')
    $mvnArgs = "exec:java -Dexec.mainClass=pl.marcinmilkowski.word_sketch.Main -Dexec.args=`"query --index $INDEX_PATH --lemma $lemma --pattern `"$escapedPattern`" --limit $limit`""

    $result = mvn -f "$PROJECT_ROOT\pom.xml" $mvnArgs.Split(" ") 2>&1

    # Extract relevant output
    $found = $false
    foreach ($line in $result) {
        if ($line -match "Found (\d+) pattern matches") {
            Write-Host "  Found: $line" -ForegroundColor Green
            $found = $true
        }
        elseif ($line -match "Results:" -or $line -match "^  \d+\.") {
            Write-Host "  $line" -ForegroundColor White
        }
        elseif ($line -match "Error|Exception|FAIL" -and $line -notmatch "^\[INFO\]") {
            Write-Host "  ERROR: $line" -ForegroundColor Red
        }
    }

    if (-not $found) {
        Write-Host "  No matches found or parsing error" -ForegroundColor Gray
    }
    Write-Host ""
}

$lemma = "problem"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Grammar Pattern Testing" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Test basic patterns first
Write-Host "Category 0: BASIC PATTERNS (baseline)" -ForegroundColor Green
Run-Test "Simple adjective constraint" '[tag="JJ.*"]' $lemma
Run-Test "Simple verb constraint" '[tag="V.*"]' $lemma

Write-Host "Category 1: MODIFIERS" -ForegroundColor Green
Run-Test "Adverb adj modifier (2:RB 1:JJ.*)" '2:"RB" 1:"JJ.*"' $lemma
Run-Test "Adverb verb modifier" '2:"RB" 1:"V.*"' $lemma

Write-Host "Category 2: VERB + OBJECT" -ForegroundColor Green
Run-Test "Verb + noun object" '"V.*" ~{0,5} [tag="N.*[^Z]"]' $lemma
Run-Test "Verb + noun with modifiers" '"V.*" ~{0,8} [tag="JJ.*"|tag="RB.*"] ~{0,3} [tag="N.*[^Z]"]' $lemma

Write-Host "Category 3: VERB + SUBJECT" -ForegroundColor Green
Run-Test "Subject before verb" '[tag="N.*[^Z]"] ~{-5,0} "V.*"' $lemma

Write-Host "Category 4: PARTICLES" -ForegroundColor Green
Run-Test "Verb + particle" '"V.*" ~{0,2} "RP"' $lemma

Write-Host "Category 5: INFINITIVES" -ForegroundColor Green
Run-Test "Verb + TO + verb" '"V.*" ~{0,3} "TO" ~{0,3} "V.P?"' $lemma

Write-Host "Category 6: -ING OBJECTS" -ForegroundColor Green
Run-Test "Verb + gerund" '"V.*" ~{0,3} "V.G"' $lemma

Write-Host "Category 7: PASSIVE" -ForegroundColor Green
Run-Test "Passive by phrase" '"V.N" ~{0,5} [word="by"] ~{0,5} [tag="N.*[^Z]"]' $lemma

Write-Host "Category 8: PREPOSITIONAL PHRASES" -ForegroundColor Green
Run-Test "Prep + noun" '"IN" ~{0,3} [tag="N.*[^Z]"]' $lemma

Write-Host "Category 9: CONJUNCTIONS (and/or)" -ForegroundColor Green
Run-Test "Noun and noun" '"N.*[^Z]" [word="and"|word="or"] ~{0,3} "N.*[^Z]"' $lemma
Run-Test "Verb and verb" '"V.*" [word="and"|word="or"] ~{0,3} "V.*"' $lemma

Write-Host "Category 10: ADJECTIVE PREDICATES" -ForegroundColor Green
Run-Test "Noun + be + adj" '"N.*[^Z]" ~{0,3} "VB.*" ~{0,3} "JJ.*"' $lemma

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Testing Complete" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
