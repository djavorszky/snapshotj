package dev.jdan.snapshotj.internal;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonRendererTest {

    record Point(int x, int y) {}

    record Reversed(int z, int a) {}

    record User(UUID id, String name) {}

    record Event(Instant at, String name) {}

    record NumberHolder(Number n) {}

    @Test
    void rendersSimpleRecord() {
        String expected = """
                {
                  "x" : 1,
                  "y" : 2
                }""";
        assertEquals(expected, JsonRenderer.render(new Point(1, 2)));
    }

    @Test
    void propertiesAlphabetizedRegardlessOfDeclarationOrder() {
        String out = JsonRenderer.render(new Reversed(9, 1));
        int aIdx = out.indexOf("\"a\"");
        int zIdx = out.indexOf("\"z\"");
        assertFalse(aIdx < 0 || zIdx < 0, "both keys must be present: " + out);
        assertEquals(true, aIdx < zIdx, "expected 'a' before 'z' in: " + out);
    }

    @Test
    void mapEntriesOrderedByKey() {
        Map<String, Integer> insertionOrder = new LinkedHashMap<>();
        insertionOrder.put("b", 1);
        insertionOrder.put("a", 2);
        insertionOrder.put("c", 3);
        String expected = """
                {
                  "a" : 2,
                  "b" : 1,
                  "c" : 3
                }""";
        assertEquals(expected, JsonRenderer.render(insertionOrder));
    }

    @Test
    void nestedMapsAndLists() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("items", List.of(1, 2, 3));
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("k", "v");
        root.put("inner", nested);
        String expected = """
                {
                  "inner" : {
                    "k" : "v"
                  },
                  "items" : [
                    1,
                    2,
                    3
                  ]
                }""";
        assertEquals(expected, JsonRenderer.render(root));
    }

    @Test
    void localDateTimeAsIso8601() {
        String out = JsonRenderer.render(LocalDateTime.of(2026, 5, 6, 13, 30));
        assertEquals("\"2026-05-06T13:30:00\"", out);
    }

    @Test
    void optionalUnwraps() {
        assertEquals("\"hello\"", JsonRenderer.render(Optional.of("hello")));
        assertEquals("null", JsonRenderer.render(Optional.empty()));
    }

    @Test
    void lineSeparatorIsAlwaysLf() {
        String out = JsonRenderer.render(Map.of("a", List.of(1, 2)));
        assertFalse(out.contains("\r"), "output must not contain CR: " + out);
    }

    @Test
    void nullValueRendersAsJsonNull() {
        assertEquals("null", JsonRenderer.render(null));
    }

    @Test
    void emptyReplacementsMatchDefaultOverload() {
        Point p = new Point(1, 2);
        assertEquals(JsonRenderer.render(p), JsonRenderer.render(p, Map.of()));
    }

    @Test
    void uuidReplacedWithPlaceholder() {
        User u = new User(UUID.fromString("11111111-2222-3333-4444-555555555555"), "Ada");
        String expected = """
                {
                  "id" : "<uuid>",
                  "name" : "Ada"
                }""";
        assertEquals(
                expected,
                JsonRenderer.render(u, Map.of(UUID.class, "<uuid>")));
    }

    @Test
    void instantReplacedWithPlaceholder() {
        Event ev = new Event(Instant.parse("2026-01-15T10:00:00Z"), "boot");
        String expected = """
                {
                  "at" : "<ts>",
                  "name" : "boot"
                }""";
        assertEquals(
                expected,
                JsonRenderer.render(ev, Map.of(Instant.class, "<ts>")));
    }

    @Test
    void nullFieldStaysNullEvenIfTypeRegistered() {
        record Box(UUID id) {}
        Box box = new Box(null);
        String out = JsonRenderer.render(box, Map.of(UUID.class, "<uuid>"));
        assertTrue(out.contains("\"id\" : null"), out);
        assertFalse(out.contains("<uuid>"), out);
    }

    @Test
    void replacementAppliesInsideListsAndMaps() {
        UUID a = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID b = UUID.fromString("00000000-0000-0000-0000-000000000002");
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("ids", List.of(a, b));
        Map<String, UUID> nested = new LinkedHashMap<>();
        nested.put("primary", a);
        root.put("nested", nested);
        String out = JsonRenderer.render(root, Map.of(UUID.class, "<uuid>"));
        String expected = """
                {
                  "ids" : [
                    "<uuid>",
                    "<uuid>"
                  ],
                  "nested" : {
                    "primary" : "<uuid>"
                  }
                }""";
        assertEquals(expected, out);
    }

    @Test
    void exactClassOnlyRegisteringSupertypeDoesNotMatchSubclass() {
        NumberHolder holder = new NumberHolder(42);
        String out = JsonRenderer.render(holder, Map.of(Number.class, "<num>"));
        assertTrue(out.contains("\"n\" : 42"), out);
        assertFalse(out.contains("<num>"), out);
    }
}
