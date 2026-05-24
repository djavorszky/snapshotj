/**
 * snapshotj — JUnit-agnostic inline snapshot testing.
 *
 * <p>Only {@code dev.jdan.snapshotj} is part of the public API. The
 * {@code dev.jdan.snapshotj.internal} package is intentionally not exported.
 */
module dev.jdan.snapshotj {
    exports dev.jdan.snapshotj;

    requires transitive com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.datatype.jsr310;
    requires com.fasterxml.jackson.datatype.jdk8;
    requires org.apache.commons.csv;
    requires io.github.javadiffutils;
}
