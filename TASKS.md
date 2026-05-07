# snapshotj — implementation task list

Companion to `PLAN.md`. Phases are ordered so each builds on tested primitives below it. Within a phase, tasks can usually be parallelized. Don't skip layer-1 unit tests — the self-snapshot layer depends on them being green.

Each task: **Goal** → **Files** → **Done when**. Check the box when verified, not when written.

---

## Phase 0 — Build & project setup

- [x] **0.1 Gradle toolchain & deps**
  - Goal: Java 17 toolchain, runtime deps wired, `withSourcesJar()` / `withJavadocJar()` enabled.
  - Files: `build.gradle.kts`
  - Deps to add (compile): `jackson-databind`, `jackson-datatype-jsr310`, `jackson-datatype-jdk8`, `commons-csv`, `java-diff-utils`. Test: keep existing JUnit 5 BOM.
  - Done when: `./gradlew build` resolves all deps; `./gradlew dependencies` shows the expected tree; toolchain reports JDK 17.

- [x] **0.2 Package skeleton**
  - Goal: empty package files exist so imports compile while later phases fill them in.
  - Files: `src/main/java/dev/jdan/snapshotj/{Snap,Snapshot,SnapshotConfig}.java` (stubs), `src/main/java/dev/jdan/snapshotj/internal/` (empty package).
  - Done when: package compiles; `Snap.snap(x)` exists as a stub returning a `Snapshot<T>` whose methods throw `UnsupportedOperationException`.

---

## Phase 1 — Pure primitives (no source-rewriting yet)

These are testable with plain `assertEquals`. No StackWalker, no file IO.

- [x] **1.1 Normalizer**
  - Goal: trailing-whitespace + trailing-newline normalization rules per `PLAN.md` §"Decisions" #3.
  - Files: `internal/Normalizer.java`, `src/test/java/.../NormalizerTest.java`
  - Done when: input pairs differing only in trailing whitespace / final newline are equal after normalization; documented as canonical form.

- [x] **1.2 JsonRenderer**
  - Goal: deterministic JSON via configured `ObjectMapper` (alphabetical props, ISO-8601 dates, `JavaTimeModule` + `Jdk8Module`, 2-space indent, `\n` line separator).
  - Files: `internal/JsonRenderer.java`, `JsonRendererTest.java`
  - Done when: POJOs / records / `Map` / `LocalDateTime` / `Optional` produce stable strings; property order is alphabetical regardless of source order; line endings are `\n` on all platforms.

- [x] **1.3 CsvRenderer**
  - Goal: Commons CSV, header derived from first element via Jackson `BeanDescription`, alphabetized columns, `\n` separator, null cells empty.
  - Files: `internal/CsvRenderer.java`, `CsvRendererTest.java`
  - Done when: `List<Record>` / `List<Map>` / arrays render correctly; non-iterable input throws `IllegalArgumentException` with a clear message.

---

## Phase 2 — Public API (compare-only, no rewriting)

Wire renderers through the user-facing API. Mismatches throw `AssertionError`. `.update()` is a no-op flag for now.

- [x] **2.1 Snap & Snapshot**
  - Goal: `Snap.snap(value)` entry; `Snapshot<T>` with `update()`, `matches(expected, renderer)`, `matchesJson(expected)`, `matchesCsv(expected)`. `matchesJson`/`matchesCsv` delegate to `matches` with the bound renderer.
  - Files: `Snap.java`, `Snapshot.java`
  - Done when: mismatched expected throws `AssertionError`; matching expected returns normally; `matchesJson` and `matchesCsv` route through `matches`.

- [ ] **2.2 Renderers facade (optional, low cost)**
  - Goal: public `Renderers.json()` / `Renderers.csv()` returning `Function<T, String>` so users can pass them to `matches` directly.
  - Files: `Renderers.java` (public API package)
  - Done when: `snap(x).matches(expected, Renderers.json())` works identically to `snap(x).matchesJson(expected)`.

---

## Phase 3 — Source location

Read-only file resolution. No rewriting yet.

- [ ] **3.1 SourceLocator**
  - Goal: from a `StackWalker.StackFrame`, resolve the `.java` file via configurable source roots.
  - Files: `internal/SourceLocator.java`, `SourceLocatorTest.java`
  - Behavior: default roots `src/test/java`, `src/main/java`; override via `-Dsnapshotj.sourceRoots=path1:path2`; missing-file error lists candidates.
  - Done when: happy path resolves; override resolves; missing file produces a clear, actionable error message.

- [ ] **3.2 Caller frame discovery**
  - Goal: in `Snapshot`, walk the stack and capture the first frame outside `dev.jdan.snapshotj`. Capture class name, file name, line number.
  - Files: extend `Snapshot.java`; helper in `internal/SourceLocator.java`
  - Done when: a test that calls `matchesJson` from any package gets the correct file + line; nested helpers in user code don't accidentally point inside snapshotj.

---

## Phase 4 — Text block parsing

- [ ] **4.1 TextBlockFinder**
  - Goal: given a file and a starting line (the call line), locate the opening and closing `"""` of the inline expected literal.
  - Files: `internal/TextBlockFinder.java`, `TextBlockFinderTest.java` + fixtures under `src/test/resources/fixtures/`
  - Edge cases handled: same-line `"""..."""`, multi-line, indented, escaped `\"""` inside the block, comments containing `"""`, multiple text blocks on the same line.
  - Edge cases that must throw "could not locate inline literal": text block split across `+` concatenation; expected supplied as a method call / variable.
  - Done when: every fixture in the test resource directory produces the expected `(start, end)` range or the expected error.

---

## Phase 5 — Text block rewriting (in-memory only)

- [ ] **5.1 TextBlockWriter**
  - Goal: produce the new file bytes given (original file, opener/closer range, replacement text).
  - Files: `internal/TextBlockWriter.java`, `TextBlockWriterTest.java`
  - Behavior: compute indent from the closing `"""` line (or opener if inline); re-indent rendered output to that level; always emit closing `"""` on its own line with trailing newline; escape only `"""`.
  - Done when: round-trip property holds (rewrite → re-read → same runtime string); a malformed indent doesn't corrupt surrounding code.

---

## Phase 6 — Pending edits & atomic flush

- [ ] **6.1 PendingEdits queue**
  - Goal: collect edits during the test run; flush per file at JVM shutdown.
  - Files: `internal/PendingEdits.java`, `PendingEditsTest.java`
  - Behavior: per-path `ReentrantLock`; on flush, re-read file, apply queued edits in **reverse line order**, write atomically via `Files.move` from a temp file in the same directory.
  - Done when: multiple edits to the same file (any submission order) produce the same final bytes; concurrent producers serialize correctly; `kill -9` mid-flush leaves either the original or the new file intact (no partial writes).

- [ ] **6.2 Shutdown hook registration**
  - Goal: register the flush hook lazily on first edit, not at class load.
  - Files: `internal/PendingEdits.java`
  - Done when: tests that never trigger a rewrite don't install the hook; tests that do, install it exactly once.

---

## Phase 7 — `.update()` integration

- [ ] **7.1 SnapshotConfig**
  - Goal: read `SNAPSHOTJ_UPDATE` env var and `-Dsnapshotj.update=true` sysprop; expose `globalUpdate()` boolean.
  - Files: `SnapshotConfig.java`
  - Done when: either toggle activates global update mode; values are read once per JVM (cached).

- [ ] **7.2 Wire update into Snapshot**
  - Goal: when `update()` was called *or* `SnapshotConfig.globalUpdate()` is true, on mismatch enqueue a rewrite via `PendingEdits` and throw `AssertionError("snapshot updated at <file>:<line>; rerun without .update() to verify")`.
  - Files: `Snapshot.java`
  - Done when: passing snapshot is a no-op even with `.update()`; failing snapshot under update mode queues the edit and fails the test; failing snapshot without update mode queues nothing.

---

## Phase 8 — Diff message

- [ ] **8.1 DiffFormatter**
  - Goal: on mismatch (without update mode), build an `AssertionError` message containing a unified diff via `java-diff-utils`.
  - Files: `internal/DiffFormatter.java`, `DiffFormatterTest.java` (plain assertions, not self-snapshot)
  - Done when: message includes file:line, both sides labelled, unified-diff body; deterministic output across runs.

---

## Phase 9 — Self-snapshot tests (Layer 2 dogfooding)

These can only land once Phases 1–8 are green.

- [ ] **9.1 JsonSnapshotTest** — curated POJOs/records/maps/dates rendered via `matchesJson`.
- [ ] **9.2 CsvSnapshotTest** — `List<Record>` and `List<Map>` rendered via `matchesCsv`.
- [ ] **9.3 DiffMessageSnapshotTest** — synthesize a deliberate mismatch, capture the `AssertionError` message via `assertThrows`, snapshot the message string with `matches`. Locks down diff output format.
- [ ] **9.4 UpdateFlowTest** — copy a fixture `.java` file into a temp dir; run a programmatic `Snapshot.update()` against it (not via the test framework's classloader); assert resulting bytes match a known-good fixture. End-to-end check of the rewriting path.

---

## Phase 10 — Smoke & verification

- [ ] **10.1 Smoke test**
  - Files: `src/test/java/dev/jdan/snapshotj/smoke/`
  - Goal: a hand-written test using `snap(...).matchesJson(...)` against a real POJO. Manually break it, run with `SNAPSHOTJ_UPDATE=1`, confirm the source is rewritten and the test fails with the expected message; rerun without the env var, confirm green.

- [ ] **10.2 `publishToMavenLocal` dry-run**
  - Goal: confirms POM, sources jar, javadoc jar all build cleanly. POM metadata can still be placeholder.
  - Done when: `./gradlew publishToMavenLocal` succeeds and the artifacts appear under `~/.m2/repository/dev/jdan/snapshotj/`.

---

## Phase 11 — Publishing (deferred until user provides metadata)

Blocked on user input — see "Open items" in `PLAN.md`.

- [ ] **11.1 License file** (recommend Apache-2.0; user to confirm).
- [ ] **11.2 POM metadata** — name, description, url, scm, developers. Needs GitHub coordinates.
- [ ] **11.3 `maven-publish` + `signing` plugins** wired up.
- [ ] **11.4 `io.github.gradle-nexus.publish-plugin`** for OSSRH staging.
- [ ] **11.5 First release** — bump version to `0.1.0`, tag, run staging path, close & release.

---

## Notes for future sessions

- Always read `PLAN.md` before picking up a task — design decisions live there, not here.
- If a task's "Done when" can't be met, don't paper over it; surface the obstacle and revise the plan.
- Layer-1 tests use plain `assertEquals`. Only Phase 9+ uses snapshot-tests-of-snapshot.
- The `<snap:ignore>` placeholder feature is **out of scope for v1** — see PLAN.md decision #9. Don't invent it midway through implementation.
