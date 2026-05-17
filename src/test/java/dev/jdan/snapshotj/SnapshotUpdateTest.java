package dev.jdan.snapshotj;

import dev.jdan.snapshotj.internal.PendingEdits;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static dev.jdan.snapshotj.Snap.snap;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapshotUpdateTest {

    record Point(int x, int y) {}

    @AfterEach
    void cleanup() {
        PendingEdits.resetForTesting();
    }

    @Test
    void updateReturnsSelf() {
        Snapshot<Point> s = snap(new Point(1, 2));
        assertSame(s, s.update());
    }

    @Test
    void passingSnapshotWithUpdateDoesNotThrow() {
        assertDoesNotThrow(() -> snap(new Point(1, 2)).update().matchesJson("""
                {
                  "x" : 1,
                  "y" : 2
                }
                """));
        assertFalse(PendingEdits.hookRegistered());
    }

    @Test
    void mismatchWithUpdateThrowsUpdatedMessage() {
        AssertionError err = assertThrows(AssertionError.class,
                () -> snap(new Point(1, 2)).update().matchesJson("""
                        {
                          "x" : 9,
                          "y" : 9
                        }
                        """));
        assertTrue(err.getMessage().contains("snapshot updated"), err.getMessage());
        assertTrue(err.getMessage().contains("rerun without .update()"), err.getMessage());
    }

    @Test
    void mismatchWithUpdateQueuesEdit() {
        assertThrows(AssertionError.class,
                () -> snap(new Point(1, 2)).update().matchesJson("""
                        {
                          "x" : 9,
                          "y" : 9
                        }
                        """));
        assertTrue(PendingEdits.hookRegistered());
    }

    @Test
    void mismatchWithoutUpdateThrowsMismatchMessage() {
        AssertionError err = assertThrows(AssertionError.class,
                () -> snap(new Point(1, 2)).matchesJson("""
                        {
                          "x" : 9,
                          "y" : 9
                        }
                        """));
        assertTrue(err.getMessage().contains("snapshot mismatch"), err.getMessage());
        assertFalse(PendingEdits.hookRegistered());
    }
}
