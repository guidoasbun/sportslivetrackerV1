package live.gameshift.producer.service;

import live.gameshift.producer.client.ApiSportsClient;
import live.gameshift.producer.config.AppProperties;
import live.gameshift.producer.config.KinesisService;
import live.gameshift.producer.model.SportEvent;
import live.gameshift.producer.model.SportType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class PollingService {

    private static final Logger log = LoggerFactory.getLogger(PollingService.class);

    private final ApiSportsClient apiSportsClient;
    private final KinesisService kinesisService;
    private final AppProperties props;

    public PollingService(ApiSportsClient apiSportsClient, KinesisService kinesisService, AppProperties props) {
        this.apiSportsClient = apiSportsClient;
        this.kinesisService = kinesisService;
        this.props = props;
    }

    @Scheduled(fixedRateString = "${app.api.sports.poll-interval-ms}")
    public void poll() {
        for (SportType sportType : props.getApi().getSports().getConfigs().keySet()) {
            try {
                Optional<SportEvent> event = apiSportsClient.fetchLatestEvent(sportType);
                if (event.isEmpty()) {
                    log.info("No live fixture data for {}, skipping publish", sportType);
                    continue;
                }
                kinesisService.publish(event.get());
                log.info("Published event: sport={} action={} participants={}",
                        event.get().getSportType(), event.get().getAction(), event.get().getParticipants());
            } catch (Exception e) {
                log.error("Poll cycle failed for sport: {}", sportType, e);
            }
        }
    }
}
