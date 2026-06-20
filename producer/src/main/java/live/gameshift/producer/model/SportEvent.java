package live.gameshift.producer.model;

import java.util.Map;

public class SportEvent {

    private String eventId;
    private SportType sportType;
    private String action;
    private Map<String, String> participants;
    private String rawPayload;
    private Long eventTimestamp;

    public SportEvent() {}

    public SportEvent(String eventId, SportType sportType, String action,
                      Map<String, String> participants, String rawPayload) {
        this.eventId = eventId;
        this.sportType = sportType;
        this.action = action;
        this.participants = participants;
        this.rawPayload = rawPayload;
        this.eventTimestamp = System.currentTimeMillis();
    }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public SportType getSportType() { return sportType; }
    public void setSportType(SportType sportType) { this.sportType = sportType; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public Map<String, String> getParticipants() { return participants; }
    public void setParticipants(Map<String, String> participants) { this.participants = participants; }

    public String getRawPayload() { return rawPayload; }
    public void setRawPayload(String rawPayload) { this.rawPayload = rawPayload; }

    public Long getEventTimestamp() { return eventTimestamp; }
    public void setEventTimestamp(Long eventTimestamp) { this.eventTimestamp = eventTimestamp; }
}
