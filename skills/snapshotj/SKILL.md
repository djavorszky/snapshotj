---
name: snapshotj
description: >
  Guide for writing tests with the snapshotj inline snapshot library (Java 17, JUnit-agnostic, group dev.jdan).
  Use when the user is writing or modifying Java tests in a project that depends on snapshotj, mentions
  "snapshot test" in a Java context, imports `dev.jdan.snapshotj.Snap`, or asks how to assert structured
  output (JSON, CSV, rendered text) inline. Covers the public API surface, the canonical comparison rules,
  update mode, configuration, and idioms to follow / mistakes to avoid.
---

# snapshotj

Inline snapshot testing library for Java 17. Expected snapshot lives as a Java text block at the call site. Mismatch → unified diff. Opt-in (`.update()` or `SNAPSHOTJ_UPDATE=1`) → literal rewritten in place.

JUnit-agnostic — throws plain `AssertionError`. Works under JUnit 4, JUnit 5, TestNG, anything else.

## When to use this skill

Trigger when:

- File under `src/test/java/**` and project depends on `dev.jdan:snapshotj`.
- User imports `dev.jdan.snapshotj.Snap` or asks to "snapshot test" Java code.
- User wants to assert against rendered JSON, CSV, or any deterministic stringification inline.
- User mentions `matchesJson`, `matchesCsv`, or `snap(...)`.

Skip when the user is using a different snapshot library (`assertj-snapshot`, `approvaltests`, `jest`-style external file snapshots, etc.).

## Public API surface

Only three types are public — everything under `dev.jdan.snapshotj.internal.*` is implementation detail.

```
dev.jdan.snapshotj.Snap              // static snap(value)
dev.jdan.snapshotj.Snapshot          // .update(), .matches(), .matchesJson(), .matchesCsv()
dev.jdan.snapshotj.SnapshotConfig    // .globalUpdate(), .sourceRoots()
```

### Entry point

```java
import static dev.jdan.snapshotj.Snap.snap;
```

`snap(value)` wraps the value in a `Snapshot<T>` handle. No assertion yet — chain a `matches*` call.

### Comparison primitives

```java
// Single primitive — supply your own renderer:
snap(value).matches(expected, renderer);  // Function<T, String> renderer

// Sugar — built-in deterministic renderers:
snap(value).matchesJson(expected);
snap(value).matchesCsv(expected);         // value is typically a Collection<Bean>
```

`matchesJson` / `matchesCsv` delegate to `matches(expected, builtInRenderer)` — there is no parallel code path. Adding a new sugar method means binding a renderer and delegating, never duplicating logic.

### Update mode

```java
snap(value).update().matchesJson("""
        """);
```

`.update()` opts into in-place rewriting. On mismatch the inline literal is queued for rewrite **and the test fails** with:

```
snapshot updated at FileName.java:42; rerun without .update() to verify
```

This is intentional. A `.update()` left in committed code must turn CI red. The test never silently passes while `.update()` is present.

Global update mode: set `SNAPSHOTJ_UPDATE=1` (env) or `-Dsnapshotj.update=true` (system property). Same fail-loud behavior — every mismatch rewrites and fails.

## Idioms

### Starting a new snapshot

Write the call with an empty text block and `.update()`:

```java
snap(user).update().matchesJson("""
        """);
```

Run. Test fails with "snapshot updated" — the literal is now populated. Remove `.update()`. Re-run. Green.

### Custom renderer

Use `matches` for anything not JSON/CSV — log lines, rendered templates, deterministic `toString`, etc.

```java
snap(report).matches("""
        Total: 42
        Errors: 0
        """, r -> "Total: %d\nErrors: %d\n".formatted(r.total(), r.errors()));
```

The renderer must be deterministic. Non-determinism (timestamps, random IDs, iteration order over `HashMap`) produces flaky snapshots. Either pin the inputs, or render to a deterministic form first.

### Asserting on a collection as CSV

```java
record User(int id, String name) {}

snap(List.of(new User(1, "Ada"), new User(2, "Grace"))).matchesCsv("""
        id,name
        1,Ada
        2,Grace
        """);
```

CSV header is derived from the bean and **alphabetized** — `id,name`, not `name,id`. JSON property order is also alphabetical. Don't fight this; it's the canonical form.

## Canonical comparison

Both sides are normalized before equality check:

- Trailing whitespace stripped from each line.
- Trailing newlines collapsed.
- Line endings normalized.

Leading whitespace is **significant** (it's the indentation inside the text block). Java text blocks already strip common indentation, so write the expected literal with natural indentation relative to the `"""` opening delimiter — the language handles the rest.

## Configuration

| Setting | Default | Effect |
|---|---|---|
| `SNAPSHOTJ_UPDATE` env var | unset | `1` / `true` / `yes` enables global update mode. |
| `-Dsnapshotj.update=true` | unset | Same as above, via system property. |
| `-Dsnapshotj.sourceRoots=path1:path2` | `src/test/java:src/main/java` | Override roots searched when resolving the calling test file. Path-separator delimited (`:` Unix, `;` Windows). |

Config is read once on first access and cached for the JVM lifetime. Changing the environment mid-run has no effect.

If snapshotj can't find the source file (e.g., non-standard layout, Bazel, custom build), set `-Dsnapshotj.sourceRoots` to the directories containing the test sources. Error message lists candidates that were tried.

## Gotchas

- **Don't commit `.update()`.** It always fails the test. CI will be red until removed. The fail-loud behavior is deliberate — see `PLAN.md` invariant 1.
- **Renderer must not return `null`** — throws `IllegalStateException`.
- **`expected` must not be `null`** — throws `NullPointerException`. Use `""` for an empty snapshot.
- **Don't use `matchesJson` for non-deterministic values** without pinning. Jackson's deterministic config covers property order and date format, not your input's `Map` iteration order or `Instant.now()`.
- **`<snap:ignore>` placeholders don't exist** in 0.1.x. To handle dynamic values, normalize them in the renderer (e.g., replace timestamps with `<TS>` before snapshotting).
- **`matchesTable` doesn't exist** either. Use `matchesCsv` for tabular data.
- **Edits flush on JVM shutdown.** If the JVM is killed (`kill -9`, OOM), queued rewrites are lost. A clean test exit (pass or `AssertionError`) flushes correctly.

## Failure messages

Mismatch without update:

```
FileName.java:42
--- expected
+++ actual
@@
-old line
+new line
```

Mismatch with update:

```
snapshot updated at FileName.java:42; rerun without .update() to verify
```

Source file not located:

```
could not locate <ClassName> source file; searched: [src/test/java, src/main/java]
```

## Dependency

```kotlin
// build.gradle.kts
dependencies {
    testImplementation("dev.jdan:snapshotj:0.1.0")
}
```

```xml
<!-- pom.xml -->
<dependency>
    <groupId>dev.jdan</groupId>
    <artifactId>snapshotj</artifactId>
    <version>0.1.0</version>
    <scope>test</scope>
</dependency>
```

Pre-1.0 — public API may shift on minor version bumps per semver.
