package dev.jdan.snapshotj.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IrTransformTest {

    private static final JsonNodeFactory F = JsonNodeFactory.instance;

    @Test
    void anchoredRootFieldReplaced() {
        ObjectNode root = F.objectNode().put("id", 1).put("name", "Ada");
        IrTransform.applyFieldPaths(root, Map.of("$.id", "<id>"));
        assertEquals("<id>", root.get("id").asText());
        assertEquals("Ada", root.get("name").asText());
    }

    @Test
    void anchoredNestedFieldReplaced() {
        ObjectNode user = F.objectNode().put("id", 1).put("name", "Ada");
        ObjectNode root = F.objectNode();
        root.set("user", user);
        IrTransform.applyFieldPaths(root, Map.of("$.user.id", "<id>"));
        assertEquals("<id>", root.get("user").get("id").asText());
        assertEquals("Ada", root.get("user").get("name").asText());
    }

    @Test
    void anchoredDoesNotMatchSibling() {
        ObjectNode user = F.objectNode().put("id", 1);
        ObjectNode root = F.objectNode().put("id", 99);
        root.set("user", user);
        IrTransform.applyFieldPaths(root, Map.of("$.user.id", "<id>"));
        assertEquals("<id>", root.get("user").get("id").asText());
        assertEquals(99, root.get("id").asInt());
    }

    @Test
    void recursiveDescentReplacesAllMatches() {
        ObjectNode inner = F.objectNode().put("id", 2).put("name", "x");
        ObjectNode root = F.objectNode().put("id", 1);
        root.set("inner", inner);
        IrTransform.applyFieldPaths(root, Map.of("..id", "<id>"));
        assertEquals("<id>", root.get("id").asText());
        assertEquals("<id>", root.get("inner").get("id").asText());
        assertEquals("x", root.get("inner").get("name").asText());
    }

    @Test
    void recursiveDescentAcrossArrayElements() {
        ArrayNode arr = F.arrayNode();
        arr.add(F.objectNode().put("id", 1).put("name", "a"));
        arr.add(F.objectNode().put("id", 2).put("name", "b"));
        IrTransform.applyFieldPaths(arr, Map.of("..id", "<id>"));
        assertEquals("<id>", arr.get(0).get("id").asText());
        assertEquals("<id>", arr.get(1).get("id").asText());
        assertEquals("a", arr.get(0).get("name").asText());
    }

    @Test
    void combinedAnchoredAndDescent() {
        ObjectNode root = F.objectNode().put("id", 9);
        ObjectNode foo = F.objectNode();
        ObjectNode inner = F.objectNode().put("id", 1);
        foo.set("inner", inner);
        foo.put("id", 2);
        root.set("foo", foo);

        IrTransform.applyFieldPaths(root, Map.of("$.foo..id", "<id>"));
        assertEquals(9, root.get("id").asInt());
        assertEquals("<id>", root.get("foo").get("id").asText());
        assertEquals("<id>", root.get("foo").get("inner").get("id").asText());
    }

    @Test
    void nullFieldStillReplaced() {
        ObjectNode root = F.objectNode();
        root.set("id", NullNode.getInstance());
        IrTransform.applyFieldPaths(root, Map.of("$.id", "<id>"));
        assertEquals("<id>", root.get("id").asText());
    }

    @Test
    void emptyMapIsNoOp() {
        ObjectNode root = F.objectNode().put("id", 1);
        IrTransform.applyFieldPaths(root, Map.of());
        assertEquals(1, root.get("id").asInt());
    }

    @Test
    void anchoredZeroMatchThrows() {
        ObjectNode root = F.objectNode().put("name", "Ada");
        AssertionError err = assertThrows(
                AssertionError.class,
                () -> IrTransform.applyFieldPaths(root, Map.of("$.id", "<id>")));
        assertTrue(err.getMessage().contains("$.id"), err.getMessage());
        assertTrue(err.getMessage().contains("name"), err.getMessage());
    }

    @Test
    void recursiveZeroMatchThrows() {
        ObjectNode root = F.objectNode().put("name", "Ada");
        AssertionError err = assertThrows(
                AssertionError.class,
                () -> IrTransform.applyFieldPaths(root, Map.of("..nope", "<x>")));
        assertTrue(err.getMessage().contains("..nope"), err.getMessage());
    }

    @Test
    void multiplePathsAppliedInOrder() {
        ObjectNode root = F.objectNode().put("id", 1).put("ts", "2026");
        LinkedHashMap<String, String> repl = new LinkedHashMap<>();
        repl.put("$.id", "<id>");
        repl.put("$.ts", "<ts>");
        IrTransform.applyFieldPaths(root, repl);
        assertEquals("<id>", root.get("id").asText());
        assertEquals("<ts>", root.get("ts").asText());
    }

    @Test
    void descendIntoNestedObject() {
        ObjectNode innerInner = F.objectNode().put("id", 3);
        ObjectNode inner = F.objectNode();
        inner.set("deep", innerInner);
        ObjectNode root = F.objectNode();
        root.set("inner", inner);
        IrTransform.applyFieldPaths(root, Map.of("..id", "<id>"));
        assertEquals("<id>", root.get("inner").get("deep").get("id").asText());
    }

    @Test
    void arrayRootDescent() {
        ArrayNode arr = F.arrayNode();
        arr.add(F.objectNode().put("id", 1));
        IrTransform.applyFieldPaths(arr, Map.of("..id", "<id>"));
        JsonNode el = arr.get(0);
        assertEquals("<id>", el.get("id").asText());
    }
}
