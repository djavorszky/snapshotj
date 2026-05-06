# snapshotj — design plan

## Context

Greenfield Java library for inline snapshot testing. Repo (`/Users/djavorszky/dev/snapshotj`) is freshly initialized — Gradle + JUnit 5 wired up, no source yet. Group `dev.jdan`, target is publication to Maven Central.

Goal: a JUnit-agnostic library where you write the expected snapshot inline as a Java text block, get a readable diff on mismatch, and rewrite the literal in place when you opt in. Inspired by:

- TigerBeetle's *Snapshot Testing for the Masses* — `Snapshot { source_location, text }`, opt-in updates via `.update()` or `UPDATE_SNAPSHOTS=1`, files only mutated when a comparison would otherwise fail, `<snap:ignore>` for volatile fields.
- matklad's *Underusing Snapshot Testing* — multiple snapshots per test, used as a "repeatable REPL" during exploratory work; failures must show data inline so you can iterate fast.

The user-facing API is fixed:

```java
import static dev.jdan.snapshotj.Snap.snap;

// compare against an inline expected literal
snap(obj).matchesJson("""
        {"x": 1}
        """);

// rewrite the literal in place; test still fails so CI never silently passes
snap(obj).update().matchesJson("""
        outdated
        """);

snap(rows).matchesCsv("""
        name,age
        alice,30
        """);

snap(obj).matches("""
        custom rendering
        """, thing -> render(thing));
```

`matchesTable` is dropped from v1 per user decision — JSON / CSV / `matches` cover the use cases, and "table" is too fuzzy a contract to lock down before a real consumer surfaces.

## Decisions and pushback (read these before the rest)

1. **`.update()` always fails the test after rewriting.** This is the only safe design. If `.update()` were a silent pass when the rewrite happened, a `.update()` call left in committed code would cause CI to perpetually rewrite + green. Failing always means: `.update()` left in the tree → CI red → reviewer notices. Matches TigerBeetle's `SnapshotUpdated` error. The failure message is explicit: `snapshot was updated, rerun without .update() to verify`.
2. **Global update via env var `SNAPSHOTJ_UPDATE=1` (or `-Dsnapshotj.update=true`).** Same semantics: rewrites all mismatching snapshots in the run, and the run still fails so the developer must rerun without the flag.
3. **Comparison ignores trailing whitespace and trailing newlines on both sides.** Java text blocks have surprising trailing-newline rules depending on whether `"""` is on its own line; normalizing once removes a class of confusing failures. Documented as the canonical form.
4. **Source path discovery assumes Maven/Gradle layout** (`src/test/java`, `src/main/java`) and the class's package. Override via `-Dsnapshotj.sourceRoots=path1:path2`. If the file can't be located, the library throws a clear error pointing the user at the override.
5. **Java 17 baseline.** Text blocks need 15+; 17 is the lower LTS most projects standardize on, and `StackWalker` + `Files`/`Path` APIs are stable. Set `toolchain { languageVersion = JavaLanguageVersion.of(17) }`.
6. **No `matchesTable` in v1** (per user). Re-add later behind `Renderer<T>` SPI if a real use case appears.
7. **CSV input must be `Iterable<?>` or `Iterator<?>` or array.** A single scalar object as CSV is meaningless; throw `IllegalArgumentException` with a clear message rather than guess.
8. **`matches` does *not* use Jackson or Commons CSV.** It takes a `Function<T, String>`; output is compared verbatim (after trailing-whitespace normalization).
9. **`<snap:ignore>` placeholders deferred to v1.1.** Real but not blocking; document as a known gap. Without it, users handle volatile data by stripping in their `matches` lambda.
10. **Diff dependency: `io.github.java-diff-utils:java-diff-utils`.** Small, no transitive deps, produces unified diff. Rolling our own line diff is a distraction at this stage.

## Public API

Single static entry point `dev.jdan.snapshotj.Snap.snap(Object)` returning a `Snapshot` builder.

```java
package dev.jdan.snapshotj;

public final class Snap {
    public static <T> Snapshot<T> snap(T value) { ... }
    private Snap() {}
}

public final class Snapshot<T> {
    public Snapshot<T> update();                          // opt into rewriting
    public void matches(String expected, Function<T, String> renderer);  // primitive
    public void matchesJson(String expected);             // sugar: matches(expected, JsonRenderer::render)
    public void matchesCsv(String expected);              // sugar: matches(expected, CsvRenderer::render); T must be iterable/array
}
```

Failure model: every `matches*` either returns normally or throws `AssertionError` (so JUnit/TestNG/anything reports it as a failed test). When `.update()` is active and a rewrite happened, the `AssertionError` message reads `snapshot updated at <file>:<line>; rerun without .update() to verify`.

## Renderers and canonical forms

`matches(expected, renderer)` is the single primitive — it normalizes both sides, compares, and on mismatch either throws or queues a rewrite. `matchesJson` and `matchesCsv` are thin wrappers that bind the corresponding built-in renderer and delegate to `matches`. There is no separate code path for the format-specific methods.

### JSON (`matchesJson`)
Delegates to `matches(expected, JsonRenderer::render)`. The renderer:
- `ObjectMapper` configured once:
    - `MapperFeature.SORT_PROPERTIES_ALPHABETICALLY = true`
    - `SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS = true`
    - `SerializationFeature.WRITE_DATES_AS_TIMESTAMPS = false` (ISO-8601 strings beat epoch-millis drift)
    - `SerializationFeature.WRITE_DATES_WITH_ZONE_ID = true`
    - Pretty-printed with `DefaultPrettyPrinter` configured for 2-space indent, `\n` line separator (override Jackson's platform default), no trailing whitespace.
    - Register `JavaTimeModule`, `Jdk8Module` so `Optional`, `LocalDate`, etc. produce stable output.
- Output is the canonical form. The expected literal, after Java's text-block stripping, is compared against this.

### CSV (`matchesCsv`)
Delegates to `matches(expected, CsvRenderer::render)`. The renderer:
- Apache Commons CSV (`org.apache.commons:commons-csv`).
- Input: `Iterable<?>`, `Iterator<?>`, or array. Otherwise `IllegalArgumentException`.
- Header derived from the first element via Jackson's `BeanDescription` (so we reuse the JSON property model — `@JsonProperty` etc. honored, getters introspected). For records, use record components in declaration order *but then alphabetize* — alphabetical headers are deterministic regardless of source-level field order, matching the JSON renderer's stance.
- `CSVFormat.DEFAULT.builder().setHeader(...).setRecordSeparator("\n").build()` — explicit `\n` so output is platform-independent.
- Null cells render as empty.

### Custom (`matches`)
- The primitive. `Function<T, String>` — the user is responsible for determinism. Output compared verbatim (after trailing-whitespace normalization). All other `matches*` methods route through here.

## Source location, in-place rewrite

This is the most failure-prone part of the library. Get it right.

### Locating the call site
- `StackWalker.getInstance(Option.RETAIN_CLASS_REFERENCE).walk(...)` — find the first frame outside `dev.jdan.snapshotj` whose method invoked us. Capture `className`, `fileName` (e.g. `MyTest.java`), `lineNumber`.
- Resolve the file: walk `snapshotj.sourceRoots` (default: `src/test/java`, `src/main/java`) joined with the class's package path joined with `fileName`. First match wins. Throw a clear error with the candidates if none match.

### Locating the text block in the file
- The line returned by StackWalker is the line of the `matchesJson` / `matchesCsv` / `matches` call (typically the line containing the method name, but for chained calls Java reports the line of the call start — verify with a fixture test).
- Search forward from that line for the first `"""` token outside a comment/string. Java's text-block opener is `"""` followed by optional whitespace + newline. The closer is the next un-escaped `"""`.
- We can get away with ad-hoc scanning (per TigerBeetle's note) provided the source uses formatted text blocks. Edge cases we *do* handle:
    - escaped `\"""` inside the block
    - leading whitespace before opening `"""`
    - closing `"""` either on its own line or immediately after content
- Edge cases we *don't* handle (and document):
    - text block split across `+` concatenation
    - the expected literal computed by a method call rather than written inline (`matchesJson(EXPECTED)`) — we just throw "could not locate inline literal"

### Rewriting
- Compute the indent of the closing `"""` line (or the opening line if closer is inline).
- Re-indent the rendered output to that level.
- Replace the contents between opener and closer.
- Always emit closing `"""` on its own line with a trailing newline in the rendered text — canonical and matches our comparison normalization.
- Escape only `"""` (rare) — newlines, quotes, etc. live inside text blocks unescaped.

### Concurrency and ordering
- Many tests can hit the same file. Strategy:
    - Edits are queued (not applied) at the moment of mismatch.
    - A JVM shutdown hook flushes per file: take a per-path `ReentrantLock`, re-read the file, apply all queued edits in **reverse line order** (so earlier offsets aren't invalidated), atomic write via `Files.move` from a temp file in the same directory.
- Tests run in the same JVM see consistent behavior because nothing is rewritten until shutdown. Tests in separate JVMs writing the same file are an undefined case — document as "don't do that" (Gradle `forkEvery 1` users beware).

## Module/package layout

```
dev.jdan.snapshotj
├── Snap                      # static `snap(value)` entry
├── Snapshot                  # fluent builder: update(), matchesJson, matchesCsv, matches
├── SnapshotConfig            # singleton: env var / sysprop reads
└── internal
    ├── SourceLocator         # StackWalker + file resolution
    ├── TextBlockFinder       # locate "..." range in source
    ├── TextBlockWriter       # re-indent + escape rules
    ├── PendingEdits          # per-file queue + shutdown flusher
    ├── DiffFormatter         # uses java-diff-utils, builds the AssertionError message
    ├── JsonRenderer
    ├── CsvRenderer
    └── Normalizer            # trailing-whitespace/newline rules
```

`internal` is *not* a Java module-system module yet — just a package convention. If the library ever grows JPMS support, `exports dev.jdan.snapshotj;` only.

## Build and publishing

`build.gradle.kts` changes:

- `java { toolchain { languageVersion = JavaLanguageVersion.of(17) } }`
- `withSourcesJar()`, `withJavadocJar()`
- Dependencies (compile):
    - `com.fasterxml.jackson.core:jackson-databind`
    - `com.fasterxml.jackson.datatype:jackson-datatype-jsr310`
    - `com.fasterxml.jackson.datatype:jackson-datatype-jdk8`
    - `org.apache.commons:commons-csv`
    - `io.github.java-diff-utils:java-diff-utils`
- Dependencies (test):
    - existing JUnit 5 BOM
- `maven-publish` + `signing` plugins.
- `io.github.gradle-nexus.publish-plugin` for the OSSRH staging dance.
- POM metadata: name, description, url (TBD), license (TBD — pick MIT or Apache-2.0; recommend Apache-2.0 for libraries that may end up in enterprise classpaths), SCM, developers.

Versioning: bump `version` to `0.1.0` for the first release; keep `-SNAPSHOT` suffix on `main` between releases.

Files to create/modify:

- `build.gradle.kts` — extend with the above
- `src/main/java/dev/jdan/snapshotj/Snap.java`
- `src/main/java/dev/jdan/snapshotj/Snapshot.java`
- `src/main/java/dev/jdan/snapshotj/SnapshotConfig.java`
- `src/main/java/dev/jdan/snapshotj/internal/{SourceLocator,TextBlockFinder,TextBlockWriter,PendingEdits,DiffFormatter,JsonRenderer,CsvRenderer,Normalizer}.java`
- `src/test/java/dev/jdan/snapshotj/...` — see testing section
- `LICENSE`, `README.md` (deferred — not in v1 plan beyond a stub)

## Testing strategy

Two layers, both required.

### Layer 1: unit tests (no self-snapshotting)
These verify primitives that the snapshot machinery itself depends on. They must use plain `assertEquals` so the snapshot library isn't testing itself with itself for these cases.

- `JsonRendererTest` — feed POJOs/records/maps/`LocalDateTime`/`Optional`, assert canonical strings. Includes property-ordering, date formatting, line endings.
- `CsvRendererTest` — `List<Record>`, `List<Map>`, arrays, mixed-type-iterable rejection.
- `NormalizerTest` — trailing whitespace/newline rules.
- `TextBlockFinderTest` — fixture `.java` files in `src/test/resources/fixtures/` covering: same-line `"""..."""`, multi-line, indented, escaped `\"""`, comments containing `"""`, multiple text blocks on the same line, expected-literal-not-found case.
- `TextBlockWriterTest` — given an indent and a target string, produce the expected source bytes. Round-trip property: rewrite then re-read should yield the same runtime string.
- `SourceLocatorTest` — happy path with default roots, override with `snapshotj.sourceRoots`, missing-file error message.
- `PendingEditsTest` — multiple edits to the same file in arbitrary order produce the same final bytes regardless of insertion order; concurrent writers serialize.

### Layer 2: snapshot tests of the library itself
The library snapshot-tests its own renderer outputs and its own diff messages. This is the dogfooding layer matklad's article argues for.

- `JsonSnapshotTest` — `snap(somePojo).matchesJson("""...""")` against a curated set of inputs. These tests catch any *unintentional* canonicalization change because the inline literal would shift.
- `CsvSnapshotTest` — same shape.
- `DiffMessageSnapshotTest` — synthesize a deliberate mismatch, capture the `AssertionError` message via `assertThrows`, then snapshot the message string with `matches`. Verifies the diff renderer's output format is stable.
- `UpdateFlowTest` — copy a fixture `.java` source into a temp dir, run a `Snapshot.update()` flow against it programmatically (i.e., not via the test framework's own classloader), and assert the resulting file bytes match a known-good fixture. This is the only way to test the actual file-rewriting behavior end-to-end without the test rewriting itself.

The bootstrapping concern (a broken library breaks its own tests) is real but bounded: Layer 1 catches catastrophic regressions independently of the snapshot machinery, so Layer 2 failing alone narrows the bug to formatting/comparison logic.

## Verification

- `./gradlew test` — all unit + self-snapshot tests pass.
- Manual smoke: write a tiny consumer test in this repo (kept under `src/test/java/dev/jdan/snapshotj/smoke/`) that uses `snap(...).matchesJson(...)` against a real POJO, deliberately break it, run with `SNAPSHOTJ_UPDATE=1`, confirm the source file is rewritten and the test fails with the expected message; rerun without the env var, confirm the test now passes.
- `./gradlew publishToMavenLocal` — confirms the POM, sources jar, javadoc jar all build.
- (Pre-1.0, optional) `./gradlew publishToSonatypeStaging` to validate the OSSRH path before the first real release.

## Open items / deferred

- `<snap:ignore>` placeholder support — v1.1.
- File-based snapshots (`.snap` sibling files) for very large outputs — only if requested by a real user; v2.
- JPMS module declaration — only when a consumer asks for it.
- License choice — recommend Apache-2.0; user to confirm before first publish.
- POM URL/SCM/developers — needs the user's GitHub coordinates.
