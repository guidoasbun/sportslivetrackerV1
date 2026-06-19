package live.gameshift.lambda.repository;

import live.gameshift.lambda.model.Summary;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

public class SummaryRepository {

    private final DynamoDbTable<Summary> summaryTable;

    public SummaryRepository(DynamoDbEnhancedClient enhancedClient, String tableName) {
        this.summaryTable = enhancedClient.table(tableName, TableSchema.fromBean(Summary.class));
    }

    public void save(Summary summary) {
        summaryTable.putItem(summary);
    }
}
