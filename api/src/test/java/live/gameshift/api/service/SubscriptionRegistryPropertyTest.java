package live.gameshift.api.service;

import net.jqwik.api.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Property-based test for SubscriptionRegistry count invariant.
 *
 * Property 5: Subscription registry count invariant — generate connect/disconnect
 * sequences, verify count equals connects minus disconnects and never drops below zero.
 *
 * Validates: Requirements 15.1, 15.2, 15.3
 */
@Tag("Feature: app-completion-hardening, Property 5: Subscription registry count invariant")
class SubscriptionRegistryPropertyTest {

    private static final List<String> SPORT_TYPES = List.of(
            "SOCCER", "BASKETBALL", "FOOTBALL", "BASEBALL", "HOCKEY", "FORMULA_1"
    );

    /**
     * Property 5: Subscription registry count invariant
     *
     * For any sequence of increment (connect) and decrement (disconnect) operations
     * on any combination of sport/fixture keys:
     * 1. The final count for each key equals the model-simulated count
     *    (connects minus disconnects, floored at zero at each step)
     * 2. The count never drops below zero at any intermediate state
     *
     * Validates: Requirements 15.1, 15.2, 15.3
     */
    @Property(tries = 200)
    void subscriptionCountEqualsConnectsMinusDisconnectsNeverNegative(
            @ForAll("operationSequences") List<Operation> operations) {

        SubscriptionRegistry registry = new SubscriptionRegistry();

        // Model: track expected count per key, applying floor-at-zero at each step
        Map<String, Integer> modelCounts = new HashMap<>();

        // Apply operations and verify intermediate invariant (count >= 0)
        for (Operation op : operations) {
            String key = op.sportType + ":" + op.fixtureId;

            if (op.isConnect) {
                registry.increment(op.sportType, op.fixtureId);
                modelCounts.merge(key, 1, Integer::sum);
            } else {
                registry.decrement(op.sportType, op.fixtureId);
                // Model the floor-at-zero behavior: decrement but never below 0
                modelCounts.merge(key, 0, (current, unused) -> Math.max(0, current - 1));
            }

            // Invariant: count must never be negative at any point
            int currentCount = registry.getCount(op.sportType, op.fixtureId);
            assertTrue(currentCount >= 0,
                    String.format("Count went negative for %s: %d", key, currentCount));
        }

        // After all operations, verify final counts match the model
        for (Map.Entry<String, Integer> entry : modelCounts.entrySet()) {
            String key = entry.getKey();
            int expectedCount = entry.getValue();

            String[] parts = key.split(":", 2);
            int actualCount = registry.getCount(parts[0], parts[1]);

            assertEquals(expectedCount, actualCount,
                    String.format("Count mismatch for %s: expected %d, got %d",
                            key, expectedCount, actualCount));
        }
    }

    @Provide
    Arbitrary<List<Operation>> operationSequences() {
        // Generate fixture IDs as random alpha strings
        Arbitrary<String> fixtureIdArb = Arbitraries.strings()
                .alpha().ofMinLength(3).ofMaxLength(8)
                .map(s -> "fix-" + s);

        // Generate sport types from the defined set
        Arbitrary<String> sportTypeArb = Arbitraries.of(SPORT_TYPES);

        // Generate individual operations (connect or disconnect)
        Arbitrary<Operation> operationArb = Combinators.combine(
                sportTypeArb,
                fixtureIdArb,
                Arbitraries.of(true, false) // true = connect, false = disconnect
        ).as(Operation::new);

        // Generate sequences of operations, varying length up to 100
        return operationArb.list().ofMinSize(1).ofMaxSize(100);
    }

    /**
     * Represents a single connect or disconnect operation.
     */
    static class Operation {
        final String sportType;
        final String fixtureId;
        final boolean isConnect;

        Operation(String sportType, String fixtureId, boolean isConnect) {
            this.sportType = sportType;
            this.fixtureId = fixtureId;
            this.isConnect = isConnect;
        }

        @Override
        public String toString() {
            return String.format("%s(%s:%s)",
                    isConnect ? "CONNECT" : "DISCONNECT", sportType, fixtureId);
        }
    }
}
