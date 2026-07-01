package live.gameshift.api.service;

import live.gameshift.api.dto.SummaryDto;
import live.gameshift.api.model.enums.SportType;
import live.gameshift.api.repository.SummaryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class SummaryService {

    private final SummaryRepository summaryRepository;
    private final boolean mockMode;

    private static final String[][] MOCK_COMMENTARY = {
        // Exciting
        {"What a moment! The crowd is on their feet as the action unfolds.",
         "Incredible play! This is what live sports is all about.",
         "The intensity just went up a notch. Both sides are giving everything."},
        // Analytical
        {"A tactical decision here — the coaching staff clearly planned for this scenario.",
         "Statistically, this kind of play succeeds about 30% of the time. Bold choice.",
         "That's the third time this pattern has emerged today. The opposition needs to adapt."},
        // Narrative
        {"The momentum is shifting. You can feel it in the atmosphere.",
         "Back and forth we go. Neither side willing to give an inch.",
         "A pivotal moment in this contest. The next few minutes will be crucial."},
    };

    public SummaryService(SummaryRepository summaryRepository,
                          @Value("${app.mock-mode:false}") boolean mockMode) {
        this.summaryRepository = summaryRepository;
        this.mockMode = mockMode;
    }

    public Optional<SummaryDto> getSummaryForEvent(String eventId) {
        if (mockMode) {
            return Optional.of(generateMockCommentary(eventId));
        }

        return summaryRepository.findByEventId(eventId)
                .map(summary -> new SummaryDto(
                        summary.getSummaryId(),
                        summary.getEventId(),
                        summary.getSportType(),
                        summary.getCommentary(),
                        summary.getTimestamp()
                ));
    }

    private SummaryDto generateMockCommentary(String eventId) {
        // Pick a random style and commentary line
        int style = ThreadLocalRandom.current().nextInt(MOCK_COMMENTARY.length);
        int line = ThreadLocalRandom.current().nextInt(MOCK_COMMENTARY[style].length);
        String commentary = "[MOCK] " + MOCK_COMMENTARY[style][line];

        return new SummaryDto(
                "mock-" + UUID.randomUUID().toString().substring(0, 8),
                eventId,
                SportType.SOCCER, // sport type doesn't matter much for commentary display
                commentary,
                System.currentTimeMillis()
        );
    }
}
