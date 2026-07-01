package live.gameshift.producer.service;

import live.gameshift.producer.config.AppProperties;
import live.gameshift.producer.config.SecretsService;
import live.gameshift.producer.model.SportType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Determines which sports are currently in-season by querying API-Sports
 * for upcoming/live fixtures within the next 24 hours.
 *
 * Checks run at startup and daily at 00:00 UTC.
 * On API failure/timeout, the previous status is retained.
 */
@Service
public class SeasonFilterService {

    private static final Logger log = LoggerFactory.getLogger(SeasonFilterService.class);

    private static final String FIXTURE_STATUS_FILTER = "1H-2H-HT-NS";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final Map<SportType, RestClient> restClients = new EnumMap<>(SportType.class);
    private final Map<SportType, AppProperties.SportConfig> configs;
    private final boolean mockMode;

    /**
     * Volatile reference to an immutable map for thread-safe reads from PollingService.
     * Updated atomically by replacing the entire map reference.
     */
    private volatile Map<SportType, Boolean> activeSports = new EnumMap<>(SportType.class);

    public SeasonFilterService(AppProperties props, SecretsService secretsService) {
        this.configs = props.getApi().getSports().getConfigs();
        this.mockMode = props.getApi().getSports().isMockMode();

        // In mock mode, skip all API-Sports connectivity — mark everything active immediately
        if (this.mockMode) {
            Map<SportType, Boolean> initial = new EnumMap<>(SportType.class);
            for (SportType sport : configs.keySet()) {
                initial.put(sport, true);
            }
            this.activeSports = Collections.unmodifiableMap(initial);
            log.info("Mock mode enabled — all configured sports marked as active: {}", configs.keySet());
            return;
        }

        String apiKey = secretsService.getApiSportsKey();

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));

        for (Map.Entry<SportType, AppProperties.SportConfig> entry : configs.entrySet()) {
            restClients.put(entry.getKey(), RestClient.builder()
                    .requestFactory(requestFactory)
                    .baseUrl(entry.getValue().getBaseUrl())
                    .defaultHeader("x-apisports-key", apiKey)
                    .build());
        }

        // Initialize all sports as active until the first check completes
        Map<SportType, Boolean> initial = new EnumMap<>(SportType.class);
        for (SportType sport : configs.keySet()) {
            initial.put(sport, true);
        }
        this.activeSports = Collections.unmodifiableMap(initial);
    }

    /**
     * Runs at application startup to perform the initial season check.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (mockMode) {
            log.info("Mock mode — skipping season filter API check at startup");
            return;
        }
        log.info("Running initial season filter check");
        refreshActiveSports();
    }

    /**
     * Runs daily at 00:00 UTC to refresh the active sports list.
     */
    @Scheduled(cron = "0 0 0 * * *", zone = "UTC")
    public void dailyCheck() {
        if (mockMode) {
            log.debug("Mock mode — skipping scheduled season filter check");
            return;
        }
        log.info("Running scheduled daily season filter check");
        refreshActiveSports();
    }

    /**
     * Returns whether the given sport is currently active (has fixtures in the next 24 hours).
     */
    public boolean isActive(SportType sportType) {
        return activeSports.getOrDefault(sportType, false);
    }

    /**
     * Returns the list of sports that are currently active.
     */
    public List<SportType> getActiveSports() {
        return activeSports.entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * Queries API-Sports for each configured sport and updates the active sports map.
     * On per-sport failure, retains the previous status for that sport.
     */
    private void refreshActiveSports() {
        Map<SportType, Boolean> currentStatus = activeSports;
        Map<SportType, Boolean> newStatus = new EnumMap<>(SportType.class);

        String todayDate = LocalDate.now(ZoneOffset.UTC).format(DATE_FORMAT);
        String tomorrowDate = LocalDate.now(ZoneOffset.UTC).plusDays(1).format(DATE_FORMAT);

        for (SportType sport : configs.keySet()) {
            try {
                boolean hasFixtures = checkFixturesForSport(sport, todayDate)
                        || checkFixturesForSport(sport, tomorrowDate);
                newStatus.put(sport, hasFixtures);
            } catch (Exception e) {
                // On failure, retain previous status
                boolean previousStatus = currentStatus.getOrDefault(sport, true);
                newStatus.put(sport, previousStatus);
                log.warn("Season check failed for sport={}, retaining previous status={}: {}",
                        sport, previousStatus, e.getMessage());
            }
        }

        // Atomic update of the volatile reference
        this.activeSports = Collections.unmodifiableMap(newStatus);

        // Log active/skipped sports
        List<SportType> active = newStatus.entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        List<SportType> skipped = newStatus.entrySet().stream()
                .filter(e -> !e.getValue())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        log.info("Season filter check complete — active sports: {}, skipped sports: {}", active, skipped);
    }

    /**
     * Checks whether the given sport has live or upcoming fixtures for the specified date.
     * Returns true if any fixtures exist, false otherwise.
     */
    private boolean checkFixturesForSport(SportType sport, String date) {
        RestClient client = restClients.get(sport);
        if (client == null) {
            log.warn("No REST client configured for sport={}, treating as inactive", sport);
            return false;
        }

        try {
            String response = client.get()
                    .uri("/fixtures?date={date}&status={status}", date, FIXTURE_STATUS_FILTER)
                    .retrieve()
                    .body(String.class);

            if (response == null || response.isBlank()) {
                return false;
            }

            // API-Sports returns a JSON structure with a "response" array.
            // If it's non-empty, there are fixtures available.
            return hasFixturesInResponse(response);
        } catch (RestClientException e) {
            throw new RuntimeException("API call failed for sport=" + sport + ": " + e.getMessage(), e);
        }
    }

    /**
     * Simple check to determine if the API-Sports response contains any fixtures.
     * The response format is: {"response": [...], ...}
     * If the "response" array is empty, there are no fixtures.
     */
    private boolean hasFixturesInResponse(String jsonResponse) {
        // Look for a non-empty "response" array in the JSON.
        // A simple heuristic: if "response":[] is present, no fixtures exist.
        // Otherwise, if "response":[...something...] is found, fixtures exist.
        int responseIdx = jsonResponse.indexOf("\"response\"");
        if (responseIdx == -1) {
            return false;
        }

        int bracketIdx = jsonResponse.indexOf('[', responseIdx);
        if (bracketIdx == -1) {
            return false;
        }

        // Check if the array is empty by looking for immediate closing bracket
        for (int i = bracketIdx + 1; i < jsonResponse.length(); i++) {
            char c = jsonResponse.charAt(i);
            if (c == ']') {
                return false; // empty array
            }
            if (!Character.isWhitespace(c)) {
                return true; // non-empty array
            }
        }
        return false;
    }
}
