package dev.jdan.snapshotj;

import dev.jdan.snapshotj.internal.CsvRenderer;
import dev.jdan.snapshotj.internal.JsonRenderer;
import dev.jdan.snapshotj.internal.Normalizer;

import java.util.Objects;
import java.util.function.Function;

/**
 * Fluent comparison handle for an inline snapshot.
 *
 * <p>{@link #matches(String, Function)} is the single comparison primitive;
 * {@link #matchesJson(String)} and {@link #matchesCsv(String)} bind the built-in
 * renderers and delegate. Both sides are folded to canonical form via
 * {@link Normalizer} before comparison, so trailing whitespace, trailing newlines,
 * and line-ending differences do not cause spurious mismatches.
 *
 * <p>{@link #update()} currently records intent only; in-place rewriting is wired
 * up in a later phase.
 */
public final class Snapshot<T> {

    private final T value;
    private boolean updateRequested;

    Snapshot(T value) {
        this.value = value;
    }

    /** Opt into rewriting the inline expected literal on mismatch. No-op until source rewriting lands. */
    public Snapshot<T> update() {
        this.updateRequested = true;
        return this;
    }

    public void matches(String expected, Function<T, String> renderer) {
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(renderer, "renderer");

        String actual = renderer.apply(value);
        if (actual == null) {
            throw new IllegalStateException("renderer returned null");
        }

        String normalizedActual = Normalizer.normalize(actual);
        String normalizedExpected = Normalizer.normalize(expected);
        if (normalizedActual.equals(normalizedExpected)) {
            return;
        }

        throw new AssertionError(
                "snapshot mismatch\nexpected:\n" + normalizedExpected
                        + "\nactual:\n" + normalizedActual);
    }

    public void matchesJson(String expected) {
        matches(expected, JsonRenderer::render);
    }

    public void matchesCsv(String expected) {
        matches(expected, CsvRenderer::render);
    }
}
