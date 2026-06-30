package live.gameshift.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.KinesisEvent;
import com.amazonaws.services.lambda.runtime.events.KinesisEvent.KinesisEventRecord;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import live.gameshift.lambda.model.Event;
import live.gameshift.lambda.model.Summary;
import live.gameshift.lambda.model.enums.SportType;
import live.gameshift.lambda.repository.EventRepository;
import live.gameshift.lambda.repository.SummaryRepository;
import live.gameshift.lambda.service.BedrockCommentaryService;
import live.gameshift.lambda.service.DynamoDbService;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.mockito.Mockito.*;

/**
 * Property-based test for Lambda batch independence.
 *
 * Property 6: Lambda batch independence — generate batches with N records
 * (K malformed at arbitrary positions), verify exactly (N-K) events persisted.
 *
 * Validates: Requirements 6.7, 6.8
 */
class SportEventHandlerPropertyTest {

    private static final SportType[] SPORT_TYPES = SportType.values();
    private static final String[] ACTIONS = {"Goal", "Foul", "Penalty", "Substitution", "Timeout", "Touchdown"};
    private static final String[] MALFORMED_PAYLOADS = {
            "not json{{{",
            "{broken",
            "}{}{}{",
            "",
            "null",
            "{\"eventId\": }",
            "{{{{",
            "<xml>not json</xml>",
            "\"just a string\"",
            "{\"eventId\": \"x\", \"sportType\": \"INVALID_SPORT\"}"
    };

    /**
     * Property 6: Lambda batch independence
     *
     * For any Kinesis batch containing N records where K records have malformed JSON
     * (at arbitrary positions in the batch), the Lambda handler SHALL successfully
     * process and persist exactly (N-K) events to DynamoDB, independent of the
     * ordering of valid and malformed records.
     *
     * Validates: Requirements 6.7, 6.8
     */
    @Property(tries = 100)
    void batchIndependence_exactlyNMinusKEventsPersisted(
            @ForAll("batchWithMalformedRecords") BatchInput batchInput) {

        // Setup mocks
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.registerModule(new JavaTimeModule());

        DynamoDbService dynamoDbService = mock(DynamoDbService.class);
        EventRepository eventRepository = mock(EventRepository.class);
        SummaryRepository summaryRepository = mock(SummaryRepository.class);
        BedrockCommentaryService bedrockService = mock(BedrockCommentaryService.class);
        Context context = mock(Context.class);
        LambdaLogger logger = mock(LambdaLogger.class);

        when(dynamoDbService.getEventRepository()).thenReturn(eventRepository);
        when(dynamoDbService.getSummaryRepository()).thenReturn(summaryRepository);
        when(bedrockService.generateCommentary(any())).thenReturn("Test commentary");
        when(context.getLogger()).thenReturn(logger);

        // Create the handler under test
        SportEventHandler handler = new SportEventHandler(objectMapper, dynamoDbService, bedrockService);

        // Build KinesisEvent from batch input
        KinesisEvent kinesisEvent = buildKinesisEvent(batchInput.records);

        // Execute
        handler.handleRequest(kinesisEvent, context);

        // Verify: exactly (N - K) events persisted
        int expectedPersisted = batchInput.totalRecords - batchInput.malformedCount;
        verify(eventRepository, times(expectedPersisted)).save(any(Event.class));
        verify(summaryRepository, times(expectedPersisted)).save(any(Summary.class));
    }

    @Provide
    Arbitrary<BatchInput> batchWithMalformedRecords() {
        return Arbitraries.integers().between(1, 20).flatMap(totalRecords ->
                Arbitraries.integers().between(0, totalRecords).flatMap(malformedCount ->
                        generateBatch(totalRecords, malformedCount)
                )
        );
    }

    private Arbitrary<BatchInput> generateBatch(int totalRecords, int malformedCount) {
        // Generate positions for malformed records
        return Arbitraries.of(generateAllPositionCombinations(totalRecords, malformedCount))
                .flatMap(malformedPositions ->
                        generateRecords(totalRecords, malformedPositions).map(records ->
                                new BatchInput(records, totalRecords, malformedCount)
                        )
                );
    }

    private List<Set<Integer>> generateAllPositionCombinations(int totalRecords, int malformedCount) {
        // Generate a representative set of position combinations
        List<Set<Integer>> combinations = new ArrayList<>();
        Random random = new Random();

        // Generate several random position combinations
        for (int i = 0; i < Math.min(10, totalRecords); i++) {
            Set<Integer> positions = new HashSet<>();
            List<Integer> available = IntStream.range(0, totalRecords).boxed().collect(Collectors.toList());
            Collections.shuffle(available, random);
            for (int j = 0; j < malformedCount && j < available.size(); j++) {
                positions.add(available.get(j));
            }
            combinations.add(positions);
        }

        if (combinations.isEmpty()) {
            combinations.add(new HashSet<>());
        }
        return combinations;
    }

    private Arbitrary<List<String>> generateRecords(int totalRecords, Set<Integer> malformedPositions) {
        List<Arbitrary<String>> recordArbitraries = new ArrayList<>();

        for (int i = 0; i < totalRecords; i++) {
            if (malformedPositions.contains(i)) {
                recordArbitraries.add(Arbitraries.of(MALFORMED_PAYLOADS));
            } else {
                recordArbitraries.add(validSportEventJson(i));
            }
        }

        return Combinators.combine(recordArbitraries).as(list -> list);
    }

    private Arbitrary<String> validSportEventJson(int index) {
        return Combinators.combine(
                Arbitraries.of(SPORT_TYPES),
                Arbitraries.of(ACTIONS),
                Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(15),
                Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(15),
                Arbitraries.longs().between(1_000_000_000L, 2_000_000_000L)
        ).as((sportType, action, homeTeam, awayTeam, timestamp) ->
                String.format(
                        "{\"eventId\":\"evt-%d-%d\",\"sportType\":\"%s\",\"action\":\"%s\"," +
                                "\"participants\":{\"home\":\"%s\",\"away\":\"%s\"}," +
                                "\"rawPayload\":\"{}\",\"eventTimestamp\":%d}",
                        index, System.nanoTime(), sportType.name(), action,
                        homeTeam, awayTeam, timestamp
                )
        );
    }

    private KinesisEvent buildKinesisEvent(List<String> payloads) {
        KinesisEvent kinesisEvent = new KinesisEvent();
        List<KinesisEventRecord> records = new ArrayList<>();

        for (String payload : payloads) {
            KinesisEventRecord record = new KinesisEventRecord();
            KinesisEvent.Record kinesisRecord = new KinesisEvent.Record();
            kinesisRecord.setData(ByteBuffer.wrap(payload.getBytes(StandardCharsets.UTF_8)));
            record.setKinesis(kinesisRecord);
            records.add(record);
        }

        kinesisEvent.setRecords(records);
        return kinesisEvent;
    }

    /**
     * Holds the generated batch input data for test assertions.
     */
    static class BatchInput {
        final List<String> records;
        final int totalRecords;
        final int malformedCount;

        BatchInput(List<String> records, int totalRecords, int malformedCount) {
            this.records = records;
            this.totalRecords = totalRecords;
            this.malformedCount = malformedCount;
        }

        @Override
        public String toString() {
            return String.format("BatchInput{total=%d, malformed=%d, records=%s}",
                    totalRecords, malformedCount, records);
        }
    }
}
