package live.gameshift.api.service;

import live.gameshift.api.dto.EventDto;
import live.gameshift.api.model.enums.SportType;
import net.jqwik.api.*;
import org.mockito.Mockito;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property-based test for fixture-filtered SSE stream.
 *
 * Property 8: Fixture-filtered SSE stream delivers only matching events —
 * generate events with various fixtureIds, verify only matching events
 * delivered to subscribed connection.
 *
 * Validates: Requirements 14.2, 14.3
 */
class SseEmitterServicePropertyTest {

    private static final SportType[] SPORT_TYPES = SportType.values();

    /**
     * Property 8: Fixture-filtered SSE stream delivers only matching events
     *
     * For any SSE connection subscribed to a specific fixtureId, all events
     * delivered through that connection SHALL have a fixtureId matching the
     * subscribed value. No event with a different fixtureId SHALL be delivered
     * to that connection.
     *
     * Additionally, emitters subscribed with null fixtureId (all events) SHALL
     * receive every broadcast event regardless of fixtureId.
     *
     * Validates: Requirements 14.2, 14.3
     */
    @Property(tries = 100)
    void fixtureFilteredSseDeliversOnlyMatchingEvents(
            @ForAll("fixtureFilterScenario") FilterScenario scenario) throws IOException {

        // Create a fresh service for each property trial
        SseEmitterService service = new SseEmitterService();

        // Track which emitters received sends via Mockito spies
        Map<String, SseEmitter> spyEmitters = new LinkedHashMap<>();

        // Register emitters with specific fixture subscriptions using reflection
        // to inject spy emitters into the service
        for (Map.Entry<String, String> sub : scenario.subscriptions.entrySet()) {
            String emitterId = sub.getKey();
            String subscribedFixtureId = sub.getValue(); // null means "all events"

            SseEmitter spyEmitter = Mockito.spy(new SseEmitter(0L));
            // Stub the send method to avoid actual HTTP response writing
            doNothing().when(spyEmitter).send(any(SseEmitter.SseEventBuilder.class));
            spyEmitters.put(emitterId, spyEmitter);

            // Inject the spy emitter into the service's internal list
            service.getEmitters().add(new SseEmitterService.EmitterEntry(spyEmitter, subscribedFixtureId));
        }

        // Broadcast each event and verify filtering
        for (EventDto event : scenario.events) {
            service.broadcast(event);
        }

        // Verify the property: each emitter only received matching events
        for (Map.Entry<String, String> sub : scenario.subscriptions.entrySet()) {
            String emitterId = sub.getKey();
            String subscribedFixtureId = sub.getValue();
            SseEmitter spyEmitter = spyEmitters.get(emitterId);

            // Count how many events should have been delivered to this emitter
            long expectedDeliveries = scenario.events.stream()
                    .filter(e -> subscribedFixtureId == null || subscribedFixtureId.equals(e.fixtureId()))
                    .count();

            // Verify exactly that many send() calls occurred
            verify(spyEmitter, times((int) expectedDeliveries)).send(any(SseEmitter.SseEventBuilder.class));
        }
    }

    @Provide
    Arbitrary<FilterScenario> fixtureFilterScenario() {
        // Generate a pool of fixture IDs to use across subscriptions and events
        Arbitrary<List<String>> fixturePoolArb = Arbitraries.strings()
                .alpha().ofMinLength(3).ofMaxLength(10)
                .map(s -> "fixture-" + s)
                .list().ofMinSize(2).ofMaxSize(5);

        return fixturePoolArb.flatMap(fixturePool -> {
            // Generate subscriptions: mix of specific fixture and null (all events)
            Arbitrary<Map<String, String>> subscriptionsArb = generateSubscriptions(fixturePool);

            // Generate events with fixtureIds from the pool
            Arbitrary<List<EventDto>> eventsArb = generateEvents(fixturePool);

            return Combinators.combine(subscriptionsArb, eventsArb)
                    .as(FilterScenario::new);
        });
    }

    private Arbitrary<Map<String, String>> generateSubscriptions(List<String> fixturePool) {
        // Each subscription is either a specific fixtureId or null (all events)
        // Use oneOf to mix specific fixture subscriptions with null (all events)
        Arbitrary<String> specificFixture = Arbitraries.of(fixturePool);
        Arbitrary<String> nullFixture = Arbitraries.just((String) null);
        Arbitrary<String> fixtureIdOrNull = Arbitraries.frequencyOf(
                Tuple.of(3, specificFixture),
                Tuple.of(1, nullFixture)
        );

        return Arbitraries.integers().between(1, 5).flatMap(count -> {
            List<Arbitrary<String>> fixtureArbs = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                fixtureArbs.add(fixtureIdOrNull);
            }
            return Combinators.combine(fixtureArbs).as(fixtureIds -> {
                Map<String, String> map = new LinkedHashMap<>();
                for (int i = 0; i < fixtureIds.size(); i++) {
                    map.put("emitter-" + i, fixtureIds.get(i));
                }
                return map;
            });
        });
    }

    private Arbitrary<List<EventDto>> generateEvents(List<String> fixturePool) {
        Arbitrary<EventDto> eventArb = Combinators.combine(
                Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(10),
                Arbitraries.of(SPORT_TYPES),
                Arbitraries.of("Goal", "Foul", "Penalty", "Timeout", "Substitution"),
                Arbitraries.longs().between(1_000_000_000L, 2_000_000_000L),
                Arbitraries.of(fixturePool)
        ).as((id, sport, action, ts, fixtureId) ->
                new EventDto(
                        "evt-" + id,
                        sport,
                        action,
                        Map.of("home", "TeamA", "away", "TeamB"),
                        ts,
                        fixtureId
                )
        );

        return eventArb.list().ofMinSize(1).ofMaxSize(15);
    }

    /**
     * Holds the generated scenario data for the property test.
     */
    static class FilterScenario {
        final Map<String, String> subscriptions; // emitterId -> subscribedFixtureId (null = all)
        final List<EventDto> events;

        FilterScenario(Map<String, String> subscriptions, List<EventDto> events) {
            this.subscriptions = subscriptions;
            this.events = events;
        }

        @Override
        public String toString() {
            return String.format("FilterScenario{subscriptions=%s, eventCount=%d, eventFixtures=%s}",
                    subscriptions, events.size(),
                    events.stream().map(EventDto::fixtureId).toList());
        }
    }
}
