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
public class HockeyNormalizer implements SportNormalizer {

    private static final Logger log = LoggerFactory.getLogger(HockeyNormalizer.class);
    private final ObjectMapper objectMapper;

    public HockeyNormalizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public SportType getSportType() {
        return SportType.HOCKEY;
    }

    @Override
    public Optional<SportEvent> normalize(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        JsonNode responseArray = root.path("response");

        if (!responseArray.isArray() || responseArray.isEmpty()) {
            log.info("No live hockey data, skipping");
            return Optional.empty();
        }

        JsonNode fixture = responseArray.path(0);
        JsonNode fixtureIdNode = fixture.path("fixture").path("id");
        String fixtureId = fixtureIdNode.isMissingNode() || fixtureIdNode.isNull() ? null : fixtureIdNode.asText();

        String homeTeam = fixture.path("teams").path("home").path("name").asText("Unknown");
        String awayTeam = fixture.path("teams").path("away").path("name").asText("Unknown");
        String homeLogo = fixture.path("teams").path("home").path("logo").asText("");
        String awayLogo = fixture.path("teams").path("away").path("logo").asText("");

        Map<String, String> participants = new java.util.LinkedHashMap<>();
        participants.put("home", homeTeam);
        participants.put("away", awayTeam);
        if (!homeLogo.isBlank()) participants.put("homeLogo", homeLogo);
        if (!awayLogo.isBlank()) participants.put("awayLogo", awayLogo);

        SportEvent event = new SportEvent(
                UUID.randomUUID().toString(),
                SportType.HOCKEY,
                "NS",
                participants,
                json
        );
        event.setFixtureId(fixtureId);

        return Optional.of(event);
    }
}
