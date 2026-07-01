package live.gameshift.api.controller;

import live.gameshift.api.model.enums.SportType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.util.Arrays;
import java.util.List;

/**
 * Exposes the list of currently active sports as determined by the producer's SeasonFilterService.
 * Fetches from the producer's internal endpoint. On failure, returns all sports as active
 * (graceful degradation).
 */
@RestController
@RequestMapping("/api/sports")
public class SportController {

    private static final Logger log = LoggerFactory.getLogger(SportController.class);

    private final RestClient restClient;

    public SportController(@Value("${app.producer.base-url}") String producerBaseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(producerBaseUrl)
                .build();
    }

    @GetMapping("/active")
    public List<SportType> getActiveSports() {
        try {
            List<String> sportNames = restClient.get()
                    .uri("/internal/sports/active")
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<String>>() {});

            if (sportNames == null || sportNames.isEmpty()) {
                return getAllSports();
            }

            return sportNames.stream()
                    .map(SportType::valueOf)
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to fetch active sports from producer, returning all sports as active: {}",
                    e.getMessage());
            return getAllSports();
        }
    }

    private List<SportType> getAllSports() {
        return Arrays.asList(SportType.values());
    }
}
