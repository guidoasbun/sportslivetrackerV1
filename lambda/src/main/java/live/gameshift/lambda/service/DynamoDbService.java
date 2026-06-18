package live.gameshift.lambda.service;

import live.gameshift.lambda.repository.EventRepository;
import live.gameshift.lambda.repository.SummaryRepository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public class DynamoDbService {

    private final EventRepository eventRepository;
    private final SummaryRepository summaryRepository;

    public DynamoDbService(String eventTableName, String summaryTableName) {
        // 1. Initialize the standard AWS SDK v2 Client
        DynamoDbClient standardClient = DynamoDbClient.builder().build();

        // 2. Wrap it with the Enhanced Client
        DynamoDbEnhancedClient enhancedClient = DynamoDbEnhancedClient.builder()
                .dynamoDbClient(standardClient)
                .build();

        // 3. Pass the fully configured enhanced client into our repositories
        this.eventRepository = new EventRepository(enhancedClient, eventTableName);
        this.summaryRepository = new SummaryRepository(enhancedClient, summaryTableName);
    }

    public EventRepository getEventRepository() {
        return eventRepository;
    }

    public SummaryRepository getSummaryRepository() {
        return summaryRepository;
    }
}
