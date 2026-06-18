package live.gameshift.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.KinesisEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import live.gameshift.lambda.model.Event;
import live.gameshift.lambda.model.SportEvent;
import live.gameshift.lambda.model.Summary;
import live.gameshift.lambda.service.BedrockCommentaryService;
import live.gameshift.lambda.service.DynamoDbService;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

public class SportEventHandler implements RequestHandler<KinesisEvent, Void> {

    private final ObjectMapper objectMapper;
    private final DynamoDbService dynamoDbService;
    private final BedrockCommentaryService bedrockService;

    public SportEventHandler() {
        // This constructor is called exactly once when AWS spins up the container
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        // Grab environment variables injected securely by Terraform
        String eventTableName = System.getenv("EVENTS_TABLE");
        String summaryTableName = System.getenv("SUMMARIES_TABLE");

        this.dynamoDbService = new DynamoDbService(eventTableName, summaryTableName);
        this.bedrockService = new BedrockCommentaryService(objectMapper);
    }

    @Override
    public Void handleRequest(KinesisEvent kinesisEvent, Context context) {

        // A single Kinesis event can contain an array of multiple records (batching)
        for (KinesisEvent.KinesisEventRecord record : kinesisEvent.getRecords()) {
            try {
                // 1. Kinesis payloads are Base64 encoded. We must decode them into a JSON
                // String.
                String payload = new String(record.getKinesis().getData().array(), StandardCharsets.UTF_8);
                context.getLogger().log("Received Kinesis Payload: " + payload);

                // 2. Deserialize JSON into our Java DTO
                SportEvent sportEvent = objectMapper.readValue(payload, SportEvent.class);

                // 3. Convert DTO to DynamoDB Entity and Save
                Event event = new Event();
                event.setEventId(sportEvent.getEventId());
                event.setSportType(sportEvent.getSportType());
                event.setAction(sportEvent.getAction());
                event.setParticipants(sportEvent.getParticipants());
                event.setRawPayload(sportEvent.getRawPayload());
                event.setTimestamp(sportEvent.getTimestamp());

                dynamoDbService.getEventRepository().save(event);
                context.getLogger().log("Saved Event to DynamoDB: " + event.getEventId());

                // 4. Ask Amazon Bedrock for AI Commentary
                String commentary = bedrockService.generateCommentary(sportEvent);

                // 5. Save the AI Commentary to the Summaries table
                Summary summary = new Summary();
                summary.setEventId(sportEvent.getEventId());
                summary.setSportType(sportEvent.getSportType());
                summary.setCommentary(commentary);
                summary.setTimestamp(Instant.now());

                dynamoDbService.getSummaryRepository().save(summary);
                context.getLogger().log("Saved Summary to DynamoDB: " + summary.getEventId());

            } catch (Exception e) {
                // We log the error but let the loop continue processing other records
                context.getLogger().log("ERROR processing record: " + e.getMessage());
            }
        }

        return null;
    }
}
