# GitHub Actions Workflows

## `ci.yml` — Build & Unit Tests

Triggers on every push and pull request targeting `main`.

### What it does

| Step | Command |
|------|---------|
| Set up Java 21 (Temurin) | `actions/setup-java@v4` |
| Restore/save Maven cache | `actions/cache@v4` (key: pom.xml hash) |
| Install BlackLab 4.0.0 | `./install-blacklab.sh` |
| Compile | `mvn compile` |
| Unit tests (no integration) | `mvn test -DexcludedGroups=integration` |

---

### Why BlackLab must be installed manually

**BlackLab 4.0.0 is not on Maven Central** (the latest published version there
is 3.0.1).  The JARs are distributed as a release zip on GitHub:

```
https://github.com/instituutnederlandsetaal/BlackLab/releases/download/v4.0.0/blacklab-4.0.0-jar.zip
```

`install-blacklab.sh` (and `install-blacklab.ps1` for Windows) downloads this
zip and runs `mvn install:install-file` for each module, making it available
under `~/.m2/repository/nl/inl/blacklab/`.

The Maven cache in CI is keyed on `pom.xml`, so the download only happens on
the first run or after dependency changes.

---

### Why integration tests are excluded in CI

Tests tagged `@Tag("integration")` (e.g. `BlackLabIntegrationTest`,
`BlackLabQueryExecutorIntegrationTest`, `BlackLabBcqlConcordanceTest`) query a
pre-indexed corpus.  The index path is supplied via the environment variable:

```
CONCEPT_SKETCH_TEST_INDEX=/path/to/corpus/index
```

A corpus index is a large binary artefact (~hundreds of MB) that cannot be
provisioned in a standard GitHub Actions runner.  These tests are therefore
excluded with `-DexcludedGroups=integration`.

---

### Running the full suite locally

```bash
# One-time: install BlackLab into local Maven repository
./install-blacklab.sh            # Linux / macOS
.\install-blacklab.ps1           # Windows

# Point to your corpus index
export CONCEPT_SKETCH_TEST_INDEX=/path/to/corpus/index

# Run everything (unit + integration)
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn test

# Run unit tests only (same as CI)
mvn test -DexcludedGroups=integration
```
