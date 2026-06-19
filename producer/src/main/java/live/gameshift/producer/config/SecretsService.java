package live.gameshift.producer.config;

import org.springframework.stereotype.Service;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;

@Service
public class SecretsService {

    private final String apiSportsKey;

    public SecretsService(AppProperties props) {
        if (props.getApi().getSports().isMockMode()) {
            this.apiSportsKey = "mock-key";
            return;
        }

        SecretsManagerClient client = SecretsManagerClient.builder()
                .region(Region.of(props.getAws().getRegion()))
                .build();

        GetSecretValueRequest request = GetSecretValueRequest.builder()
                .secretId(props.getAws().getSecrets().getApiSportsKeyArn())
                .build();

        this.apiSportsKey = client.getSecretValue(request).secretString();
        client.close();
    }

    public String getApiSportsKey() {
        return apiSportsKey;
    }
}
