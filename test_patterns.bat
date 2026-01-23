@echo off
REM Test grammar patterns for Word Sketch Lucene
set INDEX_PATH=target/corpus-udpipe
set LEMMA=problem

echo ========================================
echo Testing Grammar Patterns
echo ========================================
echo.

echo Testing: Adjectives (jj.*) within 3 words
mvn exec:java -Dexec.mainClass=pl.marcinmilkowski.word_sketch.Main -Dexec.args="query --index %INDEX_PATH% --lemma %LEMMMA% --pattern [tag=""jj.*""]~{0,3} --limit 5" 2>&1 | findstr /v "^\[INFO\]" | findstr /v "^$"

echo.
echo Testing: Nouns before headword (subjects)
mvn exec:java -Dexec.mainClass=pl.marcinmilkowski.word_sketch.Main -Dexec.args="query --index %INDEX_PATH% --lemma %LEMMMA% --pattern [tag=""nn.*""]~{-5,0} --limit 5" 2>&1 | findstr /v "^\[INFO\]" | findstr /v "^$"

echo.
echo Testing: Verbs (vb.*)
mvn exec:java -Dexec.mainClass=pl.marcinmilkowski.word_sketch.Main -Dexec.args="query --index %INDEX_PATH% --lemma %LEMMMA% --pattern ""vb.*"" --limit 5" 2>&1 | findstr /v "^\[INFO\]" | findstr /v "^$"

echo.
echo Testing: Prepositions (in)
mvn exec:java -Dexec.mainClass=pl.marcinmilkowski.word_sketch.Main -Dexec.args="query --index %INDEX_PATH% --lemma %LEMMMA% --pattern [word=""of""]~{0,3} --limit 5" 2>&1 | findstr /v "^\[INFO\]" | findstr /v "^$"

echo.
echo ========================================
echo Tests Complete
echo ========================================
