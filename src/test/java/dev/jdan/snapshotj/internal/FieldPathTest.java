package dev.jdan.snapshotj.internal;

import dev.jdan.snapshotj.internal.FieldPath.Descend;
import dev.jdan.snapshotj.internal.FieldPath.Name;
import dev.jdan.snapshotj.internal.FieldPath.Segment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FieldPathTest {

    @Test
    void anchoredSingleSegment() {
        assertEquals(List.<Segment>of(new Name("foo")), FieldPath.parse("$.foo").segments());
    }

    @Test
    void anchoredMultiSegment() {
        assertEquals(
                List.<Segment>of(new Name("foo"), new Name("bar")),
                FieldPath.parse("$.foo.bar").segments());
    }

    @Test
    void recursiveSingleSegment() {
        assertEquals(List.<Segment>of(new Descend("foo")), FieldPath.parse("..foo").segments());
    }

    @Test
    void anchoredThenDescend() {
        assertEquals(
                List.<Segment>of(new Name("foo"), new Descend("bar")),
                FieldPath.parse("$.foo..bar").segments());
    }

    @Test
    void descendThenName() {
        assertEquals(
                List.<Segment>of(new Descend("foo"), new Name("bar")),
                FieldPath.parse("..foo.bar").segments());
    }

    @Test
    void underscoreAndDigitsInIdentifier() {
        assertEquals(
                List.<Segment>of(new Name("_priv"), new Name("id2")),
                FieldPath.parse("$._priv.id2").segments());
    }

    @Test
    void bareNameRejected() {
        assertThrows(IllegalArgumentException.class, () -> FieldPath.parse("id"));
    }

    @Test
    void emptyRejected() {
        assertThrows(IllegalArgumentException.class, () -> FieldPath.parse(""));
    }

    @Test
    void nullRejected() {
        assertThrows(NullPointerException.class, () -> FieldPath.parse(null));
    }

    @Test
    void rootOnlyRejected() {
        assertThrows(IllegalArgumentException.class, () -> FieldPath.parse("$"));
    }

    @Test
    void rootDotRejected() {
        assertThrows(IllegalArgumentException.class, () -> FieldPath.parse("$."));
    }

    @Test
    void rootDoubleDotRejected() {
        assertThrows(IllegalArgumentException.class, () -> FieldPath.parse("$.."));
    }

    @Test
    void doubleDotAloneRejected() {
        assertThrows(IllegalArgumentException.class, () -> FieldPath.parse(".."));
    }

    @Test
    void trailingDotRejected() {
        assertThrows(IllegalArgumentException.class, () -> FieldPath.parse("$.foo."));
    }

    @Test
    void trailingDoubleDotRejected() {
        assertThrows(IllegalArgumentException.class, () -> FieldPath.parse("$.foo.."));
    }

    @Test
    void emptyMiddleSegmentRejected() {
        assertThrows(IllegalArgumentException.class, () -> FieldPath.parse("$.foo...bar"));
    }

    @Test
    void identifierStartingWithDigitRejected() {
        assertThrows(IllegalArgumentException.class, () -> FieldPath.parse("$.1foo"));
    }

    @Test
    void nonIdentifierCharRejected() {
        assertThrows(IllegalArgumentException.class, () -> FieldPath.parse("$.foo-bar"));
    }

    @Test
    void whitespaceRejected() {
        assertThrows(IllegalArgumentException.class, () -> FieldPath.parse("$. foo"));
        assertThrows(IllegalArgumentException.class, () -> FieldPath.parse("$.foo bar"));
    }

    @Test
    void dollarMidPathRejected() {
        assertThrows(IllegalArgumentException.class, () -> FieldPath.parse("$.foo.$.bar"));
    }
}
