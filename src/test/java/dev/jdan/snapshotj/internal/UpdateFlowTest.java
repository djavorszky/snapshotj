package dev.jdan.snapshotj.internal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class UpdateFlowTest {

    private static final String FIXTURE_RESOURCE = "/fixtures/UpdateFlowFixture.java";

    private static final String EXPECTED_REWRITTEN = ""
            + "class UpdateFlowFixture {\n"
            + "    void example() {\n"
            + "        Snap.snap(42).update().matches(\"\"\"\n"
            + "                new content\n"
            + "                \"\"\", Object::toString);\n"
            + "    }\n"
            + "}\n";

    @BeforeEach
    void resetBefore() {
        PendingEdits.resetForTesting();
    }

    @AfterEach
    void resetAfter() {
        PendingEdits.resetForTesting();
    }

    @Test
    void enqueueThenFlushRewritesFile(@TempDir Path tempDir) throws Exception {
        Path target = tempDir.resolve("UpdateFlowFixture.java");
        try (InputStream in = UpdateFlowTest.class.getResourceAsStream(FIXTURE_RESOURCE)) {
            assertNotNull(in, "fixture resource not found: " + FIXTURE_RESOURCE);
            Files.copy(in, target);
        }

        TextBlockFinder.Range range = TextBlockFinder.find(target, 3);

        PendingEdits.enqueue(target, range, "new content");
        PendingEdits.flushAll();

        assertEquals(EXPECTED_REWRITTEN, Files.readString(target));
    }
}
