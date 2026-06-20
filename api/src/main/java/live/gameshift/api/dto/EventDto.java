package live.gameshift.api.dto;

import live.gameshift.api.model.enums.SportType;
import java.util.Map;

// Java 14+ 'record' type is perfect for DTOs. It automatically generates 
// getters, equals(), hashCode(), and toString() for us!
public record EventDto(
        String eventId,
        SportType sportType,
        String action,
        Map<String, String> participants,
        Long eventTimestamp
) {}
