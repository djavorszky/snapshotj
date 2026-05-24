# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased] — v0.2.0

### Added

- JPMS module descriptor (`module dev.jdan.snapshotj`). Only the
  `dev.jdan.snapshotj` package is exported; `dev.jdan.snapshotj.internal` is
  no longer reachable from modular consumers.
- `Snapshot.replacingType(Class, String)`: neutralize transient values
  (UUIDs, `Instant`s, etc.) by substituting a placeholder string whenever
  the rendering pipeline encounters an instance of the registered type.
  Exact-class only; `null` cells are never replaced through the type
  channel. The placeholder is emitted in both verification and
  `.update()` mode, so the in-place rewrite is stable across runs.
- Internal 3-pass pipeline: `IrBuilder` (object → `JsonNode`),
  `JsonRenderer` / `CsvRenderer` (IR → string), `Snapshot.compare`
  (normalize + diff). Decouples mutation from rendering and keeps both
  renderers driven by the same IR.

### Changed

- **Breaking (unreleased):** `Snapshot.matches(String, Function<T, String>)`
  is now `Snapshot.matches(String, Function<JsonNode, String>)`. The
  custom renderer receives the same IR (with `replacingType` already
  applied) that `matchesJson` and `matchesCsv` consume, so replacements
  apply uniformly across all three entry points. To snapshot a raw
  string value, pass `JsonNode::asText` as the renderer.
- The module descriptor now reads `requires transitive
  com.fasterxml.jackson.databind` so modular consumers can name
  `com.fasterxml.jackson.databind.JsonNode` in their custom-renderer
  lambdas without adding their own `requires` clause.

## [0.1.0] — 2026-05-18

Initial release.
