package dev.jdan.snapshotj.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import dev.jdan.snapshotj.internal.FieldPath.Descend;
import dev.jdan.snapshotj.internal.FieldPath.Name;
import dev.jdan.snapshotj.internal.FieldPath.Segment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Pass 2 of the snapshot pipeline: in-place mutation of a JsonNode IR.
 *
 * <p>Currently handles field-path replacements; new mutations may be added here
 * without touching the builders or the renderers.
 */
public final class IrTransform {

    private IrTransform() {}

    /**
     * Apply each {@code path -> placeholder} entry to the tree. Throws
     * {@link AssertionError} for any path that matches zero fields.
     */
    public static void applyFieldPaths(JsonNode root, Map<String, String> fieldReplacements) {
        if (fieldReplacements.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> e : fieldReplacements.entrySet()) {
            FieldPath path = FieldPath.parse(e.getKey());
            int count = walk(root, path.segments(), e.getValue());
            if (count == 0) {
                throw new AssertionError(
                        "replacingField(\"" + e.getKey() + "\", \"" + e.getValue()
                                + "\"): no match (" + shape(root) + ")");
            }
        }
    }

    private static int walk(JsonNode node, List<Segment> segs, String placeholder) {
        if (segs.isEmpty() || node == null) {
            return 0;
        }
        Segment head = segs.get(0);
        List<Segment> rest = segs.subList(1, segs.size());

        if (head instanceof Name name) {
            if (!(node instanceof ObjectNode obj) || !obj.has(name.name())) {
                return 0;
            }
            if (rest.isEmpty()) {
                obj.set(name.name(), TextNode.valueOf(placeholder));
                return 1;
            }
            return walk(obj.get(name.name()), rest, placeholder);
        }

        Descend descend = (Descend) head;
        int count = 0;
        if (node instanceof ObjectNode obj) {
            List<String> keys = new ArrayList<>();
            obj.fieldNames().forEachRemaining(keys::add);
            for (String key : keys) {
                if (key.equals(descend.name())) {
                    if (rest.isEmpty()) {
                        obj.set(key, TextNode.valueOf(placeholder));
                        count++;
                    } else {
                        count += walk(obj.get(key), rest, placeholder);
                    }
                }
                count += walk(obj.get(key), segs, placeholder);
            }
        } else if (node instanceof ArrayNode arr) {
            for (JsonNode el : arr) {
                count += walk(el, segs, placeholder);
            }
        }
        return count;
    }

    private static String shape(JsonNode root) {
        if (root instanceof ObjectNode obj) {
            TreeSet<String> sorted = new TreeSet<>();
            obj.fieldNames().forEachRemaining(sorted::add);
            return "root has fields " + sorted;
        }
        if (root instanceof ArrayNode) {
            return "root is an array";
        }
        if (root == null) {
            return "root is null";
        }
        return "root is " + root.getNodeType();
    }
}
