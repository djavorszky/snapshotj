package dev.jdan.snapshotj;

public final class Snap {

    private Snap() {}

    public static <T> Snapshot<T> snap(T value) {
        return new Snapshot<>(value);
    }
}
