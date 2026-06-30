package live.gameshift.api.service;

import live.gameshift.api.dto.SummaryDto;
import live.gameshift.api.repository.SummaryRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class SummaryService {

    private final SummaryRepository summaryRepository;

    public SummaryService(SummaryRepository summaryRepository) {
        this.summaryRepository = summaryRepository;
    }

    public Optional<SummaryDto> getSummaryForEvent(String eventId) {
        return summaryRepository.findByEventId(eventId)
                .map(summary -> new SummaryDto(
                        summary.getSummaryId(),
                        summary.getEventId(),
                        summary.getSportType(),
                        summary.getCommentary(),
                        summary.getTimestamp()
                ));
    }
}
