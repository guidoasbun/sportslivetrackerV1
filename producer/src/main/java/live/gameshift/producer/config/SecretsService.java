package live.gameshift.producer.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;

@Service
public class SecretsService {

    private final String apiSportsKey;

    public SecretsService(
            @Value("${aws.region}") String region,
            @Value("${aws.secrets.api-sports-key-arn}") String secretArn) {

        SecretsManagerClient client = SecretsManagerClient.builder()
                .region(Region.of(region))
                .build();

        GetSecretValueRequest request = GetSecretValueRequest.builder()
                .secretId(secretArn)
                .build();

        this.apiSportsKey = client.getSecretValue(request).secretString();

        client.close();
    }

    public String getApiSportsKey() {
        return apiSportsKey;
    }
}
