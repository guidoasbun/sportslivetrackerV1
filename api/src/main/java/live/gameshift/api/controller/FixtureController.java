package live.gameshift.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import live.gameshift.api.dto.FixtureDto;
import live.gameshift.api.model.enums.SportType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.*;

/**
 * Exposes live fixture information by querying API-Sports directly.
 * In mock mode, returns simulated fixtures for local development.
 */
@RestController
@RequestMapping("/api/fixtures")
public class FixtureController {

    private static final Logger log = LoggerFactory.getLogger(FixtureController.class);

    private final boolean mockMode;
    private final ObjectMapper objectMapper;
    private final Map<SportType, String> sportBaseUrls;
    private final String apiSportsKey;

    public FixtureController(@Value("${app.mock-mode:false}") boolean mockMode,
                             @Value("${API_SPORTS_KEY:}") String apiSportsKey,
                             ObjectMapper objectMapper) {
        this.mockMode = mockMode;
        this.objectMapper = objectMapper;
        this.apiSportsKey = apiSportsKey;

        // Map sport types to their API-Sports base URLs
        this.sportBaseUrls = Map.of(
            SportType.SOCCER, "https://v3.football.api-sports.io",
            SportType.BASKETBALL, "https://v1.basketball.api-sports.io",
            SportType.FOOTBALL, "https://v1.american-football.api-sports.io",
            SportType.BASEBALL, "https://v1.baseball.api-sports.io",
            SportType.HOCKEY, "https://v1.hockey.api-sports.io",
            SportType.FORMULA_1, "https://v1.formula-1.api-sports.io"
        );

        if (mockMode) {
            log.info("[MOCK] FixtureController running in mock mode — returning simulated fixtures");
        } else {
            log.info("FixtureController configured to query API-Sports directly for live fixtures");
        }
    }

    @GetMapping
    public List<FixtureDto> getFixtures(@RequestParam("sport") SportType sport) {
        if (mockMode) {
            return getMockFixtures(sport);
        }

        if (apiSportsKey == null || apiSportsKey.isBlank()) {
            log.warn("API_SPORTS_KEY not configured — cannot fetch live fixtures");
            return Collections.emptyList();
        }

        String baseUrl = sportBaseUrls.get(sport);
        if (baseUrl == null) {
            log.warn("No API-Sports base URL configured for sport: {}", sport);
            return Collections.emptyList();
        }

        try {
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(Duration.ofSeconds(10));
            requestFactory.setReadTimeout(Duration.ofSeconds(10));

            RestClient client = RestClient.builder()
                    .requestFactory(requestFactory)
                    .baseUrl(baseUrl)
                    .defaultHeader("x-apisports-key", apiSportsKey)
                    .build();

            // Fetch live fixtures
            String response = client.get()
                    .uri("/fixtures?live=all")
                    .retrieve()
                    .body(String.class);

            return parseFixturesResponse(response, sport);
        } catch (RestClientException e) {
            log.error("Failed to fetch fixtures from API-Sports for sport={}: {}", sport, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<FixtureDto> parseFixturesResponse(String response, SportType sport) {
        if (response == null || response.isBlank()) {
            return Collections.emptyList();
        }

        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode fixtures = root.path("response");

            if (!fixtures.isArray() || fixtures.isEmpty()) {
                return Collections.emptyList();
            }

            List<FixtureDto> result = new ArrayList<>();
            for (JsonNode fixture : fixtures) {
                try {
                    String fixtureId = String.valueOf(fixture.path("fixture").path("id").asLong());
                    String statusText = fixture.path("fixture").path("status").path("long").asText("Unknown");
                    int elapsed = fixture.path("fixture").path("status").path("elapsed").asInt(0);
                    long timestamp = fixture.path("fixture").path("timestamp").asLong(0) * 1000; // to millis

                    String home = fixture.path("teams").path("home").path("name").asText("TBD");
                    String away = fixture.path("teams").path("away").path("name").asText("TBD");

                    String status = elapsed > 0 ? statusText + " - " + elapsed + "'" : statusText;

                    result.add(new FixtureDto(
                            fixtureId,
                            sport,
                            Map.of("home", home, "away", away),
                            status,
                            timestamp
                    ));
                } catch (Exception e) {
                    log.debug("Failed to parse fixture entry: {}", e.getMessage());
                }
            }

            result.sort(Comparator.comparingLong(FixtureDto::startTime));
            return result;
        } catch (Exception e) {
            log.error("Failed to parse API-Sports fixtures response: {}", e.getMessage());
            return Collections.emptyList();
        }
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
