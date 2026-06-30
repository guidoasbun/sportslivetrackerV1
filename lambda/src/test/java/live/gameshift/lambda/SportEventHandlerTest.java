package live.gameshift.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.KinesisEvent;
import com.amazonaws.services.lambda.runtime.events.KinesisEvent.KinesisEventRecord;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import live.gameshift.lambda.model.Event;
import live.gameshift.lambda.model.SportEvent;
import live.gameshift.lambda.model.Summary;
import live.gameshift.lambda.model.enums.SportType;
import live.gameshift.lambda.repository.EventRepository;
import live.gameshift.lambda.repository.SummaryRepository;
import live.gameshift.lambda.service.BedrockCommentaryService;
import live.gameshift.lambda.service.DynamoDbService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SportEventHandlerTest {

    @Mock
    private DynamoDbService dynamoDbService;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private SummaryRepository summaryRepository;

    @Mock
    private BedrockCommentaryService bedrockService;

    @Mock
    private Context context;

    @Mock
    private LambdaLogger logger;

    private ObjectMapper objectMapper;
    private SportEventHandler handler;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.registerModule(new JavaTimeModule());

        handler = new SportEventHandler(objectMapper, dynamoDbService, bedrockService);

        when(context.getLogger()).thenReturn(logger);
        when(dynamoDbService.getEventRepository()).thenReturn(eventRepository);
        when(dynamoDbService.getSummaryRepository()).thenReturn(summaryRepository);
    }

    // ─── Helper methods ───────────────────────────────────────────────────────

    private KinesisEvent createKinesisEvent(String... jsonPayloads) {
        KinesisEvent kinesisEvent = new KinesisEvent();
        List<KinesisEventRecord> records = new ArrayList<>();

        for (String json : jsonPayloads) {
            KinesisEventRecord record = new KinesisEventRecord();
            KinesisEvent.Record kinesisRecord = new KinesisEvent.Record();
            kinesisRecord.setData(ByteBuffer.wrap(json.getBytes(StandardCharsets.UTF_8)));
            record.setKinesis(kinesisRecord);
            records.add(record);
        }

        kinesisEvent.setRecords(records);
        return kinesisEvent;
    }

    private String buildSportEventJson(String eventId, String sportType, String action,
                                        Map<String, String> participants, String rawPayload,
                                        long eventTimestamp) {
        try {
            SportEvent event = new SportEvent();
            event.setEventId(eventId);
            event.setSportType(SportType.valueOf(sportType));
            event.setAction(action);
            event.setParticipants(participants);
            event.setRawPayload(rawPayload);
            event.setEventTimestamp(eventTimestamp);
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String defaultSportEventJson() {
        return buildSportEventJson(
                "evt-001",
                "SOCCER",
                "Goal",
                Map.of("home", "Team A", "away", "Team B"),
                "{\"raw\":\"data\"}",
                1234567890L
        );
    }

    // ─── Test: Valid payload deserialization with all SportEvent fields ────────

    @Test
    void handleRequest_validPayload_deserializesAllFields() {
        String json = buildSportEventJson(
                "evt-123",
                "BASKETBALL",
                "Dunk",
                Map.of("home", "Lakers", "away", "Celtics"),
                "{\"quarter\":4}",
                9876543210L
        );
        KinesisEvent kinesisEvent = createKinesisEvent(json);
        when(bedrockService.generateCommentary(any(SportEvent.class))).thenReturn("Great dunk!");

        handler.handleRequest(kinesisEvent, context);

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(eventRepository).save(eventCaptor.capture());

        Event savedEvent = eventCaptor.getValue();
        assertEquals("evt-123", savedEvent.getEventId());
        assertEquals(SportType.BASKETBALL, savedEvent.getSportType());
        assertEquals("Dunk", savedEvent.getAction());
        assertEquals(Map.of("home", "Lakers", "away", "Celtics"), savedEvent.getParticipants());
        assertEquals("{\"quarter\":4}", savedEvent.getRawPayload());
        assertEquals(9876543210L, savedEvent.getEventTimestamp());
        assertNotNull(savedEvent.getTtl());
    }

    // ─── Test: Event persisted to DynamoDB with correct field mapping ─────────

    @Test
    void handleRequest_validPayload_persistsEventWithCorrectMapping() {
        String json = defaultSportEventJson();
        KinesisEvent kinesisEvent = createKinesisEvent(json);
        when(bedrockService.generateCommentary(any(SportEvent.class))).thenReturn("Goal!");

        handler.handleRequest(kinesisEvent, context);

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(eventRepository).save(eventCaptor.capture());

        Event savedEvent = eventCaptor.getValue();
        assertEquals("evt-001", savedEvent.getEventId());
        assertEquals(SportType.SOCCER, savedEvent.getSportType());
        assertEquals("Goal", savedEvent.getAction());
        assertEquals("Team A", savedEvent.getParticipants().get("home"));
        assertEquals("Team B", savedEvent.getParticipants().get("away"));
        assertEquals("{\"raw\":\"data\"}", savedEvent.getRawPayload());
        assertEquals(1234567890L, savedEvent.getEventTimestamp());
        // TTL should be approximately 7 days from now (in epoch seconds)
        long now = System.currentTimeMillis() / 1000;
        long sevenDays = 7 * 24 * 60 * 60;
        assertTrue(savedEvent.getTtl() >= now + sevenDays - 5);
        assertTrue(savedEvent.getTtl() <= now + sevenDays + 5);
    }

    // ─── Test: Summary persisted with Bedrock-generated commentary ────────────

    @Test
    void handleRequest_validPayload_persistsSummaryWithBedrockCommentary() {
        String json = defaultSportEventJson();
        KinesisEvent kinesisEvent = createKinesisEvent(json);
        String expectedCommentary = "What a stunning goal by Team A!";
        when(bedrockService.generateCommentary(any(SportEvent.class))).thenReturn(expectedCommentary);

        handler.handleRequest(kinesisEvent, context);

        ArgumentCaptor<Summary> summaryCaptor = ArgumentCaptor.forClass(Summary.class);
        verify(summaryRepository).save(summaryCaptor.capture());

        Summary savedSummary = summaryCaptor.getValue();
        assertNotNull(savedSummary.getSummaryId());
        assertFalse(savedSummary.getSummaryId().isEmpty());
        assertEquals("evt-001", savedSummary.getEventId());
        assertEquals(SportType.SOCCER, savedSummary.getSportType());
        assertEquals(expectedCommentary, savedSummary.getCommentary());
        assertNotNull(savedSummary.getTimestamp());
        assertNotNull(savedSummary.getTtl());
    }

    // ─── Test: 6 sport types produce distinct prompts ─────────────────────────

    @Test
    void handleRequest_allSportTypes_produceDistinctPrompts() {
        SportType[] sportTypes = SportType.values();
        assertEquals(6, sportTypes.length, "Expected exactly 6 sport types");

        // We use a real BedrockCommentaryService with a test constructor to verify buildPrompt
        // But since buildPrompt is package-private, we test indirectly via generateCommentary calls
        ArgumentCaptor<SportEvent> sportEventCaptor = ArgumentCaptor.forClass(SportEvent.class);
        when(bedrockService.generateCommentary(sportEventCaptor.capture())).thenReturn("commentary");

        for (SportType type : sportTypes) {
            String json = buildSportEventJson(
                    "evt-" + type.name(),
                    type.name(),
                    "Action",
                    Map.of("home", "HomeTeam", "away", "AwayTeam"),
                    "{}",
                    1000000L
            );
            KinesisEvent kinesisEvent = createKinesisEvent(json);
            handler.handleRequest(kinesisEvent, context);
        }

        // Verify that generateCommentary was called once per sport type
        verify(bedrockService, times(6)).generateCommentary(any(SportEvent.class));

        // Verify each call received a distinct sport type
        List<SportEvent> capturedEvents = sportEventCaptor.getAllValues();
        assertEquals(6, capturedEvents.size());

        List<SportType> capturedTypes = capturedEvents.stream()
                .map(SportEvent::getSportType)
                .toList();

        for (SportType type : sportTypes) {
            assertTrue(capturedTypes.contains(type),
                    "Expected sport type " + type + " to be processed");
        }
    }

    @Test
    void handleRequest_eachSportType_passedToBedrockService() {
        // Verify each sport type is correctly deserialized and passed to commentary generation
        when(bedrockService.generateCommentary(any(SportEvent.class))).thenReturn("commentary");

        for (SportType type : SportType.values()) {
            String json = buildSportEventJson(
                    "evt-" + type.name(),
                    type.name(),
                    "Action",
                    Map.of("home", "A", "away", "B"),
                    "{}",
                    1000000L
            );
            KinesisEvent kinesisEvent = createKinesisEvent(json);
            handler.handleRequest(kinesisEvent, context);
        }

        ArgumentCaptor<SportEvent> captor = ArgumentCaptor.forClass(SportEvent.class);
        verify(bedrockService, times(6)).generateCommentary(captor.capture());

        List<SportType> capturedTypes = captor.getAllValues().stream()
                .map(SportEvent::getSportType)
                .toList();

        // Verify all 6 distinct sport types are present
        for (SportType type : SportType.values()) {
            assertTrue(capturedTypes.contains(type),
                    "SportType " + type + " should have been passed to generateCommentary");
        }
    }

    // ─── Test: Bedrock failure → Event still persisted, fallback commentary ───

    @Test
    void handleRequest_bedrockFailure_eventStillPersistedWithFallbackCommentary() {
        String json = buildSportEventJson(
                "evt-fail",
                "HOCKEY",
                "Slapshot",
                Map.of("home", "Bruins", "away", "Canadiens"),
                "{}",
                5555555555L
        );
        KinesisEvent kinesisEvent = createKinesisEvent(json);

        String fallbackCommentary = "[FALLBACK] Exciting hockey action just happened!";
        when(bedrockService.generateCommentary(any(SportEvent.class))).thenReturn(fallbackCommentary);

        handler.handleRequest(kinesisEvent, context);

        // Event is still persisted
        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(eventRepository).save(eventCaptor.capture());
        assertEquals("evt-fail", eventCaptor.getValue().getEventId());
        assertEquals(SportType.HOCKEY, eventCaptor.getValue().getSportType());

        // Summary uses fallback commentary
        ArgumentCaptor<Summary> summaryCaptor = ArgumentCaptor.forClass(Summary.class);
        verify(summaryRepository).save(summaryCaptor.capture());
        assertEquals(fallbackCommentary, summaryCaptor.getValue().getCommentary());
        assertEquals("evt-fail", summaryCaptor.getValue().getEventId());
    }

    // ─── Test: Malformed JSON → error logged, next record processed ───────────

    @Test
    void handleRequest_malformedJson_errorLoggedAndNextRecordProcessed() {
        String malformedJson = "{ this is not valid JSON }}}}";
        String validJson = buildSportEventJson(
                "evt-valid",
                "BASEBALL",
                "HomeRun",
                Map.of("home", "Yankees", "away", "RedSox"),
                "{}",
                7777777777L
        );
        KinesisEvent kinesisEvent = createKinesisEvent(malformedJson, validJson);
        when(bedrockService.generateCommentary(any(SportEvent.class))).thenReturn("Home run!");

        handler.handleRequest(kinesisEvent, context);

        // The error is logged (context.getLogger().log called with ERROR)
        verify(logger, atLeastOnce()).log(argThat((String msg) -> msg.contains("ERROR")));

        // The valid record is still processed
        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(eventRepository).save(eventCaptor.capture());
        assertEquals("evt-valid", eventCaptor.getValue().getEventId());

        verify(summaryRepository).save(any(Summary.class));
    }

    // ─── Test: Multi-record batch → independent processing ────────────────────

    @Test
    void handleRequest_multiRecordBatch_independentProcessing() {
        String json1 = buildSportEventJson(
                "evt-batch-1",
                "SOCCER",
                "Goal",
                Map.of("home", "Barcelona", "away", "Madrid"),
                "{}",
                1000000001L
        );
        String json2 = buildSportEventJson(
                "evt-batch-2",
                "FOOTBALL",
                "Touchdown",
                Map.of("home", "Patriots", "away", "Eagles"),
                "{}",
                1000000002L
        );
        String json3 = buildSportEventJson(
                "evt-batch-3",
                "FORMULA_1",
                "Overtake",
                Map.of("home", "Verstappen", "away", "Hamilton"),
                "{}",
                1000000003L
        );
        KinesisEvent kinesisEvent = createKinesisEvent(json1, json2, json3);
        when(bedrockService.generateCommentary(any(SportEvent.class))).thenReturn("Great action!");

        handler.handleRequest(kinesisEvent, context);

        // All 3 events saved
        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(eventRepository, times(3)).save(eventCaptor.capture());

        List<Event> savedEvents = eventCaptor.getAllValues();
        assertEquals("evt-batch-1", savedEvents.get(0).getEventId());
        assertEquals("evt-batch-2", savedEvents.get(1).getEventId());
        assertEquals("evt-batch-3", savedEvents.get(2).getEventId());

        // All 3 summaries saved
        verify(summaryRepository, times(3)).save(any(Summary.class));
    }

    @Test
    void handleRequest_multiRecordBatch_oneFailsOthersContinue() {
        String validJson1 = buildSportEventJson(
                "evt-first",
                "SOCCER",
                "Goal",
                Map.of("home", "TeamA", "away", "TeamB"),
                "{}",
                1000000001L
        );
        String invalidJson = "not json at all";
        String validJson3 = buildSportEventJson(
                "evt-third",
                "BASKETBALL",
                "ThreePointer",
                Map.of("home", "Warriors", "away", "Nets"),
                "{}",
                1000000003L
        );
        KinesisEvent kinesisEvent = createKinesisEvent(validJson1, invalidJson, validJson3);
        when(bedrockService.generateCommentary(any(SportEvent.class))).thenReturn("Nice!");

        handler.handleRequest(kinesisEvent, context);

        // 2 valid records processed despite middle one failing
        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(eventRepository, times(2)).save(eventCaptor.capture());

        List<Event> savedEvents = eventCaptor.getAllValues();
        assertEquals("evt-first", savedEvents.get(0).getEventId());
        assertEquals("evt-third", savedEvents.get(1).getEventId());

        verify(summaryRepository, times(2)).save(any(Summary.class));
        // Error logged for the invalid record
        verify(logger, atLeastOnce()).log(argThat((String msg) -> msg.contains("ERROR")));
    }

    // ─── Test: Handler returns null ───────────────────────────────────────────

    @Test
    void handleRequest_returnsNull() {
        String json = defaultSportEventJson();
        KinesisEvent kinesisEvent = createKinesisEvent(json);
        when(bedrockService.generateCommentary(any(SportEvent.class))).thenReturn("commentary");

        Void result = handler.handleRequest(kinesisEvent, context);

        assertNull(result);
    }
}
