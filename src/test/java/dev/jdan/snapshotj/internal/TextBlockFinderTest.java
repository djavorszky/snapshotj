package dev.jdan.snapshotj.internal;

import dev.jdan.snapshotj.internal.TextBlockFinder.Range;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextBlockFinderTest {

    private static final Path FIXTURES = Path.of("src/test/resources/fixtures/textblock");

    @Test
    void simple() {
        assertEquals(new Range(5, 28, 7, 16), find("simple.java.txt", 5));
    }

    @Test
    void closerInline() {
        assertEquals(new Range(5, 28, 6, 24), find("closer_inline.java.txt", 5));
    }

    @Test
    void indented() {
        assertEquals(new Range(7, 36, 9, 24), find("indented.java.txt", 7));
    }

    @Test
    void escapedQuotesInsideBlock() {
        assertEquals(new Range(5, 24, 7, 16), find("escaped_quotes.java.txt", 5));
    }

    @Test
    void skipsTripleQuotesInsideComments() {
        assertEquals(new Range(8, 16, 10, 16), find("comment_with_triple_quote.java.txt", 5));
    }

    @Test
    void skipsTripleQuotesInsideRegularStrings() {
        assertEquals(new Range(5, 43, 7, 16), find("string_with_triple_quote.java.txt", 5));
    }

    @Test
    void picksFirstOfMultipleBlocks() {
        assertEquals(new Range(5, 24, 7, 16), find("multiple_blocks.java.txt", 5));
    }

    @Test
    void openerOnLineAfterCall() {
        assertEquals(new Range(6, 16, 8, 16), find("call_on_next_line.java.txt", 5));
    }

    @Test
    void concatenationWithPlusThrows() {
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> find("concat.java.txt", 5));
        String msg = ex.getMessage();
        assertTrue(msg.contains("could not locate inline literal"), msg);
        assertTrue(msg.contains("concatenated with `+`"), msg);
    }

    @Test
    void variableExpectedThrows() {
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> find("variable.java.txt", 6));
        String msg = ex.getMessage();
        assertTrue(msg.contains("could not locate inline literal"), msg);
        assertTrue(msg.contains("not a method call or variable"), msg);
    }

    @Test
    void unterminatedTextBlockThrows() {
        List<String> lines = List.of(
                "class Bad {",
                "    void test() {",
                "        snap(x).matchesJson(\"\"\"",
                "                never closed",
                "    }",
                "}");
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> TextBlockFinder.find(lines, 3));
        assertTrue(ex.getMessage().contains("no closing"), ex.getMessage());
    }

    @Test
    void rejectsCallLineOutOfRange() {
        List<String> lines = List.of("a", "b");
        assertThrows(IllegalArgumentException.class, () -> TextBlockFinder.find(lines, 0));
        assertThrows(IllegalArgumentException.class, () -> TextBlockFinder.find(lines, -1));
        assertThrows(IllegalArgumentException.class, () -> TextBlockFinder.find(lines, 3));
    }

    private static Range find(String fixture, int callLine) {
        return TextBlockFinder.find(FIXTURES.resolve(fixture), callLine);
    }
}
