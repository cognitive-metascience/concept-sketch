# Merge Prep Checklist for v1.5.0

## ✅ Completed Tasks

### Version Bumping
- [x] Updated pom.xml: version 1.0.1 → 1.5.0
- [x] Updated pom.xml: artifactId word-sketch-lucene → concept-sketch
- [x] Updated pom.xml: name/description to ConceptSketch

### Naming Consistency
- [x] Updated Java files:
  - Main.java: Updated banner and usage text
  - CorpusBuilder.java: Updated print message
  - StatisticsIndexBuilder.java: Updated comments and file headers
- [x] Updated README.md: All references to word-sketch-lucene-1.0.1-shaded.jar → concept-sketch-1.5.0-shaded.jar
- [x] Updated CLAUDE.md: Version references and jar names updated
- [x] Updated BLACKLAB_SETUP.md: Version and artifact names updated
- [x] Updated shell scripts (scripts/start-*.sh, scripts/start-*.ps1)

### Documentation Cleanup
- [x] Removed outdated planning documents:
  - plans/query-architecture-action-plan.md
  - plans/query-architecture-review.md
  - plans/query-engine-overhaul-plan.md
  - plans/snowball-cql-fix-plan.md
  - plans/stateful-stirring-shore.md
- [x] Renamed main spec: word-sketch-lucene-spec.md → concept-sketch-spec.md

### Debug File Cleanup
- [x] Removed debug log files:
  - hs_err_pid38036.log
  - server-out.log
  - server.log
  - server3.log
- [x] Removed test data: test_filter_out.conllu
- [x] Removed temporary files: *.bak, *.tmp files
- [x] Kept utility scripts (filter_conllu_boilerplate.py, filter_text_corpus.py - still useful)

### Artifact Naming
- [x] pom.xml: artifactId concept-sketch, version 1.5.0
- [x] All script references updated to use concept-sketch artifact
- [x] All documentation updated with correct jar filename pattern

## 📋 Before Merging

**Final Steps:**
1. Run `mvn clean package` to generate the correct JAR:
   - Expected: `target/concept-sketch-1.5.0-shaded.jar`
   - Old: `target/word-sketch-lucene-1.0.1-shaded.jar` (should be gone)

2. Verify no "Word Sketch Lucene" references remain:
   ```bash
   grep -r "Word Sketch Lucene" . --exclude-dir=.git --exclude-dir=.history --exclude-dir=.omc
   ```

3. Run tests:
   ```bash
   mvn test
   ```

4. Test API server startup:
   ```bash
   java -jar target/concept-sketch-1.5.0-shaded.jar server --help
   ```

5. Commit with message:
   ```
   chore: bump version to 1.5.0 and rename to ConceptSketch

   - Updated artifact ID from word-sketch-lucene to concept-sketch
   - Bumped version from 1.0.1 to 1.5.0
   - Updated all references in code, docs, and scripts
   - Removed outdated planning documents and debug files
   - Main spec renamed to concept-sketch-spec.md
   ```

6. Push to blacklab-sketches branch for PR review

## 📊 Summary of Changes

| Category | Count |
|----------|-------|
| Files modified | 15+ |
| Files deleted | 8 |
| Version references updated | 30+ |
| Documentation files | 5 |

**Branch Ready For Merging:** ✅ Yes (after final build verification)
