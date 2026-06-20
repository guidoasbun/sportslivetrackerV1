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
                Optional<SportEvent> event;
                if (props.getApi().getSports().isMockMode()) {
                    event = generateMockEvent(sportType);
                } else {
                    event = apiSportsClient.fetchLatestEvent(sportType);
                }
                
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

    private Optional<SportEvent> generateMockEvent(SportType sportType) {
        SportEvent event = new SportEvent();
        event.setEventId(java.util.UUID.randomUUID().toString());
        event.setSportType(sportType);
        event.setEventTimestamp(System.currentTimeMillis());
        event.setRawPayload("{ \"mock\": true }");

        java.util.Map<String, String> participants = new java.util.HashMap<>();
        
        switch (sportType) {
            case SOCCER:
                event.setAction("GOAL");
                participants.put("scorer", "Mock Player");
                participants.put("team", "Mock Team FC");
                break;
            case BASKETBALL:
                event.setAction("3_POINTER");
                participants.put("player", "Mock Shooter");
                participants.put("team", "Mock City Hoops");
                break;
            case FOOTBALL:
                event.setAction("TOUCHDOWN");
                participants.put("player", "Mock Quarterback");
                participants.put("team", "Mock City Eagles");
                break;
            default:
                event.setAction("SCORE");
                participants.put("player", "Mock Athlete");
                break;
        }
        event.setParticipants(participants);
        return Optional.of(event);
    }
}
