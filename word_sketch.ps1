#!/usr/bin/env pwsh
# Word Sketch Generator - Generates complete word sketches for nouns, verbs, and adjectives
# Based on sketchgrammar.wsdef.m4 patterns

param(
    [string]$Lemma = "problem",
    [string]$IndexPath = "target/corpus-udpipe",
    [int]$LimitPerRelation = 10,
    [switch]$Nouns,
    [switch]$Verbs,
    [switch]$Adjectives,
    [switch]$All,
    [switch]$Json
)

$ErrorActionPreference = "Stop"

# Color helpers
$Colors = @{
    Section = "Cyan"
    Relation = "Yellow"
    Result = "White"
    Error = "Red"
    Gray = "Gray"
    Green = "Green"
}

function Write-Section {
    param([string]$Text)
    Write-Host ""
    Write-Host ("=" * 60) -ForegroundColor $Colors.Section
    Write-Host ("  " + $Text) -ForegroundColor $Colors.Section
    Write-Host ("=" * 60) -ForegroundColor $Colors.Section
}

function Write-Relation {
    param([string]$Name, $Results)

    if ($Results.Count -eq 0) {
        Write-Host "  $Name" -ForegroundColor $Colors.Relation -NoNewline
        Write-Host " - no data" -ForegroundColor $Colors.Gray
        return
    }

    Write-Host "  $Name" -ForegroundColor $Colors.Relation
    Write-Host "  " ("-" * 50) -ForegroundColor $Colors.Gray

    $displayResults = $Results | Select-Object -First $LimitPerRelation
    foreach ($r in $displayResults) {
        $lemma = $r.lemma
        $pos = $r.pos
        $freq = $r.frequency
        $logDice = $r.logDice.ToString("F2")
        $relFreq = ($r.relativeFrequency * 100).ToString("F1") + "%"

        Write-Host ("    {0,-15} {1,-6} freq={2,4} logDice={3,5} ({4})" -f $lemma, "($pos)", $freq, $logDice, $relFreq)
    }

    if ($Results.Count -gt $LimitPerRelation) {
        Write-Host "    ... and $($Results.Count - $LimitPerRelation) more" -ForegroundColor $Colors.Gray
    }
    Write-Host ""
}

# Parse query results
function Parse-QueryResults {
    param([string]$Output)

    $results = @()
    foreach ($line in $Output -split "`n") {
        if ($line -match "^\s+(\S+)\s+\((\S+)\):\s+freq=(\d+),\s+logDice=([\d.]+),\s+relFreq=([\d.]+)") {
            $results += @{
                lemma = $matches[1]
                pos = $matches[2]
                frequency = [int]$matches[3]
                logDice = [double]$matches[4]
                relativeFrequency = [double]$matches[5]
            }
        }
    }
    return $results
}

# Query using bash (more reliable for complex patterns)
function Invoke-Query {
    param(
        [string]$Pattern,
        [string]$Lemma,
        [int]$Limit
    )

    # Build the command - use PowerShell native execution, avoiding bash escaping issues
    $mvnArgs = "query --index `"$IndexPath`" --lemma `"$lemma`" --pattern `"$pattern`" --limit $Limit"

    try {
        $output = & bash -c "cd '$PWD' && mvn exec:java -Dexec.mainClass=pl.marcinmilkowski.word_sketch.Main -Dexec.args='$mvnArgs' 2>&1"
        return Parse-QueryResults -Output ($output -join "`n")
    }
    catch {
        Write-Host "  ERROR: $_" -ForegroundColor $Colors.Error
        return @()
    }
}

# ============================================================================
# WORD SKETCH DEFINITIONS
# ============================================================================

$NounRelations = @(
    @{ Name = "Adjectives modifying (modifiers)"; Pattern = '[tag=jj.*]~{0,3}' },
    @{ Name = "Verbs with as object (objects)"; Pattern = '[tag=vb.*]~{0,5} [tag=nn.*]' },
    @{ Name = "Verbs as subject (subjects)"; Pattern = '[tag=nn.*]~{-5,0} [tag=vb.*]' },
    @{ Name = "Nouns in compound (noun+noun)"; Pattern = '[tag=nn.*]~{1,2} [tag=nn.*]' },
    @{ Name = "Adverbs"; Pattern = '[tag=rb.*]~{0,3}' },
    @{ Name = "Determiners"; Pattern = '[tag=dt]~{0,1}' },
    @{ Name = "Prepositions (of, for, etc.)"; Pattern = '[word=of]~{0,3}' }
)

$VerbRelations = @(
    @{ Name = "Direct objects (what is VERBed)"; Pattern = '[tag=vb.*]~{0,5} [tag=nn.*]' },
    @{ Name = "Subjects (who VERBs)"; Pattern = '[tag=nn.*]~{-5,0} [tag=vb.*]' },
    @{ Name = "Particles (verb+particle)"; Pattern = '[tag=vb.*]~{0,2} [tag=rp]' },
    @{ Name = "Infinitive 'to'"; Pattern = '[tag=vb.*]~{0,3} [word=to]~{0,2}' },
    @{ Name = "Gerunds (-ing)"; Pattern = '[tag=vb.*]~{0,3} [tag=vbg]' },
    @{ Name = "Passive 'by' agent"; Pattern = '[tag=vbn]~{0,3} [word=by]~{0,2}' }
)

$AdjectiveRelations = @(
    @{ Name = "Nouns modified (predicates)"; Pattern = '[tag=nn.*]~{-3,0} [tag=jj.*]' },
    @{ Name = "Verbs with adjective complement"; Pattern = '[tag=vb.*]~{0,5} [tag=jj.*]' },
    @{ Name = "Adverbs modifying"; Pattern = '[tag=rb.*]~{0,2} [tag=jj.*]' },
    @{ Name = "After noun (postnominal)"; Pattern = '[tag=nn.*]~{0,3} [tag=jj.*]' },
    @{ Name = "With 'very' or 'too'"; Pattern = '[word=very]~{0,1} [tag=jj.*]' }
)

# ============================================================================
# MAIN EXECUTION
# ============================================================================

$PROJECT_ROOT = (Get-Item $PSScriptRoot).FullName
$IndexFullPath = Join-Path $PROJECT_ROOT $IndexPath

Write-Host ""
Write-Host ("=" * 60) -ForegroundColor Magenta
Write-Host ("  WORD SKETCH GENERATOR") -ForegroundColor Magenta
Write-Host ("  Lemma: $Lemma") -ForegroundColor Magenta
Write-Host ("  Index: $IndexFullPath") -ForegroundColor Magenta
Write-Host ("=" * 60) -ForegroundColor Magenta

# Check if index exists
if (-not (Test-Path $IndexFullPath)) {
    Write-Host ""
    Write-Host "ERROR: Index not found at: $IndexFullPath" -ForegroundColor Red
    Write-Host "Please run the indexing pipeline first." -ForegroundColor Yellow
    exit 1
}

# Determine which relations to show
$showNouns = $All -or $Nouns -or (-not ($Verbs -or $Adjectives))
$showVerbs = $All -or $Verbs
$showAdjectives = $All -or $Adjectives

# Save current directory and change to project root
$originalDir = Get-Location
Set-Location $PROJECT_ROOT

# ---------------------------------------------------------------------------
# NOUN SKETCH
# ---------------------------------------------------------------------------
if ($showNouns) {
    Write-Section "WORD SKETCH: $LEMMA (noun)"

    foreach ($rel in $NounRelations) {
        Write-Host "  $($rel.Name)..." -ForegroundColor Gray -NoNewline
        $sw = [System.Diagnostics.Stopwatch]::StartNew()
        $results = Invoke-Query -Pattern $rel.Pattern -Lemma $LEMMA -Limit $LimitPerRelation
        $sw.Stop()
        Write-Host " ($($results.Count) results, $($sw.ElapsedMilliseconds)ms)" -ForegroundColor $Colors.Gray
        Write-Relation -Name $rel.Name -Results $results
    }
}

# ---------------------------------------------------------------------------
# VERB SKETCH
# ---------------------------------------------------------------------------
if ($showVerbs) {
    Write-Section "WORD SKETCH: $LEMMA (verb)"

    foreach ($rel in $VerbRelations) {
        Write-Host "  $($rel.Name)..." -ForegroundColor Gray -NoNewline
        $sw = [System.Diagnostics.Stopwatch]::StartNew()
        $results = Invoke-Query -Pattern $rel.Pattern -Lemma $LEMMA -Limit $LimitPerRelation
        $sw.Stop()
        Write-Host " ($($results.Count) results, $($sw.ElapsedMilliseconds)ms)" -ForegroundColor $Colors.Gray
        Write-Relation -Name $rel.Name -Results $results
    }
}

# ---------------------------------------------------------------------------
# ADJECTIVE SKETCH
# ---------------------------------------------------------------------------
if ($showAdjectives) {
    Write-Section "WORD SKETCH: $LEMMA (adjective)"

    foreach ($rel in $AdjectiveRelations) {
        Write-Host "  $($rel.Name)..." -ForegroundColor Gray -NoNewline
        $sw = [System.Diagnostics.Stopwatch]::StartNew()
        $results = Invoke-Query -Pattern $rel.Pattern -Lemma $LEMMA -Limit $LimitPerRelation
        $sw.Stop()
        Write-Host " ($($results.Count) results, $($sw.ElapsedMilliseconds)ms)" -ForegroundColor $Colors.Gray
        Write-Relation -Name $rel.Name -Results $results
    }
}

# Restore original directory
Set-Location $originalDir

Write-Host ""
Write-Host "Word sketch complete." -ForegroundColor $Colors.Green
Write-Host ""
