package live.gameshift.api.service;

import live.gameshift.api.dto.EventDto;
import live.gameshift.api.model.enums.SportType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SseEmitterServiceTest {

    private SseEmitterService sseEmitterService;

    @BeforeEach
    void setUp() {
        sseEmitterService = new SseEmitterService();
    }

    @Test
    void createEmitter_shouldReturnValidEmitterAndAddToTracker() {
        SseEmitter emitter = sseEmitterService.createEmitter(null);
        
        assertNotNull(emitter);
        
        // Verify it was added to the internal list
        List<SseEmitterService.EmitterEntry> entries = sseEmitterService.getEmitters();
        assertEquals(1, entries.size());
    }

    @Test
    void createEmitter_withFixtureId_shouldStoreMetadata() {
        SseEmitter emitter = sseEmitterService.createEmitter("fixture-123");
        
        assertNotNull(emitter);
        
        List<SseEmitterService.EmitterEntry> entries = sseEmitterService.getEmitters();
        assertEquals(1, entries.size());
        assertEquals("fixture-123", entries.get(0).fixtureId());
    }

    @Test
    void createEmitter_noArgs_shouldSubscribeToAllEvents() {
        SseEmitter emitter = sseEmitterService.createEmitter();
        
        assertNotNull(emitter);
        
        List<SseEmitterService.EmitterEntry> entries = sseEmitterService.getEmitters();
        assertEquals(1, entries.size());
        // null fixtureId means subscribed to all events
        assertEquals(null, entries.get(0).fixtureId());
    }

    @Test
    void broadcast_shouldExecuteWithoutExceptions() {
        // Create an emitter subscribed to all events
        sseEmitterService.createEmitter(null);
        
        EventDto dto = new EventDto("1", SportType.SOCCER, "goal", null, 1000L, "fixture-1");
        
        // This should run without throwing any exceptions
        sseEmitterService.broadcast(dto);
    }

    @Test
    void broadcast_shouldNotSendToMismatchedFixtureEmitter() {
        // Create an emitter subscribed only to fixture-999
        sseEmitterService.createEmitter("fixture-999");
        
        // Broadcast an event for fixture-1 — should not crash or fail
        EventDto dto = new EventDto("1", SportType.SOCCER, "goal", null, 1000L, "fixture-1");
        sseEmitterService.broadcast(dto);
        
        // Emitter should still be in the list (not removed due to error)
        assertEquals(1, sseEmitterService.getEmitters().size());
    }

    @Test
    void broadcast_nullEventDto_shouldNotThrow() {
        sseEmitterService.createEmitter(null);
        
        // Should handle gracefully
        sseEmitterService.broadcast(null);
    }
}
