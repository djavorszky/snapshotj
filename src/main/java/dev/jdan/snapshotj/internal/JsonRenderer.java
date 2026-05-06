package dev.jdan.snapshotj.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Deterministic JSON renderer for snapshot comparison.
 *
 * <p>Output is stable across platforms and source-level field order:
 * <ul>
 *   <li>object properties and map entries sorted alphabetically by key;</li>
 *   <li>{@code java.time} values rendered as ISO-8601 strings (never epoch millis);</li>
 *   <li>{@code Optional} unwraps via {@link Jdk8Module};</li>
 *   <li>2-space indent, {@code \n} line separator on every platform.</li>
 * </ul>
 */
public final class JsonRenderer {

    private static final ObjectWriter WRITER = buildWriter();

    private JsonRenderer() {}

    public static String render(Object value) {
        try {
            return WRITER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("could not render value as JSON", e);
        }
    }

    private static ObjectWriter buildWriter() {
        ObjectMapper mapper = JsonMapper.builder()
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.WRITE_DATES_WITH_ZONE_ID)
                .addModule(new JavaTimeModule())
                .addModule(new Jdk8Module())
                .build();

        DefaultIndenter indenter = new DefaultIndenter("  ", "\n");
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter()
                .withObjectIndenter(indenter)
                .withArrayIndenter(indenter);

        return mapper.writer(printer);
    }
}
