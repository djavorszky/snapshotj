package dev.jdan.snapshotj.internal;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class JsonRendererTest {

    record Point(int x, int y) {}

    record Reversed(int z, int a) {}

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
}
