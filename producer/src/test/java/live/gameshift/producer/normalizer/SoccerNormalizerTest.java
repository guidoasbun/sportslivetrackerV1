package live.gameshift.producer.normalizer;

import live.gameshift.producer.model.SportEvent;
import live.gameshift.producer.model.SportType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SoccerNormalizerTest {

    private final SoccerNormalizer normalizer = new SoccerNormalizer(new com.fasterxml.jackson.databind.ObjectMapper());

    @BeforeEach
    void setUp() {
    }

    @Test
    void normalize_returnsEmptyWhenNoLiveFixture() throws Exception {
        String emptyResponse = "{\"response\": []}";

        Optional<SportEvent> result = normalizer.normalize(emptyResponse);

        assertTrue(result.isEmpty());
    }

    @Test
    void normalize_parsesLiveFixture() throws Exception {
        String response = """
                {
                  "response": [{
                    "fixture": {
                      "status": { "short": "1H", "elapsed": 42 }
                    },
                    "teams": {
                      "home": { "name": "Arsenal" },
                      "away": { "name": "Chelsea" }
                    },
                    "score": {
                      "fulltime": { "home": 1, "away": 0 }
                    }
                  }]
                }
                """;

        Optional<SportEvent> result = normalizer.normalize(response);

        assertTrue(result.isPresent());
        SportEvent event = result.get();
        assertEquals(SportType.SOCCER, event.getSportType());
        assertEquals("1H", event.getAction());
        assertEquals("Arsenal", event.getParticipants().get("home"));
        assertEquals("Chelsea", event.getParticipants().get("away"));
        assertNotNull(event.getRawPayload());
    }
}
