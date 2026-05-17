package dev.jdan.snapshotj;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static dev.jdan.snapshotj.Snap.snap;

class CsvSnapshotTest {

    record Point(int x, int y) {}

    record Person(String name, Integer age) {}

    @Test
    void listOfRecords() {
        snap(List.of(new Point(1, 2), new Point(3, 4))).matchesCsv("""
                x,y
                1,2
                3,4
                """);
    }

    @Test
    void singleRowList() {
        snap(List.of(new Person("alice", 30))).matchesCsv("""
                age,name
                30,alice
                """);
    }

    @Test
    void listOfMaps() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("b", 1);
        first.put("a", 2);
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("b", 3);
        second.put("a", 4);
        snap(List.of(first, second)).matchesCsv("""
                a,b
                2,1
                4,3
                """);
    }

    @Test
    void nullCellEmpty() {
        snap(List.of(new Person("bob", null))).matchesCsv("""
                age,name
                ,bob
                """);
    }
}
