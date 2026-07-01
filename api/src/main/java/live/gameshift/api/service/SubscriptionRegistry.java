package live.gameshift.api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Tracks active SSE subscriptions grouped by sport type and fixture ID.
 * Used to inform the producer service which fixtures have active viewers,
 * enabling session-aware polling that stops when no users are watching.
 */
@Service
public class SubscriptionRegistry {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionRegistry.class);

    /**
     * Heartbeat timeout in milliseconds. If an emitter has not received
     * a heartbeat acknowledgment within this period, it is considered stale.
     */
    private static final long HEARTBEAT_TIMEOUT_MS = 120_000L;

    /**
     * Subscription counts keyed by "SPORT_TYPE:fixtureId".
     */
    private final ConcurrentHashMap<String, AtomicInteger> subscriptionCounts = new ConcurrentHashMap<>();

    /**
     * Tracks last activity time for each emitter to detect stale connections.
     */
    private final ConcurrentHashMap<SseEmitter, Long> emitterLastActivity = new ConcurrentHashMap<>();

    /**
     * Maps each emitter to its subscription key for cleanup on timeout.
     */
    private final ConcurrentHashMap<SseEmitter, String> emitterKeys = new ConcurrentHashMap<>();

    /**
     * Builds the subscription key from sport type and fixture ID.
     */
    private String buildKey(String sportType, String fixtureId) {
        return sportType + ":" + fixtureId;
    }

    /**
     * Increments the subscriber count for the given sport and fixture.
     * Called when a new SSE connection is established.
     */
    public void increment(String sportType, String fixtureId) {
        String key = buildKey(sportType, fixtureId);
        subscriptionCounts.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();
        log.debug("Incremented subscription count for {}: {}", key, subscriptionCounts.get(key).get());
    }

    /**
     * Decrements the subscriber count for the given sport and fixture.
     * The count never falls below zero.
     * Called when an SSE connection is closed, times out, or errors.
     */
    public void decrement(String sportType, String fixtureId) {
        String key = buildKey(sportType, fixtureId);
        subscriptionCounts.computeIfAbsent(key, k -> new AtomicInteger(0)).updateAndGet(current -> Math.max(0, current - 1));
        log.debug("Decremented subscription count for {}: {}", key, subscriptionCounts.get(key).get());
    }

    /**
     * Returns a snapshot of active subscriptions (entries with count > 0).
     * Keys are "SPORT_TYPE:fixtureId", values are the current subscriber count.
     */
    public Map<String, Integer> getActiveSubscriptions() {
        return subscriptionCounts.entrySet().stream()
                .filter(entry -> entry.getValue().get() > 0)
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().get()));
    }

    /**
     * Registers an emitter for heartbeat tracking.
     * Call this when a new SSE connection is created.
     */
    public void registerEmitter(SseEmitter emitter, String sportType, String fixtureId) {
        String key = buildKey(sportType, fixtureId);
        emitterLastActivity.put(emitter, System.currentTimeMillis());
        emitterKeys.put(emitter, key);
    }

    /**
     * Records a heartbeat for the given emitter, resetting its timeout clock.
     */
    public void recordHeartbeat(SseEmitter emitter) {
        if (emitterLastActivity.containsKey(emitter)) {
            emitterLastActivity.put(emitter, System.currentTimeMillis());
        }
    }

    /**
     * Unregisters an emitter from heartbeat tracking.
     * Call this when an emitter is completed or closed.
     */
    public void unregisterEmitter(SseEmitter emitter) {
        emitterLastActivity.remove(emitter);
        emitterKeys.remove(emitter);
    }

    /**
     * Periodically checks for stale emitters that haven't received a heartbeat
     * within the timeout period (120s). Stale emitters are closed, which triggers
     * the onCompletion/onError callback in SseEmitterService — that callback is
     * responsible for decrementing the subscription count. We only clean up the
     * tracking maps here to avoid double-decrementing.
     */
    @Scheduled(fixedRate = 30_000L)
    public void evictStaleEmitters() {
        long now = System.currentTimeMillis();

        emitterLastActivity.forEach((emitter, lastActivity) -> {
            if (now - lastActivity > HEARTBEAT_TIMEOUT_MS) {
                String key = emitterKeys.get(emitter);
                log.info("Evicting stale emitter for key={}, last activity {}ms ago", key, now - lastActivity);

                // Clean up tracking maps before completing, so the callback's
                // unregisterEmitter() call is a safe no-op
                emitterLastActivity.remove(emitter);
                emitterKeys.remove(emitter);

                // Close the stale emitter — this triggers SseEmitterService's
                // onCompletion callback which handles the decrement
                try {
                    emitter.complete();
                } catch (Exception e) {
                    log.debug("Error completing stale emitter: {}", e.getMessage());
                }
            }
        });
    }

    /**
     * Returns the current count for a specific sport and fixture.
     * Returns 0 if no entry exists.
     */
    public int getCount(String sportType, String fixtureId) {
        String key = buildKey(sportType, fixtureId);
        AtomicInteger count = subscriptionCounts.get(key);
        return count != null ? count.get() : 0;
    }

    /**
     * Returns the full subscription counts map. Primarily for testing/monitoring.
     */
    ConcurrentHashMap<String, AtomicInteger> getSubscriptionCounts() {
        return subscriptionCounts;
    }

    /**
     * Returns the emitter tracking maps size. Primarily for testing/monitoring.
     */
    int getTrackedEmitterCount() {
        return emitterLastActivity.size();
    }
}
