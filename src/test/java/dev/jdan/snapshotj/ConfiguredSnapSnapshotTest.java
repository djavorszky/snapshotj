package dev.jdan.snapshotj;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

class ConfiguredSnapSnapshotTest {

    private static final ConfiguredSnap SNAP = Snap.configure()
            .replacingType(UUID.class, "<uuid>")
            .replacingType(Instant.class, "<ts>")
            .replacingField("$.requestId", "<req-id>");

    record Request(String requestId, UUID userId, Instant submittedAt, String action) {}

    @Test
    void firstAssertionUsesSharedConfig() {
        Request r = new Request(
                "req-" + System.nanoTime(),
                UUID.randomUUID(),
                Instant.now(),
                "open");
        SNAP.snap(r).matchesJson("""
                {
                  "action" : "open",
                  "requestId" : "<req-id>",
                  "submittedAt" : "<ts>",
                  "userId" : "<uuid>"
                }
                """);
    }

    @Test
    void secondAssertionReusesSameConfig() {
        Request r = new Request(
                "req-" + System.nanoTime(),
                UUID.randomUUID(),
                Instant.now(),
                "close");
        SNAP.snap(r).matchesJson("""
                {
                  "action" : "close",
                  "requestId" : "<req-id>",
                  "submittedAt" : "<ts>",
                  "userId" : "<uuid>"
                }
                """);
    }

    @Test
    void perCallOverrideLayersOnSharedConfig() {
        Request r = new Request(
                "req-" + System.nanoTime(),
                UUID.randomUUID(),
                Instant.now(),
                "audit");
        SNAP.snap(r)
                .replacingType(UUID.class, "<masked-user>")
                .matchesJson("""
                        {
                          "action" : "audit",
                          "requestId" : "<req-id>",
                          "submittedAt" : "<ts>",
                          "userId" : "<masked-user>"
                        }
                        """);
    }
}
