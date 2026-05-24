package dev.jdan.snapshotj.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.jdan.snapshotj.internal.FieldPath.Descend;
import dev.jdan.snapshotj.internal.FieldPath.Name;
import dev.jdan.snapshotj.internal.FieldPath.Segment;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Deterministic CSV renderer for snapshot comparison.
 *
 * <p>Accepts an {@code Iterable<?>}, {@code Iterator<?>}, or array of homogeneous rows.
 * Each row must be a {@link Map}, a record, or a POJO; scalars are rejected. Output is:
 * <ul>
 *   <li>headers derived from the first row's keys, sorted alphabetically;</li>
 *   <li>rows in iteration order, values extracted by header name;</li>
 *   <li>{@code null} cells emitted as empty (type-replacement channel);</li>
 *   <li>{@code \n} record separator on every platform.</li>
 * </ul>
 *
 * <p>The {@link #render(Object, Map, Map)} overload accepts type and field replacements.
 * Type replacements substitute any non-null cell whose runtime class exactly matches a
 * registered type. Field replacements substitute every cell in the named column (including
 * {@code null} cells). When both apply, field wins.
 */
public final class CsvRenderer {

    private CsvRenderer() {}

    public static String render(Object value) {
        return render(value, Map.of(), Map.of());
    }

    public static String render(Object value, Map<Class<?>, String> typeReplacements) {
        return render(value, typeReplacements, Map.of());
    }

    public static String render(
            Object value,
            Map<Class<?>, String> typeReplacements,
            Map<String, String> fieldReplacements) {

        Map<String, String> columnRepl = validateFieldPaths(fieldReplacements);

        List<Object> rawRows = collectRows(value);
        if (rawRows.isEmpty()) {
            throw new IllegalArgumentException(
                    "CSV renderer requires at least one element to derive header");
        }
        Object first = rawRows.get(0);
        if (first == null) {
            throw new IllegalArgumentException(
                    "CSV renderer requires a non-null first element to derive header");
        }
        if (!(first instanceof Map<?, ?>) && isScalar(first.getClass())) {
            throw new IllegalArgumentException(
                    "CSV row must be a Map, record, or POJO; got " + first.getClass().getName());
        }

        JsonNode tree = IrBuilder.build(rawRows, typeReplacements);
        if (!(tree instanceof ArrayNode array)) {
            throw new IllegalArgumentException(
                    "CSV renderer requires Iterable, Iterator, or array; got "
                            + value.getClass().getName());
        }

        JsonNode firstNode = array.get(0);
        if (!(firstNode instanceof ObjectNode firstObj)) {
            throw new IllegalArgumentException(
                    "CSV row must be a Map, record, or POJO; got " + first.getClass().getName());
        }

        List<String> headers = sortedFieldNames(firstObj);
        if (headers.isEmpty()) {
            throw new IllegalArgumentException(
                    "CSV row type " + first.getClass().getName() + " has no readable properties");
        }
        Set<String> headerSet = new HashSet<>(headers);

        for (String col : columnRepl.keySet()) {
            if (!headerSet.contains(col)) {
                throw new AssertionError(
                        "replacingField: no column '" + col + "' in headers " + headers);
            }
        }

        for (int i = 1; i < array.size(); i++) {
            Object rawRow = rawRows.get(i);
            if (rawRow == null) {
                throw new IllegalArgumentException(
                        "CSV rows must be homogeneous; row " + i + " is null");
            }
            JsonNode rowNode = array.get(i);
            if (!(rowNode instanceof ObjectNode rowObj)) {
                throw new IllegalArgumentException(
                        "CSV rows must be homogeneous; row " + i + " is "
                                + rawRow.getClass().getName()
                                + ", expected " + first.getClass().getName());
            }
            Set<String> rowKeys = new HashSet<>();
            rowObj.fieldNames().forEachRemaining(rowKeys::add);
            if (!rowKeys.equals(headerSet)) {
                if (first instanceof Map<?, ?> && rawRow instanceof Map<?, ?>) {
                    throw new IllegalArgumentException(
                            "CSV rows must be homogeneous; row " + i + " has keys "
                                    + new TreeSet<>(rowKeys) + ", expected " + headers);
                }
                throw new IllegalArgumentException(
                        "CSV rows must be homogeneous; row " + i + " is "
                                + rawRow.getClass().getName()
                                + ", expected " + first.getClass().getName());
            }
        }

        return printTree(array, headers, columnRepl);
    }

    private static Map<String, String> validateFieldPaths(Map<String, String> fieldReplacements) {
        if (fieldReplacements.isEmpty()) {
            return Map.of();
        }
        Map<String, String> columnRepl = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : fieldReplacements.entrySet()) {
            FieldPath path = FieldPath.parse(e.getKey());
            List<Segment> segs = path.segments();
            if (segs.size() != 1) {
                throw new IllegalArgumentException(
                        "path '" + e.getKey() + "' is not applicable to CSV — CSV is flat");
            }
            Segment seg = segs.get(0);
            String column = seg instanceof Name n ? n.name() : ((Descend) seg).name();
            columnRepl.put(column, e.getValue());
        }
        return columnRepl;
    }

    private static String printTree(
            ArrayNode array, List<String> headers, Map<String, String> columnRepl) {
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader(headers.toArray(String[]::new))
                .setRecordSeparator("\n")
                .build();

        StringWriter writer = new StringWriter();
        try (CSVPrinter printer = new CSVPrinter(writer, format)) {
            for (int i = 0; i < array.size(); i++) {
                ObjectNode rowObj = (ObjectNode) array.get(i);
                List<Object> cells = new ArrayList<>(headers.size());
                for (String h : headers) {
                    if (columnRepl.containsKey(h)) {
                        cells.add(columnRepl.get(h));
                    } else {
                        cells.add(cellValue(rowObj.get(h)));
                    }
                }
                printer.printRecord(cells);
            }
        } catch (IOException e) {
            throw new IllegalStateException("CSV rendering failed", e);
        }
        return writer.toString();
    }

    private static Object cellValue(JsonNode cell) {
        if (cell == null || cell.isNull() || cell.isMissingNode()) {
            return null;
        }
        if (cell.isValueNode()) {
            return cell.asText();
        }
        return cell.toString();
    }

    private static List<String> sortedFieldNames(ObjectNode obj) {
        TreeSet<String> sorted = new TreeSet<>();
        obj.fieldNames().forEachRemaining(sorted::add);
        return new ArrayList<>(sorted);
    }

    private static List<Object> collectRows(Object value) {
        if (value == null) {
            throw new IllegalArgumentException(
                    "CSV renderer requires Iterable, Iterator, or array; got null");
        }
        List<Object> out = new ArrayList<>();
        if (value instanceof Iterable<?> it) {
            for (Object o : it) {
                out.add(o);
            }
            return out;
        }
        if (value instanceof Iterator<?> it) {
            while (it.hasNext()) {
                out.add(it.next());
            }
            return out;
        }
        if (value.getClass().isArray()) {
            int n = Array.getLength(value);
            for (int i = 0; i < n; i++) {
                out.add(Array.get(value, i));
            }
            return out;
        }
        throw new IllegalArgumentException(
                "CSV renderer requires Iterable, Iterator, or array; got "
                        + value.getClass().getName());
    }

    private static boolean isScalar(Class<?> cls) {
        return cls.isPrimitive()
                || cls == String.class
                || cls == Character.class
                || Number.class.isAssignableFrom(cls)
                || cls == Boolean.class;
    }
}
