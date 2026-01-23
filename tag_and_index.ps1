# Word Sketch Lucene - Full Database Indexing Pipeline
# Indexes the entire table from PostgreSQL into a Word Sketch Lucene index

param(
    [string]$TableName = "parallel_corpus",
    [string]$IdColumn = "id",
    [string]$TextColumn = "source_text",
    [int]$BatchSize = 10000,
    [int]$MaxSentences = 0,
    [switch]$Rebuild,
    [string]$OutputDir = "target",
    [string]$ProjectRoot = "D:\git\word-sketch-lucene"
)

# Database Configuration
$POSTGRES_HOST = "localhost"
$POSTGRES_PORT = "5432"
$POSTGRES_USER = "dict_user"
$POSTGRES_PASSWORD = "dict_pass"
$POSTGRES_DB = "dictionary_analytics"
$PSQL_PATH = "C:\Program Files\PostgreSQL\17\bin\psql.exe"

# UDPipe Configuration
$UDPIPE_DIR = "udpipe_bin"
$UDPIPE_MODEL = "$UDPIPE_DIR\english-ewt-ud-2.5-191206.udpipe"

$env:PGPASSWORD = $POSTGRES_PASSWORD
$env:JAVA_HOME = "C:/Program Files/Java/jdk-22"
$env:PATH = "$env:JAVA_HOME/bin;$env:PATH"

Write-Host "============================================" -ForegroundColor Cyan
Write-Host "Word Sketch Lucene - Full Database Indexing" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

$totalToProcess = if ($MaxSentences -gt 0) { $MaxSentences } else { "ALL" }
Write-Host "Configuration:" -ForegroundColor Green
Write-Host "  Database: $POSTGRES_HOST`:$POSTGRES_PORT/$POSTGRES_DB"
Write-Host "  Table: $TableName"
Write-Host "  Column: $TextColumn"
Write-Host "  Batch size: $BatchSize sentences"
Write-Host "  Max sentences: $totalToProcess"
Write-Host "  Rebuild: $($Rebuild -or $Rebuild.IsPresent)"
Write-Host ""

# Check prerequisites
if (-not (Test-Path $PSQL_PATH)) {
    Write-Host "ERROR: psql not found at: $PSQL_PATH" -ForegroundColor Red
    exit 1
}

if (-not (Test-Path $UDPIPE_MODEL)) {
    Write-Host "ERROR: UDPipe model not found: $UDPIPE_MODEL" -ForegroundColor Red
    exit 1
}

if (-not (Test-Path "$UDPIPE_DIR\udpipe.exe")) {
    Write-Host "ERROR: UDPipe not found: $UDPIPE_DIR\udpipe.exe" -ForegroundColor Red
    exit 1
}

# Paths - use project directory as base
$TEMP_DIR = Join-Path $ProjectRoot "target\indexing_temp"
$FINAL_CONLLU = Join-Path $ProjectRoot "target\corpus_full.conllu"
$INDEX_PATH = Join-Path $ProjectRoot "target\corpus-udpipe"

if (-not (Test-Path $TEMP_DIR)) {
    New-Item -ItemType Directory -Path $TEMP_DIR -Force | Out-Null
}

# Get total count
Write-Host "Step 1: Checking database..." -ForegroundColor Yellow
Write-Host ""

# For large tables, use index-friendly count or skip counting entirely
# Try to get count with an index, otherwise process all records until exhaustion
Write-Host "Estimating sentence count (using index)..." -ForegroundColor Gray

# Try counting with index hint first (faster if id column is indexed)
$countQuery = "SELECT reltuples::bigint FROM pg_class WHERE relname = '$TableName';"
$totalCountResult = & $PSQL_PATH -h $POSTGRES_HOST -p $POSTGRES_PORT -U $POSTGRES_USER -d $POSTGRES_DB -t -A -F"|" -c $countQuery 2>&1

# Extract the actual count from the result (handle array output)
if ($totalCountResult -is [array]) {
    $totalCountResult = ($totalCountResult | Where-Object { $_ -match '^\d+$' } | Select-Object -First 1)
}

if ([string]::IsNullOrWhiteSpace($totalCountResult)) {
    # Fallback: just set to 0 and we'll process until exhaustion
    $totalCountResult = "0"
}

$totalCount = [int]$totalCountResult.Trim()
if ($MaxSentences -gt 0 -and $MaxSentences -lt $totalCount) {
    $totalCount = $MaxSentences
}

Write-Host "Total sentences in table: $totalCount" -ForegroundColor Green
Write-Host ""

if ($totalCount -eq 0) {
    Write-Host "ERROR: Table is empty" -ForegroundColor Red
    exit 1
}

$numBatches = [math]::Ceiling($totalCount / $BatchSize)
Write-Host "Will process in $numBatches batches of $BatchSize sentences each" -ForegroundColor Cyan
Write-Host ""

if ($Rebuild -or $Rebuild.IsPresent -and (Test-Path $INDEX_PATH)) {
    Write-Host "Removing existing index..." -ForegroundColor Yellow
    Remove-Item -Recurse -Force $INDEX_PATH -ErrorAction SilentlyContinue
}

if (-not $Rebuild -and (Test-Path $FINAL_CONLLU) -and (Test-Path $INDEX_PATH)) {
    Write-Host "Index already exists. Use -Rebuild to recreate." -ForegroundColor Yellow
    goto :RunQueries
}

# Clean up temp files
Get-ChildItem -Path $TEMP_DIR -Filter "batch_*.conllu" -ErrorAction SilentlyContinue | ForEach-Object {
    Remove-Item $_.FullName
}

# Step 2: Process in batches
Write-Host "Step 2: Processing sentences in batches..." -ForegroundColor Yellow
Write-Host ""

$processedCount = 0
$batchNum = 1
$sw = [System.Diagnostics.Stopwatch]::StartNew()

while ($processedCount -lt $totalCount) {
    $remaining = $totalCount - $processedCount
    $currentBatchSize = [math]::Min($BatchSize, $remaining)

    Write-Host "Batch $batchNum of $numBatches ($currentBatchSize sentences)..." -ForegroundColor Gray

    $tempSentences = "$TEMP_DIR\sentences_$batchNum.txt"
    $tempConllu = "$TEMP_DIR\batch_$batchNum.conllu"

    $offset = $processedCount
    $query = "SELECT $TextColumn FROM $TableName ORDER BY $IdColumn LIMIT $currentBatchSize OFFSET $offset;"

    & $PSQL_PATH -h $POSTGRES_HOST -p $POSTGRES_PORT -U $POSTGRES_USER -d $POSTGRES_DB -t -A -F"|" -c $query 2>&1 | Out-File -FilePath $tempSentences -Encoding UTF8

    if ($LASTEXITCODE -ne 0) {
        Write-Host "ERROR: Failed to fetch batch $batchNum" -ForegroundColor Red
        exit 1
    }

    $sentenceCount = (Get-Content $tempSentences | Measure-Object -Line).Lines
    if ($sentenceCount -eq 0) {
        Write-Host "No more sentences to process" -ForegroundColor Yellow
        break
    }

    $udpExe = (Get-Item "$UDPIPE_DIR\udpipe.exe").FullName
    $udpModel = (Get-Item $UDPIPE_MODEL).FullName

    $udpCmd = "`"$udpExe`" --tokenize --tag --output=conllu `"$udpModel`" < `"$tempSentences`" > `"$tempConllu`""
    cmd /c $udpCmd

    if ($LASTEXITCODE -ne 0) {
        Write-Host "ERROR: UDPipe failed on batch $batchNum" -ForegroundColor Red
        exit 1
    }

    Remove-Item $tempSentences -ErrorAction SilentlyContinue

    $processedCount += $currentBatchSize
    $batchNum++

    $percent = [math]::Round(($processedCount / $totalCount) * 100, 1)
    $elapsed = $sw.Elapsed.TotalSeconds
    $rate = if ($elapsed -gt 0) { [math]::Round($processedCount / $elapsed, 0) } else { 0 }
    Write-Host "  -> Processed $processedCount / $totalCount ($percent%) - $rate sentences/sec" -ForegroundColor Gray
}

$sw.Stop()
Write-Host ""
Write-Host "Completed UDPipe tagging in $($sw.Elapsed.TotalSeconds) seconds" -ForegroundColor Green
Write-Host ""

# Step 3: Merge all batch files
Write-Host "Step 3: Merging CoNLL-U batches..." -ForegroundColor Yellow
Write-Host ""

if (Test-Path $FINAL_CONLLU) {
    Remove-Item $FINAL_CONLLU
}

$batchFiles = Get-ChildItem -Path $TEMP_DIR -Filter "batch_*.conllu" | Sort-Object Name

if ($batchFiles.Count -eq 0) {
    Write-Host "ERROR: No CoNLL-U files found to merge" -ForegroundColor Red
    exit 1
}

Write-Host "Merging $($batchFiles.Count) batch files..." -ForegroundColor Gray

foreach ($file in $batchFiles) {
    Get-Content $file.FullName | Add-Content -Path $FINAL_CONLLU -Encoding UTF8
    "" | Add-Content -Path $FINAL_CONLLU -Encoding UTF8
}

Write-Host "Merged output: $FINAL_CONLLU" -ForegroundColor Green
$finalSize = (Get-ChildItem $FINAL_CONLLU).Length / 1MB
Write-Host "File size: $([math]::Round($finalSize, 2)) MB" -ForegroundColor Gray

# Clean up batch files
Write-Host "Cleaning up temporary files..." -ForegroundColor Gray
$batchFiles | ForEach-Object { Remove-Item $_.FullName }
Get-ChildItem -Path $TEMP_DIR -Filter "*.txt" | ForEach-Object { Remove-Item $_.FullName }

Write-Host ""

# Step 4: Build Word Sketch index
Write-Host "Step 4: Building Word Sketch index..." -ForegroundColor Yellow
Write-Host ""

# Use .NET Path methods for path resolution (doesn't require path to exist)
$INDEX_PATH_ABS = [System.IO.Path]::GetFullPath($INDEX_PATH)
$FINAL_CONLLU_ABS = [System.IO.Path]::GetFullPath($FINAL_CONLLU)

Write-Host "Input:  $FINAL_CONLLU_ABS" -ForegroundColor Gray
Write-Host "Output: $INDEX_PATH_ABS" -ForegroundColor Gray
Write-Host ""

# Build Maven command arguments as array to avoid quoting issues
$mvnArgs = @(
    "-f", "$ProjectRoot/pom.xml",
    "exec:java",
    "-Dexec.mainClass=pl.marcinmilkowski.word_sketch.Main",
    "-Dexec.args=conllu --input $FINAL_CONLLU_ABS --output $INDEX_PATH_ABS --commit 50000"
)

# Run Maven and capture output
$mvnOutput = & "mvn" @mvnArgs 2>&1 | Out-String

# Filter and display relevant output
$mvnOutput -split "`n" | ForEach-Object {
    if ($_ -notmatch "^\[INFO\]" -or $_ -match "(Building|Processing|Indexed|Indexing|Complete|Error|Exception)") {
        Write-Host $_
    }
}

if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: Failed to build word sketch index" -ForegroundColor Red
    exit 1
}

if (-not (Test-Path $INDEX_PATH)) {
    Write-Host "ERROR: Index was not created" -ForegroundColor Red
    exit 1
}

$indexSize = (Get-ChildItem $INDEX_PATH -Recurse | Measure-Object -Property Length -Sum).Sum / 1MB
Write-Host ""
Write-Host "Index created: $INDEX_PATH" -ForegroundColor Green
Write-Host "Index size: $([math]::Round($indexSize, 2)) MB" -ForegroundColor Gray

# Step 5: Run test queries
Write-Host ""
Write-Host "Step 5: Testing word sketch queries..." -ForegroundColor Yellow
Write-Host ""

# Ensure we have absolute paths
$INDEX_PATH_ABS = [System.IO.Path]::GetFullPath($INDEX_PATH)

# Helper function to run Maven query
function Run-MvnQuery {
    param([string]$Description, [string]$Lemma, [string]$Pattern, [int]$Limit = 5)

    Write-Host $Description -ForegroundColor Green

    $patternEscaped = $Pattern -replace '`', '``'
    $mvnArgs = @(
        "-f", "$ProjectRoot/pom.xml",
        "exec:java",
        "-Dexec.mainClass=pl.marcinmilkowski.word_sketch.Main",
        "-Dexec.args=query --index $INDEX_PATH_ABS --lemma $lemma --pattern `"$patternEscaped`" --limit $limit"
    )

    & "mvn" @mvnArgs 2>&1 |
        Where-Object { $_ -notmatch "^\[INFO\]" -or $_ -match "(Querying|Headword|Found|Results:|logDice|Error)" }
}

# Run test queries
Run-MvnQuery "1. Adjectives modifying 'time':" "time" "[tag=jj.*]~{0,3}" 5
Write-Host ""
Run-MvnQuery "2. Verbs associated with 'problem':" "problem" "[tag=vb.*]~{0,5}" 5
Write-Host ""
Run-MvnQuery "3. Nouns modified by 'big':" "big" "[tag=nn.*]~{-3,0}" 5

Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "Indexing Complete!" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "To start the API server:" -ForegroundColor Green
Write-Host "  mvn exec:java -Dexec.mainClass=pl.marcinmilkowski.word_sketch.Main -Dexec.args=\`"server --index $INDEX_PATH_ABS --port 8080\`""
Write-Host ""
Write-Host "To query via API:" -ForegroundColor Green
Write-Host "  curl http://localhost:8080/api/sketch/problem?pos=noun"
Write-Host ""

exit 0
