package dev.jdan.snapshotj.internal;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Pass 1 of the snapshot pipeline: convert a user value into a JsonNode IR.
 *
 * <p>Type replacements ride here rather than in the renderers: once a value
 * has been turned into a tree node, its runtime class is gone (an
 * {@link java.time.Instant} becomes a {@code TextNode}), so exact-class
 * substitution has to happen on the serializer pipeline that builds the tree.
 *
 * <p>The mapper configuration (alphabetical properties, sorted map entries,
 * ISO-8601 dates, {@code Optional} unwrap) lives on this class so every
 * downstream consumer of the IR observes the same canonical shape.
 */
public final class IrBuilder {

    private static final ObjectMapper SHARED = buildMapper(Map.of());

    private IrBuilder() {}

    public static JsonNode build(Object value) {
        return build(value, Map.of());
    }

    public static JsonNode build(Object value, Map<Class<?>, String> typeReplacements) {
        ObjectMapper mapper = typeReplacements.isEmpty()
                ? SHARED
                : buildMapper(typeReplacements);
        return mapper.valueToTree(value);
    }

    static ObjectMapper sharedMapper() {
        return SHARED;
    }

    static ObjectMapper buildMapper(Map<Class<?>, String> typeReplacements) {
        JsonMapper.Builder builder = JsonMapper.builder()
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.WRITE_DATES_WITH_ZONE_ID)
                .addModule(new JavaTimeModule())
                .addModule(new Jdk8Module());

        if (!typeReplacements.isEmpty()) {
            builder.addModule(replacementModule(typeReplacements));
        }

        return builder.build();
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
