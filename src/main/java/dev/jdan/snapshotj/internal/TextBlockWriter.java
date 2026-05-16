package dev.jdan.snapshotj.internal;

import dev.jdan.snapshotj.internal.TextBlockFinder.Range;

import java.util.Objects;

/**
 * Pure rewrite of an inline Java text block.
 *
 * <p>Given the original source of a {@code .java} file, the {@link Range} of an
 * inline {@code """..."""} located by {@link TextBlockFinder}, and a freshly
 * rendered replacement string, produces the new source bytes with the text
 * block's contents rewritten in canonical form: closer on its own line,
 * content re-indented to match the closer, {@code """} runs escaped.
 *
 * <p>This class performs no file IO — Phase 6's {@code PendingEdits} is
 * responsible for atomic writes.
 */
public final class TextBlockWriter {

    private TextBlockWriter() {}

    /**
     * Returns {@code source} with the inline text-block contents identified by
     * {@code range} replaced by a canonical rendering of {@code rendered}.
     *
     * <p>The opener line's prefix (everything up to and including the opening
     * {@code """}) and the closer line's suffix (the closing {@code """} plus
     * any trailing code such as {@code ");"}) are preserved verbatim. Anything
     * between is replaced.
     */
    public static String rewrite(String source, Range range, String rendered) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(rendered, "rendered");

        int[] lineStarts = computeLineStarts(source);
        int totalLines = lineStarts.length;

        if (range.openLine() < 1 || range.openLine() > totalLines) {
            throw new IllegalArgumentException(
                    "openLine " + range.openLine() + " out of bounds (1.." + totalLines + ")");
        }
        if (range.closeLine() < 1 || range.closeLine() > totalLines) {
            throw new IllegalArgumentException(
                    "closeLine " + range.closeLine() + " out of bounds (1.." + totalLines + ")");
        }

        int openerStart = lineStarts[range.openLine() - 1] + range.openCol();
        int openerEnd = openerStart + 3;
        int closerStart = lineStarts[range.closeLine() - 1] + range.closeCol();

        String closeLineText = lineAt(source, lineStarts, range.closeLine());
        String openLineText = lineAt(source, lineStarts, range.openLine());
        String indent = resolveIndent(openLineText, closeLineText, range.closeCol());

        String canonical = Normalizer.normalize(rendered);
        String escaped = escapeTripleQuotes(canonical);

        StringBuilder body = new StringBuilder();
        body.append('\n');
        if (!escaped.isEmpty()) {
            int from = 0;
            int len = escaped.length();
            while (from <= len) {
                int nl = escaped.indexOf('\n', from);
                int end = (nl == -1) ? len : nl;
                body.append(indent);
                body.append(escaped, from, end);
                body.append('\n');
                if (nl == -1) {
                    break;
                }
                from = nl + 1;
            }
        }
        body.append(indent);

        StringBuilder out = new StringBuilder(source.length() + body.length());
        out.append(source, 0, openerEnd);
        out.append(body);
        out.append(source, closerStart, source.length());
        return out.toString();
    }

    private static String resolveIndent(String openLineText, String closeLineText, int closeCol) {
        boolean prefixAllWhitespace = true;
        for (int i = 0; i < closeCol && i < closeLineText.length(); i++) {
            char c = closeLineText.charAt(i);
            if (c != ' ' && c != '\t') {
                prefixAllWhitespace = false;
                break;
            }
        }
        if (prefixAllWhitespace) {
            return closeLineText.substring(0, Math.min(closeCol, closeLineText.length()));
        }
        String closerLeading = leadingWhitespace(closeLineText);
        if (!closerLeading.isEmpty()) {
            return closerLeading;
        }
        return leadingWhitespace(openLineText);
    }

    private static String leadingWhitespace(String s) {
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c != ' ' && c != '\t') {
                break;
            }
            i++;
        }
        return s.substring(0, i);
    }

    private static String escapeTripleQuotes(String s) {
        int n = s.length();
        StringBuilder out = new StringBuilder(n);
        int run = 0;
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            if (c == '"') {
                if (run == 2) {
                    out.append('\\');
                    out.append('"');
                    run = 1;
                } else {
                    out.append('"');
                    run++;
                }
            } else {
                out.append(c);
                run = 0;
            }
        }
        return out.toString();
    }

    private static int[] computeLineStarts(String source) {
        int n = source.length();
        int count = 1;
        for (int i = 0; i < n; i++) {
            if (source.charAt(i) == '\n') {
                count++;
            }
        }
        int[] starts = new int[count];
        starts[0] = 0;
        int idx = 1;
        for (int i = 0; i < n; i++) {
            if (source.charAt(i) == '\n') {
                starts[idx++] = i + 1;
            }
        }
        return starts;
    }

    private static String lineAt(String source, int[] lineStarts, int line1) {
        int start = lineStarts[line1 - 1];
        int end;
        if (line1 < lineStarts.length) {
            end = lineStarts[line1] - 1;
            if (end > start && source.charAt(end - 1) == '\r') {
                end--;
            }
        } else {
            end = source.length();
        }
        return source.substring(start, end);
    }
}
