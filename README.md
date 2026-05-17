# snapshotj

A JUnit-agnostic Java 17 library for **inline** snapshot testing. The expected snapshot lives as a Java text block at the call site; on mismatch you get a unified diff, and on opt-in the literal is rewritten in place.

> **Status:** `0.1.0` (semver, pre-1.0 — public API may shift on minor bumps). Not yet published to Maven Central — see [`TASKS.md`](TASKS.md) Phase 11.

## TL;DR

```java
import static dev.jdan.snapshotj.Snap.snap;

snap(user).matchesJson("""
        {
          "id": 1,
          "name": "Ada"
        }
        """);
```

- Inline expected value as a text block — no external snapshot files.
- Mismatch → `AssertionError` with a unified diff.
- Chain `.update()` (or set `SNAPSHOTJ_UPDATE=1`) to rewrite the literal in place; the test still fails so a stray `.update()` can't slip into CI.
- JUnit-agnostic. Java 17+. JSON / CSV / custom renderer.

## Why inline?

External snapshot files drift out of sight, lose context, and turn every diff into a hunt across directories. Inline snapshots keep the expected value next to the assertion that uses it, so a code review shows you both the change and its effect on the snapshot in one place.

## Features

- **One primitive, three flavors.** `matches(expected, renderer)` is the core; `matchesJson(expected)` and `matchesCsv(expected)` are sugar with built-in deterministic renderers.
- **JUnit-agnostic.** Throws plain `AssertionError` — works under JUnit 4, JUnit 5, TestNG, or anything else that recognizes the JDK assertion contract.
- **Update mode that fails loud.** `.update()` rewrites the literal *and* fails the test, so a stray `.update()` left in committed code always turns CI red.
- **Canonical comparison.** Trailing whitespace, trailing newlines, and line-ending differences are normalized on both sides before comparison — no flaky failures from invisible characters.
- **Deterministic renderers.** JSON uses alphabetical property order, ISO-8601 dates, and `\n` line endings. CSV headers are derived from the bean and alphabetized.
- **Safe in-place rewrites.** Edits are queued and flushed on JVM shutdown in reverse line order via atomic `Files.move`, so concurrent updates across a test run never corrupt source.

## Installation

Not yet published. Once the first release lands:

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

To try the current snapshot locally:

```bash
./gradlew publishToMavenLocal
```

## Quick start

```java
import static dev.jdan.snapshotj.Snap.snap;

class UserTest {

    @Test
    void serializesUser() {
        var user = new User(1, "Ada");

        snap(user).matchesJson("""
                {
                  "id": 1,
                  "name": "Ada"
                }
                """);
    }
}
```

First run with an empty literal? Chain `.update()` once, run the test, and the text block is rewritten in place:

```java
snap(user).update().matchesJson("""
        """);
```

The test fails with `snapshot updated at UserTest.java:14; rerun without .update() to verify`. Remove `.update()`, rerun, and the test passes.

### Custom renderer

```java
snap(report).matches("""
        Total: 42
        Errors: 0
        """, r -> "Total: %d\nErrors: %d\n".formatted(r.total(), r.errors()));
```

### CSV

```java
snap(users).matchesCsv("""
        id,name
        1,Ada
        2,Grace
        """);
```

## Configuration

| Setting | Default | Effect |
|---|---|---|
| `SNAPSHOTJ_UPDATE=1` env var | unset | Global update mode — equivalent to `.update()` on every comparison. Also accepts `true` / `yes`. |
| `-Dsnapshotj.update=true` | unset | Same as above, via system property. |
| `-Dsnapshotj.sourceRoots=path1:path2` | `src/test/java:src/main/java` | Override the source roots searched when resolving the calling test file. Path-separator delimited. |

## How it works

1. `snap(value)` captures the caller's stack frame.
2. `matches(...)` renders the value, normalizes both sides, and compares.
3. On mismatch without `.update()`: throw `AssertionError` with a unified diff.
4. On mismatch with `.update()`: locate the text block in the source file, queue a rewrite, throw `AssertionError("snapshot updated ...")`.
5. On JVM shutdown: each modified file is read, queued edits are applied in reverse line order (so earlier offsets don't shift), and the result is written via `Files.move` from a same-directory temp file.

See [`PLAN.md`](PLAN.md) for the full design rationale and the constraints that drove it.

## Public API

```
dev.jdan.snapshotj.Snap              // static snap(value)
dev.jdan.snapshotj.Snapshot          // .update(), .matches(), .matchesJson(), .matchesCsv()
dev.jdan.snapshotj.SnapshotConfig    // .globalUpdate(), .sourceRoots()
```

Everything under `dev.jdan.snapshotj.internal` is implementation detail and may change without notice.

## Agent skill

A Claude Code skill ships in [`skills/snapshotj/`](skills/snapshotj/SKILL.md) so AI coding agents can pick up the idioms (canonical comparison, fail-loud update mode, deterministic renderers) without you re-explaining them every session.

Install into your agent setup:

```bash
# user-wide
cp -r skills/snapshotj ~/.claude/skills/

# or project-local
mkdir -p .claude/skills && cp -r skills/snapshotj .claude/skills/
```

The skill auto-triggers when an agent edits Java tests that import `dev.jdan.snapshotj.Snap` or mentions `matchesJson` / `matchesCsv`.

## Building

```bash
./gradlew build    # compile + test
./gradlew test     # tests only
```

Requires JDK 17 (the Gradle toolchain resolves it automatically if missing).

## Contributing

See [`CONTRIBUTING.md`](CONTRIBUTING.md) for the workflow, invariants, and testing strategy. Read [`PLAN.md`](PLAN.md) before non-trivial changes — design decisions live there, not in `TASKS.md`.

## License

[MIT](LICENSE) © 2026 Daniel Javorszky
