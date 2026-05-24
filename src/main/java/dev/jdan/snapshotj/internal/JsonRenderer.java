package dev.jdan.snapshotj.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectWriter;

import java.util.Map;

/**
 * Pass 3 of the snapshot pipeline (JSON): serialize a JsonNode IR to a
 * canonical string.
 *
 * <p>Output is stable across platforms and source-level field order:
 * <ul>
 *   <li>object properties and map entries sorted alphabetically by key
 *       (applied at IR-build time; see {@link IrBuilder});</li>
 *   <li>2-space indent, {@code \n} line separator on every platform.</li>
 * </ul>
 *
 * <p>Type replacements and field-path replacements are applied to the IR
 * before this renderer runs; see {@link IrBuilder} and {@link IrTransform}.
 */
public final class JsonRenderer {

    private static final ObjectWriter WRITER =
            IrBuilder.sharedMapper().writer(prettyPrinter());

    private JsonRenderer() {}

    public static String render(Object value) {
        return render(value, Map.of());
    }

    public static String render(Object value, Map<Class<?>, String> typeReplacements) {
        return renderTree(IrBuilder.build(value, typeReplacements));
    }

    public static String renderTree(JsonNode node) {
        try {
            return WRITER.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("could not render value as JSON", e);
        }
    }

    private static DefaultPrettyPrinter prettyPrinter() {
        DefaultIndenter indenter = new DefaultIndenter("  ", "\n");
        return new DefaultPrettyPrinter()
                .withObjectIndenter(indenter)
                .withArrayIndenter(indenter);
    }
}
