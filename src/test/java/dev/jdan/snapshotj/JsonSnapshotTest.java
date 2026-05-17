package dev.jdan.snapshotj;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static dev.jdan.snapshotj.Snap.snap;

class JsonSnapshotTest {

    record Point(int x, int y) {}

    record Event(LocalDateTime at, String name) {}

    record Box(Optional<String> label) {}

    @Test
    void simpleRecord() {
        snap(new Point(1, 2)).matchesJson("""
                {
                  "x" : 1,
                  "y" : 2
                }
                """);
    }

    @Test
    void mapAlphabetized() {
        Map<String, Integer> insertionOrder = new LinkedHashMap<>();
        insertionOrder.put("b", 1);
        insertionOrder.put("a", 2);
        insertionOrder.put("c", 3);
        snap(insertionOrder).matchesJson("""
                {
                  "a" : 2,
                  "b" : 1,
                  "c" : 3
                }
                """);
    }

    @Test
    void nestedMapsAndLists() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("items", List.of(1, 2, 3));
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("k", "v");
        root.put("inner", nested);
        snap(root).matchesJson("""
                {
                  "inner" : {
                    "k" : "v"
                  },
                  "items" : [
                    1,
                    2,
                    3
                  ]
                }
                """);
    }

    @Test
    void localDateTimeAsIso8601() {
        Event ev = new Event(LocalDateTime.of(2026, 5, 6, 13, 30), "release");
        snap(ev).matchesJson("""
                {
                  "at" : "2026-05-06T13:30:00",
                  "name" : "release"
                }
                """);
    }

    @Test
    void optionalPresentUnwraps() {
        snap(new Box(Optional.of("hello"))).matchesJson("""
                {
                  "label" : "hello"
                }
                """);
    }

    @Test
    void optionalEmptyRendersNull() {
        snap(new Box(Optional.empty())).matchesJson("""
                {
                  "label" : null
                }
                """);
    }
}
