package live.gameshift.api.model;

import live.gameshift.api.model.enums.SportType;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;

import java.time.Instant;
import java.util.Map;

@DynamoDbBean
public class Event {

    private String eventId;
    private SportType sportType;
    private String action;
    private Map<String, String> participants;
    private String rawPayload;
    private Instant eventTimestamp; // Note the name here!

    public Event() {
    }

    // 1. Primary Partition Key
    @DynamoDbPartitionKey
    public String getEventId() {
        return eventId;
    }
    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    // 2. GSI Partition Key
    @DynamoDbSecondaryPartitionKey(indexNames = "sport-type-timestamp-index")
    public SportType getSportType() {
        return sportType;
    }
    public void setSportType(SportType sportType) {
        this.sportType = sportType;
    }

    // 3. GSI Sort Key
    @DynamoDbSecondarySortKey(indexNames = "sport-type-timestamp-index")
    public Instant getEventTimestamp() {
        return eventTimestamp;
    }
    public void setEventTimestamp(Instant eventTimestamp) {
        this.eventTimestamp = eventTimestamp;
    }

    // 4. Standard fields
    public String getAction() {
        return action;
    }
    public void setAction(String action) {
        this.action = action;
    }

    public Map<String, String> getParticipants() {
        return participants;
    }
    public void setParticipants(Map<String, String> participants) {
        this.participants = participants;
    }

    public String getRawPayload() {
        return rawPayload;
    }
    public void setRawPayload(String rawPayload) {
        this.rawPayload = rawPayload;
    }
}
