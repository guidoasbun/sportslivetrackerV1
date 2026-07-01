package live.gameshift.producer.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import live.gameshift.producer.model.SportType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages active subscription state by querying the API service's
 * /api/subscriptions/active endpoint. Provides methods for the PollingService
 * to determine which sports and fixtures should be polled based on active subscribers.
 */
@Component
public class PollingController {

    private static final Logger log = LoggerFactory.getLogger(PollingController.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    /**
     * Set of active sport+fixture combinations from the most recent successful subscription check.
     * Thread-safe via volatile reference to an immutable set.
     */
    private volatile Set<ActiveSubscription> activeSubscriptions = Collections.emptySet();

    public PollingController(
            @Value("${app.api.service.url}") String apiServiceUrl,
            ObjectMapper objectMapper) {
        this.restClient = RestClient.builder()
                .baseUrl(apiServiceUrl)
                .build();
        this.objectMapper = objectMapper;
    }

    /**
     * Refreshes the active subscriptions by calling the API service.
     * Should be called at the start of each poll cycle.
     *
     * If the API service is unreachable, retains the previous cycle's active list and logs a warning.
     */
    public void refreshSubscriptions() {
        try {
            String response = restClient.get()
                    .uri("/api/subscriptions/active")
                    .retrieve()
                    .body(String.class);

            if (response == null || response.isBlank() || "[]".equals(response.trim())) {
                activeSubscriptions = Collections.emptySet();
                log.debug("No active subscriptions returned from API service");
                return;
            }

            List<ActiveSubscriptionDto> dtos = objectMapper.readValue(
                    response, new TypeReference<List<ActiveSubscriptionDto>>() {});

            Set<ActiveSubscription> newActive = ConcurrentHashMap.newKeySet();
            for (ActiveSubscriptionDto dto : dtos) {
                try {
                    SportType sportType = SportType.valueOf(dto.sportType());
                    newActive.add(new ActiveSubscription(sportType, dto.fixtureId()));
                } catch (IllegalArgumentException e) {
                    log.warn("Unknown sport type in subscription response: {}", dto.sportType());
                }
            }

            activeSubscriptions = Collections.unmodifiableSet(newActive);
            log.debug("Refreshed active subscriptions: {} sport/fixture combinations", newActive.size());

        } catch (RestClientException e) {
            log.warn("API service subscriptions endpoint unreachable, retaining previous active list: {}",
                    e.getMessage());
        } catch (Exception e) {
            log.warn("Failed to parse subscriptions response, retaining previous active list: {}",
                    e.getMessage());
        }
    }

    /**
     * Returns true if there is at least one active subscriber for any sport/fixture.
     */
    public boolean hasActiveSubscribers() {
        return !activeSubscriptions.isEmpty();
    }

    /**
     * Returns true if the given sport type has at least one active subscriber
     * (regardless of fixture).
     */
    public boolean isSportActive(SportType sportType) {
        return activeSubscriptions.stream()
                .anyMatch(sub -> sub.sportType() == sportType);
    }

    /**
     * Returns true if the specific sport+fixture combination has active subscribers.
     */
    public boolean isFixtureActive(SportType sportType, String fixtureId) {
        return activeSubscriptions.contains(new ActiveSubscription(sportType, fixtureId));
    }

    /**
     * Returns the current set of active subscriptions (for testing/debugging).
     */
    Set<ActiveSubscription> getActiveSubscriptions() {
        return activeSubscriptions;
    }

    /**
     * DTO matching the API service's /api/subscriptions/active response format.
     */
    record ActiveSubscriptionDto(String sportType, String fixtureId, int subscriberCount) {}

    /**
     * Represents an active sport+fixture combination.
     */
    record ActiveSubscription(SportType sportType, String fixtureId) {}
}
