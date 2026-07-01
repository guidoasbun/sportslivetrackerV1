package live.gameshift.api.controller;

import live.gameshift.api.dto.FixtureDto;
import live.gameshift.api.model.Event;
import live.gameshift.api.model.enums.SportType;
import live.gameshift.api.repository.EventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Exposes fixture information derived from recent events.
 * In mock mode, returns simulated fixtures instead of querying DynamoDB.
 */
@RestController
@RequestMapping("/api/fixtures")
public class FixtureController {

    private static final Logger log = LoggerFactory.getLogger(FixtureController.class);

    private final EventRepository eventRepository;
    private final boolean mockMode;

    public FixtureController(EventRepository eventRepository,
                             @Value("${app.mock-mode:false}") boolean mockMode) {
        this.eventRepository = eventRepository;
        this.mockMode = mockMode;
        if (mockMode) {
            log.info("[MOCK] FixtureController running in mock mode — returning simulated fixtures");
        }
    }

    @GetMapping
    public List<FixtureDto> getFixtures(@RequestParam("sport") SportType sport) {
        if (mockMode) {
            return getMockFixtures(sport);
        }

        // Production: query events from the last 24 hours to capture live + upcoming fixtures
        long sinceEpochMillis = Instant.now().minus(24, ChronoUnit.HOURS).toEpochMilli();

        List<Event> recentEvents = eventRepository.findRecentEvents(sport, sinceEpochMillis);

        if (recentEvents.isEmpty()) {
            return Collections.emptyList();
        }

        // Filter out events with null fixtureId (legacy events before fixture feature)
        Map<String, List<Event>> eventsByFixture = recentEvents.stream()
                .filter(event -> event.getFixtureId() != null)
                .collect(Collectors.groupingBy(Event::getFixtureId));

        if (eventsByFixture.isEmpty()) {
            return Collections.emptyList();
        }

        // For each fixture group: take the latest action/status and earliest timestamp as startTime
        return eventsByFixture.entrySet().stream()
                .map(entry -> {
                    String fixtureId = entry.getKey();
                    List<Event> fixtureEvents = entry.getValue();

                    Event latestEvent = fixtureEvents.stream()
                            .max(Comparator.comparingLong(Event::getEventTimestamp))
                            .orElseThrow();

                    Long startTime = fixtureEvents.stream()
                            .map(Event::getEventTimestamp)
                            .min(Long::compareTo)
                            .orElseThrow();

                    return new FixtureDto(
                            fixtureId,
                            sport,
                            latestEvent.getParticipants(),
                            latestEvent.getAction(),
                            startTime
                    );
                })
                .sorted(Comparator.comparingLong(FixtureDto::startTime))
                .toList();
    }

    /**
     * [MOCK] Returns simulated fixture data matching the producer's MockDataService fixtures.
     * These fixture IDs align with what the producer publishes to Kinesis in mock mode.
     */
    private List<FixtureDto> getMockFixtures(SportType sport) {
        long now = System.currentTimeMillis();
        // Start times staggered to look realistic
        long started30MinAgo = now - (30 * 60 * 1000);
        long started15MinAgo = now - (15 * 60 * 1000);
        long started5MinAgo = now - (5 * 60 * 1000);

        return switch (sport) {
            case SOCCER -> List.of(
                new FixtureDto("MOCK-S01", SportType.SOCCER,
                    Map.of("home", "Manchester City", "away", "Real Madrid"),
                    "LIVE - 1H", started30MinAgo),
                new FixtureDto("MOCK-S02", SportType.SOCCER,
                    Map.of("home", "Barcelona", "away", "Bayern Munich"),
                    "LIVE - 2H", started30MinAgo),
                new FixtureDto("MOCK-S03", SportType.SOCCER,
                    Map.of("home", "Liverpool", "away", "Inter Milan"),
                    "LIVE - 1H", started15MinAgo)
            );
            case BASKETBALL -> List.of(
                new FixtureDto("MOCK-B01", SportType.BASKETBALL,
                    Map.of("home", "Lakers", "away", "Celtics"),
                    "LIVE - Q2", started30MinAgo),
                new FixtureDto("MOCK-B02", SportType.BASKETBALL,
                    Map.of("home", "Warriors", "away", "Nuggets"),
                    "LIVE - Q3", started15MinAgo)
            );
            case FOOTBALL -> List.of(
                new FixtureDto("MOCK-F01", SportType.FOOTBALL,
                    Map.of("home", "Chiefs", "away", "Eagles"),
                    "LIVE - Q1", started15MinAgo),
                new FixtureDto("MOCK-F02", SportType.FOOTBALL,
                    Map.of("home", "49ers", "away", "Cowboys"),
                    "LIVE - Q2", started30MinAgo)
            );
            case BASEBALL -> List.of(
                new FixtureDto("MOCK-X01", SportType.BASEBALL,
                    Map.of("home", "Yankees", "away", "Dodgers"),
                    "LIVE - Top 4th", started30MinAgo),
                new FixtureDto("MOCK-X02", SportType.BASEBALL,
                    Map.of("home", "Astros", "away", "Braves"),
                    "LIVE - Bot 6th", started30MinAgo)
            );
            case HOCKEY -> List.of(
                new FixtureDto("MOCK-H01", SportType.HOCKEY,
                    Map.of("home", "Oilers", "away", "Panthers"),
                    "LIVE - P2", started15MinAgo),
                new FixtureDto("MOCK-H02", SportType.HOCKEY,
                    Map.of("home", "Avalanche", "away", "Rangers"),
                    "LIVE - P1", started5MinAgo)
            );
            case FORMULA_1 -> List.of(
                new FixtureDto("MOCK-R01", SportType.FORMULA_1,
                    Map.of("home", "Monaco Grand Prix", "away", "Race"),
                    "LIVE - Lap 23", started30MinAgo),
                new FixtureDto("MOCK-R02", SportType.FORMULA_1,
                    Map.of("home", "Silverstone Grand Prix", "away", "Race"),
                    "LIVE - Lap 12", started15MinAgo)
            );
        };
    }
}
