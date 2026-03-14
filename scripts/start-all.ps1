# Start both API Server and Web UI
# Usage: .\start-all.ps1 [--port 8080] [--web-port 3000] [--index path/to/index]
#
# NOTE: collocations support and related command-line options were deprecated
# along with the precomputed collocations file; the server now ignores
# `--collocations` entirely.

param(
    [int]$Port = 8080,
    [int]$WebPort = 3000,
    [string]$Index = 'D:\corpora_philsci\fpsyg_index'
)

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Split-Path -Parent $scriptDir

# Log file setup
$logDir = Join-Path $projectRoot "logs"
if (-not (Test-Path $logDir)) { New-Item -ItemType Directory -Path $logDir | Out-Null }
$logFile = Join-Path $logDir "start-all_$(Get-Date -Format 'yyyyMMdd_HHmmss').log"

function Write-Log {
    param([string]$Message, [string]$Color = '', [string]$Prefix = '')
    $line = "[$(Get-Date -Format 'HH:mm:ss')] $Prefix$Message"
    Add-Content -Path $logFile -Value $line
    if ($Color) {
        Write-Host $Message -ForegroundColor $Color
    } else {
        Write-Host $Message
    }
}

# Check dependencies
$jarFile = Join-Path $projectRoot "target\concept-sketch-1.5.0-shaded.jar"
if (-not (Test-Path $jarFile)) {
    Write-Log "JAR file not found: $jarFile" -Color Red
    Write-Log "Please build with: mvn clean package"
    exit 1
}

if (-not (Test-Path $Index)) {
    Write-Log "Index directory not found: $Index" -Color Red
    exit 1
}

# collocations_v2.bin and associated switching logic are no longer used

Write-Log ""
Write-Log "================================" -Color Cyan
Write-Log "ConceptSketch - Full Stack" -Color Cyan
Write-Log "================================" -Color Cyan
Write-Log ""
Write-Log "Configuration:" -Color Green
Write-Log "  API Port:  $Port"
Write-Log "  Web Port:  $WebPort"
Write-Log "  Index path: $Index"
Write-Log "  Log file:  $logFile"
Write-Log ""
Write-Log "Starting API Server (port $Port)..." -Color Green
Write-Log "Starting Web Server (port $WebPort)..." -Color Green
Write-Log ""

# Start API server in background from project root so grammars/relations.json is found
$apiJob = Start-Job -ScriptBlock {
    param($root, $jar, $idx, $p, $log)
    Set-Location $root
    & java -jar $jar server --index $idx --port $p *>&1 | ForEach-Object {
        Add-Content -Path $log -Value "[$(Get-Date -Format 'HH:mm:ss')] [API] $_"
        $_
    }
} -ArgumentList $projectRoot, $jarFile, $Index, $Port, $logFile

# Give server time to start
Start-Sleep -Seconds 3

# Start web server in background; pipe output into the log file
$webDir = Join-Path $projectRoot "webapp"
$webJob = Start-Job -ScriptBlock {
    param($webPath, $p, $log)
    Push-Location $webPath
    python -m http.server $p *>&1 | ForEach-Object {
        Add-Content -Path $log -Value "[$(Get-Date -Format 'HH:mm:ss')] [WEB] $_"
        $_
    }
} -ArgumentList $webDir, $WebPort, $logFile

Write-Log ""
Write-Log "Services started successfully!" -Color Green
Write-Log ""
Write-Log "API Server:" -Color Cyan
Write-Log "  http://localhost:$Port/health"
Write-Log ""
Write-Log "Web Interface:" -Color Cyan
Write-Log "  http://localhost:$WebPort" -Color Yellow
Write-Log ""
Write-Log "Log file: $logFile" -Color Cyan
Write-Log ""
Write-Log "Press Ctrl+C to stop all services..."
Write-Log ""

# Relay job output to console; stop cleanly on Ctrl+C
try {
    while ($apiJob.State -eq 'Running' -or $webJob.State -eq 'Running') {
        Start-Sleep -Seconds 2
        Receive-Job $apiJob, $webJob | ForEach-Object { Write-Host $_ }
    }
} finally {
    Stop-Job $apiJob, $webJob -ErrorAction SilentlyContinue
    Remove-Job $apiJob, $webJob -ErrorAction SilentlyContinue
    Write-Log "Services stopped." -Color Yellow
}
