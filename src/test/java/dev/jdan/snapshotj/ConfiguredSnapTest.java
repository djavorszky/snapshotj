package dev.jdan.snapshotj;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfiguredSnapTest {

    record User(UUID id, Instant createdAt, String name) {}

    record Auto(long id, String name) {}

    @Test
    void configureReturnsEmptyDefaultInstance() {
        ConfiguredSnap a = Snap.configure();
        ConfiguredSnap b = Snap.configure();
        assertSame(a, b);
    }

    @Test
    void replacingTypeReturnsNewInstance() {
        ConfiguredSnap base = Snap.configure();
        ConfiguredSnap derived = base.replacingType(UUID.class, "<uuid>");
        assertNotSame(base, derived);
    }

    @Test
    void replacingFieldReturnsNewInstance() {
        ConfiguredSnap base = Snap.configure();
        ConfiguredSnap derived = base.replacingField("$.id", "<id>");
        assertNotSame(base, derived);
    }

    @Test
    void replacingTypeRejectsNullType() {
        assertThrows(
                NullPointerException.class,
                () -> Snap.configure().replacingType(null, "<x>"));
    }

    @Test
    void replacingTypeRejectsNullPlaceholder() {
        assertThrows(
                NullPointerException.class,
                () -> Snap.configure().replacingType(UUID.class, null));
    }

    @Test
    void replacingTypeRejectsEmptyPlaceholder() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Snap.configure().replacingType(UUID.class, ""));
    }

    @Test
    void replacingFieldRejectsNullPath() {
        assertThrows(
                NullPointerException.class,
                () -> Snap.configure().replacingField(null, "<x>"));
    }

    @Test
    void replacingFieldRejectsNullPlaceholder() {
        assertThrows(
                NullPointerException.class,
                () -> Snap.configure().replacingField("$.id", null));
    }

    @Test
    void replacingFieldRejectsEmptyPlaceholder() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Snap.configure().replacingField("$.id", ""));
    }

    @Test
    void replacingFieldValidatesPathEagerly() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Snap.configure().replacingField("not-a-path", "<x>"));
    }

    @Test
    void snapSeedsTypeReplacements() {
        ConfiguredSnap snap = Snap.configure()
                .replacingType(UUID.class, "<uuid>")
                .replacingType(Instant.class, "<ts>");
        User u = new User(UUID.randomUUID(), Instant.now(), "Ada");
        snap.snap(u).matchesJson("""
                {
                  "createdAt" : "<ts>",
                  "id" : "<uuid>",
                  "name" : "Ada"
                }
                """);
    }

    @Test
    void snapSeedsFieldReplacements() {
        ConfiguredSnap snap = Snap.configure().replacingField("$.id", "<id>");
        snap.snap(new Auto(System.nanoTime(), "Ada")).matchesJson("""
                {
                  "id" : "<id>",
                  "name" : "Ada"
                }
                """);
    }

    @Test
    void perCallTypeOverridesSeed() {
        ConfiguredSnap snap = Snap.configure().replacingType(UUID.class, "<seed>");
        UUID id = UUID.fromString("11111111-2222-3333-4444-555555555555");
        snap.snap(new User(id, Instant.parse("2026-01-01T00:00:00Z"), "Ada"))
                .replacingType(UUID.class, "<call>")
                .replacingType(Instant.class, "<ts>")
                .matchesJson("""
                        {
                          "createdAt" : "<ts>",
                          "id" : "<call>",
                          "name" : "Ada"
                        }
                        """);
    }

    @Test
    void perCallFieldOverridesSeed() {
        ConfiguredSnap snap = Snap.configure().replacingField("$.id", "<seed>");
        snap.snap(new Auto(1L, "Ada"))
                .replacingField("$.id", "<call>")
                .matchesJson("""
                        {
                          "id" : "<call>",
                          "name" : "Ada"
                        }
                        """);
    }

    @Test
    void perCallLayersAdditivelyOnSeed() {
        ConfiguredSnap snap = Snap.configure().replacingType(UUID.class, "<uuid>");
        snap.snap(new User(UUID.randomUUID(), Instant.now(), "Ada"))
                .replacingType(Instant.class, "<ts>")
                .matchesJson("""
                        {
                          "createdAt" : "<ts>",
                          "id" : "<uuid>",
                          "name" : "Ada"
                        }
                        """);
    }

    @Test
    void receiverUnchangedAfterDerivation() {
        ConfiguredSnap base = Snap.configure();
        base.replacingType(UUID.class, "<uuid>");
        AssertionError err = assertThrows(
                AssertionError.class,
                () -> base.snap(new User(UUID.randomUUID(), Instant.now(), "Ada"))
                        .matchesJson("""
                                {
                                  "createdAt" : "<ts>",
                                  "id" : "<uuid>",
                                  "name" : "Ada"
                                }
                                """));
        assertTrue(err.getMessage().contains("<uuid>") || err.getMessage().contains("uuid"),
                err.getMessage());
    }

    @Test
    void staticSnapEquivalentToConfigureSnap() {
        Snap.snap(new Auto(1L, "Ada")).matchesJson("""
                {
                  "id" : 1,
                  "name" : "Ada"
                }
                """);
        Snap.configure().snap(new Auto(1L, "Ada")).matchesJson("""
                {
                  "id" : 1,
                  "name" : "Ada"
                }
                """);
    }

    @Test
    void defaultInstanceUnaffectedByConfigure() {
        Snap.configure().replacingType(UUID.class, "<should-not-leak>");
        Snap.configure().replacingField("$.id", "<should-not-leak>");
        Snap.snap(new Auto(1L, "Ada")).matchesJson("""
                {
                  "id" : 1,
                  "name" : "Ada"
                }
                """);
    }
}
