# Comprehensive grammar pattern test script
$PROJECT_ROOT = "D:\git\word-sketch-lucene"
$INDEX_PATH = "$PROJECT_ROOT\target\corpus-udpipe"

function Test-Pattern {
    param([string]$Name, [string]$Pattern, [string]$Lemma, [int]$Limit = 5)

    Write-Host "Testing: $Name" -ForegroundColor Yellow
    Write-Host "  Pattern: $Pattern" -ForegroundColor Gray

    # Build command with proper escaping
    $cmdArgs = "query --index $INDEX_PATH --lemma $lemma --pattern `"$pattern`" --limit $limit"
    $result = mvn -f "$PROJECT_ROOT\pom.xml" exec:java -Dexec.mainClass=pl.marcinmilkowski.word_sketch.Main -Dexec.args="$cmdArgs" 2>&1

    $found = $false
    foreach ($line in $result) {
        if ($line -match "Found (\d+) pattern matches") {
            Write-Host "  Found: $matches[1] matches" -ForegroundColor Green
            $found = $true
        }
        elseif ($line -match "Results:") {
            Write-Host "  Results:" -ForegroundColor Green
        }
        elseif ($line -match "^  \d+\." -or $line -match "logDice") {
            Write-Host "  $line" -ForegroundColor White
        }
        elseif ($line -match "Error|Exception" -and $line -notmatch "^\[INFO\]") {
            Write-Host "  ERROR: $line" -ForegroundColor Red
        }
    }
    if (-not $found -and $result -notmatch "ERROR") {
        Write-Host "  No matches" -ForegroundColor Gray
    }
    Write-Host ""
}

$lemma = "problem"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Grammar Pattern Testing - Word Sketch" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Basic POS patterns
Test-Pattern "Adjectives (modifier pattern)" '[tag="jj.*"]' $lemma
Test-Pattern "Nouns (subject/object pattern)" '[tag="nn.*"]' $lemma
Test-Pattern "Verbs" '[tag="vb.*"]' $lemma
Test-Pattern "Adverbs" '[tag="rb.*"]' $lemma

# Distance patterns (from sketchgrammar.wsdef.m4)
Test-Pattern "Adj within 3 words (modifier)" '[tag="jj.*"]~{0,3}' $lemma
Test-Pattern "Noun 1-5 words before (subject)" '[tag="nn.*"]~{-5,-1}' $lemma
Test-Pattern "Verb within 5 words" '[tag="vb.*"]~{0,5}' $lemma
Test-Pattern "Adverb within 2 words" '[tag="rb.*"]~{0,2}' $lemma

# Word-specific patterns
Test-Pattern "Word 'of' near problem" '[word="of"]' $lemma
Test-Pattern "Word 'the' near problem" '[word="the"]' $lemma
Test-Pattern "Preposition 'to'" '[word="to"]' $lemma

# Complex patterns with AND
Test-Pattern "Not noun AND not CC" '[tag!="nn.*" & tag!="cc"]' $lemma

# Macro-expanded patterns (from sketchgrammar)
Test-Pattern "MODIFIER (jj or rb)" '[tag="jj.*" / tag="rb.*"]' $lemma 3

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Testing Complete" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
