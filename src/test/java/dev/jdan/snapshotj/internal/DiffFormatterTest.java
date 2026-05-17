package dev.jdan.snapshotj.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DiffFormatterTest {

    @Test
    void singleLineChange() {
        String result = DiffFormatter.format("Foo.java:10", "expected text", "actual text");
        assertEquals(
                "snapshot mismatch at Foo.java:10\n"
                        + "--- expected\n"
                        + "+++ actual\n"
                        + "@@ -1,1 +1,1 @@\n"
                        + "-expected text\n"
                        + "+actual text",
                result);
    }

    @Test
    void multiLineWithContextLines() {
        String expected = "line1\nline2\nchanged\nline4\nline5";
        String actual   = "line1\nline2\nreplaced\nline4\nline5";
        String result = DiffFormatter.format("Bar.java:20", expected, actual);
        assertEquals(
                "snapshot mismatch at Bar.java:20\n"
                        + "--- expected\n"
                        + "+++ actual\n"
                        + "@@ -1,5 +1,5 @@\n"
                        + " line1\n"
                        + " line2\n"
                        + "-changed\n"
                        + "+replaced\n"
                        + " line4\n"
                        + " line5",
                result);
    }

    @Test
    void noChangesProducesHeaderOnly() {
        String result = DiffFormatter.format("Baz.java:5", "same", "same");
        assertEquals("snapshot mismatch at Baz.java:5", result);
    }

    @Test
    void bothEmptyProducesHeaderOnly() {
        String result = DiffFormatter.format("X.java:1", "", "");
        assertEquals("snapshot mismatch at X.java:1", result);
    }

    @Test
    void addedLines() {
        String result = DiffFormatter.format("X.java:1", "a", "a\nb");
        assertEquals(
                "snapshot mismatch at X.java:1\n"
                        + "--- expected\n"
                        + "+++ actual\n"
                        + "@@ -1,1 +1,2 @@\n"
                        + " a\n"
                        + "+b",
                result);
    }

    @Test
    void removedLines() {
        String result = DiffFormatter.format("X.java:1", "a\nb", "a");
        assertEquals(
                "snapshot mismatch at X.java:1\n"
                        + "--- expected\n"
                        + "+++ actual\n"
                        + "@@ -1,2 +1,1 @@\n"
                        + " a\n"
                        + "-b",
                result);
    }
}
