package dev.jdan.snapshotj.internal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Locates the inline expected Java text block ({@code """...""" }) belonging to a
 * {@code matches} / {@code matchesJson} / {@code matchesCsv} call.
 *
 * <p>The finder is a small character-level state machine that tracks Java's
 * lexical contexts (line comment, block comment, regular string, char literal,
 * text block) starting at the call line reported by {@link StackWalker} and
 * scanning forward up to {@link #MAX_FORWARD_SCAN_LINES} lines.
 *
 * <p>It is not a parser. The two error cases the task spec requires us to
 * surface are detected heuristically:
 * <ul>
 *   <li>concatenation ({@code "abc" + """..."""}) — flagged when a {@code +} sits
 *       immediately before the opener (after intervening whitespace);</li>
 *   <li>non-inline expected ({@code matchesJson(EXPECTED)}) — flagged when no
 *       {@code """} is found within the forward scan window.</li>
 * </ul>
 */
public final class TextBlockFinder {

    /**
     * Position of the inline expected text block within the source file.
     * Lines are 1-based (matching {@link StackWalker}); columns are 0-based
     * character indices into their respective lines, pointing at the first
     * {@code "} of the {@code """} marker.
     */
    public record Range(int openLine, int openCol, int closeLine, int closeCol) {}

    static final int MAX_FORWARD_SCAN_LINES = 50;

    private static final int NORMAL = 0;
    private static final int LINE_COMMENT = 1;
    private static final int BLOCK_COMMENT = 2;
    private static final int STRING = 3;
    private static final int CHAR = 4;
    private static final int TEXT_BLOCK = 5;

    private TextBlockFinder() {}

    public static Range find(Path file, int callLine) {
        Objects.requireNonNull(file, "file");
        try {
            return find(Files.readAllLines(file), callLine);
        } catch (IOException e) {
            throw new IllegalStateException("could not read source file " + file, e);
        }
    }

    public static Range find(List<String> lines, int callLine) {
        Objects.requireNonNull(lines, "lines");
        if (callLine < 1) {
            throw new IllegalArgumentException("callLine must be >= 1, got " + callLine);
        }
        if (callLine > lines.size()) {
            throw new IllegalArgumentException(
                    "callLine " + callLine + " is past end of file (" + lines.size() + " lines)");
        }

        int n = lines.size();
        int endLine = Math.min(n, callLine + MAX_FORWARD_SCAN_LINES);

        int state = NORMAL;
        int openLine = -1;
        int openCol = -1;

        for (int li = callLine; li <= endLine; li++) {
            String line = lines.get(li - 1);
            int len = line.length();

            if (state == LINE_COMMENT) {
                state = NORMAL;
            }

            int i = 0;
            while (i < len) {
                char c = line.charAt(i);

                switch (state) {
                    case NORMAL -> {
                        if (isTriple(line, i, len)) {
                            if (precededByPlus(line, i)) {
                                throw new IllegalStateException(
                                        "could not locate inline literal: text block at "
                                                + li + ":" + i
                                                + " is concatenated with `+`; the expected literal must be a single inline text block");
                            }
                            openLine = li;
                            openCol = i;
                            state = TEXT_BLOCK;
                            i += 3;
                        } else if (c == '"') {
                            state = STRING;
                            i++;
                        } else if (c == '\'') {
                            state = CHAR;
                            i++;
                        } else if (c == '/' && i + 1 < len && line.charAt(i + 1) == '/') {
                            state = LINE_COMMENT;
                            i = len;
                        } else if (c == '/' && i + 1 < len && line.charAt(i + 1) == '*') {
                            state = BLOCK_COMMENT;
                            i += 2;
                        } else {
                            i++;
                        }
                    }
                    case BLOCK_COMMENT -> {
                        if (c == '*' && i + 1 < len && line.charAt(i + 1) == '/') {
                            state = NORMAL;
                            i += 2;
                        } else {
                            i++;
                        }
                    }
                    case STRING -> {
                        if (c == '\\' && i + 1 < len) {
                            i += 2;
                        } else if (c == '"') {
                            state = NORMAL;
                            i++;
                        } else {
                            i++;
                        }
                    }
                    case CHAR -> {
                        if (c == '\\' && i + 1 < len) {
                            i += 2;
                        } else if (c == '\'') {
                            state = NORMAL;
                            i++;
                        } else {
                            i++;
                        }
                    }
                    case TEXT_BLOCK -> {
                        if (c == '\\' && i + 1 < len) {
                            i += 2;
                        } else if (isTriple(line, i, len)) {
                            return new Range(openLine, openCol, li, i);
                        } else {
                            i++;
                        }
                    }
                    default -> throw new AssertionError("unreachable state " + state);
                }
            }
        }

        if (openLine != -1) {
            throw new IllegalStateException(
                    "could not locate inline literal: text block opener at "
                            + openLine + ":" + openCol + " has no closing \"\"\" within "
                            + MAX_FORWARD_SCAN_LINES + " lines");
        }
        throw new IllegalStateException(
                "could not locate inline literal at line " + callLine
                        + ": no \"\"\" found within " + MAX_FORWARD_SCAN_LINES
                        + " lines (expected must be a Java text block, not a method call or variable)");
    }

    private static boolean isTriple(String line, int i, int len) {
        return i + 2 < len
                && line.charAt(i) == '"'
                && line.charAt(i + 1) == '"'
                && line.charAt(i + 2) == '"';
    }

    private static boolean precededByPlus(String line, int i) {
        int k = i - 1;
        while (k >= 0) {
            char c = line.charAt(k);
            if (c == ' ' || c == '\t') {
                k--;
            } else {
                return c == '+';
            }
        }
        return false;
    }
}
