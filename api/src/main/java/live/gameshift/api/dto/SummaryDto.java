package live.gameshift.api.dto;

public record SummaryDto(
        String summaryId,
        String eventId,
        String commentary,
        Long timestamp
) {}
