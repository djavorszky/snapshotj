package dev.jdan.snapshotj.internal;

import java.util.Objects;

/**
 * Canonical-form normalization for snapshot comparison.
 *
 * <p>Two strings are considered equal by snapshotj if their normalized forms are
 * byte-identical. Normalization:
 * <ol>
 *   <li>line endings are folded to {@code \n} (CRLF and lone CR both become LF);</li>
 *   <li>trailing spaces and tabs are stripped from every line;</li>
 *   <li>trailing newlines are stripped from the end of the string.</li>
 * </ol>
 *
 * <p>Interior blank lines and interior whitespace are preserved.
 */
public final class Normalizer {

    private Normalizer() {}

    public static String normalize(String s) {
        Objects.requireNonNull(s, "s");

        StringBuilder out = new StringBuilder(s.length());
        int lineStart = 0;
        int i = 0;
        int n = s.length();

        while (i < n) {
            char c = s.charAt(i);
            if (c == '\n' || c == '\r') {
                appendStripped(out, s, lineStart, i);
                out.append('\n');
                if (c == '\r' && i + 1 < n && s.charAt(i + 1) == '\n') {
                    i += 2;
                } else {
                    i++;
                }
                lineStart = i;
            } else {
                i++;
            }
        }
        appendStripped(out, s, lineStart, n);

        int end = out.length();
        while (end > 0 && out.charAt(end - 1) == '\n') {
            end--;
        }
        out.setLength(end);

        return out.toString();
    }

    private static void appendStripped(StringBuilder out, String s, int start, int end) {
        int e = end;
        while (e > start) {
            char c = s.charAt(e - 1);
            if (c == ' ' || c == '\t') {
                e--;
            } else {
                break;
            }
        }
        out.append(s, start, e);
    }
}
