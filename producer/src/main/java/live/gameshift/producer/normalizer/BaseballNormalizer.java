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
public class BaseballNormalizer implements SportNormalizer {

    private static final Logger log = LoggerFactory.getLogger(BaseballNormalizer.class);
    private final ObjectMapper objectMapper;

    public BaseballNormalizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public SportType getSportType() {
        return SportType.BASEBALL;
    }

    @Override
    public Optional<SportEvent> normalize(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        JsonNode responseArray = root.path("response");

        if (!responseArray.isArray() || responseArray.isEmpty()) {
            log.info("No live baseball data, skipping");
            return Optional.empty();
        }

        return Optional.of(new SportEvent(
                UUID.randomUUID().toString(),
                SportType.BASEBALL,
                "NS",
                Map.of("home", "Unknown", "away", "Unknown"),
                json
        ));
    }
}
