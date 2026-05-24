package dev.jdan.snapshotj;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static dev.jdan.snapshotj.Snap.snap;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DiffMessageSnapshotTest {

    record Point(int x, int y) {}

    @Test
    void diffMessageFormat() {
        AssertionError err = assertThrows(AssertionError.class,
                () -> snap(new Point(1, 2)).matchesJson("""
                        {
                          "x" : 9,
                          "y" : 9
                        }
                        """));
        String message = err.getMessage();
        int firstNewline = message.indexOf('\n');
        String diffBody = firstNewline < 0 ? message : message.substring(firstNewline + 1);
        snap(diffBody).matches("""
                --- expected
                +++ actual
                @@ -1,4 +1,4 @@
                 {
                -  "x" : 9,
                -  "y" : 9
                +  "x" : 1,
                +  "y" : 2
                 }
                """, JsonNode::asText);
    }
}
