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
    
    // Thread-safe list because clients can connect/disconnect at any time
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter createEmitter() {
        // 0L means "infinite timeout". Keep the connection open forever!
        SseEmitter emitter = new SseEmitter(0L);
        this.emitters.add(emitter);

        try {
            // Send a welcome message so you can see it in Postman immediately!
            emitter.send(SseEmitter.event().name("message").data("Welcome to SportsLiveTracker API! Your SSE connection is successful."));
        } catch (IOException e) {
            this.emitters.remove(emitter);
        }

        // When a client closes their browser, we remove them from the list
        emitter.onCompletion(() -> this.emitters.remove(emitter));
        emitter.onTimeout(() -> {
            emitter.complete();
            this.emitters.remove(emitter);
        });
        emitter.onError((e) -> {
            emitter.completeWithError(e);
            this.emitters.remove(emitter);
        });

        return emitter;
    }

    public void broadcast(EventDto eventDto) {
        List<SseEmitter> deadEmitters = new ArrayList<>();
        
        this.emitters.forEach(emitter -> {
            try {
                // Spring automatically converts our EventDto into a JSON string!
                emitter.send(SseEmitter.event().name("message").data(eventDto));
            } catch (IOException e) {
                // If we get an exception, the client probably disconnected abruptly
                deadEmitters.add(emitter);
            }
        });
        
        // Clean up dead connections so we don't leak memory
        this.emitters.removeAll(deadEmitters);
    }
}
