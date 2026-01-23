@echo off
REM Word Sketch Generator - Simple batch file for Windows
REM Usage: word_sketch_simple.bat [lemma] [limit]

setlocal EnableDelayedExpansion

set "LEMMA=%~1"
if "%LEMMA%"=="" set LEMMA=problem
set "LIMIT=%~2"
if "%LIMIT%"=="" set LIMIT=10

set "INDEX_PATH=target/corpus-udpipe"

echo.
echo ============================================================
echo   WORD SKETCH GENERATOR
echo   Lemma: %LEMMA%
echo   Index: %INDEX_PATH%
echo ============================================================
echo.

echo NOTE: First query will trigger Maven build if needed...

echo.
echo NOUN RELATIONS
echo ===============

echo.
echo   Adjectives modifying (modifiers)...
call mvn exec:java -Dexec.mainClass=pl.marcinmilkowski.word_sketch.Main -Dexec.args="query --index %INDEX_PATH% --lemma %LEMMA% --pattern ""[tag=jj.*]~{0,3}"" --limit %LIMIT%" 2>&1
echo.

echo   Verbs with as object...
call mvn exec:java -Dexec.mainClass=pl.marcinmilkowski.word_sketch.Main -Dexec.args="query --index %INDEX_PATH% --lemma %LEMMA% --pattern ""[tag=vb.*]~{0,5}"" --limit %LIMIT%" 2>&1
echo.

echo   Verbs as subject...
call mvn exec:java -Dexec.mainClass=pl.marcinmilkowski.word_sketch.Main -Dexec.args="query --index %INDEX_PATH% --lemma %LEMMA% --pattern ""[tag=nn.*]~{-5,0}"" --limit %LIMIT%" 2>&1
echo.

echo   Nouns in compound (noun+noun)...
call mvn exec:java -Dexec.mainClass=pl.marcinmilkowski.word_sketch.Main -Dexec.args="query --index %INDEX_PATH% --lemma %LEMMA% --pattern ""[tag=nn.*]~{1,2} [tag=nn.*]"" --limit %LIMIT%" 2>&1
echo.

echo   Adverbs...
call mvn exec:java -Dexec.mainClass=pl.marcinmilkowski.word_sketch.Main -Dexec.args="query --index %INDEX_PATH% --lemma %LEMMA% --pattern ""[tag=rb.*]~{0,3}"" --limit %LIMIT%" 2>&1
echo.

echo   Determiners...
call mvn exec:java -Dexec.mainClass=pl.marcinmilkowski.word_sketch.Main -Dexec.args="query --index %INDEX_PATH% --lemma %LEMMA% --pattern ""[tag=dt]~{0,1}"" --limit %LIMIT%" 2>&1
echo.

echo   Prepositions (of, for, etc.)...
call mvn exec:java -Dexec.mainClass=pl.marcinmilkowski.word_sketch.Main -Dexec.args="query --index %INDEX_PATH% --lemma %LEMMA% --pattern ""[word=of]~{0,3}"" --limit %LIMIT%" 2>&1
echo.

echo.
echo VERB RELATIONS
echo ===============

echo.
echo   Direct objects...
call mvn exec:java -Dexec.mainClass=pl.marcinmilkowski.word_sketch.Main -Dexec.args="query --index %INDEX_PATH% --lemma %LEMMA% --pattern ""[tag=vb.*]~{0,5} [tag=nn.*]"" --limit %LIMIT%" 2>&1
echo.

echo   Subjects (who VERBs)...
call mvn exec:java -Dexec.mainClass=pl.marcinmilkowski.word_sketch.Main -Dexec.args="query --index %INDEX_PATH% --lemma %LEMMA% --pattern ""[tag=nn.*]~{-5,0} [tag=vb.*]"" --limit %LIMIT%" 2>&1
echo.

echo   Particles (verb+particle)...
call mvn exec:java -Dexec.mainClass=pl.marcinmilkowski.word_sketch.Main -Dexec.args="query --index %INDEX_PATH% --lemma %LEMMA% --pattern ""[tag=vb.*]~{0,2} [tag=rp]"" --limit %LIMIT%" 2>&1
echo.

echo   Infinitive 'to'...
call mvn exec:java -Dexec.mainClass=pl.marcinmilkowski.word_sketch.Main -Dexec.args="query --index %INDEX_PATH% --lemma %LEMMA% --pattern ""[tag=vb.*]~{0,3} [word=to]~{0,2}"" --limit %LIMIT%" 2>&1
echo.

echo   Gerunds (-ing)...
call mvn exec:java -Dexec.mainClass=pl.marcinmilkowski.word_sketch.Main -Dexec.args="query --index %INDEX_PATH% --lemma %LEMMA% --pattern ""[tag=vb.*]~{0,3} [tag=vbg]"" --limit %LIMIT%" 2>&1
echo.

echo   Passive 'by' agent...
call mvn exec:java -Dexec.mainClass=pl.marcinmilkowski.word_sketch.Main -Dexec.args="query --index %INDEX_PATH% --lemma %LEMMA% --pattern ""[tag=vbn]~{0,3} [word=by]~{0,2}"" --limit %LIMIT%" 2>&1
echo.

echo.
echo ADJECTIVE RELATIONS
echo ====================

echo.
echo   Nouns modified (predicates)...
call mvn exec:java -Dexec.mainClass=pl.marcinmilkowski.word_sketch.Main -Dexec.args="query --index %INDEX_PATH% --lemma %LEMMA% --pattern ""[tag=nn.*]~{-3,0} [tag=jj.*]"" --limit %LIMIT%" 2>&1
echo.

echo   Verbs with adjective complement...
call mvn exec:java -Dexec.mainClass=pl.marcinmilkowski.word_sketch.Main -Dexec.args="query --index %INDEX_PATH% --lemma %LEMMA% --pattern ""[tag=vb.*]~{0,5} [tag=jj.*]"" --limit %LIMIT%" 2>&1
echo.

echo   Adverbs modifying...
call mvn exec:java -Dexec.mainClass=pl.marcinmilkowski.word_sketch.Main -Dexec.args="query --index %INDEX_PATH% --lemma %LEMMA% --pattern ""[tag=rb.*]~{0,2} [tag=jj.*]"" --limit %LIMIT%" 2>&1
echo.

echo   After noun (postnominal)...
call mvn exec:java -Dexec.mainClass=pl.marcinmilkowski.word_sketch.Main -Dexec.args="query --index %INDEX_PATH% --lemma %LEMMA% --pattern ""[tag=nn.*]~{0,3} [tag=jj.*]"" --limit %LIMIT%" 2>&1
echo.

echo   With 'very' or 'too'...
call mvn exec:java -Dexec.mainClass=pl.marcinmilkowski.word_sketch.Main -Dexec.args="query --index %INDEX_PATH% --lemma %LEMMA% --pattern ""[word=very|word=too]~{0,1} [tag=jj.*]"" --limit %LIMIT%" 2>&1
echo.

echo.
echo ============================================================
echo Word sketch complete.
echo ============================================================
echo.

endlocal
