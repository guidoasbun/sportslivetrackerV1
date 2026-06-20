package live.gameshift.api.service;

import live.gameshift.api.dto.EventDto;
import live.gameshift.api.model.enums.SportType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
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
        SseEmitter emitter = sseEmitterService.createEmitter();
        
        assertNotNull(emitter);
        
        // Verify it was added to the internal thread-safe list
        @SuppressWarnings("unchecked")
        List<SseEmitter> emitters = (List<SseEmitter>) ReflectionTestUtils.getField(sseEmitterService, "emitters");
        assertEquals(1, emitters.size());
    }

    @Test
    void broadcast_shouldExecuteWithoutExceptions() {
        // Create an emitter
        sseEmitterService.createEmitter();
        
        // We can't easily simulate a broken network pipe here because SseEmitter is instantiated 
        // directly inside the service, but we can ensure broadcasting doesn't crash the app.
        EventDto dto = new EventDto("1", SportType.SOCCER, "goal", null, 1000L);
        
        // This should run without throwing any exceptions
        sseEmitterService.broadcast(dto);
    }
}
