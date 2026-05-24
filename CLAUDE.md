# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

`snapshotj` = JUnit-agnostic Java 17 library for **inline** snapshot testing. Expected snapshot written as Java text block at call site; on mismatch user gets unified diff, on opt-in (`.update()` or `SNAPSHOTJ_UPDATE=1`) literal rewritten in place. Group `dev.jdan`, targets Maven Central. Current version `0.1.0` (pre-1.0, semver — public API may shift on minor bumps).

## Commands

- `./gradlew build` — full build incl. tests
- `./gradlew test` — unit tests only
- `./gradlew test --tests dev.jdan.snapshotj.internal.NormalizerTest` — single test class
- `./gradlew test --tests '*NormalizerTest.normalizesTrailingNewlines'` — single test method
- `./gradlew dependencies` — verify dep tree
- `./gradlew publishToMavenLocal` — validates POM/sources/javadoc jars locally (publish workflow in `.github/workflows/`)

## Architecture

User-facing surface tiny — one static entry point + fluent builder. Internals follow a 3-pass pipeline: **build IR → render IR → compare**.

```
dev.jdan.snapshotj
├── Snap                # static snap(value) entry
├── Snapshot            # update(), replacingType, matches(expected, Function<JsonNode,String>), matchesJson, matchesCsv
├── SnapshotConfig      # env var / sysprop reads (SNAPSHOTJ_UPDATE, snapshotj.update, snapshotj.sourceRoots)
└── internal/           # everything else; not exported by intent
    ├── IrBuilder          # Pass 1: object -> JsonNode IR; owns canonical Jackson config; applies type replacements at build time
    ├── JsonRenderer       # Pass 2 (JSON): JsonNode -> canonical string (2-space indent, \n)
    ├── CsvRenderer        # Pass 2 (CSV): JsonNode (ArrayNode<ObjectNode>) -> Commons CSV; headers alphabetized
    ├── Normalizer         # canonical form: strip trailing whitespace + final newlines
    ├── SourceLocator      # StackWalker + file resolution against snapshotj.sourceRoots
    ├── TextBlockFinder    # locate """..."""  range in the source file
    ├── TextBlockWriter    # re-indent + escape rules for the rewrite
    ├── PendingEdits       # per-file queue, JVM shutdown flush, atomic Files.move
    └── DiffFormatter      # java-diff-utils → unified diff for AssertionError messages
```

`matches(expected, Function<JsonNode,String> renderer)` = **single primitive** for the custom-renderer path. The renderer receives the IR after type replacements are applied, so all three entry points (`matches`, `matchesJson`, `matchesCsv`) honor substitutions uniformly. `matchesJson` and `matchesCsv` are sugar that wire the built-in renderer over the same `compare()` helper — no parallel diff/update logic.

`com.fasterxml.jackson.databind.JsonNode` is re-exported via `requires transitive` in `module-info.java` so modular consumers can name it in custom-renderer lambdas.

### Critical invariants

1. **`.update()` always fails test after rewriting** — never silently green. `.update()` left in committed code must turn CI red. Failure message: `snapshot updated at <file>:<line>; rerun without .update() to verify`.
2. **Comparison normalizes trailing whitespace and trailing newlines on both sides** — Java text blocks have surprising trailing-newline behavior. Normalize once, document as canonical.
3. **Edits queued, not applied at mismatch.** JVM shutdown hook (registered lazily on first edit) flushes per file: reads, applies queued edits in **reverse line order** (so earlier offsets don't shift), writes via `Files.move` from same-directory temp file.
4. **Source path discovery uses `src/test/java` and `src/main/java` defaults**, overridable via `-Dsnapshotj.sourceRoots=path1:path2`. Throw clear error listing candidates if file can't be located.
5. **`<snap:ignore>` placeholders explicitly out of scope.** Don't parse magic tokens inside the expected literal. Transients are neutralized at the IR layer via `.replacingType(Class, String)` (exact-class only; `null` cells unaffected through this channel). Custom `matches(expected, Function<JsonNode, String>)` receives the same already-mutated IR, so substitutions apply uniformly across all three entry points.
6. **`matchesTable` dropped from v1.** Don't add it.

## Testing strategy (two layers — both required)

- **Layer 1 (plain `assertEquals`)**: tests for `Normalizer`, `JsonRenderer`, `CsvRenderer`, `TextBlockFinder`, `TextBlockWriter`, `SourceLocator`, `PendingEdits`, `DiffFormatter`. Must NOT use snapshot machinery — they're foundation it depends on.
- **Layer 2 (self-snapshotting)**: dogfoods library against itself for renderer outputs and diff messages. Catches unintentional canonicalization drift. See `DiffMessageSnapshotTest`, `CsvSnapshotTest`, `JsonSnapshotTest`, smoke tests.

`TextBlockFinder` fixtures live under `src/test/resources/fixtures/`.

## Workflow notes

- When a task's "Done when" can't be met, surface the obstacle and revise the approach; don't paper over it.
- `internal/` is hidden via the JPMS module descriptor at `src/main/java/module-info.java` — only `dev.jdan.snapshotj` is exported. Keep that boundary intact when adding new internals.