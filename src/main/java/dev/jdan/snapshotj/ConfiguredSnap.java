package dev.jdan.snapshotj;

import dev.jdan.snapshotj.internal.FieldPath;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Reusable, immutable factory of pre-configured {@link Snapshot} handles.
 *
 * <p>Use when many assertions in a test class share the same transient-value
 * substitutions ({@link #replacingType(Class, String)} / {@link #replacingField(String, String)}).
 * Build the factory once, typically as a {@code static final} field, then call
 * {@link #snap(Object)} per assertion:
 *
 * <pre>{@code
 * class MyTest {
 *     private static final ConfiguredSnap SNAP = Snap.configure()
 *             .replacingType(UUID.class, "<uuid>")
 *             .replacingType(Instant.class, "<ts>")
 *             .replacingField("$.requestId", "<req-id>");
 *
 *     @Test
 *     void example() {
 *         SNAP.snap(someObject).matchesJson("""
 *                 { ... }
 *                 """);
 *     }
 * }
 * }</pre>
 *
 * <p>Instances are immutable: every fluent method returns a new {@code ConfiguredSnap},
 * leaving the receiver unchanged. Sharing a single instance across threads or tests is safe.
 *
 * <p>Per-call overrides on the returned {@link Snapshot} are allowed and win on key
 * conflict against the configured seed — see {@link Snapshot#replacingType(Class, String)}
 * and {@link Snapshot#replacingField(String, String)}.
 *
 * <p>{@link Snap#snap(Object)} is sugar for {@code Snap.configure().snap(value)}; both
 * paths share one construction route via an empty {@code ConfiguredSnap}.
 */
public final class ConfiguredSnap {

    private final Map<Class<?>, String> typeReplacements;
    private final Map<String, String> fieldReplacements;

    ConfiguredSnap(Map<Class<?>, String> typeReplacements,
                   Map<String, String> fieldReplacements) {
        this.typeReplacements = Map.copyOf(typeReplacements);
        this.fieldReplacements = Map.copyOf(fieldReplacements);
    }

    /**
     * Return a new {@code ConfiguredSnap} that adds (or overwrites) a type-replacement seed.
     *
     * <p>Semantics match {@link Snapshot#replacingType(Class, String)} exactly: exact-class
     * runtime match, no subclass walk, {@code null} cells never replaced. Re-registering the
     * same {@code type} overwrites the prior placeholder.
     *
     * @param type        the exact runtime class whose instances should be replaced; must not be {@code null}
     * @param placeholder the literal string to emit; must not be {@code null} or empty
     * @param <X>         the registered type
     * @return a new {@code ConfiguredSnap}; the receiver is unchanged
     * @throws NullPointerException     if {@code type} or {@code placeholder} is {@code null}
     * @throws IllegalArgumentException if {@code placeholder} is empty
     */
    public <X> ConfiguredSnap replacingType(Class<X> type, String placeholder) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(placeholder, "placeholder");
        if (placeholder.isEmpty()) {
            throw new IllegalArgumentException("placeholder must not be empty");
        }
        LinkedHashMap<Class<?>, String> next = new LinkedHashMap<>(typeReplacements);
        next.put(type, placeholder);
        return new ConfiguredSnap(next, fieldReplacements);
    }

    /**
     * Return a new {@code ConfiguredSnap} that adds (or overwrites) a field-path replacement
     * seed.
     *
     * <p>Semantics match {@link Snapshot#replacingField(String, String)} exactly, including
     * eager path validation: a malformed {@code path} throws
     * {@link IllegalArgumentException} here, not at render time.
     *
     * @param path        the field path to match; must not be {@code null} or empty
     * @param placeholder the literal string to emit at each match; must not be {@code null} or empty
     * @return a new {@code ConfiguredSnap}; the receiver is unchanged
     * @throws NullPointerException     if {@code path} or {@code placeholder} is {@code null}
     * @throws IllegalArgumentException if {@code path} or {@code placeholder} is empty, or {@code path} is malformed
     */
    public ConfiguredSnap replacingField(String path, String placeholder) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(placeholder, "placeholder");
        if (placeholder.isEmpty()) {
            throw new IllegalArgumentException("placeholder must not be empty");
        }
        FieldPath.parse(path);
        LinkedHashMap<String, String> next = new LinkedHashMap<>(fieldReplacements);
        next.put(path, placeholder);
        return new ConfiguredSnap(typeReplacements, next);
    }

    /**
     * Wrap {@code value} in a {@link Snapshot} pre-seeded with this factory's replacements.
     *
     * <p>Further per-call {@code .replacingType} / {@code .replacingField} on the returned
     * {@link Snapshot} layer on top of the seed and win on key conflict.
     *
     * @param value the actual value under test; may be {@code null} if the renderer accepts it
     * @param <T>   the value type
     * @return a fluent comparison handle seeded with this factory's replacements
     */
    public <T> Snapshot<T> snap(T value) {
        return new Snapshot<>(value, typeReplacements, fieldReplacements);
    }
}
