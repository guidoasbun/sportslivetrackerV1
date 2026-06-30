package live.gameshift.producer.health;

import live.gameshift.producer.config.AppProperties;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.DescribeStreamRequest;

/**
 * Custom health indicator that verifies connectivity to the configured Kinesis stream.
 * When the stream is unreachable or any error occurs, the actuator /health endpoint
 * returns HTTP 503 with {"status": "DOWN"}.
 */
@Component
public class KinesisHealthIndicator implements HealthIndicator {

    private final KinesisClient kinesisClient;
    private final String streamName;

    public KinesisHealthIndicator(KinesisClient kinesisClient, AppProperties props) {
        this.kinesisClient = kinesisClient;
        this.streamName = props.getAws().getKinesis().getStreamName();
    }

    @Override
    public Health health() {
        try {
            kinesisClient.describeStream(
                    DescribeStreamRequest.builder()
                            .streamName(streamName)
                            .build()
            );
            return Health.up().build();
        } catch (Exception e) {
            return Health.down(e).build();
        }
    }
}
