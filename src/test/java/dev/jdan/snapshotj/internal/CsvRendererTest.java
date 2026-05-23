package dev.jdan.snapshotj.internal;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvRendererTest {

    record Point(int x, int y) {}

    record Reversed(int z, int a) {}

    record Nullable(String name, Integer age) {}

    @Test
    void rendersListOfRecords() {
        String expected = """
                x,y
                1,2
                3,4
                """;
        assertEquals(expected, CsvRenderer.render(List.of(new Point(1, 2), new Point(3, 4))));
    }

    @Test
    void headersAlphabetizedRegardlessOfDeclarationOrder() {
        String expected = """
                a,z
                1,9
                """;
        assertEquals(expected, CsvRenderer.render(List.of(new Reversed(9, 1))));
    }

    @Test
    void rendersListOfMaps() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("b", 1);
        first.put("a", 2);
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("b", 3);
        second.put("a", 4);
        String expected = """
                a,b
                2,1
                4,3
                """;
        assertEquals(expected, CsvRenderer.render(List.of(first, second)));
    }

    @Test
    void rendersArrayInput() {
        Point[] arr = {new Point(1, 2), new Point(3, 4)};
        assertEquals(
                CsvRenderer.render(List.of(new Point(1, 2), new Point(3, 4))),
                CsvRenderer.render(arr));
    }

    @Test
    void rendersIteratorInput() {
        List<Point> list = List.of(new Point(1, 2), new Point(3, 4));
        assertEquals(CsvRenderer.render(list), CsvRenderer.render(list.iterator()));
    }

    @Test
    void nullCellRendersAsEmpty() {
        String expected = """
                age,name
                ,alice
                """;
        assertEquals(expected, CsvRenderer.render(List.of(new Nullable("alice", null))));
    }

    @Test
    void lineSeparatorIsAlwaysLf() {
        String out = CsvRenderer.render(List.of(new Point(1, 2), new Point(3, 4)));
        assertFalse(out.contains("\r"), "output must not contain CR: " + out);
    }

    @Test
    void nonIterableThrows() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> CsvRenderer.render(new Point(1, 2)));
        assertTrue(ex.getMessage().contains("Iterable"), ex.getMessage());
    }

    @Test
    void emptyIterableThrows() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> CsvRenderer.render(List.of()));
        assertTrue(ex.getMessage().contains("at least one"), ex.getMessage());
    }

    @Test
    void heterogeneousRowsThrow() {
        List<Object> rows = new ArrayList<>();
        rows.add(new Point(1, 2));
        rows.add(new Reversed(3, 4));
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> CsvRenderer.render(rows));
        assertTrue(ex.getMessage().contains("homogeneous"), ex.getMessage());
    }

    @Test
    void mapsWithDifferentKeySetsThrow() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("a", 1);
        first.put("b", 2);
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("a", 3);
        second.put("c", 4);
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> CsvRenderer.render(Arrays.asList(first, second)));
        assertTrue(ex.getMessage().contains("homogeneous"), ex.getMessage());
    }

    @Test
    void nullInputThrows() {
        assertThrows(IllegalArgumentException.class, () -> CsvRenderer.render(null));
    }

    @Test
    void scalarFirstElementThrows() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> CsvRenderer.render(List.of(1, 2, 3)));
        assertTrue(ex.getMessage().contains("Map, record, or POJO"), ex.getMessage());
    }

    record User(UUID id, String name) {}

    @Test
    void emptyReplacementsMatchDefaultOverload() {
        List<Point> rows = List.of(new Point(1, 2));
        assertEquals(CsvRenderer.render(rows), CsvRenderer.render(rows, Map.of()));
    }

    @Test
    void uuidCellReplacedForBeanRows() {
        UUID id = UUID.fromString("11111111-2222-3333-4444-555555555555");
        String expected = """
                id,name
                <uuid>,Ada
                """;
        assertEquals(
                expected,
                CsvRenderer.render(
                        List.of(new User(id, "Ada")), Map.of(UUID.class, "<uuid>")));
    }

    @Test
    void uuidCellReplacedForMapRows() {
        UUID id = UUID.fromString("11111111-2222-3333-4444-555555555555");
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("name", "Ada");
        String expected = """
                id,name
                <uuid>,Ada
                """;
        assertEquals(
                expected,
                CsvRenderer.render(List.of(row), Map.of(UUID.class, "<uuid>")));
    }

    @Test
    void nullCellStaysEmptyEvenIfTypeRegistered() {
        String expected = """
                age,name
                ,alice
                """;
        assertEquals(
                expected,
                CsvRenderer.render(
                        List.of(new Nullable("alice", null)),
                        Map.of(Integer.class, "<int>")));
    }

    @Test
    void placeholderWithCommaIsQuoted() {
        UUID id = UUID.fromString("11111111-2222-3333-4444-555555555555");
        String out = CsvRenderer.render(
                List.of(new User(id, "Ada")), Map.of(UUID.class, "x,y"));
        assertTrue(out.contains("\"x,y\""), out);
    }
}
