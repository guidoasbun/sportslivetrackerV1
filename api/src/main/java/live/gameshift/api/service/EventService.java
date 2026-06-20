package live.gameshift.api.service;

import live.gameshift.api.dto.EventDto;
import live.gameshift.api.model.Event;
import live.gameshift.api.model.enums.SportType;
import live.gameshift.api.repository.EventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class EventService {
    
    private static final Logger log = LoggerFactory.getLogger(EventService.class);
    private final EventRepository eventRepository;
    private final SseEmitterService sseEmitterService;
    
    private Long lastPollTime;

    public EventService(EventRepository eventRepository, SseEmitterService sseEmitterService) {
        this.eventRepository = eventRepository;
        this.sseEmitterService = sseEmitterService;
        // Start polling from the exact moment the server starts
        this.lastPollTime = Instant.now().toEpochMilli();
    }

    // Tells Spring to run this method automatically every 5,000 milliseconds
    @Scheduled(fixedRate = 5000)
    public void pollForNewEvents() {
        Long currentPollTime = Instant.now().toEpochMilli();
        
        for (SportType sportType : SportType.values()) {
            List<Event> recentEvents = eventRepository.findRecentEvents(sportType, lastPollTime);
            
            for (Event event : recentEvents) {
                // Map the Database Entity to our decoupled DTO
                EventDto dto = new EventDto(
                        event.getEventId(),
                        event.getSportType(),
                        event.getAction(),
                        event.getParticipants(),
                        event.getEventTimestamp()
                );
                
                log.info("Broadcasting new event: {} - {}", sportType, event.getAction());
                sseEmitterService.broadcast(dto);
            }
        }
        
        // Move the window forward so we don't broadcast the same events twice!
        lastPollTime = currentPollTime;
    }
}
