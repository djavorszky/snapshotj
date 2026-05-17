package dev.jdan.snapshotj.internal;

import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.Patch;

import java.util.Arrays;
import java.util.List;

public final class DiffFormatter {

    private static final int CONTEXT_LINES = 3;

    private DiffFormatter() {}

    public static String format(String fileAndLine, String expected, String actual) {
        List<String> expectedLines = toLines(expected);
        List<String> actualLines = toLines(actual);
        Patch<String> patch = DiffUtils.diff(expectedLines, actualLines);
        List<String> diff = UnifiedDiffUtils.generateUnifiedDiff(
                "expected", "actual", expectedLines, patch, CONTEXT_LINES);
        StringBuilder sb = new StringBuilder();
        sb.append("snapshot mismatch at ").append(fileAndLine);
        for (String line : diff) {
            sb.append('\n').append(line);
        }
        return sb.toString();
    }

    private static List<String> toLines(String s) {
        if (s.isEmpty()) {
            return List.of();
        }
        return Arrays.asList(s.split("\n", -1));
    }
}
