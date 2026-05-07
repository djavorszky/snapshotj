package dev.jdan.snapshotj.internal;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Deterministic CSV renderer for snapshot comparison.
 *
 * <p>Accepts an {@code Iterable<?>}, {@code Iterator<?>}, or array of homogeneous rows.
 * Each row must be a {@link Map}, a record, or a POJO; scalars are rejected. Output is:
 * <ul>
 *   <li>headers derived from the first row's properties (Jackson {@link BeanDescription})
 *       or map keys, sorted alphabetically;</li>
 *   <li>rows in iteration order, values extracted by header name;</li>
 *   <li>{@code null} cells emitted as empty;</li>
 *   <li>{@code \n} record separator on every platform.</li>
 * </ul>
 */
public final class CsvRenderer {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private CsvRenderer() {}

    public static String render(Object value) {
        List<Object> rows = collectRows(value);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException(
                    "CSV renderer requires at least one element to derive header");
        }

        Object first = rows.get(0);
        if (first == null) {
            throw new IllegalArgumentException(
                    "CSV renderer requires a non-null first element to derive header");
        }

        Schema schema = schemaFor(first);
        validateHomogeneous(rows, schema);

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader(schema.headers().toArray(String[]::new))
                .setRecordSeparator("\n")
                .build();

        StringWriter writer = new StringWriter();
        try (CSVPrinter printer = new CSVPrinter(writer, format)) {
            for (Object row : rows) {
                printer.printRecord(schema.extract(row));
            }
        } catch (IOException e) {
            throw new IllegalStateException("CSV rendering failed", e);
        }
        return writer.toString();
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

    private static Schema schemaFor(Object first) {
        if (first instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                sorted.put(Objects.toString(e.getKey(), ""), null);
            }
            List<String> headers = new ArrayList<>(sorted.keySet());
            return new MapSchema(headers);
        }
        Class<?> cls = first.getClass();
        if (isScalar(cls)) {
            throw new IllegalArgumentException(
                    "CSV row must be a Map, record, or POJO; got " + cls.getName());
        }
        JavaType type = MAPPER.constructType(cls);
        BeanDescription desc = MAPPER.getSerializationConfig().introspect(type);
        Map<String, BeanPropertyDefinition> byName = new TreeMap<>();
        for (BeanPropertyDefinition prop : desc.findProperties()) {
            AnnotatedMember accessor = prop.getAccessor();
            if (accessor == null) {
                continue;
            }
            accessor.fixAccess(true);
            byName.put(prop.getName(), prop);
        }
        if (byName.isEmpty()) {
            throw new IllegalArgumentException(
                    "CSV row type " + cls.getName() + " has no readable properties");
        }
        return new BeanSchema(cls, new ArrayList<>(byName.keySet()), new LinkedHashMap<>(byName));
    }

    private static boolean isScalar(Class<?> cls) {
        return cls.isPrimitive()
                || cls == String.class
                || cls == Character.class
                || Number.class.isAssignableFrom(cls)
                || cls == Boolean.class;
    }

    private static void validateHomogeneous(List<Object> rows, Schema schema) {
        for (int i = 1; i < rows.size(); i++) {
            Object row = rows.get(i);
            if (row == null) {
                throw new IllegalArgumentException(
                        "CSV rows must be homogeneous; row " + i + " is null");
            }
            schema.checkCompatible(row, i);
        }
    }

    private sealed interface Schema permits BeanSchema, MapSchema {
        List<String> headers();
        List<Object> extract(Object row);
        void checkCompatible(Object row, int index);
    }

    private record MapSchema(List<String> headers) implements Schema {
        @Override
        public List<Object> extract(Object row) {
            Map<?, ?> map = (Map<?, ?>) row;
            Map<String, Object> byKey = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                byKey.put(Objects.toString(e.getKey(), ""), e.getValue());
            }
            List<Object> out = new ArrayList<>(headers.size());
            for (String h : headers) {
                out.add(byKey.get(h));
            }
            return out;
        }

        @Override
        public void checkCompatible(Object row, int index) {
            if (!(row instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException(
                        "CSV rows must be homogeneous; row " + index + " is "
                                + row.getClass().getName() + ", expected Map");
            }
            if (map.size() != headers.size()) {
                throw new IllegalArgumentException(
                        "CSV rows must be homogeneous; row " + index + " has keys "
                                + map.keySet() + ", expected " + headers);
            }
            for (Object key : map.keySet()) {
                if (!headers.contains(Objects.toString(key, ""))) {
                    throw new IllegalArgumentException(
                            "CSV rows must be homogeneous; row " + index + " has keys "
                                    + map.keySet() + ", expected " + headers);
                }
            }
        }
    }

    private record BeanSchema(
            Class<?> type,
            List<String> headers,
            Map<String, BeanPropertyDefinition> props) implements Schema {

        @Override
        public List<Object> extract(Object row) {
            List<Object> out = new ArrayList<>(headers.size());
            for (String h : headers) {
                AnnotatedMember accessor = props.get(h).getAccessor();
                try {
                    out.add(accessor.getValue(row));
                } catch (RuntimeException e) {
                    throw new IllegalStateException(
                            "failed to read property '" + h + "' from " + type.getName(), e);
                }
            }
            return out;
        }

        @Override
        public void checkCompatible(Object row, int index) {
            if (!type.isInstance(row)) {
                throw new IllegalArgumentException(
                        "CSV rows must be homogeneous; row " + index + " is "
                                + row.getClass().getName() + ", expected " + type.getName());
            }
        }
    }
}
