package live.gameshift.api.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SubscriptionRegistryTest {

    private SubscriptionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SubscriptionRegistry();
    }

    @Test
    void increment_createsEntryAndIncrementsCount() {
        registry.increment("SOCCER", "fixture1");
        assertEquals(1, registry.getCount("SOCCER", "fixture1"));

        registry.increment("SOCCER", "fixture1");
        assertEquals(2, registry.getCount("SOCCER", "fixture1"));
    }

    @Test
    void decrement_decrementsCount() {
        registry.increment("SOCCER", "fixture1");
        registry.increment("SOCCER", "fixture1");
        registry.decrement("SOCCER", "fixture1");
        assertEquals(1, registry.getCount("SOCCER", "fixture1"));
    }

    @Test
    void decrement_neverFallsBelowZero() {
        registry.decrement("SOCCER", "fixture1");
        assertEquals(0, registry.getCount("SOCCER", "fixture1"));

        registry.increment("SOCCER", "fixture1");
        registry.decrement("SOCCER", "fixture1");
        registry.decrement("SOCCER", "fixture1");
        assertEquals(0, registry.getCount("SOCCER", "fixture1"));
    }

    @Test
    void getActiveSubscriptions_returnsOnlyPositiveCounts() {
        registry.increment("SOCCER", "fixture1");
        registry.increment("BASKETBALL", "fixture2");
        registry.increment("HOCKEY", "fixture3");
        registry.decrement("HOCKEY", "fixture3");

        Map<String, Integer> active = registry.getActiveSubscriptions();
        assertEquals(2, active.size());
        assertEquals(1, active.get("SOCCER:fixture1"));
        assertEquals(1, active.get("BASKETBALL:fixture2"));
        assertNull(active.get("HOCKEY:fixture3"));
    }

    @Test
    void getActiveSubscriptions_returnsEmptyMapWhenNoSubscriptions() {
        Map<String, Integer> active = registry.getActiveSubscriptions();
        assertTrue(active.isEmpty());
    }

    @Test
    void getCount_returnsZeroForUnknownKey() {
        assertEquals(0, registry.getCount("UNKNOWN", "fixture99"));
    }

    @Test
    void multipleSports_trackedIndependently() {
        registry.increment("SOCCER", "fixture1");
        registry.increment("BASKETBALL", "fixture1");
        registry.increment("SOCCER", "fixture2");

        assertEquals(1, registry.getCount("SOCCER", "fixture1"));
        assertEquals(1, registry.getCount("BASKETBALL", "fixture1"));
        assertEquals(1, registry.getCount("SOCCER", "fixture2"));
    }

    @Test
    void registerEmitter_tracksEmitterForHeartbeat() {
        SseEmitter emitter = new SseEmitter();
        registry.registerEmitter(emitter, "SOCCER", "fixture1");
        assertEquals(1, registry.getTrackedEmitterCount());
    }

    @Test
    void unregisterEmitter_removesFromTracking() {
        SseEmitter emitter = new SseEmitter();
        registry.registerEmitter(emitter, "SOCCER", "fixture1");
        registry.unregisterEmitter(emitter);
        assertEquals(0, registry.getTrackedEmitterCount());
    }

    @Test
    void recordHeartbeat_updatesLastActivity() {
        SseEmitter emitter = new SseEmitter();
        registry.registerEmitter(emitter, "SOCCER", "fixture1");

        // Should not throw, just update the timestamp
        registry.recordHeartbeat(emitter);
        assertEquals(1, registry.getTrackedEmitterCount());
    }

    @Test
    void recordHeartbeat_ignoredForUnregisteredEmitter() {
        SseEmitter emitter = new SseEmitter();
        // Should not throw even if emitter is not registered
        registry.recordHeartbeat(emitter);
        assertEquals(0, registry.getTrackedEmitterCount());
    }

    @Test
    void evictStaleEmitters_noOpWhenNoEmitters() {
        // Should not throw
        registry.evictStaleEmitters();
    }

    @Test
    void evictStaleEmitters_doesNotEvictFreshEmitters() {
        SseEmitter emitter = new SseEmitter(0L);
        registry.registerEmitter(emitter, "SOCCER", "fixture1");
        registry.increment("SOCCER", "fixture1");

        registry.evictStaleEmitters();

        // Emitter still tracked, count unchanged
        assertEquals(1, registry.getTrackedEmitterCount());
        assertEquals(1, registry.getCount("SOCCER", "fixture1"));
    }
}
