package live.gameshift.producer.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class SportEvent {

    private String eventId;
    private String sportType;
    private String action;
    private List<String> participants;
    private Map<String, String> metadata;
    private Instant timestamp;

    public SportEvent() {}

    public SportEvent(String eventId, String sportType, String action,
                      List<String> participants, Map<String, String> metadata) {
        this.eventId = eventId;
        this.sportType = sportType;
        this.action = action;
        this.participants = participants;
        this.metadata = metadata;
        this.timestamp = Instant.now();
    }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getSportType() { return sportType; }
    public void setSportType(String sportType) { this.sportType = sportType; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public List<String> getParticipants() { return participants; }
    public void setParticipants(List<String> participants) { this.participants = participants; }

    public Map<String, String> getMetadata() { return metadata; }
    public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}
