# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

`snapshotj` is a JUnit-agnostic Java 17 library for **inline** snapshot testing. The expected snapshot is written as a Java text block at the call site; on mismatch the user gets a unified diff, and on opt-in (`.update()` or `SNAPSHOTJ_UPDATE=1`) the literal is rewritten in place. Group `dev.jdan`, targeting Maven Central.

The repo is greenfield — Phase 0 is complete (Gradle wired up, package skeleton with stubs that throw `UnsupportedOperationException`). Real work lives in `PLAN.md` and `TASKS.md`. **Always read `PLAN.md` first** — design decisions and pushback live there, not in TASKS.md.

## Commands

- `./gradlew build` — full build incl. tests
- `./gradlew test` — unit tests only
- `./gradlew test --tests dev.jdan.snapshotj.internal.NormalizerTest` — single test class
- `./gradlew test --tests '*NormalizerTest.normalizesTrailingNewlines'` — single test method
- `./gradlew dependencies` — verify dep tree
- `./gradlew publishToMavenLocal` — once publish plugins land (Phase 11), validates POM/sources/javadoc jars

## Architecture

The user-facing surface is intentionally tiny — one static entry point and a fluent builder:

```
dev.jdan.snapshotj
├── Snap                # static snap(value) entry
├── Snapshot            # update(), matches(expected, renderer), matchesJson, matchesCsv
├── SnapshotConfig      # env var / sysprop reads (SNAPSHOTJ_UPDATE, snapshotj.update, snapshotj.sourceRoots)
└── internal/           # everything else; not exported by intent
    ├── Normalizer          # canonical form: strip trailing whitespace + final newlines
    ├── JsonRenderer        # deterministic Jackson config (alphabetical props, ISO-8601, \n line endings)
    ├── CsvRenderer         # Commons CSV; header from Jackson BeanDescription, alphabetized
    ├── SourceLocator       # StackWalker + file resolution against snapshotj.sourceRoots
    ├── TextBlockFinder     # locate """..."""  range in the source file
    ├── TextBlockWriter     # re-indent + escape rules for the rewrite
    ├── PendingEdits        # per-file queue, JVM shutdown flush, atomic Files.move
    └── DiffFormatter       # java-diff-utils → unified diff for AssertionError messages
```

`matches(expected, Function<T,String> renderer)` is the **single primitive**. `matchesJson` / `matchesCsv` are sugar that bind the built-in renderer and delegate. There is no parallel code path for them.

### Critical invariants (don't violate without revisiting PLAN.md)

1. **`.update()` always fails the test after rewriting** — never silently green. A `.update()` left in committed code must turn CI red. Failure message: `snapshot updated at <file>:<line>; rerun without .update() to verify`.
2. **Comparison normalizes trailing whitespace and trailing newlines on both sides** — Java text blocks have surprising trailing-newline behavior. Normalize once, document as canonical.
3. **Edits are queued, not applied at mismatch.** A JVM shutdown hook (registered lazily on first edit) flushes per file: reads, applies queued edits in **reverse line order** (so earlier offsets don't shift), writes via `Files.move` from a same-directory temp file.
4. **Source path discovery uses `src/test/java` and `src/main/java` defaults**, overridable via `-Dsnapshotj.sourceRoots=path1:path2`. Throw a clear error listing candidates if the file can't be located.
5. **`<snap:ignore>` placeholders are explicitly out of scope for v1.** Don't invent them.
6. **`matchesTable` is dropped from v1.** Don't add it.

## Testing strategy (two layers — both required)

- **Layer 1 (plain `assertEquals`)**: tests for `Normalizer`, `JsonRenderer`, `CsvRenderer`, `TextBlockFinder`, `TextBlockWriter`, `SourceLocator`, `PendingEdits`, `DiffFormatter`. These must NOT use the snapshot machinery — they're the foundation it depends on.
- **Layer 2 (self-snapshotting, Phase 9 only)**: dogfoods the library against itself for renderer outputs and diff messages. Catches unintentional canonicalization drift.

`TextBlockFinder` fixtures live under `src/test/resources/fixtures/` and cover edge cases enumerated in TASKS.md §4.1.

## Workflow notes

- Tasks are tracked in `TASKS.md` with phase ordering — phases build on tested primitives below them. Layer-2 tests can only land after Phases 1–8 are green.
- When a task's "Done when" can't be met, surface the obstacle and revise the plan; don't paper over it.
- `internal/` is a package convention, not a JPMS module. If JPMS is ever added, only `dev.jdan.snapshotj` is exported.
