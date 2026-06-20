package live.gameshift.api.repository;

import live.gameshift.api.model.Summary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

import java.util.Optional;

@Repository
public class SummaryRepository {

    private final DynamoDbIndex<Summary> eventIdIndex;

    public SummaryRepository(DynamoDbEnhancedClient enhancedClient, 
                             @Value("${app.aws.dynamodb.summaries-table:sportslivetrackerV1-dev-summaries}") String tableName) {
        
        DynamoDbTable<Summary> table = enhancedClient.table(tableName, TableSchema.fromBean(Summary.class));
        this.eventIdIndex = table.index("event-id-index");
    }

    public Optional<Summary> findByEventId(String eventId) {
        // Query condition: "Partition key equals eventId"
        QueryConditional queryConditional = QueryConditional.keyEqualTo(
                Key.builder()
                        .partitionValue(eventId)
                        .build()
        );

        // Execute the query. Since eventIds are unique per event, 
        // we expect either 0 or 1 summary back, so we use .findFirst()
        return eventIdIndex.query(
                        QueryEnhancedRequest.builder()
                                .queryConditional(queryConditional)
                                .build()
                )
                .stream()
                .flatMap(page -> page.items().stream())
                .findFirst();
    }
}
