# Build collocations using single-pass V2 algorithm
#
# Usage:
#   .\build_collocations_v2.ps1                           # uses defaults
#   .\build_collocations_v2.ps1 -IndexPath "d:\my-index"  # custom index
#
param(
    [string]$IndexPath = "d:\corpus_74m\index-hybrid",
    [string]$OutputPath = "",
    [int]$Window = 5,
    [int]$TopK = 100,
    [int]$MinFreq = 10,
    [int]$MinCooc = 2,
    [int]$Shards = 64,
    [int]$SpillThreshold = 2000000
)

if (-not $OutputPath) {
    $OutputPath = Join-Path $IndexPath "collocations.bin"
}

$jarPath = "target\word-sketch-lucene-1.0-SNAPSHOT.jar"
if (-not (Test-Path $jarPath)) {
    Write-Host "JAR not found. Building..."
    mvn package -DskipTests -q
}

$depPath = "target\dependency\*"

Write-Host "=== Single-Pass Collocations Builder V2 ==="
Write-Host "Index:       $IndexPath"
Write-Host "Output:      $OutputPath"
Write-Host "Window:      $Window"
Write-Host "TopK:        $TopK"
Write-Host "MinFreq:     $MinFreq"
Write-Host "MinCooc:     $MinCooc"
Write-Host "Shards:      $Shards"
Write-Host "SpillThresh: $SpillThreshold"
Write-Host ""

$startTime = Get-Date

java -Xmx8g -cp "$jarPath;$depPath" `
    pl.marcinmilkowski.word_sketch.indexer.hybrid.CollocationsBuilderV2 `
    $IndexPath `
    $OutputPath `
    --window $Window `
    --top-k $TopK `
    --min-freq $MinFreq `
    --min-cooc $MinCooc `
    --shards $Shards `
    --spill $SpillThreshold

$elapsed = (Get-Date) - $startTime
Write-Host ""
Write-Host "Completed in $($elapsed.TotalMinutes.ToString('F1')) minutes"

if (Test-Path $OutputPath) {
    $size = (Get-Item $OutputPath).Length / 1MB
    Write-Host "Output size: $($size.ToString('F1')) MB"
}
