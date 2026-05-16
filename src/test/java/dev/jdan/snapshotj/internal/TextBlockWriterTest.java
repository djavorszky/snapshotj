package dev.jdan.snapshotj.internal;

import dev.jdan.snapshotj.internal.TextBlockFinder.Range;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextBlockWriterTest {

    private static final String TQ = "\"\"\"";

    @Test
    void simpleMultiLineBlock() {
        String source = ""
                + "        snap(x).matchesJson(" + TQ + "\n"
                + "                old\n"
                + "                " + TQ + ");\n";
        Range range = findRange(source, 1);

        String expected = ""
                + "        snap(x).matchesJson(" + TQ + "\n"
                + "                line one\n"
                + "                line two\n"
                + "                " + TQ + ");\n";

        assertEquals(expected, TextBlockWriter.rewrite(source, range, "line one\nline two\n"));
    }

    @Test
    void closerInlineCanonicalizesToOwnLine() {
        String source = ""
                + "        snap(x).matchesJson(" + TQ + "\n"
                + "                {\"a\":1}" + TQ + ");\n";
        Range range = findRange(source, 1);

        String expected = ""
                + "        snap(x).matchesJson(" + TQ + "\n"
                + "                {\"b\":2}\n"
                + "                " + TQ + ");\n";

        assertEquals(expected, TextBlockWriter.rewrite(source, range, "{\"b\":2}"));
    }

    @Test
    void contentInheritsCloserIndent() {
        String source = ""
                + "    void deep() {\n"
                + "        if (true) {\n"
                + "            snap(x).matches(" + TQ + "\n"
                + "                    placeholder\n"
                + "                    " + TQ + ", r);\n"
                + "        }\n"
                + "    }\n";
        Range range = findRange(source, 3);

        String rewritten = TextBlockWriter.rewrite(source, range, "alpha\nbeta");

        String expected = ""
                + "    void deep() {\n"
                + "        if (true) {\n"
                + "            snap(x).matches(" + TQ + "\n"
                + "                    alpha\n"
                + "                    beta\n"
                + "                    " + TQ + ", r);\n"
                + "        }\n"
                + "    }\n";
        assertEquals(expected, rewritten);
    }

    @Test
    void singleLineReplacement() {
        String source = ""
                + "        snap(x).matchesJson(" + TQ + "\n"
                + "                old content\n"
                + "                " + TQ + ");\n";
        Range range = findRange(source, 1);

        String expected = ""
                + "        snap(x).matchesJson(" + TQ + "\n"
                + "                hi\n"
                + "                " + TQ + ");\n";

        assertEquals(expected, TextBlockWriter.rewrite(source, range, "hi"));
    }

    @Test
    void multiLineReplacementInPreviouslyInlineBlock() {
        String source = ""
                + "        snap(x).matchesJson(" + TQ + "\n"
                + "                old" + TQ + ");\n";
        Range range = findRange(source, 1);

        String expected = ""
                + "        snap(x).matchesJson(" + TQ + "\n"
                + "                a\n"
                + "                b\n"
                + "                c\n"
                + "                " + TQ + ");\n";

        assertEquals(expected, TextBlockWriter.rewrite(source, range, "a\nb\nc"));
    }

    @Test
    void emptyRenderedProducesEmptyBody() {
        String source = ""
                + "        snap(x).matchesJson(" + TQ + "\n"
                + "                some old text\n"
                + "                " + TQ + ");\n";
        Range range = findRange(source, 1);

        String expected = ""
                + "        snap(x).matchesJson(" + TQ + "\n"
                + "                " + TQ + ");\n";

        assertEquals(expected, TextBlockWriter.rewrite(source, range, ""));
    }

    @Test
    void escapesTripleQuoteRunOfThree() {
        String source = ""
                + "        snap(x).matches(" + TQ + "\n"
                + "                old\n"
                + "                " + TQ + ", r);\n";
        Range range = findRange(source, 1);

        String rewritten = TextBlockWriter.rewrite(source, range, "before " + TQ + " after");

        String expected = ""
                + "        snap(x).matches(" + TQ + "\n"
                + "                before \"\"\\\" after\n"
                + "                " + TQ + ", r);\n";
        assertEquals(expected, rewritten);
    }

    @Test
    void escapesTripleQuoteRunOfFour() {
        String source = ""
                + "        snap(x).matches(" + TQ + "\n"
                + "                old\n"
                + "                " + TQ + ", r);\n";
        Range range = findRange(source, 1);

        String rewritten = TextBlockWriter.rewrite(source, range, "x\"\"\"\"y");

        String expected = ""
                + "        snap(x).matches(" + TQ + "\n"
                + "                x\"\"\\\"\"y\n"
                + "                " + TQ + ", r);\n";
        assertEquals(expected, rewritten);
    }

    @Test
    void escapesTripleQuoteRunOfFive() {
        String source = ""
                + "        snap(x).matches(" + TQ + "\n"
                + "                old\n"
                + "                " + TQ + ", r);\n";
        Range range = findRange(source, 1);

        String rewritten = TextBlockWriter.rewrite(source, range, "x\"\"\"\"\"y");

        String expected = ""
                + "        snap(x).matches(" + TQ + "\n"
                + "                x\"\"\\\"\"\\\"y\n"
                + "                " + TQ + ", r);\n";
        assertEquals(expected, rewritten);
    }

    @Test
    void normalizesPlatformLineEndings() {
        String source = ""
                + "        snap(x).matchesJson(" + TQ + "\n"
                + "                old\n"
                + "                " + TQ + ");\n";
        Range range = findRange(source, 1);

        String expected = ""
                + "        snap(x).matchesJson(" + TQ + "\n"
                + "                line a\n"
                + "                line b\n"
                + "                " + TQ + ");\n";

        assertEquals(expected, TextBlockWriter.rewrite(source, range, "line a\r\nline b\r\n"));
    }

    @Test
    void preservesSurroundingCode() {
        String source = ""
                + "package foo;\n"
                + "\n"
                + "class T {\n"
                + "    void m() {\n"
                + "        // before\n"
                + "        snap(x).matchesJson(" + TQ + "\n"
                + "                old\n"
                + "                " + TQ + ");  // trailing comment\n"
                + "        int next = 1;\n"
                + "    }\n"
                + "}\n";
        Range range = findRange(source, 6);

        String rewritten = TextBlockWriter.rewrite(source, range, "fresh");

        String expected = ""
                + "package foo;\n"
                + "\n"
                + "class T {\n"
                + "    void m() {\n"
                + "        // before\n"
                + "        snap(x).matchesJson(" + TQ + "\n"
                + "                fresh\n"
                + "                " + TQ + ");  // trailing comment\n"
                + "        int next = 1;\n"
                + "    }\n"
                + "}\n";
        assertEquals(expected, rewritten);
    }

    @Test
    void rewriteIsIdempotentForCanonicalInput() {
        String source = ""
                + "        snap(x).matchesJson(" + TQ + "\n"
                + "                stale\n"
                + "                " + TQ + ");\n";
        Range r1 = findRange(source, 1);
        String once = TextBlockWriter.rewrite(source, r1, "fresh");

        Range r2 = findRange(once, 1);
        String twice = TextBlockWriter.rewrite(once, r2, "fresh");

        assertEquals(once, twice);
    }

    @Test
    void roundTripPropertyContentMatchesAfterStripIndent() {
        String rendered = "{\n  \"key\": \"value\",\n  \"nums\": [1, 2, 3]\n}";
        String source = ""
                + "        snap(x).matchesJson(" + TQ + "\n"
                + "                placeholder\n"
                + "                " + TQ + ");\n";
        Range range = findRange(source, 1);

        String rewritten = TextBlockWriter.rewrite(source, range, rendered);
        Range newRange = findRange(rewritten, 1);

        String runtime = simulateTextBlockRuntime(rewritten, newRange);
        assertEquals(Normalizer.normalize(rendered), Normalizer.normalize(runtime));
    }

    private static Range findRange(String source, int callLine) {
        List<String> lines = Arrays.asList(source.split("\n", -1));
        // split with -1 keeps trailing empty; drop the last if source ends with '\n'
        if (!lines.isEmpty() && source.endsWith("\n")) {
            lines = lines.subList(0, lines.size() - 1);
        }
        return TextBlockFinder.find(lines, callLine);
    }

    /**
     * Mirrors Java's text-block stripping: takes the lines between opener and closer,
     * removes the common indent (equal to the closer's indent), joins with {@code \n}.
     * Used to verify the round-trip invariant without invoking the compiler.
     */
    private static String simulateTextBlockRuntime(String source, Range range) {
        String[] all = source.split("\n", -1);
        String closerLine = all[range.closeLine() - 1];
        int indentLen = 0;
        while (indentLen < closerLine.length()
                && (closerLine.charAt(indentLen) == ' ' || closerLine.charAt(indentLen) == '\t')) {
            indentLen++;
        }
        StringBuilder out = new StringBuilder();
        for (int li = range.openLine() + 1; li <= range.closeLine() - 1; li++) {
            String line = all[li - 1];
            String stripped = line.length() >= indentLen ? line.substring(indentLen) : "";
            stripped = unescapeTripleQuotes(stripped);
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append(stripped);
        }
        return out.toString();
    }

    private static String unescapeTripleQuotes(String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length() && s.charAt(i + 1) == '"') {
                out.append('"');
                i++;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

}
