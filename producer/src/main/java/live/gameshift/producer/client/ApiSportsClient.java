package live.gameshift.producer.client;

import live.gameshift.producer.config.AppProperties;
import live.gameshift.producer.config.SecretsService;
import live.gameshift.producer.model.SportEvent;
import live.gameshift.producer.model.SportType;
import live.gameshift.producer.normalizer.SportNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

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

    public ApiSportsClient(SecretsService secretsService, AppProperties props, List<SportNormalizer> normalizerList) {
        this.configs = props.getApi().getSports().getConfigs();

        this.normalizers = normalizerList.stream()
                .collect(Collectors.toMap(SportNormalizer::getSportType, Function.identity()));

        String apiKey = secretsService.getApiSportsKey();
        for (Map.Entry<SportType, AppProperties.SportConfig> entry : configs.entrySet()) {
            restClients.put(entry.getKey(), RestClient.builder()
                    .baseUrl(
                            java.util.Objects.requireNonNull(entry.getValue().getBaseUrl(), "baseUrl must not be null"))
                    .defaultHeader("x-apisports-key", apiKey)
                    .build());
        }
    }

    public Optional<SportEvent> fetchLatestEvent(SportType sportType) throws Exception {
        AppProperties.SportConfig config = configs.get(sportType);
        SportNormalizer normalizer = normalizers.get(sportType);
        RestClient client = restClients.get(sportType);

        if (config == null || normalizer == null || client == null) {
            log.warn("Missing configuration or normalizer for sport: {}", sportType);
            return Optional.empty();
        }

        String response = client.get()
                .uri("/fixtures?id={id}&live=all", config.getFixtureId())
                .retrieve()
                .body(String.class);

        return normalizer.normalize(response);
    }
}
