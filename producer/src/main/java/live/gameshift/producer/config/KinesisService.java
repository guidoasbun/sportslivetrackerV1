package live.gameshift.producer.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import live.gameshift.producer.model.SportEvent;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.PutRecordRequest;

@Service
public class KinesisService {

    private final KinesisClient kinesisClient;
    private final String streamName;
    private final ObjectMapper objectMapper;

    public KinesisService(KinesisClient kinesisClient, AppProperties props, ObjectMapper objectMapper) {
        this.kinesisClient = kinesisClient;
        this.streamName = props.getAws().getKinesis().getStreamName();
        this.objectMapper = objectMapper;
    }

    public void publish(SportEvent event) throws Exception {
        byte[] json = objectMapper.writeValueAsBytes(event);

        PutRecordRequest request = PutRecordRequest.builder()
                .streamName(streamName)
                .partitionKey(event.getSportType().name())
                .data(SdkBytes.fromByteArray(json))
                .build();

        kinesisClient.putRecord(request);
    }
}
