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
        Long maxEventTimeSeen = lastPollTime;
        
        for (SportType sportType : SportType.values()) {
            List<Event> recentEvents = eventRepository.findRecentEvents(sportType, lastPollTime);
            
            for (Event event : recentEvents) {
                // Track the highest timestamp we've successfully processed
                if (event.getEventTimestamp() > maxEventTimeSeen) {
                    maxEventTimeSeen = event.getEventTimestamp();
                }

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
        
        // Only move the window forward based on the data we ACTUALLY saw.
        // Combined with our exclusive lower bound (sortGreaterThan), this guarantees
        // we never miss delayed events and never broadcast duplicates!
        lastPollTime = maxEventTimeSeen;
    }
}
