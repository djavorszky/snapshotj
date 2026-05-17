package dev.jdan.snapshotj;

import dev.jdan.snapshotj.internal.JsonRenderer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;

import static dev.jdan.snapshotj.Snap.snap;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapshotTest {

    record Point(int x, int y) {}

    @Test
    void customMatchesHappyPath() {
        snap(42).matches("forty-two", n -> "forty-two");
    }

    @Test
    void customMatchesMismatchThrowsAssertionError() {
        AssertionError err = assertThrows(
                AssertionError.class,
                () -> snap(42).matches("expected text", n -> "actual text"));
        assertTrue(err.getMessage().contains("expected text"), err.getMessage());
        assertTrue(err.getMessage().contains("actual text"), err.getMessage());
    }

    @Test
    void matchesJsonHappyPath() {
        snap(new Point(1, 2)).matchesJson("""
                {
                  "x" : 1,
                  "y" : 2
                }
                """);
    }

    @Test
    void matchesJsonMismatchThrows() {
        assertThrows(
                AssertionError.class,
                () -> snap(new Point(1, 2)).matchesJson("""
                        {
                          "x" : 9,
                          "y" : 9
                        }
                        """));
    }

    @Test
    void matchesCsvHappyPath() {
        snap(List.of(new Point(1, 2), new Point(3, 4))).matchesCsv("""
                x,y
                1,2
                3,4
                """);
    }

    @Test
    void matchesCsvMismatchThrows() {
        assertThrows(
                AssertionError.class,
                () -> snap(List.of(new Point(1, 2))).matchesCsv("""
                        x,y
                        9,9
                        """));
    }

    @Test
    void trailingWhitespaceTolerance() {
        Function<Integer, String> renderer = n -> "line one\nline two";
        snap(0).matches("line one   \nline two   \n\n\n", renderer);
    }

    @Test
    void crlfTolerance() {
        Function<Integer, String> renderer = n -> "alpha\nbeta";
        snap(0).matches("alpha\r\nbeta\r\n", renderer);
    }

    @Test
    void nullExpectedThrows() {
        assertThrows(
                NullPointerException.class,
                () -> snap(42).matches(null, Object::toString));
    }

    @Test
    void nullRendererThrows() {
        assertThrows(
                NullPointerException.class,
                () -> snap(42).matches("anything", null));
    }

    @Test
    void rendererReturningNullThrows() {
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> snap(42).matches("anything", n -> null));
        assertTrue(ex.getMessage().contains("renderer returned null"), ex.getMessage());
    }

    @Test
    void matchesJsonRoutesThroughMatches() {
        Point p = new Point(1, 2);
        String expected = """
                {
                  "x" : 1,
                  "y" : 2
                }
                """;
        assertDoesNotThrow(() -> snap(p).matchesJson(expected));
        assertDoesNotThrow(() -> snap(p).matches(expected, JsonRenderer::render));

        String wrong = """
                {
                  "x" : 9,
                  "y" : 9
                }
                """;
        assertThrows(AssertionError.class, () -> snap(p).matchesJson(wrong));
        assertThrows(AssertionError.class, () -> snap(p).matches(wrong, JsonRenderer::render));
    }
}
