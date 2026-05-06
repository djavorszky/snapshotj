package dev.jdan.snapshotj.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NormalizerTest {

    @Test
    void normalizesTrailingNewlines() {
        String canonical = Normalizer.normalize("abc");
        assertEquals(canonical, Normalizer.normalize("abc\n"));
        assertEquals(canonical, Normalizer.normalize("abc\n\n\n"));
    }

    @Test
    void normalizesTrailingWhitespacePerLine() {
        assertEquals("a\nb", Normalizer.normalize("a   \nb\t\n"));
    }

    @Test
    void normalizesCrlfToLf() {
        assertEquals(Normalizer.normalize("a\nb\n"), Normalizer.normalize("a\r\nb\r\n"));
    }

    @Test
    void normalizesStandaloneCr() {
        assertEquals("a\nb", Normalizer.normalize("a\rb\r"));
    }

    @Test
    void preservesInteriorBlankLines() {
        assertEquals("a\n\nb", Normalizer.normalize("a\n\nb"));
    }

    @Test
    void preservesInteriorWhitespace() {
        assertEquals("a   b", Normalizer.normalize("a   b"));
    }

    @Test
    void emptyString() {
        assertEquals("", Normalizer.normalize(""));
    }

    @Test
    void onlyNewlines() {
        assertEquals("", Normalizer.normalize("\n\n"));
    }

    @Test
    void onlyWhitespaceAndNewlines() {
        assertEquals("", Normalizer.normalize("   \n   "));
    }

    @Test
    void idempotent() {
        String input = "  a  \n\nb\t\n   \n";
        String once = Normalizer.normalize(input);
        assertEquals(once, Normalizer.normalize(once));
    }

    @Test
    void nullThrows() {
        assertThrows(NullPointerException.class, () -> Normalizer.normalize(null));
    }
}
