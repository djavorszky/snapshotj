package dev.jdan.snapshotj;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

public final class SnapshotConfig {

    private SnapshotConfig() {}

    public static boolean globalUpdate() {
        return GlobalUpdateHolder.VALUE;
    }

    private static final class GlobalUpdateHolder {
        private static final boolean VALUE = resolve();

        private static boolean resolve() {
            String env = System.getenv("SNAPSHOTJ_UPDATE");
            if (env != null && (env.equals("1")
                    || env.equalsIgnoreCase("true")
                    || env.equalsIgnoreCase("yes"))) {
                return true;
            }
            return Boolean.parseBoolean(System.getProperty("snapshotj.update"));
        }
    }

    public static List<Path> sourceRoots() {
        return SourceRootsHolder.VALUE;
    }

    private static final class SourceRootsHolder {
        private static final List<Path> VALUE = resolve();

        private static List<Path> resolve() {
            String raw = System.getProperty("snapshotj.sourceRoots");
            if (raw == null || raw.isBlank()) {
                return List.of(Path.of("src/test/java"), Path.of("src/main/java"));
            }
            String[] parts = raw.split(java.util.regex.Pattern.quote(File.pathSeparator));
            List<Path> roots = new java.util.ArrayList<>(parts.length);
            for (String p : parts) {
                if (!p.isBlank()) {
                    roots.add(Path.of(p));
                }
            }
            return List.copyOf(roots);
        }
    }
}
