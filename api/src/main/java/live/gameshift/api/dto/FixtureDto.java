package live.gameshift.api.dto;

import live.gameshift.api.model.enums.SportType;
import java.util.Map;

public record FixtureDto(
    String fixtureId,
    SportType sportType,
    Map<String, String> participants,
    String status,
    Long startTime
) {}
