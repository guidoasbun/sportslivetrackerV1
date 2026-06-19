package live.gameshift.lambda.service;

import live.gameshift.lambda.model.SportEvent;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class BedrockCommentaryService {

    private final BedrockRuntimeClient bedrockClient;
    private final ObjectMapper objectMapper;
    // We use the model ID passed in securely via Terraform environment variables
    private final String modelId = System.getenv("BEDROCK_MODEL_ID");

    public BedrockCommentaryService(ObjectMapper objectMapper) {
        // Initializes Bedrock client and automatically uses Lambda's IAM permissions
        this.bedrockClient = BedrockRuntimeClient.builder().build();
        this.objectMapper = objectMapper;
    }

    public String generateCommentary(SportEvent event) {
        String prompt = buildPrompt(event);

        try {
            // Build the JSON payload required by Anthropic's Messages API
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("anthropic_version", "bedrock-2023-05-31");
            payload.put("max_tokens", 150);

            ObjectNode message = payload.putArray("messages").addObject();
            message.put("role", "user");
            message.putArray("content").addObject().put("type", "text").put("text", prompt);

            InvokeModelRequest request = InvokeModelRequest.builder()
                    .modelId(modelId)
                    .contentType("application/json")
                    .accept("application/json")
                    .body(SdkBytes.fromUtf8String(payload.toString()))
                    .build();

            // Make the actual HTTP call to AWS Bedrock
            InvokeModelResponse response = bedrockClient.invokeModel(request);

            // Parse the response from Bedrock to grab just the text
            String responseBody = response.body().asUtf8String();
            return objectMapper.readTree(responseBody)
                    .get("content").get(0).get("text").asText();

        } catch (Exception e) {
            System.err.println("Failed to generate commentary: " + e.getMessage());
            // Fallback string if Bedrock is down or rate-limited
            return "Exciting " + event.getSportType().name().toLowerCase() + " action just happened!";
        }
    }

    private String buildPrompt(SportEvent event) {
        // Switch expressions (Java 14+) map each sport to a unique AI personality
        String sportContext = switch (event.getSportType()) {
            case SOCCER ->
                "You are a passionate English soccer commentator. Describe this event with extreme excitement.";
            case FOOTBALL ->
                "You are an American football color commentator. Give a gritty, tactical summary of this play.";
            case BASKETBALL -> "You are an energetic NBA announcer. React to this play like it's a playoff game.";
            case BASEBALL -> "You are a classic baseball radio broadcaster. Describe this play vividly.";
            case HOCKEY -> "You are an intense NHL commentator. Speak fast and focus on the physicality.";
            case FORMULA_1 -> "You are a frantic F1 commentator. Focus on speed, strategy, and overtaking.";
        };

        return String.format(
                "%s The event is a '%s' involving these participants: %s. Give me exactly 1-2 thrilling sentences of commentary. Do not say 'Here is the commentary'.",
                sportContext, event.getAction(), event.getParticipants());
    }
}
