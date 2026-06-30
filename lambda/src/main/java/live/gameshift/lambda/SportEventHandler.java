package live.gameshift.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.KinesisEvent;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import live.gameshift.lambda.model.Event;
import live.gameshift.lambda.model.SportEvent;
import live.gameshift.lambda.model.Summary;
import live.gameshift.lambda.service.BedrockCommentaryService;
import live.gameshift.lambda.service.DynamoDbService;

import java.nio.charset.StandardCharsets;

public class SportEventHandler implements RequestHandler<KinesisEvent, Void> {

    private final ObjectMapper objectMapper;
    private final DynamoDbService dynamoDbService;
    private final BedrockCommentaryService bedrockService;

    /**
     * Default constructor — called by AWS Lambda runtime exactly once per container.
     */
    public SportEventHandler() {
        this.objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.registerModule(new JavaTimeModule());

        String eventTableName = System.getenv("EVENTS_TABLE_NAME");
        String summaryTableName = System.getenv("SUMMARIES_TABLE_NAME");

        this.dynamoDbService = new DynamoDbService(eventTableName, summaryTableName);
        this.bedrockService = new BedrockCommentaryService(objectMapper);
    }

    /**
     * Test-visible constructor for dependency injection (mocking DynamoDb and Bedrock).
     */
    public SportEventHandler(ObjectMapper objectMapper, DynamoDbService dynamoDbService, BedrockCommentaryService bedrockService) {
        this.objectMapper = objectMapper;
        this.dynamoDbService = dynamoDbService;
        this.bedrockService = bedrockService;
    }

    @Override
    public Void handleRequest(KinesisEvent kinesisEvent, Context context) {

        for (KinesisEvent.KinesisEventRecord record : kinesisEvent.getRecords()) {
            try {
                // 1. Decode Kinesis payload from Base64
                String payload = new String(record.getKinesis().getData().array(), StandardCharsets.UTF_8);
                context.getLogger().log("Processing event from Kinesis (length=" + payload.length() + ")");

                // 2. Deserialize JSON into SportEvent DTO
                SportEvent sportEvent = objectMapper.readValue(payload, SportEvent.class);
                context.getLogger().log("Deserialized event: id=" + sportEvent.getEventId() + " sport=" + sportEvent.getSportType());

                // 3. Persist event to DynamoDB (always, even if Bedrock fails)
                Event event = new Event();
                event.setEventId(sportEvent.getEventId());
                event.setSportType(sportEvent.getSportType());
                event.setAction(sportEvent.getAction());
                event.setParticipants(sportEvent.getParticipants());
                event.setRawPayload(sportEvent.getRawPayload());
                event.setEventTimestamp(sportEvent.getEventTimestamp());
                // TTL: auto-delete after 7 days (DynamoDB TTL uses epoch seconds)
                event.setTtl(System.currentTimeMillis() / 1000 + (7 * 24 * 60 * 60));

                dynamoDbService.getEventRepository().save(event);
                context.getLogger().log("Saved Event to DynamoDB: " + event.getEventId());

                // 4. Generate AI commentary (fallback on failure)
                String commentary = bedrockService.generateCommentary(sportEvent);

                // 5. Persist commentary to Summaries table
                Summary summary = new Summary();
                summary.setSummaryId(java.util.UUID.randomUUID().toString());
                summary.setEventId(sportEvent.getEventId());
                summary.setSportType(sportEvent.getSportType());
                summary.setCommentary(commentary);
                summary.setTimestamp(System.currentTimeMillis());
                // TTL: auto-delete after 7 days
                summary.setTtl(System.currentTimeMillis() / 1000 + (7 * 24 * 60 * 60));

                dynamoDbService.getSummaryRepository().save(summary);
                context.getLogger().log("Saved Summary for event: " + summary.getEventId());

            } catch (Exception e) {
                // Log error and continue processing remaining records in the batch
                context.getLogger().log("ERROR processing record: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            }
        }

        return null;
    }
}
