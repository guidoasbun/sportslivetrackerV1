package live.gameshift.producer.client;

import live.gameshift.producer.config.AppProperties;
import live.gameshift.producer.config.SecretsService;
import live.gameshift.producer.model.SportEvent;
import live.gameshift.producer.model.SportType;
import live.gameshift.producer.normalizer.SportNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ApiSportsClient {

    private static final Logger log = LoggerFactory.getLogger(ApiSportsClient.class);

    private final Map<SportType, RestClient> restClients = new EnumMap<>(SportType.class);
    private final Map<SportType, SportNormalizer> normalizers;
    private final Map<SportType, AppProperties.SportConfig> configs;
    private final Map<SportType, Instant> pausedUntil = new EnumMap<>(SportType.class);

    public ApiSportsClient(SecretsService secretsService, AppProperties props, List<SportNormalizer> normalizerList) {
        this.configs = props.getApi().getSports().getConfigs();

        this.normalizers = normalizerList.stream()
                .collect(Collectors.toMap(SportNormalizer::getSportType, Function.identity()));

        String apiKey = secretsService.getApiSportsKey();

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));

        for (Map.Entry<SportType, AppProperties.SportConfig> entry : configs.entrySet()) {
            restClients.put(entry.getKey(), RestClient.builder()
                    .requestFactory(requestFactory)
                    .baseUrl(
                            java.util.Objects.requireNonNull(entry.getValue().getBaseUrl(), "baseUrl must not be null"))
                    .defaultHeader("x-apisports-key", apiKey)
                    .build());
        }
    }

    public Optional<SportEvent> fetchLatestEvent(SportType sportType) throws Exception {
        // Rate limit check: skip if sport is paused
        Instant pauseExpiry = pausedUntil.get(sportType);
        if (pauseExpiry != null && Instant.now().isBefore(pauseExpiry)) {
            log.debug("Sport {} is rate-limited until {}, skipping", sportType, pauseExpiry);
            return Optional.empty();
        }

        AppProperties.SportConfig config = configs.get(sportType);
        SportNormalizer normalizer = normalizers.get(sportType);
        RestClient client = restClients.get(sportType);

        if (config == null || normalizer == null || client == null) {
            log.warn("Missing configuration or normalizer for sport: {}", sportType);
            return Optional.empty();
        }

        try {
            String response = client.get()
                    .uri("/fixtures?id={id}&live=all", config.getFixtureId())
                    .retrieve()
                    .body(String.class);

            return normalizer.normalize(response);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 429) {
                pausedUntil.put(sportType, Instant.now().plusSeconds(60));
                log.warn("Rate limited for sport={}, pausing for 60s", sportType);
            } else if (status == 401 || status == 403) {
                log.error("API-Sports auth error: sport={} endpoint=/fixtures status={}", sportType, status);
            } else if (status >= 500) {
                log.error("API-Sports server error: sport={} endpoint=/fixtures status={}", sportType, status);
            } else {
                log.error("API-Sports error: sport={} endpoint=/fixtures status={}", sportType, status);
            }
            return Optional.empty();
        }
    }
}
