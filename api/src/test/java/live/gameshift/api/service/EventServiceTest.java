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
import java.util.List;
import java.util.Map;

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
    }

    @Test
    void pollForNewEvents_shouldUpdateLastPollTime_whenNewEventsFound() {
        // Arrange
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
                .thenReturn(List.of(event1, event2));
        
        // Mock empty for others
        for (SportType type : SportType.values()) {
            if (type != SportType.SOCCER) {
                when(eventRepository.findRecentEvents(eq(type), eq(1000L))).thenReturn(Collections.emptyList());
            }
        }

        // Act
        eventService.pollForNewEvents();

        // Assert
        verify(sseEmitterService, times(2)).broadcast(eventDtoCaptor.capture());
        
        List<EventDto> broadcasted = eventDtoCaptor.getAllValues();
        assertEquals("1", broadcasted.get(0).eventId());
        assertEquals("2", broadcasted.get(1).eventId());
        
        // Verify window moved to the max timestamp seen
        Long updatedPollTime = (Long) ReflectionTestUtils.getField(eventService, "lastPollTime");
        assertEquals(2000L, updatedPollTime);
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

    @Test
    void pollForNewEvents_shouldBroadcastLateEventsWithSameTimestampWithoutDuplicates() {
        Event first = new Event();
        first.setEventId("1");
        first.setSportType(SportType.SOCCER);
        first.setAction("goal");
        first.setParticipants(Map.of("player", "Messi"));
        first.setEventTimestamp(1000L);

        Event lateArrival = new Event();
        lateArrival.setEventId("2");
        lateArrival.setSportType(SportType.SOCCER);
        lateArrival.setAction("goal");
        lateArrival.setParticipants(Map.of("player", "Ronaldo"));
        lateArrival.setEventTimestamp(1000L);

        when(eventRepository.findRecentEvents(eq(SportType.SOCCER), eq(1000L)))
                .thenReturn(List.of(first))
                .thenReturn(List.of(first, lateArrival));

        for (SportType type : SportType.values()) {
            if (type != SportType.SOCCER) {
                when(eventRepository.findRecentEvents(eq(type), eq(1000L))).thenReturn(Collections.emptyList());
            }
        }

        eventService.pollForNewEvents();
        eventService.pollForNewEvents();

        verify(sseEmitterService, times(2)).broadcast(eventDtoCaptor.capture());
        List<EventDto> broadcasted = eventDtoCaptor.getAllValues();
        assertEquals("1", broadcasted.get(0).eventId());
        assertEquals("2", broadcasted.get(1).eventId());
        assertEquals(1, broadcasted.stream().filter(dto -> "1".equals(dto.eventId())).count());

        Long updatedPollTime = (Long) ReflectionTestUtils.getField(eventService, "lastPollTime");
        assertEquals(1000L, updatedPollTime);
    }
}
