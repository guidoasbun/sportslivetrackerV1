package live.gameshift.producer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.kinesis.KinesisClient;

// Defines long-lived AWS SDK clients as Spring beans.
// SecretsManagerClient is intentionally excluded — it is only needed once at startup
// to fetch the API key, so SecretsService creates and closes it inline rather than
// keeping a connection open for the lifetime of the application.
@Configuration
public class AwsConfig {

    @Bean
    KinesisClient kinesisClient(AppProperties props) {
        return KinesisClient.builder()
                .region(Region.of(props.getAws().getRegion()))
                .build();
    }
}
