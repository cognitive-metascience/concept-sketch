# Word Sketch Lucene - Optimized Full Database Indexing Pipeline
# All data stored in D:/corpus_74m/ to avoid C: drive space issues

# Set UTF-8 encoding for console and file operations
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

# Default configuration
$Script:TableName = "parallel_corpus"
$Script:IdColumn = "id"
$Script:TextColumn = "source_text"
$Script:BatchSize = 100000
$Script:MaxSentences = 0
$Script:Rebuild = $false
$Script:SkipTagging = $false
$Script:Dedup = $false
$Script:ParallelJobs = 4
$Script:IndexThreads = 8
$Script:OutputBase = "D:/corpus_74m"

# Parse command line arguments
foreach ($arg in $args) {
    if ($arg -match "^-TableName=") { $Script:TableName = $arg.Replace("-TableName=", "") }
    elseif ($arg -match "^-IdColumn=") { $Script:IdColumn = $arg.Replace("-IdColumn=", "") }
    elseif ($arg -match "^-TextColumn=") { $Script:TextColumn = $arg.Replace("-TextColumn=", "") }
    elseif ($arg -match "^-BatchSize=") { $Script:BatchSize = [int]($arg.Replace("-BatchSize=", "")) }
    elseif ($arg -match "^-MaxSentences=") { $Script:MaxSentences = [int]($arg.Replace("-MaxSentences=", "")) }
    elseif ($arg -eq "-Rebuild") { $Script:Rebuild = $true }
    elseif ($arg -eq "-SkipTagging") { $Script:SkipTagging = $true }
    elseif ($arg -eq "-Dedup") { $Script:Dedup = $true }
    elseif ($arg -match "^-ParallelJobs=") { $Script:ParallelJobs = [int]($arg.Replace("-ParallelJobs=", "")) }
    elseif ($arg -match "^-IndexThreads=") { $Script:IndexThreads = [int]($arg.Replace("-IndexThreads=", "")) }
    elseif ($arg -match "^-OutputBase=") { $Script:OutputBase = $arg.Replace("-OutputBase=", "") }
}

# Function to run test queries
function Run-TestQueries {
    $INDEX_PATH_ABS = [System.IO.Path]::GetFullPath($Script:INDEX_PATH)

    function Invoke-MvnQuery {
        param([string]$Description, [string]$Lemma, [string]$Pattern, [int]$Limit = 5)

        Write-Host $Description -ForegroundColor Green

        $patternEscaped = $Pattern -replace '`', '``'
        $mvnArgs = @(
            "-f", "$PSScriptRoot/pom.xml",
            "exec:java",
            "-Dexec.mainClass=pl.marcinmilkowski.word_sketch.Main",
            "-Dexec.args=query --index `"$INDEX_PATH_ABS`" --lemma $lemma --pattern `"$patternEscaped`" --limit $limit"
        )

        & "mvn" @mvnArgs 2>&1 |
            Where-Object { $_ -notmatch "^\[INFO\]" -or $_ -match "(Querying|Headword|Found|Results:|logDice|Error)" }
    }

    Write-Host ""
    Write-Host "Step 6: Testing word sketch queries..." -ForegroundColor Yellow
    Write-Host ""

    Invoke-MvnQuery "1. Adjectives modifying 'time':" "time" "[tag=jj.*]~{0,3}" 5
    Write-Host ""
    Invoke-MvnQuery "2. Verbs associated with 'problem':" "problem" "[tag=vb.*]~{0,5}" 5
    Write-Host ""
    Invoke-MvnQuery "3. Nouns modified by 'big':" "big" "[tag=nn.*]~{-3,0}" 5
}

# Database Configuration
$POSTGRES_HOST = "localhost"
$POSTGRES_PORT = "5432"
$POSTGRES_USER = "dict_user"
$POSTGRES_PASSWORD = "dict_pass"
$POSTGRES_DB = "dictionary_analytics"
$PSQL_PATH = "C:/Program Files/PostgreSQL/17/bin/psql.exe"
$COPY_PATH = "C:/Program Files/PostgreSQL/17/bin/psql.exe"

# UDPipe Configuration
$UDPIPE_DIR = "udpipe_bin"
$UDPIPE_MODEL = "$UDPIPE_DIR/english-ewt-ud-2.5-191206.udpipe"

$env:PGPASSWORD = $POSTGRES_PASSWORD
$env:JAVA_HOME = "C:/Program Files/Java/jdk-22"
$env:PATH = "$env:JAVA_HOME/bin;$env:PATH"
$env:PGCLIENTENCODING = "UTF8"

# ALL output directories in the same location (not C: drive!)
$Script:TEMP_DIR = Join-Path $Script:OutputBase "temp"
$Script:FINAL_CONLLU = Join-Path $Script:OutputBase "corpus.conllu"
$Script:INDEX_PATH = Join-Path $Script:OutputBase "index"

# Create output directories FIRST
if (-not (Test-Path $Script:OutputBase)) {
    New-Item -ItemType Directory -Path $Script:OutputBase -Force | Out-Null
}
if (-not (Test-Path $Script:TEMP_DIR)) {
    New-Item -ItemType Directory -Path $Script:TEMP_DIR -Force | Out-Null
}

# Temp file path - in the same drive as the index
$Script:tempRaw = Join-Path $Script:TEMP_DIR "all_sentences.txt"

Write-Host "============================================" -ForegroundColor Cyan
Write-Host "Word Sketch Lucene - OPTIMIZED Indexing" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Output location: $Script:OutputBase" -ForegroundColor Green
Write-Host "  Temp dir:      $Script:TEMP_DIR" -ForegroundColor Gray
Write-Host "  CoNLL-U:       $Script:FINAL_CONLLU" -ForegroundColor Gray
Write-Host "  Index:         $Script:INDEX_PATH" -ForegroundColor Gray
Write-Host ""
Write-Host "Configuration:" -ForegroundColor Green
Write-Host "  Database: $POSTGRES_HOST`:$POSTGRES_PORT/$POSTGRES_DB"
Write-Host "  Table: $Script:TableName"
Write-Host "  Parallel UDPipe jobs: $Script:ParallelJobs"
Write-Host "  Index threads: $Script:IndexThreads"
Write-Host "  Deduplication: $(if ($Script:Dedup) { 'Enabled' } else { 'Disabled' })"
Write-Host ""

# Validate prerequisites
if (-not (Test-Path $PSQL_PATH)) {
    Write-Host "ERROR: psql not found at: $PSQL_PATH" -ForegroundColor Red
    exit 1
}

if (-not $Script:SkipTagging) {
    if (-not (Test-Path $UDPIPE_MODEL)) {
        Write-Host "ERROR: UDPipe model not found: $UDPIPE_MODEL" -ForegroundColor Red
        exit 1
    }
    if (-not (Test-Path "$UDPIPE_DIR/udpipe.exe")) {
        Write-Host "ERROR: UDPipe not found: $UDPIPE_DIR/udpipe.exe" -ForegroundColor Red
        exit 1
    }
}

# Get total count
Write-Host "Step 1: Checking database..." -ForegroundColor Yellow
$countQuery = "SELECT reltuples::bigint FROM pg_class WHERE relname = '$Script:TableName';"
$totalCountResult = & $PSQL_PATH -h $POSTGRES_HOST -p $POSTGRES_PORT -U $POSTGRES_USER -d $POSTGRES_DB -t -A -F"|" -c $countQuery 2>&1

if ($totalCountResult -is [array]) {
    $totalCountResult = ($totalCountResult | Where-Object { $_ -match '^\d+$' } | Select-Object -First 1)
}
$totalCount = [int]($totalCountResult.Trim())
if ($Script:MaxSentences -gt 0 -and $Script:MaxSentences -lt $totalCount) {
    $totalCount = $Script:MaxSentences
}

Write-Host "Total sentences in table: $totalCount" -ForegroundColor Green
if ($totalCount -eq 0) {
    Write-Host "ERROR: Table is empty" -ForegroundColor Red
    exit 1
}

# Check if CoNLL-U already exists
if (-not $Script:Rebuild -and (Test-Path $Script:FINAL_CONLLU) -and (Test-Path $Script:INDEX_PATH)) {
    Write-Host "Index already exists. Use -Rebuild to recreate." -ForegroundColor Yellow
    # Run queries
    Run-TestQueries
    exit 0
}

if (-not $Script:SkipTagging) {
    # Clean up old temp files (but NOT all_sentences.txt if it exists!)
    Get-ChildItem -Path $Script:TEMP_DIR -Filter "udpipe_*.conllu" -ErrorAction SilentlyContinue | ForEach-Object {
        Remove-Item $_.FullName
    }
    Get-ChildItem -Path $Script:TEMP_DIR -Filter "udpipe_*.txt" -ErrorAction SilentlyContinue | ForEach-Object {
        Remove-Item $_.FullName
    }

    # Step 2: BULK EXPORT - Check if file already exists
    Write-Host ""
    Write-Host "Step 2: Bulk exporting from PostgreSQL..." -ForegroundColor Yellow

    if (Test-Path $Script:tempRaw) {
        Write-Host "  Reusing existing: $Script:tempRaw" -ForegroundColor Gray
        $fileSize = (Get-ChildItem $Script:tempRaw).Length / 1MB
        Write-Host "  File size: $([math]::Round($fileSize, 2)) MB" -ForegroundColor Gray
    } else {
        if ($Script:Dedup) {
            $copyQuery = "\COPY (SELECT DISTINCT $Script:TextColumn FROM $Script:TableName ORDER BY $Script:TextColumn) TO '$Script:tempRaw' WITH CSV"
            Write-Host "  Exporting with deduplication (DISTINCT) to $Script:TEMP_DIR..." -ForegroundColor Gray
        } else {
            $copyQuery = "\COPY (SELECT $Script:TextColumn FROM $Script:TableName ORDER BY $Script:IdColumn) TO '$Script:tempRaw' WITH CSV"
            Write-Host "  Exporting all records to $Script:TEMP_DIR..." -ForegroundColor Gray
        }

        $sw = [System.Diagnostics.Stopwatch]::StartNew()
        & $COPY_PATH -h $POSTGRES_HOST -p $POSTGRES_PORT -U $POSTGRES_USER -d $POSTGRES_DB -c $copyQuery 2>&1
        $sw.Stop()

        $fileSize = (Get-ChildItem $Script:tempRaw).Length / 1MB
        Write-Host "  Exported $([math]::Round($fileSize, 2)) MB in $($sw.Elapsed.TotalSeconds)s" -ForegroundColor Gray
    }

    # Step 3: PARALLEL UDPipe processing - split ONCE, then process
    Write-Host ""
    Write-Host "Step 3: Parallel UDPipe tagging..." -ForegroundColor Yellow
    Write-Host "  Running $Script:ParallelJobs concurrent UDPipe instances" -ForegroundColor Gray

    # Count lines first (single pass)
    $totalLines = 0
    $reader = [System.IO.File]::OpenText($Script:tempRaw)
    $line = $reader.ReadLine()
    while ($null -ne $line) {
        $totalLines++
        $line = $reader.ReadLine()
    }
    $reader.Close()
    $linesPerJob = [math]::Ceiling($totalLines / $Script:ParallelJobs)

    Write-Host "  Splitting $totalLines lines into $Script:ParallelJobs jobs ($linesPerJob lines/job)" -ForegroundColor Gray

    # Create writers for each job (one pass through source file)
    $writers = @()
    for ($job = 0; $job -lt $Script:ParallelJobs; $job++) {
        $tempInput = "$Script:TEMP_DIR/udpipe_$job.txt"
        $writers += @{
            Writer = [System.IO.File]::CreateText($tempInput)
            Job = $job
            Count = 0
        }
    }

    # Single pass: distribute lines to chunks
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $lineNum = 0
    $reader = [System.IO.File]::OpenText($Script:tempRaw)
    while ($line = $reader.ReadLine()) {
        $jobIndex = [math]::Floor($lineNum / $linesPerJob)
        if ($jobIndex -ge $Script:ParallelJobs) { $jobIndex = $Script:ParallelJobs - 1 }
        $writers[$jobIndex].Writer.WriteLine($line)
        $writers[$jobIndex].Count++
        $lineNum++
    }
    $reader.Close()
    foreach ($w in $writers) { $w.Writer.Close() }
    $sw.Stop()

    Write-Host "  Split to files in $($sw.Elapsed.TotalSeconds)s" -ForegroundColor Gray

    # Now run UDPipe on each chunk in parallel
    # UDPipe takes input file directly: udpipe [options] model input_file
    $jobs = @()
    $sw = [System.Diagnostics.Stopwatch]::StartNew()

    for ($job = 0; $job -lt $Script:ParallelJobs; $job++) {
        $tempInput = "$Script:TEMP_DIR/udpipe_$job.txt"
        $tempOutput = "$Script:TEMP_DIR/udpipe_$job.conllu"

        # Skip empty chunks
        if (-not (Test-Path $tempInput)) { continue }
        if ((Get-Item $tempInput).Length -eq 0) {
            Remove-Item $tempInput
            continue
        }

        $udpExe = Resolve-Path "$UDPIPE_DIR/udpipe.exe"
        $udpModel = Resolve-Path $UDPIPE_MODEL

        # Use PowerShell background job for parallel execution
        # UDPipe writes directly to file with --output and --outfile
        # No redirection needed - stderr still goes to console
        $jobScript = {
            param($exe, $model, $inputFile, $outputFile)
            & $exe --tokenize --tag --output=conllu --outfile=$outputFile $model $inputFile 2>$null
        }

        Write-Host "    Starting UDPipe job $job..." -ForegroundColor Gray
        $psJob = Start-Job -ScriptBlock $jobScript -ArgumentList $udpExe, $udpModel, $tempInput, $tempOutput
        $jobs += @{ Job = $psJob; Output = $tempOutput; Input = $tempInput }
    }

    # Wait for all UDPipe jobs
    Write-Host "  Running $($jobs.Count) UDPipe processes..." -ForegroundColor Gray
    $completed = 0
    while ($completed -lt $jobs.Count) {
        $completed = ($jobs | Where-Object { $_.Job.State -eq 'Completed' }).Count
        Start-Sleep -Seconds 2
        $percent = [math]::Round(($completed / $jobs.Count) * 100, 1)
        Write-Host "    $completed/$($jobs.Count) jobs completed ($percent%)..." -ForegroundColor Gray
    }
    $sw.Stop()
    Write-Host "  UDPipe complete in $($sw.Elapsed.TotalSeconds)s" -ForegroundColor Green

    # Clean up jobs
    $jobs | ForEach-Object { Remove-Job -Job $_.Job -ErrorAction SilentlyContinue }

    # Clean up input files (keep all_sentences.txt for potential re-runs)
    $jobs | ForEach-Object { Remove-Item $_.Input -ErrorAction SilentlyContinue }

    # Step 4: Merge results
    Write-Host ""
    Write-Host "Step 4: Merging UDPipe results..." -ForegroundColor Yellow

    if (Test-Path $Script:FINAL_CONLLU) {
        Remove-Item $Script:FINAL_CONLLU
    }

    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $first = $true
    Get-ChildItem -Path $Script:TEMP_DIR -Filter "udpipe_*.conllu" | Sort-Object Name | ForEach-Object {
        try {
            $content = [System.IO.File]::ReadAllText($_.FullName, [System.Text.Encoding]::UTF8)
            if ($null -ne $content -and $content.Length -gt 0) {
                if (-not $first) {
                    [System.IO.File]::AppendAllText($Script:FINAL_CONLLU, "`n", [System.Text.Encoding]::UTF8)
                }
                [System.IO.File]::AppendAllText($Script:FINAL_CONLLU, $content, [System.Text.Encoding]::UTF8)
                $first = $false
            }
        } catch {
            Write-Host "    Warning: Could not read $_" -ForegroundColor Yellow
        }
    }
    $sw.Stop()

    $finalSize = (Get-ChildItem $Script:FINAL_CONLLU).Length / 1MB
    Write-Host "  Merged to $finalSize MB in $($sw.Elapsed.TotalSeconds)s" -ForegroundColor Gray

    # Clean up temp files (keep all_sentences.txt)
    Get-ChildItem -Path $Script:TEMP_DIR -Filter "udpipe_*.conllu" | Remove-Item
}

# Step 5: Build Word Sketch index
Write-Host ""
Write-Host "Step 5: Building Word Sketch index..." -ForegroundColor Yellow
Write-Host "  Using $Script:IndexThreads parallel indexing threads" -ForegroundColor Gray

$INDEX_PATH_ABS = [System.IO.Path]::GetFullPath($Script:INDEX_PATH)
$FINAL_CONLLU_ABS = [System.IO.Path]::GetFullPath($Script:FINAL_CONLLU)

$mvnArgs = @(
    "-f", "$PSScriptRoot/pom.xml",
    "exec:java",
    "-Dexec.mainClass=pl.marcinmilkowski.word_sketch.Main",
    "-Dexec.args=conllu --input `"$FINAL_CONLLU_ABS`" --output `"$INDEX_PATH_ABS`" --commit 100000 --threads $Script:IndexThreads"
)

$sw = [System.Diagnostics.Stopwatch]::StartNew()
$mvnOutput = & "mvn" @mvnArgs 2>&1 | Out-String
$sw.Stop()

$mvnOutput -split "`n" | ForEach-Object {
    if ($_ -notmatch "^\[INFO\]" -or $_ -match "(Building|Processing|Indexed|Indexing|Complete|Error|Exception|tokens/sec)") {
        Write-Host $_
    }
}

if ($LASTEXITCODE -ne 0 -or -not (Test-Path $Script:INDEX_PATH)) {
    Write-Host "ERROR: Failed to build word sketch index" -ForegroundColor Red
    exit 1
}

$indexSize = (Get-ChildItem $Script:INDEX_PATH -Recurse -ErrorAction SilentlyContinue | Measure-Object -Property Length -Sum -ErrorAction SilentlyContinue).Sum / 1MB
Write-Host ""
Write-Host "Index created: $Script:INDEX_PATH" -ForegroundColor Green
Write-Host "Index size: $([math]::Round($indexSize, 2)) MB" -ForegroundColor Gray

# Step 6: Test queries
Write-Host ""
Write-Host "Step 6: Testing word sketch queries..." -ForegroundColor Yellow
Write-Host ""

$INDEX_PATH_ABS = [System.IO.Path]::GetFullPath($Script:INDEX_PATH)

function Invoke-MvnQuery {
    param([string]$Description, [string]$Lemma, [string]$Pattern, [int]$Limit = 5)

    Write-Host $Description -ForegroundColor Green

    $patternEscaped = $Pattern -replace '`', '``'
    $mvnArgs = @(
        "-f", "$PSScriptRoot/pom.xml",
        "exec:java",
        "-Dexec.mainClass=pl.marcinmilkowski.word_sketch.Main",
        "-Dexec.args=query --index `"$INDEX_PATH_ABS`" --lemma $lemma --pattern `"$patternEscaped`" --limit $limit"
    )

    & "mvn" @mvnArgs 2>&1 |
        Where-Object { $_ -notmatch "^\[INFO\]" -or $_ -match "(Querying|Headword|Found|Results:|logDice|Error)" }
}

Invoke-MvnQuery "1. Adjectives modifying 'time':" "time" "[tag=jj.*]~{0,3}" 5
Write-Host ""
Invoke-MvnQuery "2. Verbs associated with 'problem':" "problem" "[tag=vb.*]~{0,5}" 5
Write-Host ""
Invoke-MvnQuery "3. Nouns modified by 'big':" "big" "[tag=nn.*]~{-3,0}" 5

Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "Indexing Complete!" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Data location: $Script:OutputBase" -ForegroundColor Green
Write-Host "To start the API server:" -ForegroundColor Green
Write-Host "  java -jar word-sketch-lucene-1.0.0.jar server --index `"$Script:INDEX_PATH`" --port 8080"
Write-Host ""

exit 0
