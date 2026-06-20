package live.gameshift.api.repository;

import live.gameshift.api.model.Event;
import live.gameshift.api.model.enums.SportType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

import java.util.List;
import java.util.stream.Collectors;

@Repository
public class EventRepository {

    private final DynamoDbIndex<Event> sportTypeIndex;

    // Spring will automatically inject the enhancedClient we defined in AwsConfig.
    // @Value pulls the table name from our application.properties file!
    public EventRepository(DynamoDbEnhancedClient enhancedClient, 
                           @Value("${app.aws.dynamodb.events-table:sportslivetrackerV1-dev-events}") String tableName) {
        
        DynamoDbTable<Event> table = enhancedClient.table(tableName, TableSchema.fromBean(Event.class));
        
        // We get a reference to the Global Secondary Index so we can query by SportType
        this.sportTypeIndex = table.index("sport-type-timestamp-index");
    }

    public List<Event> findRecentEvents(SportType sportType, Long sinceEpochMillis) {
        // Query condition: "Partition key equals SportType AND Sort key >= sinceEpochMillis"
        QueryConditional queryConditional = QueryConditional.sortGreaterThanOrEqualTo(
                Key.builder()
                        .partitionValue(sportType.name())
                        .sortValue(sinceEpochMillis) 
                        .build()
        );

        // Execute the query against the GSI and collect the results into a List
        return sportTypeIndex.query(
                        QueryEnhancedRequest.builder()
                                .queryConditional(queryConditional)
                                .build()
                )
                .stream()
                .flatMap(page -> page.items().stream())
                .collect(Collectors.toList());
    }
}
