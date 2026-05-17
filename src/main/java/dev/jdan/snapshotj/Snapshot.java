package dev.jdan.snapshotj;

import dev.jdan.snapshotj.internal.CsvRenderer;
import dev.jdan.snapshotj.internal.JsonRenderer;
import dev.jdan.snapshotj.internal.Normalizer;
import dev.jdan.snapshotj.internal.PendingEdits;
import dev.jdan.snapshotj.internal.SourceLocator;
import dev.jdan.snapshotj.internal.SourceLocator.CallerFrame;
import dev.jdan.snapshotj.internal.TextBlockFinder;

import java.nio.file.Path;
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
 * <p>{@link #update()} opts into in-place rewriting: on mismatch the inline literal
 * is queued for rewriting and the test fails with a "snapshot updated" message so
 * CI never silently passes a stale {@code .update()} call.
 */
public final class Snapshot<T> {

    private final T value;
    private boolean updateRequested;

    Snapshot(T value) {
        this.value = value;
    }

    /** Opt into rewriting the inline expected literal on mismatch. */
    public Snapshot<T> update() {
        this.updateRequested = true;
        return this;
    }

    public void matches(String expected, Function<T, String> renderer) {
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(renderer, "renderer");

        CallerFrame caller = SourceLocator.callerFrame();

        String actual = renderer.apply(value);
        if (actual == null) {
            throw new IllegalStateException("renderer returned null");
        }

        String normalizedActual = Normalizer.normalize(actual);
        String normalizedExpected = Normalizer.normalize(expected);
        if (normalizedActual.equals(normalizedExpected)) {
            return;
        }

        if (updateRequested || SnapshotConfig.globalUpdate()) {
            Path file = SourceLocator.locate(caller.className(), caller.fileName());
            TextBlockFinder.Range range = TextBlockFinder.find(file, caller.lineNumber());
            PendingEdits.enqueue(file, range, normalizedActual);
            throw new AssertionError(
                    "snapshot updated at " + caller.fileName() + ":" + caller.lineNumber()
                            + "; rerun without .update() to verify");
        }

        throw new AssertionError(
                "snapshot mismatch at " + caller.fileName() + ":" + caller.lineNumber()
                        + "\nexpected:\n" + normalizedExpected
                        + "\nactual:\n" + normalizedActual);
    }

    public void matchesJson(String expected) {
        matches(expected, JsonRenderer::render);
    }

    public void matchesCsv(String expected) {
        matches(expected, CsvRenderer::render);
    }
}
