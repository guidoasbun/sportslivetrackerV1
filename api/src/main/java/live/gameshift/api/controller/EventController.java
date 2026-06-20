package live.gameshift.api.controller;

import live.gameshift.api.service.SseEmitterService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final SseEmitterService sseEmitterService;

    public EventController(SseEmitterService sseEmitterService) {
        this.sseEmitterService = sseEmitterService;
    }

    // Notice the "produces" type! This tells Spring not to close the HTTP connection
    // and instead format the response as a continuous stream of events.
    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamEvents() {
        return sseEmitterService.createEmitter();
    }
}
