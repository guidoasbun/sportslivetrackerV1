package live.gameshift.producer.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;

@Service
public class SecretsService {

    private static final Logger log = LoggerFactory.getLogger(SecretsService.class);

    private final String apiSportsKey;

    public SecretsService(AppProperties props) {
        if (props.getApi().getSports().isMockMode()) {
            this.apiSportsKey = "mock-key";
            log.info("Mock mode enabled — using placeholder API key");
            return;
        }

        String secretArn = props.getAws().getSecrets().getApiSportsKeyArn();
        log.info("Fetching API-Sports key from Secrets Manager: {}", secretArn);

        try (SecretsManagerClient client = SecretsManagerClient.builder()
                .region(Region.of(props.getAws().getRegion()))
                .build()) {

            GetSecretValueRequest request = GetSecretValueRequest.builder()
                    .secretId(secretArn)
                    .build();

            this.apiSportsKey = client.getSecretValue(request).secretString();
            log.info("Successfully retrieved API-Sports key from Secrets Manager");

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to retrieve API-Sports key from Secrets Manager (ARN: " + secretArn + "). " +
                    "Ensure the ARN is correct and the ECS Task Role has secretsmanager:GetSecretValue permission.",
                    e);
        }
    }

    public String getApiSportsKey() {
        return apiSportsKey;
    }
}
