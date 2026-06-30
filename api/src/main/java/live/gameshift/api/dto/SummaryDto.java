package live.gameshift.api.dto;

import live.gameshift.api.model.enums.SportType;

public record SummaryDto(
        String summaryId,
        String eventId,
        SportType sportType,
        String commentary,
        Long timestamp
) {}
