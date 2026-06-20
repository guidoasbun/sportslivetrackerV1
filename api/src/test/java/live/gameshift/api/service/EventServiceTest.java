package live.gameshift.api.service;

import live.gameshift.api.dto.EventDto;
import live.gameshift.api.model.Event;
import live.gameshift.api.model.enums.SportType;
import live.gameshift.api.repository.EventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private SseEmitterService sseEmitterService;

    @Captor
    private ArgumentCaptor<EventDto> eventDtoCaptor;

    private EventService eventService;

    @BeforeEach
    void setUp() {
        eventService = new EventService(eventRepository, sseEmitterService);
        // Reset lastPollTime to a known value for testing
        ReflectionTestUtils.setField(eventService, "lastPollTime", 1000L);
        ReflectionTestUtils.setField(eventService, "processedIdsAtLastPollTime", new HashSet<>(List.of("0")));
    }

    @Test
    void pollForNewEvents_shouldUpdateLastPollTime_whenNewEventsFound() {
        // Arrange
        Event event0 = new Event();
        event0.setEventId("0"); // Already processed!
        event0.setSportType(SportType.SOCCER);
        event0.setAction("start");
        event0.setParticipants(Map.of());
        event0.setEventTimestamp(1000L);

        Event event0b = new Event();
        event0b.setEventId("0b"); // New event at boundary!
        event0b.setSportType(SportType.SOCCER);
        event0b.setAction("foul");
        event0b.setParticipants(Map.of());
        event0b.setEventTimestamp(1000L);

        Event event1 = new Event();
        event1.setEventId("1");
        event1.setSportType(SportType.SOCCER);
        event1.setAction("goal");
        event1.setParticipants(Map.of("player", "Messi"));
        event1.setEventTimestamp(1500L); // Greater than 1000L

        Event event2 = new Event();
        event2.setEventId("2");
        event2.setSportType(SportType.SOCCER);
        event2.setAction("card");
        event2.setParticipants(Map.of("player", "Ronaldo"));
        event2.setEventTimestamp(2000L); // Max timestamp

        when(eventRepository.findRecentEvents(eq(SportType.SOCCER), eq(1000L)))
                .thenReturn(List.of(event0, event0b, event1, event2));
        
        // Mock empty for others
        for (SportType type : SportType.values()) {
            if (type != SportType.SOCCER) {
                when(eventRepository.findRecentEvents(eq(type), eq(1000L))).thenReturn(Collections.emptyList());
            }
        }

        // Act
        eventService.pollForNewEvents();

        // Assert (Should broadcast 0b, 1, and 2. It should skip 0.)
        verify(sseEmitterService, times(3)).broadcast(eventDtoCaptor.capture());
        
        List<EventDto> broadcasted = eventDtoCaptor.getAllValues();
        assertEquals("0b", broadcasted.get(0).eventId());
        assertEquals("1", broadcasted.get(1).eventId());
        assertEquals("2", broadcasted.get(2).eventId());
        
        // Verify window moved to the max timestamp seen
        Long updatedPollTime = (Long) ReflectionTestUtils.getField(eventService, "lastPollTime");
        assertEquals(2000L, updatedPollTime);
        
        // Verify new processed IDs set
        @SuppressWarnings("unchecked")
        Set<String> processedIds = (Set<String>) ReflectionTestUtils.getField(eventService, "processedIdsAtLastPollTime");
        assertEquals(1, processedIds.size());
        assertEquals("2", processedIds.iterator().next());
    }
    
    @Test
    void pollForNewEvents_shouldNotUpdateLastPollTime_whenNoNewEvents() {
        // Arrange
        for (SportType type : SportType.values()) {
            when(eventRepository.findRecentEvents(eq(type), eq(1000L))).thenReturn(Collections.emptyList());
        }

        // Act
        eventService.pollForNewEvents();

        // Assert
        verify(sseEmitterService, never()).broadcast(any());
        
        // Verify window did not move
        Long updatedPollTime = (Long) ReflectionTestUtils.getField(eventService, "lastPollTime");
        assertEquals(1000L, updatedPollTime);
    }
}
