# Start Word Sketch Lucene API Server
# Usage: .\start-server.ps1 [--port 8080] [--index path/to/index]

param(
    [int]$Port = 8080,
    [string]$Index = "d:\corpus_74m\index-hybrid"
)

# Get script directory
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Split-Path -Parent $scriptDir

# Check if JAR exists
$jarFile = Join-Path $projectRoot "target\word-sketch-lucene-1.0.0.jar"
if (-not (Test-Path $jarFile)) {
    Write-Error "JAR file not found: $jarFile"
    Write-Host "Please build with: mvn clean package"
    exit 1
}

# Check if index exists
if (-not (Test-Path $Index)) {
    Write-Error "Index directory not found: $Index"
    exit 1
}

Write-Host "================================" -ForegroundColor Cyan
Write-Host "Word Sketch Lucene API Server" -ForegroundColor Cyan
Write-Host "================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Configuration:" -ForegroundColor Green
Write-Host "  Port:  $Port"
Write-Host "  Index: $Index"
Write-Host ""
Write-Host "Starting server..." -ForegroundColor Green
Write-Host ""

# Start server
& java -jar $jarFile server --index $Index --port $Port
