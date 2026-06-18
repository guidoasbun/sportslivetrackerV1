package live.gameshift.producer.normalizer;

import live.gameshift.producer.model.SportEvent;

import java.util.Optional;

public interface SportNormalizer {
    live.gameshift.producer.model.SportType getSportType();
    Optional<SportEvent> normalize(String json) throws Exception;
}
