# Build Hybrid Index from CoNLL-U corpus files
# Usage: .\build_hybrid_index.ps1 [-InputDir <path>] [-OutputDir <path>]

param(
    [string]$InputDir = "d:\corpus_74m\temp",
    [string]$OutputDir = "d:\corpus_74m\index-hybrid",
    [int]$HeapGB = 32
)

$ErrorActionPreference = "Stop"

# Configuration
$JarPath = "target\word-sketch-lucene-1.0.0.jar"
$JavaOpts = @(
    "-Xmx${HeapGB}g",
    "-XX:+UseG1GC",
    "-XX:MaxGCPauseMillis=200",
    "--add-modules", "jdk.incubator.vector"
)

# Check prerequisites
if (-not (Test-Path $JarPath)) {
    Write-Host "Building JAR file..." -ForegroundColor Cyan
    mvn package -DskipTests -q
}

# Ensure output directory exists
if (-not (Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
}

# Find CoNLL-U files
$ConlluFiles = Get-ChildItem -Path $InputDir -Filter "*.conllu" | Sort-Object Name

if ($ConlluFiles.Count -eq 0) {
    Write-Host "No CoNLL-U files found in $InputDir" -ForegroundColor Red
    exit 1
}

Write-Host "=== Hybrid Index Builder ===" -ForegroundColor Green
Write-Host "Input: $InputDir ($($ConlluFiles.Count) files)"
Write-Host "Output: $OutputDir"
Write-Host "Heap: ${HeapGB}GB"
Write-Host ""

# Calculate total size
$TotalSize = ($ConlluFiles | Measure-Object -Property Length -Sum).Sum
Write-Host "Total size: $([math]::Round($TotalSize / 1GB, 2)) GB"
Write-Host ""

# Run the indexer
$StartTime = Get-Date

Write-Host "Starting hybrid index build..." -ForegroundColor Cyan
& java $JavaOpts -jar $JarPath hybrid-index `
    --input $InputDir `
    --output $OutputDir `
    --commit-interval 100000

$ElapsedTime = (Get-Date) - $StartTime

Write-Host ""
Write-Host "=== Complete ===" -ForegroundColor Green
Write-Host "Time: $([math]::Round($ElapsedTime.TotalMinutes, 1)) minutes"
Write-Host "Output: $OutputDir"

# Show index size
$IndexSize = (Get-ChildItem -Path $OutputDir -Recurse | Measure-Object -Property Length -Sum).Sum
Write-Host "Index size: $([math]::Round($IndexSize / 1GB, 2)) GB"
