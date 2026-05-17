package dev.jdan.snapshotj.internal;

import dev.jdan.snapshotj.internal.TextBlockFinder.Range;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Per-file queue of inline text-block rewrites, flushed atomically at JVM
 * shutdown.
 *
 * <p>Edits enqueued during a test run are not applied immediately. A
 * shutdown hook, registered lazily on the first {@link #enqueue} call,
 * iterates each touched file under a per-path lock, re-reads it, applies its
 * pending rewrites in <strong>descending {@code openLine}</strong> order (so
 * earlier offsets aren't shifted), and writes the result via
 * {@link Files#move} from a sibling temp file. Either the original or the new
 * file survives — never a half-written one.
 */
public final class PendingEdits {

    private static final ConcurrentMap<Path, List<Edit>> QUEUE = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Path, ReentrantLock> LOCKS = new ConcurrentHashMap<>();
    private static final AtomicBoolean HOOK_REGISTERED = new AtomicBoolean(false);
    private static volatile Thread hookThread;

    private PendingEdits() {}

    record Edit(Range range, String rendered) {}

    /**
     * Queue a rewrite of the inline text block at {@code range} in {@code file}
     * to {@code rendered}. The first call also registers a JVM shutdown hook
     * that performs the actual write.
     */
    public static void enqueue(Path file, Range range, String rendered) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(rendered, "rendered");

        Path key = normalize(file);
        ReentrantLock lock = LOCKS.computeIfAbsent(key, k -> new ReentrantLock());
        lock.lock();
        try {
            QUEUE.computeIfAbsent(key, k -> new ArrayList<>())
                    .add(new Edit(range, rendered));
        } finally {
            lock.unlock();
        }
        registerShutdownHook();
    }

    static void flushAll() {
        for (Path path : List.copyOf(QUEUE.keySet())) {
            flushOne(path);
        }
    }

    private static void flushOne(Path path) {
        ReentrantLock lock = LOCKS.computeIfAbsent(path, k -> new ReentrantLock());
        lock.lock();
        try {
            List<Edit> edits = QUEUE.remove(path);
            if (edits == null || edits.isEmpty()) {
                return;
            }

            String source = Files.readString(path);
            edits.sort(Comparator.comparingInt((Edit e) -> e.range().openLine()).reversed());
            for (Edit edit : edits) {
                source = TextBlockWriter.rewrite(source, edit.range(), edit.rendered());
            }

            Path dir = path.getParent();
            String name = path.getFileName().toString();
            Path tmp = Files.createTempFile(dir, name, ".snapshotj.tmp");
            try {
                Files.writeString(tmp, source);
                try {
                    Files.move(tmp, path,
                            StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException ignored) {
                    // best-effort cleanup; surface the original write failure
                }
                throw e;
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to flush snapshot edits to " + path, e);
        } finally {
            lock.unlock();
        }
    }

    static void clear() {
        QUEUE.clear();
    }

    public static boolean hookRegistered() {
        return HOOK_REGISTERED.get();
    }

    private static void registerShutdownHook() {
        if (HOOK_REGISTERED.compareAndSet(false, true)) {
            Thread t = new Thread(PendingEdits::flushAll, "snapshotj-flush");
            hookThread = t;
            Runtime.getRuntime().addShutdownHook(t);
        }
    }

    public static void resetForTesting() {
        QUEUE.clear();
        LOCKS.clear();
        Thread t = hookThread;
        if (t != null) {
            try {
                Runtime.getRuntime().removeShutdownHook(t);
            } catch (IllegalStateException ignored) {
                // JVM is shutting down; nothing to do
            }
            hookThread = null;
        }
        HOOK_REGISTERED.set(false);
    }

    private static Path normalize(Path file) {
        return file.toAbsolutePath().normalize();
    }
}
