# snapshotj documentation

In-repo reference for using `snapshotj` beyond the basics. The [README](../README.md) covers installation, the public API, and a five-line quick start; these pages cover the recurring questions that come up once you start snapshotting real code.

If you only need the API surface, the agent-facing [`SKILL.md`](../skills/snapshotj/SKILL.md) is the densest summary.

## Pages

- [Patterns](patterns.md) — handling time (`Clock`), Spring Boot caveats, and database-generated identifiers (sequences, `AUTO_INCREMENT`, UUIDs) in snapshots.

## Conventions used in these docs

- Code samples assume `import static dev.jdan.snapshotj.Snap.snap;`.
- "Renderer" always means `Function<T, String>` — the third argument to `matches(expected, renderer)`. The built-in `matchesJson` / `matchesCsv` bind a deterministic renderer for you.
- "Canonical form" means after `Normalizer` has stripped trailing whitespace per line and collapsed trailing newlines. Both sides of every comparison go through it.

## Design background

For the *why* behind the public API — fail-loud `.update()`, alphabetical JSON property order, deterministic CSV headers, shutdown-flushed edits — see [`PLAN.md`](../PLAN.md). Invariants are listed in [`CLAUDE.md`](../CLAUDE.md).
