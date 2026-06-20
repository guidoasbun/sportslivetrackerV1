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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class EventService {
    
    private static final Logger log = LoggerFactory.getLogger(EventService.class);
    private final EventRepository eventRepository;
    private final SseEmitterService sseEmitterService;
    
    private Long lastPollTime;
    private Set<String> processedEventIdsAtLastPollTime;

    public EventService(EventRepository eventRepository, SseEmitterService sseEmitterService) {
        this.eventRepository = eventRepository;
        this.sseEmitterService = sseEmitterService;
        // Start polling from the exact moment the server starts
        this.lastPollTime = Instant.now().toEpochMilli();
        this.processedEventIdsAtLastPollTime = new HashSet<>();
    }

    // Tells Spring to run this method automatically every 5,000 milliseconds
    @Scheduled(fixedRate = 5000)
    public void pollForNewEvents() {
        Long pollLowerBound = lastPollTime;
        Set<String> alreadyProcessedAtLowerBound = processedEventIdsAtLastPollTime;
        Long maxEventTimeSeen = lastPollTime;
        Set<String> processedEventIdsAtMaxTimestamp = new HashSet<>();
        
        for (SportType sportType : SportType.values()) {
            List<Event> recentEvents = eventRepository.findRecentEvents(sportType, pollLowerBound);
            
            for (Event event : recentEvents) {
                if (event.getEventTimestamp().equals(pollLowerBound)
                        && alreadyProcessedAtLowerBound.contains(event.getEventId())) {
                    continue;
                }

                // Track the highest timestamp we've successfully processed
                if (event.getEventTimestamp() > maxEventTimeSeen) {
                    maxEventTimeSeen = event.getEventTimestamp();
                    processedEventIdsAtMaxTimestamp.clear();
                }
                if (event.getEventTimestamp().equals(maxEventTimeSeen)) {
                    processedEventIdsAtMaxTimestamp.add(event.getEventId());
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
        
        // Keep an inclusive lower bound while de-duping IDs we've already emitted
        // at the watermark timestamp. This allows late arrivals with the same timestamp.
        if (maxEventTimeSeen.equals(pollLowerBound)) {
            processedEventIdsAtMaxTimestamp.addAll(processedEventIdsAtLastPollTime);
        }
        lastPollTime = maxEventTimeSeen;
        processedEventIdsAtLastPollTime = processedEventIdsAtMaxTimestamp;
    }
}
