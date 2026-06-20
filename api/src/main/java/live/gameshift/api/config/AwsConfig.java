package live.gameshift.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@Configuration
public class AwsConfig {

    @Bean
    public DynamoDbClient dynamoDbClient() {
        // We use the builder to create the standard client.
        // It will automatically pick up AWS credentials from your environment 
        // (like ~/.aws/credentials or the ECS Task Role when deployed).
        return DynamoDbClient.builder()
                .region(Region.US_EAST_1) // Change this if your AWS region isn't us-east-1!
                .build();
    }

    @Bean
    public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient dynamoDbClient) {
        // We wrap the standard client in the Enhanced Client
        return DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .build();
    }
}
