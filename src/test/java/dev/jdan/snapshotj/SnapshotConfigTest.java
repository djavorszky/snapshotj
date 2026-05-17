package dev.jdan.snapshotj;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

// note: snapshotj.sourceRoots is read once per JVM and cached, so the override path
// cannot be exercised reliably here. SourceLocatorTest covers that path via the
// three-arg locate(...) overload, which is the surface that actually matters.
class SnapshotConfigTest {

    @Test
    void defaultsWhenSyspropUnset() {
        if (System.getProperty("snapshotj.sourceRoots") != null) {
            return;
        }
        assertEquals(
                List.of(Path.of("src/test/java"), Path.of("src/main/java")),
                SnapshotConfig.sourceRoots());
    }

    @Test
    void defaultGlobalUpdateIsFalse() {
        if (System.getenv("SNAPSHOTJ_UPDATE") != null
                || System.getProperty("snapshotj.update") != null) {
            return;
        }
        assertFalse(SnapshotConfig.globalUpdate());
    }
}
