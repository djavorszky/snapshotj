package dev.jdan.snapshotj;

import com.fasterxml.jackson.databind.JsonNode;
import dev.jdan.snapshotj.internal.JsonRenderer;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
        snap(0).matches("line one   \nline two   \n\n\n", n -> "line one\nline two");
    }

    @Test
    void crlfTolerance() {
        snap(0).matches("alpha\r\nbeta\r\n", n -> "alpha\nbeta");
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

    record User(UUID id, Instant createdAt, String name) {}

    @Test
    void replacingNeutralizesTransientFieldsInJson() {
        User u = new User(
                UUID.randomUUID(),
                Instant.now(),
                "Ada");
        snap(u)
                .replacingType(UUID.class, "<uuid>")
                .replacingType(Instant.class, "<timestamp>")
                .matchesJson("""
                        {
                          "createdAt" : "<timestamp>",
                          "id" : "<uuid>",
                          "name" : "Ada"
                        }
                        """);
    }

    @Test
    void replacingNeutralizesTransientFieldsInCsv() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        snap(List.of(new User(a, Instant.now(), "Ada"), new User(b, Instant.now(), "Grace")))
                .replacingType(UUID.class, "<uuid>")
                .replacingType(Instant.class, "<ts>")
                .matchesCsv("""
                        createdAt,id,name
                        <ts>,<uuid>,Ada
                        <ts>,<uuid>,Grace
                        """);
    }

    @Test
    void replacingMismatchStillThrowsAssertionError() {
        User u = new User(UUID.randomUUID(), Instant.now(), "Ada");
        AssertionError err = assertThrows(
                AssertionError.class,
                () -> snap(u)
                        .replacingType(UUID.class, "<uuid>")
                        .replacingType(Instant.class, "<ts>")
                        .matchesJson("""
                                {
                                  "createdAt" : "<ts>",
                                  "id" : "<uuid>",
                                  "name" : "Grace"
                                }
                                """));
        assertTrue(err.getMessage().contains("Ada"), err.getMessage());
    }

    @Test
    void replacingTypeAppliesInCustomMatches() {
        snap(UUID.randomUUID())
                .replacingType(UUID.class, "<uuid>")
                .matches("<uuid>", JsonNode::asText);
    }

    @Test
    void replacingNullTypeThrows() {
        assertThrows(
                NullPointerException.class,
                () -> snap(42).replacingType(null, "<x>"));
    }

    @Test
    void replacingNullPlaceholderThrows() {
        assertThrows(
                NullPointerException.class,
                () -> snap(42).replacingType(UUID.class, null));
    }

    @Test
    void replacingEmptyPlaceholderThrows() {
        assertThrows(
                IllegalArgumentException.class,
                () -> snap(42).replacingType(UUID.class, ""));
    }

    @Test
    void replacingSameTypeTwiceOverwrites() {
        UUID id = UUID.fromString("11111111-2222-3333-4444-555555555555");
        snap(new User(id, Instant.parse("2026-01-01T00:00:00Z"), "Ada"))
                .replacingType(UUID.class, "<first>")
                .replacingType(UUID.class, "<second>")
                .replacingType(Instant.class, "<ts>")
                .matchesJson("""
                        {
                          "createdAt" : "<ts>",
                          "id" : "<second>",
                          "name" : "Ada"
                        }
                        """);
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
        assertDoesNotThrow(() -> snap(p).matches(expected, JsonRenderer::renderTree));

        String wrong = """
                {
                  "x" : 9,
                  "y" : 9
                }
                """;
        assertThrows(AssertionError.class, () -> snap(p).matchesJson(wrong));
        assertThrows(AssertionError.class, () -> snap(p).matches(wrong, JsonRenderer::renderTree));
    }

    record Auto(long id, String name) {}

    record WrapAuto(Auto user) {}

    @Test
    void replacingFieldNeutralizesAnchoredRootInJson() {
        snap(new Auto(System.nanoTime(), "Ada"))
                .replacingField("$.id", "<id>")
                .matchesJson("""
                        {
                          "id" : "<id>",
                          "name" : "Ada"
                        }
                        """);
    }

    @Test
    void replacingFieldNeutralizesNestedAnchoredInJson() {
        snap(new WrapAuto(new Auto(System.nanoTime(), "Ada")))
                .replacingField("$.user.id", "<id>")
                .matchesJson("""
                        {
                          "user" : {
                            "id" : "<id>",
                            "name" : "Ada"
                          }
                        }
                        """);
    }

    record Tree(long id, Tree child) {}

    @Test
    void replacingFieldRecursiveDescentReplacesAllInJson() {
        snap(new Tree(1L, new Tree(2L, null)))
                .replacingField("..id", "<id>")
                .matchesJson("""
                        {
                          "child" : {
                            "child" : null,
                            "id" : "<id>"
                          },
                          "id" : "<id>"
                        }
                        """);
    }

    @Test
    void replacingFieldAnchoredIsScoped() {
        snap(new WrapAuto(new Auto(99L, "Ada")))
                .replacingField("$.user.id", "<id>")
                .matchesJson("""
                        {
                          "user" : {
                            "id" : "<id>",
                            "name" : "Ada"
                          }
                        }
                        """);
    }

    record HasUuidId(UUID id, String name) {}

    @Test
    void replacingFieldWinsOverType() {
        snap(new HasUuidId(UUID.randomUUID(), "Ada"))
                .replacingType(UUID.class, "<uuid>")
                .replacingField("$.id", "<id>")
                .matchesJson("""
                        {
                          "id" : "<id>",
                          "name" : "Ada"
                        }
                        """);
    }

    record Box(String name, String marker) {}

    @Test
    void replacingFieldOnNullStillReplacesInJson() {
        snap(new Box("Ada", null))
                .replacingField("$.marker", "<set>")
                .matchesJson("""
                        {
                          "marker" : "<set>",
                          "name" : "Ada"
                        }
                        """);
    }

    @Test
    void replacingFieldNoMatchThrowsInJson() {
        AssertionError err = assertThrows(
                AssertionError.class,
                () -> snap(new Auto(1L, "Ada"))
                        .replacingField("$.missing", "<x>")
                        .matchesJson("""
                                {
                                  "id" : 1,
                                  "name" : "Ada"
                                }
                                """));
        assertTrue(err.getMessage().contains("$.missing"), err.getMessage());
    }

    @Test
    void replacingFieldRecursiveNoMatchThrowsInJson() {
        AssertionError err = assertThrows(
                AssertionError.class,
                () -> snap(new Auto(1L, "Ada"))
                        .replacingField("..nonexistent", "<x>")
                        .matchesJson("""
                                {
                                  "id" : 1,
                                  "name" : "Ada"
                                }
                                """));
        assertTrue(err.getMessage().contains("..nonexistent"), err.getMessage());
    }

    @Test
    void replacingFieldSamePathTwiceOverwrites() {
        snap(new Auto(1L, "Ada"))
                .replacingField("$.id", "<first>")
                .replacingField("$.id", "<second>")
                .matchesJson("""
                        {
                          "id" : "<second>",
                          "name" : "Ada"
                        }
                        """);
    }

    @Test
    void replacingFieldBarePathRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> snap(new Auto(1L, "Ada")).replacingField("id", "<x>"));
    }

    @Test
    void replacingFieldNullPathThrows() {
        assertThrows(
                NullPointerException.class,
                () -> snap(new Auto(1L, "Ada")).replacingField(null, "<x>"));
    }

    @Test
    void replacingFieldNullPlaceholderThrows() {
        assertThrows(
                NullPointerException.class,
                () -> snap(new Auto(1L, "Ada")).replacingField("$.id", null));
    }

    @Test
    void replacingFieldEmptyPlaceholderThrows() {
        assertThrows(
                IllegalArgumentException.class,
                () -> snap(new Auto(1L, "Ada")).replacingField("$.id", ""));
    }

    @Test
    void replacingFieldAppliesInCustomMatches() {
        snap(new Auto(1L, "Ada"))
                .replacingField("$.id", "<id>")
                .matches("""
                        {"id":"<id>","name":"Ada"}""",
                        JsonNode::toString);
    }

    @Test
    void replacingFieldAnchoredColumnInCsv() {
        snap(List.of(new Auto(1L, "Ada"), new Auto(2L, "Grace")))
                .replacingField("$.id", "<id>")
                .matchesCsv("""
                        id,name
                        <id>,Ada
                        <id>,Grace
                        """);
    }

    @Test
    void replacingFieldRecursiveColumnInCsv() {
        snap(List.of(new Auto(1L, "Ada"), new Auto(2L, "Grace")))
                .replacingField("..id", "<id>")
                .matchesCsv("""
                        id,name
                        <id>,Ada
                        <id>,Grace
                        """);
    }

    @Test
    void replacingFieldNestedPathOnCsvThrows() {
        assertThrows(
                IllegalArgumentException.class,
                () -> snap(List.of(new Auto(1L, "Ada")))
                        .replacingField("$.foo.id", "<id>")
                        .matchesCsv("""
                                id,name
                                <id>,Ada
                                """));
    }

    @Test
    void replacingFieldColumnMissingInCsvThrows() {
        AssertionError err = assertThrows(
                AssertionError.class,
                () -> snap(List.of(new Auto(1L, "Ada")))
                        .replacingField("$.missing", "<x>")
                        .matchesCsv("""
                                id,name
                                1,Ada
                                """));
        assertTrue(err.getMessage().contains("missing"), err.getMessage());
    }

    @Test
    void replacingFieldWinsOverTypeInCsv() {
        snap(List.of(new HasUuidId(UUID.randomUUID(), "Ada")))
                .replacingType(UUID.class, "<uuid>")
                .replacingField("$.id", "<id>")
                .matchesCsv("""
                        id,name
                        <id>,Ada
                        """);
    }

    @Test
    void replacingFieldOnNullCellInCsv() {
        snap(List.of(new Box("Ada", null)))
                .replacingField("$.marker", "<set>")
                .matchesCsv("""
                        marker,name
                        <set>,Ada
                        """);
    }
}
