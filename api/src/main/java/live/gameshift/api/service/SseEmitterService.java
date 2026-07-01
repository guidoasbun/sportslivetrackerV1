package live.gameshift.api.service;

import live.gameshift.api.dto.EventDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class SseEmitterService {

    private static final Logger log = LoggerFactory.getLogger(SseEmitterService.class);

    /**
     * Holds an SseEmitter together with its subscription metadata.
     * If fixtureId is null, the emitter receives ALL events (backward compatible).
     */
    public record EmitterEntry(SseEmitter emitter, String fixtureId) {}

    // Thread-safe list because clients can connect/disconnect at any time
    private final List<EmitterEntry> emitters = new CopyOnWriteArrayList<>();

    /**
     * Creates an emitter subscribed to a specific fixture.
     * Pass null to receive all events.
     */
    public SseEmitter createEmitter(String fixtureId) {
        // 0L means "infinite timeout". Keep the connection open forever!
        SseEmitter emitter = new SseEmitter(0L);
        EmitterEntry entry = new EmitterEntry(emitter, fixtureId);
        this.emitters.add(entry);

        try {
            // Send a connection confirmation using a distinct event name so it doesn't
            // interfere with the "message" events that carry actual data
            emitter.send(SseEmitter.event().name("connected").data("{\"status\":\"connected\"}"));
            log.info("New SSE connection created successfully (fixtureId={})", fixtureId);
        } catch (IOException e) {
            log.error("Error creating new SSE connection", e);
            emitter.completeWithError(e);
            this.emitters.remove(entry);
        }

        // When a client closes their browser, we remove them from the list
        emitter.onCompletion(() -> {
            log.debug("SSE connection completed");
            this.emitters.remove(entry);
        });
        emitter.onTimeout(() -> {
            log.debug("SSE connection timed out");
            emitter.complete();
            this.emitters.remove(entry);
        });
        emitter.onError((Throwable e) -> {
            log.error("SSE connection error", e);
            emitter.completeWithError(e != null ? e : new RuntimeException("Unknown SSE error"));
            this.emitters.remove(entry);
        });

        return emitter;
    }

    /**
     * Backward-compatible overload: creates an emitter that receives all events.
     */
    public SseEmitter createEmitter() {
        return createEmitter(null);
    }

    public void broadcast(EventDto eventDto) {
        if (eventDto == null) {
            log.warn("Attempted to broadcast a null eventDto");
            return;
        }

        List<EmitterEntry> deadEntries = new ArrayList<>();

        this.emitters.forEach(entry -> {
            // Only send to emitters whose fixtureId matches, or that are subscribed to all events
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
     * Provides read access to current emitters for testing/monitoring purposes.
     */
    List<EmitterEntry> getEmitters() {
        return emitters;
    }
}
