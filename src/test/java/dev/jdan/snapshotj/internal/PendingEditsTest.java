package dev.jdan.snapshotj.internal;

import dev.jdan.snapshotj.internal.TextBlockFinder.Range;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Atomic-move durability (a {@code kill -9} mid-flush leaving either the original
 * or the new file intact) is provided by {@code Files.move(..., ATOMIC_MOVE)} and
 * therefore not exercised here; the platform contract is what we rely on.
 */
class PendingEditsTest {

    private static final String TQ = "\"\"\"";

    @BeforeEach
    void reset() {
        PendingEdits.resetForTesting();
    }

    @AfterEach
    void clear() {
        PendingEdits.resetForTesting();
    }

    @Test
    void flushesSingleEdit(@TempDir Path dir) throws IOException {
        String source = ""
                + "        snap(x).matchesJson(" + TQ + "\n"
                + "                old\n"
                + "                " + TQ + ");\n";
        Path file = dir.resolve("Sample.java");
        Files.writeString(file, source);

        Range range = new Range(1, 28, 3, 16);
        PendingEdits.enqueue(file, range, "new content");
        PendingEdits.flushAll();

        String expected = TextBlockWriter.rewrite(source, range, "new content");
        assertEquals(expected, Files.readString(file));
    }

    @Test
    void appliesEditsOrderIndependently(@TempDir Path dir) throws IOException {
        String source = ""
                + "snap(a).matchesJson(" + TQ + "\n"
                + "        old-a\n"
                + "        " + TQ + ");\n"
                + "snap(b).matchesJson(" + TQ + "\n"
                + "        old-b\n"
                + "        " + TQ + ");\n";

        Range rangeA = new Range(1, 20, 3, 8);
        Range rangeB = new Range(4, 20, 6, 8);

        Path forwardOrder = dir.resolve("Forward.java");
        Files.writeString(forwardOrder, source);
        PendingEdits.enqueue(forwardOrder, rangeA, "new-a");
        PendingEdits.enqueue(forwardOrder, rangeB, "new-b");
        PendingEdits.flushAll();
        String forwardBytes = Files.readString(forwardOrder);

        PendingEdits.resetForTesting();

        Path reverseOrder = dir.resolve("Reverse.java");
        Files.writeString(reverseOrder, source);
        PendingEdits.enqueue(reverseOrder, rangeB, "new-b");
        PendingEdits.enqueue(reverseOrder, rangeA, "new-a");
        PendingEdits.flushAll();
        String reverseBytes = Files.readString(reverseOrder);

        assertEquals(forwardBytes, reverseBytes);

        String expected = TextBlockWriter.rewrite(
                TextBlockWriter.rewrite(source, rangeB, "new-b"), rangeA, "new-a");
        assertEquals(expected, forwardBytes);
    }

    @Test
    void concurrentProducersSerialize(@TempDir Path dir) throws Exception {
        int n = 8;
        StringBuilder src = new StringBuilder();
        for (int i = 0; i < n; i++) {
            src.append("snap(v).matchesJson(").append(TQ).append("\n")
                    .append("        old-").append(i).append("\n")
                    .append("        ").append(TQ).append(");\n");
        }
        String source = src.toString();
        Path file = dir.resolve("Concurrent.java");
        Files.writeString(file, source);

        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(n);
        try {
            for (int i = 0; i < n; i++) {
                int idx = i;
                int openLine = 3 * idx + 1;
                int closeLine = 3 * idx + 3;
                Range range = new Range(openLine, 20, closeLine, 8);
                pool.submit(() -> {
                    try {
                        start.await();
                        PendingEdits.enqueue(file, range, "new-" + idx);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertTrue(done.await(5, TimeUnit.SECONDS), "producers timed out");
        } finally {
            pool.shutdownNow();
        }

        PendingEdits.flushAll();

        String written = Files.readString(file);
        for (int i = 0; i < n; i++) {
            assertTrue(written.contains("new-" + i), "missing edit new-" + i + " in:\n" + written);
            assertFalse(written.contains("old-" + i), "old-" + i + " still present in:\n" + written);
        }
        long tripleQuotes = countOccurrences(written, TQ);
        assertEquals(2L * n, tripleQuotes, "triple-quote count drifted:\n" + written);
    }

    @Test
    void noEditsMeansNoWrite(@TempDir Path dir) throws IOException {
        PendingEdits.flushAll();

        try (Stream<Path> entries = Files.list(dir)) {
            List<Path> leaked = entries.toList();
            assertEquals(List.of(), leaked, "flush leaked files into the directory");
        }
    }

    @Test
    void hookRegisteredLazilyOnce(@TempDir Path dir) throws IOException {
        assertFalse(PendingEdits.hookRegistered(), "hook should not be registered before any enqueue");

        Path file = dir.resolve("Hook.java");
        Files.writeString(file, "x\n");
        Range range = new Range(1, 0, 1, 0);

        PendingEdits.enqueue(file, range, "ignored");
        assertTrue(PendingEdits.hookRegistered(), "first enqueue must register the hook");

        PendingEdits.enqueue(file, range, "ignored");
        assertTrue(PendingEdits.hookRegistered(), "hook stays registered after subsequent enqueues");
    }

    private static long countOccurrences(String haystack, String needle) {
        long count = 0;
        int from = 0;
        while (true) {
            int hit = haystack.indexOf(needle, from);
            if (hit < 0) return count;
            count++;
            from = hit + needle.length();
        }
    }
}
