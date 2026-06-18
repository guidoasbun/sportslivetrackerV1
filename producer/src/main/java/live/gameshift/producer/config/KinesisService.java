package live.gameshift.producer.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import live.gameshift.producer.model.SportEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.PutRecordRequest;

@Service
public class KinesisService {

    private final KinesisClient kinesisClient;
    private final String streamName;
    private final ObjectMapper objectMapper;

    public KinesisService(
            @Value("${aws.region}") String region,
            @Value("${aws.kinesis.stream-name}") String streamName) {

        this.kinesisClient = KinesisClient.builder()
                .region(Region.of(region))
                .build();

        this.streamName = streamName;

        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    public void publish(SportEvent event) throws Exception {
        byte[] json = objectMapper.writeValueAsBytes(event);

        PutRecordRequest request = PutRecordRequest.builder()
                .streamName(streamName)
                .partitionKey(event.getSportType())
                .data(SdkBytes.fromByteArray(json))
                .build();

        kinesisClient.putRecord(request);
    }
}
