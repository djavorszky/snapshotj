# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased] — v0.2.0

### Added

- JPMS module descriptor (`module dev.jdan.snapshotj`). Only the
  `dev.jdan.snapshotj` package is exported; `dev.jdan.snapshotj.internal` is
  no longer reachable from modular consumers.
- `Snapshot.replacing(Class, String)`: neutralize transient values (UUIDs,
  `Instant`s, etc.) by substituting a placeholder string whenever the
  built-in JSON or CSV renderer encounters an instance of the registered
  type. Exact-class only; `null` cells are never replaced. The placeholder
  is emitted in both verification and `.update()` mode, so the in-place
  rewrite is stable across runs. Calling `matches(expected, customRenderer)`
  after `.replacing(...)` throws `IllegalStateException` (a custom renderer
  is opaque to the substitution).

## [0.1.0] — 2026-05-18

Initial release.
