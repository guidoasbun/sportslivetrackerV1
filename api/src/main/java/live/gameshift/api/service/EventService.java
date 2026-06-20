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

import java.util.HashSet;
import java.util.Set;

@Service
public class EventService {
    
    private static final Logger log = LoggerFactory.getLogger(EventService.class);
    private final EventRepository eventRepository;
    private final SseEmitterService sseEmitterService;
    
    private Long lastPollTime;
    private Set<String> processedIdsAtLastPollTime = new HashSet<>();

    public EventService(EventRepository eventRepository, SseEmitterService sseEmitterService) {
        this.eventRepository = eventRepository;
        this.sseEmitterService = sseEmitterService;
        // Start polling from the exact moment the server starts
        this.lastPollTime = Instant.now().toEpochMilli();
    }

    // Tells Spring to run this method automatically every 5,000 milliseconds
    @Scheduled(fixedRate = 5000)
    public void pollForNewEvents() {
        Long newMaxTime = lastPollTime;
        Set<String> newMaxIds = new HashSet<>();
        
        for (SportType sportType : SportType.values()) {
            List<Event> recentEvents = eventRepository.findRecentEvents(sportType, lastPollTime);
            
            for (Event event : recentEvents) {
                // 1. If it's at the exact boundary and we already processed it, skip duplicate
                if (event.getEventTimestamp().equals(lastPollTime) && processedIdsAtLastPollTime.contains(event.getEventId())) {
                    continue;
                }

                // 2. Track the highest timestamp we've successfully processed
                if (event.getEventTimestamp() > newMaxTime) {
                    newMaxTime = event.getEventTimestamp();
                    newMaxIds.clear();
                    newMaxIds.add(event.getEventId());
                } else if (event.getEventTimestamp().equals(newMaxTime)) {
                    newMaxIds.add(event.getEventId());
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
        
        // 3. Move the window forward safely
        if (newMaxTime > lastPollTime) {
            lastPollTime = newMaxTime;
            processedIdsAtLastPollTime = newMaxIds;
        } else if (newMaxTime.equals(lastPollTime)) {
            // We found more events at the exact same boundary timestamp
            processedIdsAtLastPollTime.addAll(newMaxIds);
        }
    }
}
