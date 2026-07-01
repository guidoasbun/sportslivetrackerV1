package live.gameshift.producer.service;

import live.gameshift.producer.config.AppProperties;
import live.gameshift.producer.config.SecretsService;
import live.gameshift.producer.model.SportType;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.mockito.Mockito;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Feature: app-completion-hardening, Property 7: Season filter partitions sports correctly
 *
 * Validates: Requirements 13.1, 13.2, 13.3
 *
 * Property: For any set of configured sports where a subset has fixtures within the next 24 hours,
 * the Season Filter marks exactly that subset as active and excludes the remainder from polling.
 * When a previously inactive sport gains fixtures, it transitions to active on the next check.
 * On API failure/timeout for a sport, it retains that sport's previous active/inactive status.
 */
class SeasonFilterServicePropertyTest {

    private static final String NON_EMPTY_RESPONSE = "{\"response\":[{\"fixture\":{\"id\":1}}]}";
    private static final String EMPTY_RESPONSE = "{\"response\":[]}";

    /**
     * Creates a SeasonFilterService with mocked dependencies where REST clients
     * are replaced via reflection to control fixture responses.
     */
    private SeasonFilterService createServiceWithMockedClients(
            Set<SportType> configuredSports,
            Map<SportType, RestClient> mockClients) throws Exception {

        // Set up AppProperties mock
        AppProperties props = mock(AppProperties.class);
        AppProperties.Api api = mock(AppProperties.Api.class);
        AppProperties.Api.Sports sports = mock(AppProperties.Api.Sports.class);
        when(props.getApi()).thenReturn(api);
        when(api.getSports()).thenReturn(sports);

        Map<SportType, AppProperties.SportConfig> configs = new HashMap<>();
        for (SportType sport : configuredSports) {
            AppProperties.SportConfig config = new AppProperties.SportConfig();
            config.setBaseUrl("https://mock-" + sport.name().toLowerCase() + ".api.com");
            configs.put(sport, config);
        }
        when(sports.getConfigs()).thenReturn(configs);

        // Set up SecretsService mock
        SecretsService secrets = mock(SecretsService.class);
        when(secrets.getApiSportsKey()).thenReturn("test-key");

        // Create the service — constructor will build real RestClients
        SeasonFilterService service = new SeasonFilterService(props, secrets);

        // Replace the restClients map via reflection with our mocked clients
        Field restClientsField = SeasonFilterService.class.getDeclaredField("restClients");
        restClientsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<SportType, RestClient> internalClients = (Map<SportType, RestClient>) restClientsField.get(service);
        internalClients.clear();
        internalClients.putAll(mockClients);

        return service;
    }

    /**
     * Triggers refreshActiveSports via the public onStartup() method.
     */
    private void triggerRefresh(SeasonFilterService service) {
        service.onStartup();
    }

    /**
     * Creates a mock RestClient that returns the given response body for any GET request.
     */
    private RestClient createMockRestClient(String responseBody) {
        RestClient client = mock(RestClient.class);
        RestClient.RequestHeadersUriSpec uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec headersSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(client.get()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString(), any(), any())).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(String.class)).thenReturn(responseBody);

        return client;
    }

    /**
     * Creates a mock RestClient that throws a RestClientException.
     */
    private RestClient createFailingRestClient() {
        RestClient client = mock(RestClient.class);
        RestClient.RequestHeadersUriSpec uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec headersSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(client.get()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString(), any(), any())).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(String.class)).thenThrow(new RestClientException("Connection timeout"));

        return client;
    }

    // --- Arbitraries ---

    @Provide
    Arbitrary<Set<SportType>> configuredSports() {
        return Arbitraries.of(SportType.values())
                .set()
                .ofMinSize(1)
                .ofMaxSize(SportType.values().length);
    }

    @Provide
    Arbitrary<Set<SportType>> activeSportsSubset() {
        // Generate a subset to be marked as active (can be empty)
        return Arbitraries.of(SportType.values())
                .set()
                .ofMinSize(0)
                .ofMaxSize(SportType.values().length);
    }

    // --- Property Tests ---

    @Property(tries = 200)
    @Label("Active/inactive partition matches fixture availability exactly")
    void activeInactivePartitionMatchesFixtureAvailability(
            @ForAll("configuredSports") Set<SportType> configured,
            @ForAll("activeSportsSubset") Set<SportType> sportsWithFixtures) throws Exception {

        // sportsWithFixtures should be a subset of configured
        Set<SportType> actualActive = sportsWithFixtures.stream()
                .filter(configured::contains)
                .collect(Collectors.toSet());

        // Set up mock clients: sports with fixtures return non-empty JSON, others return empty
        Map<SportType, RestClient> mockClients = new HashMap<>();
        for (SportType sport : configured) {
            if (actualActive.contains(sport)) {
                mockClients.put(sport, createMockRestClient(NON_EMPTY_RESPONSE));
            } else {
                mockClients.put(sport, createMockRestClient(EMPTY_RESPONSE));
            }
        }

        SeasonFilterService service = createServiceWithMockedClients(configured, mockClients);
        triggerRefresh(service);

        // Verify: exactly the sports with fixtures are active
        for (SportType sport : configured) {
            if (actualActive.contains(sport)) {
                assertThat(service.isActive(sport))
                        .as("Sport %s should be ACTIVE (has fixtures)", sport)
                        .isTrue();
            } else {
                assertThat(service.isActive(sport))
                        .as("Sport %s should be INACTIVE (no fixtures)", sport)
                        .isFalse();
            }
        }

        // Verify getActiveSports returns exactly the active subset
        List<SportType> activeSportsList = service.getActiveSports();
        assertThat(new HashSet<>(activeSportsList))
                .as("getActiveSports() should return exactly the sports with fixtures")
                .isEqualTo(actualActive);
    }

    @Property(tries = 200)
    @Label("Previously inactive sport transitions to active when fixtures appear")
    void inactiveSportTransitionsToActiveWhenFixturesAppear(
            @ForAll("configuredSports") Set<SportType> configured,
            @ForAll("activeSportsSubset") Set<SportType> initiallyActive,
            @ForAll("activeSportsSubset") Set<SportType> newlyActive) throws Exception {

        Set<SportType> phase1Active = initiallyActive.stream()
                .filter(configured::contains)
                .collect(Collectors.toSet());

        // In phase 2, some previously inactive sports gain fixtures
        Set<SportType> phase2Active = newlyActive.stream()
                .filter(configured::contains)
                .collect(Collectors.toSet());

        // Phase 1: set initial active/inactive state
        Map<SportType, RestClient> phase1Clients = new HashMap<>();
        for (SportType sport : configured) {
            if (phase1Active.contains(sport)) {
                phase1Clients.put(sport, createMockRestClient(NON_EMPTY_RESPONSE));
            } else {
                phase1Clients.put(sport, createMockRestClient(EMPTY_RESPONSE));
            }
        }

        SeasonFilterService service = createServiceWithMockedClients(configured, phase1Clients);
        triggerRefresh(service);

        // Verify phase 1 state
        for (SportType sport : configured) {
            assertThat(service.isActive(sport))
                    .as("Phase 1: Sport %s active status", sport)
                    .isEqualTo(phase1Active.contains(sport));
        }

        // Phase 2: change fixture availability — replace rest clients
        Field restClientsField = SeasonFilterService.class.getDeclaredField("restClients");
        restClientsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<SportType, RestClient> internalClients = (Map<SportType, RestClient>) restClientsField.get(service);
        internalClients.clear();

        for (SportType sport : configured) {
            if (phase2Active.contains(sport)) {
                internalClients.put(sport, createMockRestClient(NON_EMPTY_RESPONSE));
            } else {
                internalClients.put(sport, createMockRestClient(EMPTY_RESPONSE));
            }
        }

        // Trigger second refresh
        triggerRefresh(service);

        // Verify: sports that gained fixtures are now active
        for (SportType sport : configured) {
            assertThat(service.isActive(sport))
                    .as("Phase 2: Sport %s should now reflect new fixture availability", sport)
                    .isEqualTo(phase2Active.contains(sport));
        }

        // Specifically verify transitions from inactive → active
        Set<SportType> transitioned = phase2Active.stream()
                .filter(s -> !phase1Active.contains(s))
                .collect(Collectors.toSet());
        for (SportType sport : transitioned) {
            assertThat(service.isActive(sport))
                    .as("Sport %s transitioned inactive→active", sport)
                    .isTrue();
        }
    }

    @Property(tries = 200)
    @Label("On API failure, sport retains previous active/inactive status")
    void onFailureRetainsPreviousStatus(
            @ForAll("configuredSports") Set<SportType> configured,
            @ForAll("activeSportsSubset") Set<SportType> initiallyActive,
            @ForAll("activeSportsSubset") Set<SportType> failingSports) throws Exception {

        Set<SportType> phase1Active = initiallyActive.stream()
                .filter(configured::contains)
                .collect(Collectors.toSet());

        Set<SportType> failing = failingSports.stream()
                .filter(configured::contains)
                .collect(Collectors.toSet());

        // Phase 1: establish initial state
        Map<SportType, RestClient> phase1Clients = new HashMap<>();
        for (SportType sport : configured) {
            if (phase1Active.contains(sport)) {
                phase1Clients.put(sport, createMockRestClient(NON_EMPTY_RESPONSE));
            } else {
                phase1Clients.put(sport, createMockRestClient(EMPTY_RESPONSE));
            }
        }

        SeasonFilterService service = createServiceWithMockedClients(configured, phase1Clients);
        triggerRefresh(service);

        // Capture the state after phase 1
        Map<SportType, Boolean> phase1State = new HashMap<>();
        for (SportType sport : configured) {
            phase1State.put(sport, service.isActive(sport));
        }

        // Phase 2: failing sports throw exceptions, non-failing sports return empty (inactive)
        Field restClientsField = SeasonFilterService.class.getDeclaredField("restClients");
        restClientsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<SportType, RestClient> internalClients = (Map<SportType, RestClient>) restClientsField.get(service);
        internalClients.clear();

        for (SportType sport : configured) {
            if (failing.contains(sport)) {
                internalClients.put(sport, createFailingRestClient());
            } else {
                // Non-failing sports get empty response (would become inactive)
                internalClients.put(sport, createMockRestClient(EMPTY_RESPONSE));
            }
        }

        // Trigger second refresh
        triggerRefresh(service);

        // Verify: failing sports retain their previous status
        for (SportType sport : failing) {
            assertThat(service.isActive(sport))
                    .as("Failing sport %s should retain previous status %s", sport, phase1State.get(sport))
                    .isEqualTo(phase1State.get(sport));
        }

        // Non-failing sports should reflect new fixture data (all inactive now)
        for (SportType sport : configured) {
            if (!failing.contains(sport)) {
                assertThat(service.isActive(sport))
                        .as("Non-failing sport %s should be inactive (empty response)", sport)
                        .isFalse();
            }
        }
    }
}
