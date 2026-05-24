package dev.jdan.snapshotj.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Parsed representation of a snapshot field path.
 *
 * <p>Supported syntax (JSONPath subset):
 * <ul>
 *   <li>{@code $.foo} — anchored to the root.</li>
 *   <li>{@code $.foo.bar} — anchored, multi-segment.</li>
 *   <li>{@code ..foo} — recursive descent for any field named {@code foo}.</li>
 *   <li>{@code $.foo..bar} — recursive descent for {@code bar} under {@code $.foo}.</li>
 *   <li>{@code ..foo.bar} — {@code bar} of every {@code foo}-value anywhere.</li>
 * </ul>
 *
 * <p>Each segment is an identifier matching {@code [A-Za-z_][A-Za-z0-9_]*}. Bare names
 * (e.g., {@code id}) are rejected to avoid ambiguity between root-only and recursive matching.
 */
public final class FieldPath {

    public sealed interface Segment permits Name, Descend {}

    public record Name(String name) implements Segment {}

    public record Descend(String name) implements Segment {}

    private final String raw;
    private final List<Segment> segments;

    private FieldPath(String raw, List<Segment> segments) {
        this.raw = raw;
        this.segments = List.copyOf(segments);
    }

    public String raw() {
        return raw;
    }

    public List<Segment> segments() {
        return segments;
    }

    public static FieldPath parse(String raw) {
        Objects.requireNonNull(raw, "path");
        if (raw.isEmpty()) {
            throw new IllegalArgumentException("path must not be empty");
        }
        for (int i = 0; i < raw.length(); i++) {
            if (Character.isWhitespace(raw.charAt(i))) {
                throw new IllegalArgumentException(
                        "path must not contain whitespace: '" + raw + "'");
            }
        }

        int idx;
        boolean firstIsDescend;
        if (raw.startsWith("$.")) {
            idx = 2;
            firstIsDescend = false;
        } else if (raw.startsWith("..")) {
            idx = 2;
            firstIsDescend = true;
        } else {
            throw new IllegalArgumentException(
                    "path must start with '$.' or '..': '" + raw + "'");
        }

        List<Segment> segs = new ArrayList<>();
        int end = readIdentifier(raw, idx);
        if (end == idx) {
            throw new IllegalArgumentException(
                    "path is missing the first identifier: '" + raw + "'");
        }
        String firstName = raw.substring(idx, end);
        segs.add(firstIsDescend ? new Descend(firstName) : new Name(firstName));
        idx = end;

        while (idx < raw.length()) {
            boolean descend;
            if (raw.startsWith("..", idx)) {
                descend = true;
                idx += 2;
            } else if (raw.charAt(idx) == '.') {
                descend = false;
                idx += 1;
            } else {
                throw new IllegalArgumentException(
                        "path is malformed near index " + idx + ": '" + raw + "'");
            }
            end = readIdentifier(raw, idx);
            if (end == idx) {
                throw new IllegalArgumentException(
                        "path has an empty segment near index " + idx + ": '" + raw + "'");
            }
            String name = raw.substring(idx, end);
            segs.add(descend ? new Descend(name) : new Name(name));
            idx = end;
        }

        return new FieldPath(raw, segs);
    }

    private static int readIdentifier(String s, int start) {
        if (start >= s.length()) {
            return start;
        }
        if (!isIdStart(s.charAt(start))) {
            return start;
        }
        int i = start + 1;
        while (i < s.length() && isIdPart(s.charAt(i))) {
            i++;
        }
        return i;
    }

    private static boolean isIdStart(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }

    private static boolean isIdPart(char c) {
        return isIdStart(c) || (c >= '0' && c <= '9');
    }
}
