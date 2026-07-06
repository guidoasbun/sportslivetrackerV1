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
public class Formula1Normalizer implements SportNormalizer {

    private static final Logger log = LoggerFactory.getLogger(Formula1Normalizer.class);
    private final ObjectMapper objectMapper;

    public Formula1Normalizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public SportType getSportType() {
        return SportType.FORMULA_1;
    }

    @Override
    public Optional<SportEvent> normalize(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        JsonNode responseArray = root.path("response");

        if (!responseArray.isArray() || responseArray.isEmpty()) {
            log.info("No live formula-1 data, skipping");
            return Optional.empty();
        }

        JsonNode fixture = responseArray.path(0);
        JsonNode fixtureIdNode = fixture.path("fixture").path("id");
        String fixtureId = fixtureIdNode.isMissingNode() || fixtureIdNode.isNull() ? null : fixtureIdNode.asText();

        // F1 API structure differs — teams/drivers have logo fields at different paths
        String driver = fixture.path("teams").path("home").path("name").asText("Unknown");
        String team = fixture.path("teams").path("away").path("name").asText("Unknown");
        String driverLogo = fixture.path("teams").path("home").path("logo").asText("");
        String teamLogo = fixture.path("teams").path("away").path("logo").asText("");

        Map<String, String> participants = new java.util.LinkedHashMap<>();
        participants.put("driver", driver);
        participants.put("team", team);
        if (!driverLogo.isBlank()) participants.put("driverLogo", driverLogo);
        if (!teamLogo.isBlank()) participants.put("teamLogo", teamLogo);

        SportEvent event = new SportEvent(
                UUID.randomUUID().toString(),
                SportType.FORMULA_1,
                "NS",
                participants,
                json
        );
        event.setFixtureId(fixtureId);

        return Optional.of(event);
    }
}
