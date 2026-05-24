package dev.jdan.snapshotj.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
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
 *   <li>{@code null} cells emitted as empty;</li>
 *   <li>{@code \n} record separator on every platform.</li>
 * </ul>
 *
 * <p>The {@link #render(Object, Map)} overload accepts a type-to-placeholder map.
 * Any non-null cell whose runtime class exactly matches a registered type is
 * emitted as the placeholder string instead of its natural representation.
 * Exact-class only — no subclass walk. Headers are never replaced.
 */
public final class CsvRenderer {

    private CsvRenderer() {}

    public static String render(Object value) {
        return render(value, Map.of());
    }

    public static String render(Object value, Map<Class<?>, String> typeReplacements) {
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

        return printTree(array, headers);
    }

    private static String printTree(ArrayNode array, List<String> headers) {
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
                    cells.add(cellValue(rowObj.get(h)));
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
