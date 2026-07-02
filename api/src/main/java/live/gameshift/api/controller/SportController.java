package live.gameshift.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import live.gameshift.api.model.enums.SportType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Exposes the list of currently active sports by checking API-Sports directly
 * for live fixtures in each sport. Caches results for 5 minutes to avoid
 * excessive API calls.
 */
@RestController
@RequestMapping("/api/sports")
public class SportController {

    private static final Logger log = LoggerFactory.getLogger(SportController.class);

    private final String apiSportsKey;
    private final ObjectMapper objectMapper;

    private static final Map<SportType, String> SPORT_URLS = Map.of(
        SportType.SOCCER, "https://v3.football.api-sports.io/fixtures?live=all",
        SportType.BASKETBALL, "https://v1.basketball.api-sports.io/games?live=all",
        SportType.FOOTBALL, "https://v1.american-football.api-sports.io/games?live=all",
        SportType.BASEBALL, "https://v1.baseball.api-sports.io/games?live=all",
        SportType.HOCKEY, "https://v1.hockey.api-sports.io/games?live=all",
        SportType.FORMULA_1, "https://v1.formula-1.api-sports.io/races?live=all"
    );

    // Cache: active sports list + timestamp
    private volatile List<SportType> cachedActiveSports = null;
    private volatile long cacheTimestamp = 0;
    private static final long CACHE_TTL_MS = 5 * 60 * 1000; // 5 minutes

    public SportController(@Value("${API_SPORTS_KEY:}") String apiSportsKey,
                           ObjectMapper objectMapper) {
        this.apiSportsKey = apiSportsKey;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/active")
    public List<SportType> getActiveSports() {
        // Return cached result if still fresh
        if (cachedActiveSports != null && (System.currentTimeMillis() - cacheTimestamp) < CACHE_TTL_MS) {
            return cachedActiveSports;
        }

        if (apiSportsKey == null || apiSportsKey.isBlank()) {
            log.warn("API_SPORTS_KEY not configured, returning all sports as active");
            return Arrays.asList(SportType.values());
        }

        List<SportType> activeSports = new ArrayList<>();

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));

        for (Map.Entry<SportType, String> entry : SPORT_URLS.entrySet()) {
            try {
                RestClient client = RestClient.builder()
                        .requestFactory(requestFactory)
                        .defaultHeader("x-apisports-key", apiSportsKey)
                        .build();

                String response = client.get()
                        .uri(entry.getValue())
                        .retrieve()
                        .body(String.class);

                if (response != null) {
                    JsonNode root = objectMapper.readTree(response);
                    int results = root.path("results").asInt(0);
                    if (results > 0) {
                        activeSports.add(entry.getKey());
                    }
                }
            } catch (Exception e) {
                log.debug("Failed to check live fixtures for {}: {}", entry.getKey(), e.getMessage());
                // On failure, include the sport (graceful degradation)
                activeSports.add(entry.getKey());
            }
        }

        // Cache the result
        cachedActiveSports = Collections.unmodifiableList(activeSports);
        cacheTimestamp = System.currentTimeMillis();

        log.info("Active sports check: {} active out of {}", activeSports.size(), SPORT_URLS.size());
        return activeSports;
    }
}
