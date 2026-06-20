package live.gameshift.api.model;

import live.gameshift.api.model.enums.SportType;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;

import java.time.Instant;

@DynamoDbBean
public class Summary {

    private String summaryId;
    private String eventId;
    private SportType sportType;
    private String commentary;
    private Long timestamp;

    public Summary() {
    }

    // 1. Primary Partition Key
    @DynamoDbPartitionKey
    public String getSummaryId() {
        return summaryId;
    }
    public void setSummaryId(String summaryId) {
        this.summaryId = summaryId;
    }

    // 2. GSI Partition Key - Lets us look up a summary by its event ID!
    @DynamoDbSecondaryPartitionKey(indexNames = "event-id-index")
    public String getEventId() {
        return eventId;
    }
    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    // 3. Standard fields
    public SportType getSportType() {
        return sportType;
    }
    public void setSportType(SportType sportType) {
        this.sportType = sportType;
    }

    public String getCommentary() {
        return commentary;
    }
    public void setCommentary(String commentary) {
        this.commentary = commentary;
    }

    public Long getTimestamp() {
        return timestamp;
    }
    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }
}
