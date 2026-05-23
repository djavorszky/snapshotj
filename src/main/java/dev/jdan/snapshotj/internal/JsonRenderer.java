package dev.jdan.snapshotj.internal;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.ser.Serializers;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

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
 *
 * <p>The {@link #render(Object, Map)} overload accepts a type-to-placeholder map.
 * Any value whose runtime class exactly matches a registered type is serialized
 * as the placeholder string instead of its natural representation. This lets
 * snapshot tests neutralize transient values (UUIDs, timestamps, etc.) without
 * losing the field in the rendered output. Exact-class only — no subclass walk.
 */
public final class JsonRenderer {

    private static final ObjectWriter WRITER = buildWriter(Map.of());

    private JsonRenderer() {}

    public static String render(Object value) {
        return render(value, Map.of());
    }

    public static String render(Object value, Map<Class<?>, String> replacements) {
        ObjectWriter writer = replacements.isEmpty() ? WRITER : buildWriter(replacements);
        try {
            return writer.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("could not render value as JSON", e);
        }
    }

    private static ObjectWriter buildWriter(Map<Class<?>, String> replacements) {
        JsonMapper.Builder builder = JsonMapper.builder()
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.WRITE_DATES_WITH_ZONE_ID)
                .addModule(new JavaTimeModule())
                .addModule(new Jdk8Module());

        if (!replacements.isEmpty()) {
            builder.addModule(replacementModule(replacements));
        }

        ObjectMapper mapper = builder.build();

        DefaultIndenter indenter = new DefaultIndenter("  ", "\n");
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter()
                .withObjectIndenter(indenter)
                .withArrayIndenter(indenter);

        return mapper.writer(printer);
    }

    private static Module replacementModule(Map<Class<?>, String> replacements) {
        Map<Class<?>, JsonSerializer<?>> exact = new HashMap<>();
        for (Map.Entry<Class<?>, String> entry : replacements.entrySet()) {
            exact.put(entry.getKey(), new PlaceholderSerializer(entry.getValue()));
        }
        return new Module() {
            @Override
            public String getModuleName() {
                return "snapshotj-replacements";
            }

            @Override
            public Version version() {
                return Version.unknownVersion();
            }

            @Override
            public void setupModule(SetupContext context) {
                context.addSerializers(new ExactClassSerializers(exact));
            }
        };
    }

    private static final class ExactClassSerializers extends Serializers.Base {
        private final Map<Class<?>, JsonSerializer<?>> exact;

        ExactClassSerializers(Map<Class<?>, JsonSerializer<?>> exact) {
            this.exact = exact;
        }

        @Override
        public JsonSerializer<?> findSerializer(
                SerializationConfig config, JavaType type, BeanDescription beanDesc) {
            return exact.get(type.getRawClass());
        }
    }

    private static final class PlaceholderSerializer extends JsonSerializer<Object> {
        private final String placeholder;

        PlaceholderSerializer(String placeholder) {
            this.placeholder = placeholder;
        }

        @Override
        public void serialize(Object value, JsonGenerator gen, SerializerProvider serializers)
                throws IOException {
            gen.writeString(placeholder);
        }
    }
}
