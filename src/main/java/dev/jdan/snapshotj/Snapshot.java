package dev.jdan.snapshotj;

import java.util.function.Function;

public final class Snapshot<T> {

    private final T value;

    Snapshot(T value) {
        this.value = value;
    }

    public Snapshot<T> update() {
        throw new UnsupportedOperationException("not implemented");
    }

    public void matches(String expected, Function<T, String> renderer) {
        throw new UnsupportedOperationException("not implemented");
    }

    public void matchesJson(String expected) {
        throw new UnsupportedOperationException("not implemented");
    }

    public void matchesCsv(String expected) {
        throw new UnsupportedOperationException("not implemented");
    }
}
