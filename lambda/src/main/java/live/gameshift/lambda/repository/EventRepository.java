package live.gameshift.lambda.repository;

import live.gameshift.lambda.model.Event;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

public class EventRepository {

    private final DynamoDbTable<Event> eventTable;

    public EventRepository(DynamoDbEnhancedClient enhancedClient, String tableName) {
        // This maps the Event.class structure to the actual physical DynamoDB table in
        // AWS
        this.eventTable = enhancedClient.table(tableName, TableSchema.fromBean(Event.class));
    }

    public void save(Event event) {
        // putItem() automatically inserts or overwrites the item in the table
        eventTable.putItem(event);
    }
}
