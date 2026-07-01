package live.gameshift.producer.normalizer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import live.gameshift.producer.model.SportEvent;
import live.gameshift.producer.model.SportType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class BasketballNormalizer implements SportNormalizer {

    private static final Logger log = LoggerFactory.getLogger(BasketballNormalizer.class);
    private final ObjectMapper objectMapper;

    public BasketballNormalizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public SportType getSportType() {
        return SportType.BASKETBALL;
    }

    @Override
    public Optional<SportEvent> normalize(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        JsonNode responseArray = root.path("response");

        if (!responseArray.isArray() || responseArray.isEmpty()) {
            log.info("No live basketball data, skipping");
            return Optional.empty();
        }

        JsonNode fixture = responseArray.path(0);
        JsonNode fixtureIdNode = fixture.path("id");
        String fixtureId = fixtureIdNode.isMissingNode() || fixtureIdNode.isNull() ? null : String.valueOf(fixtureIdNode.asInt());

        SportEvent event = new SportEvent(
                UUID.randomUUID().toString(),
                SportType.BASKETBALL,
                "NS",
                Map.of("home", "Unknown", "away", "Unknown"),
                json
        );
        event.setFixtureId(fixtureId);

        return Optional.of(event);
    }
}
