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

        return Optional.of(new SportEvent(
                UUID.randomUUID().toString(),
                SportType.FORMULA_1,
                "NS",
                Map.of("driver", "Unknown", "team", "Unknown"),
                json
        ));
    }
}
