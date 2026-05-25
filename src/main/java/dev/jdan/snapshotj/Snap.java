package dev.jdan.snapshotj;

import java.util.Map;

/**
 * Entry point for inline snapshot testing.
 *
 * <p>Wrap a value with {@link #snap(Object)} to obtain a {@link Snapshot} handle, then
 * compare against an inline Java text block via {@link Snapshot#matches(String, java.util.function.Function)},
 * {@link Snapshot#matchesJson(String)}, or {@link Snapshot#matchesCsv(String)}.
 *
 * <p>Typical usage:
 * <pre>{@code
 * import static dev.jdan.snapshotj.Snap.snap;
 *
 * snap(user).matchesJson("""
 *         {
 *           "id": 1,
 *           "name": "Ada"
 *         }
 *         """);
 * }</pre>
 *
 * <p>For shared transient-value substitutions across many assertions, build a
 * {@link ConfiguredSnap} once via {@link #configure()} and reuse it:
 * <pre>{@code
 * private static final ConfiguredSnap SNAP = Snap.configure()
 *         .replacingType(UUID.class, "<uuid>")
 *         .replacingType(Instant.class, "<ts>");
 *
 * SNAP.snap(value).matchesJson("""...""");
 * }</pre>
 *
 * <p>{@code Snap.snap(v)} is sugar for {@code Snap.configure().snap(v)} — both share one
 * construction route via a hidden empty {@code ConfiguredSnap}.
 *
 * <p>On mismatch the test fails with a unified diff. To rewrite the expected literal
 * in place, chain {@link Snapshot#update()} or set the {@code SNAPSHOTJ_UPDATE=1}
 * environment variable (equivalently, {@code -Dsnapshotj.update=true}).
 */
public final class Snap {

    private static final ConfiguredSnap DEFAULT = new ConfiguredSnap(Map.of(), Map.of());

    private Snap() {}

    /**
     * Wrap {@code value} in a {@link Snapshot} handle for inline comparison.
     *
     * <p>Equivalent to {@code Snap.configure().snap(value)} with no replacements seeded.
     *
     * @param value the actual value under test; may be {@code null} if the renderer accepts it
     * @param <T>   the value type
     * @return a fluent comparison handle
     */
    public static <T> Snapshot<T> snap(T value) {
        return DEFAULT.snap(value);
    }

    /**
     * Return an empty {@link ConfiguredSnap} for building a reusable, pre-configured snapshot
     * factory.
     *
     * <p>Chain {@link ConfiguredSnap#replacingType(Class, String)} and
     * {@link ConfiguredSnap#replacingField(String, String)} to seed substitutions, then call
     * {@link ConfiguredSnap#snap(Object)} per assertion. Each fluent method returns a new
     * {@code ConfiguredSnap}; the receiver is unchanged, so storing the result as a
     * {@code static final} field is safe across threads and tests.
     *
     * @return an empty {@link ConfiguredSnap}
     */
    public static ConfiguredSnap configure() {
        return DEFAULT;
    }
}
