package live.gameshift.api.service;

import live.gameshift.api.dto.EventDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class SseEmitterService {

    private static final Logger log = LoggerFactory.getLogger(SseEmitterService.class);

    private final SubscriptionRegistry subscriptionRegistry;

    /**
     * Holds an SseEmitter together with its subscription metadata.
     * If fixtureId is null, the emitter receives ALL events (backward compatible).
     * sportType defaults to "ALL" when not specified.
     */
    public record EmitterEntry(SseEmitter emitter, String sportType, String fixtureId) {}

    // Thread-safe list because clients can connect/disconnect at any time
    private final List<EmitterEntry> emitters = new CopyOnWriteArrayList<>();

    public SseEmitterService(SubscriptionRegistry subscriptionRegistry) {
        this.subscriptionRegistry = subscriptionRegistry;
    }

    /**
     * Creates an emitter subscribed to a specific sport and fixture.
     * Pass null fixtureId to receive all events.
     * Pass "ALL" sportType to indicate no specific sport filter.
     */
    public SseEmitter createEmitter(String sportType, String fixtureId) {
        String effectiveSport = (sportType != null && !sportType.isBlank()) ? sportType : "ALL";

        // 0L means "infinite timeout". Keep the connection open forever!
        SseEmitter emitter = new SseEmitter(0L);
        EmitterEntry entry = new EmitterEntry(emitter, effectiveSport, fixtureId);
        this.emitters.add(entry);

        // Register with subscription registry for tracking
        subscriptionRegistry.increment(effectiveSport, fixtureId != null ? fixtureId : "ALL");
        subscriptionRegistry.registerEmitter(emitter, effectiveSport, fixtureId != null ? fixtureId : "ALL");

        try {
            // Send a connection confirmation using a distinct event name so it doesn't
            // interfere with the "message" events that carry actual data
            emitter.send(SseEmitter.event().name("connected").data("{\"status\":\"connected\"}"));
            log.info("New SSE connection created successfully (sport={}, fixtureId={})", effectiveSport, fixtureId);
        } catch (IOException e) {
            log.error("Error creating new SSE connection", e);
            emitter.completeWithError(e);
            this.emitters.remove(entry);
            subscriptionRegistry.decrement(effectiveSport, fixtureId != null ? fixtureId : "ALL");
            subscriptionRegistry.unregisterEmitter(emitter);
        }

        // When a client closes their browser, we remove them from the list
        emitter.onCompletion(() -> {
            log.debug("SSE connection completed");
            this.emitters.remove(entry);
            subscriptionRegistry.decrement(effectiveSport, fixtureId != null ? fixtureId : "ALL");
            subscriptionRegistry.unregisterEmitter(emitter);
        });
        emitter.onTimeout(() -> {
            log.debug("SSE connection timed out");
            emitter.complete();
            this.emitters.remove(entry);
            subscriptionRegistry.decrement(effectiveSport, fixtureId != null ? fixtureId : "ALL");
            subscriptionRegistry.unregisterEmitter(emitter);
        });
        emitter.onError((Throwable e) -> {
            log.error("SSE connection error", e);
            emitter.completeWithError(e != null ? e : new RuntimeException("Unknown SSE error"));
            this.emitters.remove(entry);
            subscriptionRegistry.decrement(effectiveSport, fixtureId != null ? fixtureId : "ALL");
            subscriptionRegistry.unregisterEmitter(emitter);
        });

        return emitter;
    }

    /**
     * Backward-compatible overload: creates an emitter with a specific fixture
     * but defaults sport to "ALL".
     */
    public SseEmitter createEmitter(String fixtureId) {
        return createEmitter("ALL", fixtureId);
    }

    /**
     * Backward-compatible overload: creates an emitter that receives all events.
     */
    public SseEmitter createEmitter() {
        return createEmitter("ALL", null);
    }

    public void broadcast(EventDto eventDto) {
        if (eventDto == null) {
            log.warn("Attempted to broadcast a null eventDto");
            return;
        }

        List<EmitterEntry> deadEntries = new ArrayList<>();

        this.emitters.forEach(entry -> {
            // Skip if this emitter is filtering for a specific sport that doesn't match
            if (!"ALL".equals(entry.sportType()) && !entry.sportType().equals(eventDto.sportType().name())) {
                return; // skip — this emitter is filtering for a different sport
            }

            // Skip if this emitter is filtering for a specific fixture that doesn't match
            if (entry.fixtureId() != null && !entry.fixtureId().equals(eventDto.fixtureId())) {
                return; // skip — this emitter is filtering for a different fixture
            }

            try {
                // Spring automatically converts our EventDto into a JSON string!
                entry.emitter().send(SseEmitter.event().name("message").data(eventDto));
            } catch (IOException e) {
                // If we get an exception, the client probably disconnected abruptly
                log.debug("Failed to send event to emitter, client probably disconnected", e);
                entry.emitter().completeWithError(e);
                deadEntries.add(entry);
            }
        });

        // Clean up dead connections so we don't leak memory
        this.emitters.removeAll(deadEntries);
    }

    /**
     * Sends a heartbeat ping to all connected emitters every 30 seconds.
     * When the heartbeat send succeeds, records activity in the SubscriptionRegistry.
     * When it fails, the emitter will be cleaned up via the error callback.
     */
    @Scheduled(fixedRate = 30_000L)
    public void sendHeartbeats() {
        List<EmitterEntry> deadEntries = new ArrayList<>();

        this.emitters.forEach(entry -> {
            try {
                entry.emitter().send(SseEmitter.event().comment("heartbeat"));
                subscriptionRegistry.recordHeartbeat(entry.emitter());
            } catch (IOException e) {
                log.debug("Heartbeat failed for emitter (sport={}, fixtureId={}), removing",
                        entry.sportType(), entry.fixtureId());
                entry.emitter().completeWithError(e);
                deadEntries.add(entry);
            }
        });

        this.emitters.removeAll(deadEntries);
    }

    /**
     * Provides read access to current emitters for testing/monitoring purposes.
     */
    List<EmitterEntry> getEmitters() {
        return emitters;
    }
}
