package dev.jdan.snapshotj;

import com.fasterxml.jackson.databind.JsonNode;
import dev.jdan.snapshotj.internal.CsvRenderer;
import dev.jdan.snapshotj.internal.DiffFormatter;
import dev.jdan.snapshotj.internal.FieldPath;
import dev.jdan.snapshotj.internal.IrBuilder;
import dev.jdan.snapshotj.internal.IrTransform;
import dev.jdan.snapshotj.internal.JsonRenderer;
import dev.jdan.snapshotj.internal.Normalizer;
import dev.jdan.snapshotj.internal.PendingEdits;
import dev.jdan.snapshotj.internal.SourceLocator;
import dev.jdan.snapshotj.internal.SourceLocator.CallerFrame;
import dev.jdan.snapshotj.internal.TextBlockFinder;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
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
    private final LinkedHashMap<Class<?>, String> typeReplacements = new LinkedHashMap<>();
    private final LinkedHashMap<String, String> fieldReplacements = new LinkedHashMap<>();
    private boolean updateRequested;

    Snapshot(T value) {
        this(value, Map.of(), Map.of());
    }

    Snapshot(T value,
             Map<Class<?>, String> typeSeed,
             Map<String, String> fieldSeed) {
        this.value = value;
        this.typeReplacements.putAll(typeSeed);
        this.fieldReplacements.putAll(fieldSeed);
    }

    /**
     * Opt into rewriting the inline expected literal on mismatch.
     *
     * <p>When set, a mismatching {@code matches*} call queues an in-place rewrite of
     * the inline text block at the caller site and then fails the test with a
     * {@code "snapshot updated at <file>:<line>; rerun without .update() to verify"}
     * message. The test never silently passes while {@code .update()} is present, so a
     * forgotten call in committed code always turns CI red.
     *
     * @return this handle for chaining
     */
    public Snapshot<T> update() {
        this.updateRequested = true;
        return this;
    }

    /**
     * Register a placeholder string to be substituted for any value whose runtime class
     * exactly matches {@code type} during built-in rendering ({@link #matchesJson(String)} or
     * {@link #matchesCsv(String)}).
     *
     * <p>Use this to neutralize transient values such as {@link java.util.UUID UUIDs},
     * {@link java.time.Instant Instants}, or other non-deterministic fields that would
     * otherwise cause spurious mismatches. The placeholder is emitted verbatim in both
     * verification and {@link #update()} mode, so the in-place rewrite is stable across runs.
     *
     * <p>Matching is exact-class only — no subclass walk. {@code null} cells are never
     * replaced. Calling this method multiple times with the same {@code type} overwrites
     * the previously registered placeholder.
     *
     * <p>{@link #matches(String, Function)}, {@link #matchesJson(String)}, and
     * {@link #matchesCsv(String)} all honor registrations — the substitution is applied to
     * the IR before any renderer (built-in or custom) sees it.
     *
     * @param type        the exact runtime class whose instances should be replaced; must not be {@code null}
     * @param placeholder the literal string to emit in place of matching instances; must not be {@code null} or empty
     * @param <X>         the registered type
     * @return this handle for chaining
     * @throws NullPointerException     if {@code type} or {@code placeholder} is {@code null}
     * @throws IllegalArgumentException if {@code placeholder} is empty
     */
    public <X> Snapshot<T> replacingType(Class<X> type, String placeholder) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(placeholder, "placeholder");
        if (placeholder.isEmpty()) {
            throw new IllegalArgumentException("placeholder must not be empty");
        }
        typeReplacements.put(type, placeholder);
        return this;
    }

    /**
     * Register a placeholder string to be substituted at every field-path match during
     * built-in JSON rendering ({@link #matchesJson(String)}).
     *
     * <p>The {@code path} uses a JSONPath subset:
     * <ul>
     *   <li>{@code $.foo} — root object's {@code foo} field.</li>
     *   <li>{@code $.foo.bar} — nested anchored path.</li>
     *   <li>{@code ..foo} — recursive descent: every {@code foo} field anywhere in the tree.</li>
     *   <li>{@code $.foo..bar} — descent within a subtree.</li>
     * </ul>
     *
     * <p>A path that matches zero fields throws {@link AssertionError} at render time — silent
     * no-op would let tests rot when fields get renamed. {@code null} fields named by an
     * anchored or recursive path <em>are</em> replaced (the user explicitly named the field).
     *
     * <p>Registering the same path twice overwrites the previously registered placeholder.
     * Field syntax errors are detected eagerly here — invalid paths throw
     * {@link IllegalArgumentException} at registration time.
     *
     * @param path        the field path to match; must not be {@code null} or empty
     * @param placeholder the literal string to emit at each match; must not be {@code null} or empty
     * @return this handle for chaining
     * @throws NullPointerException     if {@code path} or {@code placeholder} is {@code null}
     * @throws IllegalArgumentException if {@code path} or {@code placeholder} is empty, or {@code path} is malformed
     */
    public Snapshot<T> replacingField(String path, String placeholder) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(placeholder, "placeholder");
        if (placeholder.isEmpty()) {
            throw new IllegalArgumentException("placeholder must not be empty");
        }
        FieldPath.parse(path);
        fieldReplacements.put(path, placeholder);
        return this;
    }

    /**
     * Compare the wrapped value against an inline expected text block using a custom renderer
     * that operates on the {@link JsonNode} IR.
     *
     * <p>This is the single comparison primitive; {@link #matchesJson(String)} and
     * {@link #matchesCsv(String)} are sugar that bind the built-in renderers and delegate
     * here. The wrapped value is converted to a {@code JsonNode} via the same canonical
     * pipeline used by {@code matchesJson} (alphabetical properties, ISO-8601 dates, etc.),
     * any {@link #replacingType(Class, String)} and {@link #replacingField(String, String)}
     * substitutions are applied, and the resulting tree is handed to {@code renderer}.
     *
     * <p>Both {@code expected} and the renderer output are folded to canonical form
     * (trailing whitespace stripped, trailing newlines collapsed) before equality check.
     *
     * <p>Behavior on mismatch:
     * <ul>
     *   <li>If {@link #update()} was called on this handle or {@link SnapshotConfig#globalUpdate()}
     *       is true, the inline literal is queued for rewriting and the test fails with a
     *       {@code "snapshot updated at <file>:<line>"} message.</li>
     *   <li>Otherwise the test fails with a unified diff against the canonical expected
     *       and actual strings.</li>
     * </ul>
     *
     * @param expected the inline expected snapshot (typically a Java text block); must not be {@code null}
     * @param renderer function that converts the IR to its snapshot string; must not be {@code null}
     *                 and must not return {@code null}. To snapshot a raw string value, pass
     *                 {@link JsonNode#asText()}.
     * @throws NullPointerException  if {@code expected} or {@code renderer} is {@code null}
     * @throws IllegalStateException if {@code renderer} returns {@code null}
     * @throws AssertionError        on mismatch, on a {@code replacingField} path with zero matches,
     *                               or after queuing an update-mode rewrite
     */
    public void matches(String expected, Function<JsonNode, String> renderer) {
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(renderer, "renderer");

        JsonNode tree = IrBuilder.build(value, typeReplacements);
        IrTransform.applyFieldPaths(tree, fieldReplacements);

        String actual = renderer.apply(tree);
        if (actual == null) {
            throw new IllegalStateException("renderer returned null");
        }
        compare(expected, actual);
    }

    /**
     * Compare the wrapped value against an inline expected JSON text block.
     *
     * <p>Renders the value via the 4-pass pipeline: build {@link JsonNode} IR (honoring
     * {@link #replacingType(Class, String)} registrations), apply
     * {@link #replacingField(String, String)} mutations, serialize the tree, then compare
     * via the same normalize-then-equal pipeline used by {@link #matches(String, Function)}.
     *
     * @param expected the inline expected JSON snapshot; must not be {@code null}
     * @throws NullPointerException if {@code expected} is {@code null}
     * @throws AssertionError       on mismatch, on a {@code replacingField} path with zero matches,
     *                              or after queuing an update-mode rewrite
     */
    public void matchesJson(String expected) {
        Objects.requireNonNull(expected, "expected");
        JsonNode tree = IrBuilder.build(value, typeReplacements);
        IrTransform.applyFieldPaths(tree, fieldReplacements);
        compare(expected, JsonRenderer.renderTree(tree));
    }

    /**
     * Compare the wrapped value against an inline expected CSV text block.
     *
     * <p>Renders the value via the built-in CSV renderer. Honors any
     * {@link #replacingType(Class, String)} registrations, and any
     * {@link #replacingField(String, String)} registrations whose path collapses to a single
     * column name (CSV is flat — nested paths throw {@link IllegalArgumentException}).
     *
     * @param expected the inline expected CSV snapshot; must not be {@code null}
     * @throws NullPointerException     if {@code expected} is {@code null}
     * @throws IllegalArgumentException if a registered field path is not flat (e.g. {@code $.foo.bar})
     * @throws AssertionError           on mismatch, on a {@code replacingField} column missing
     *                                  from the headers, or after queuing an update-mode rewrite
     */
    public void matchesCsv(String expected) {
        Objects.requireNonNull(expected, "expected");
        compare(expected, CsvRenderer.render(value, typeReplacements, fieldReplacements));
    }

    private void compare(String expected, String actual) {
        CallerFrame caller = SourceLocator.callerFrame();

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
                DiffFormatter.format(
                        caller.fileName() + ":" + caller.lineNumber(),
                        normalizedExpected,
                        normalizedActual));
    }
}
