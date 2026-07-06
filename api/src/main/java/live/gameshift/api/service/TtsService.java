package live.gameshift.api.service;

import java.io.IOException;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.polly.PollyClient;
import software.amazon.awssdk.services.polly.model.Engine;
import software.amazon.awssdk.services.polly.model.OutputFormat;
import software.amazon.awssdk.services.polly.model.PollyException;
import software.amazon.awssdk.services.polly.model.SynthesizeSpeechRequest;
import software.amazon.awssdk.services.polly.model.SynthesizeSpeechResponse;
import software.amazon.awssdk.services.polly.model.TextType;
import software.amazon.awssdk.services.polly.model.VoiceId;

@Service
public class TtsService {

    private static final Logger log = LoggerFactory.getLogger(TtsService.class);

    private static final Set<String> SUPPORTED_VOICES = Set.of("Matthew", "Joanna", "Liam", "Ruth");
    private static final String DEFAULT_VOICE = "Matthew";
    private static final int MAX_TEXT_LENGTH = 3000;

    private final PollyClient pollyClient;

    public TtsService(PollyClient pollyClient) {
        this.pollyClient = pollyClient;
    }

    public byte[] synthesize(String text, String voiceId) {
        // Validate text
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Commentary text is required");
        }
        if (text.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("Text exceeds maximum length of 3000 characters");
        }

        // Resolve and validate voiceId
        String resolvedVoiceId = voiceId == null ? DEFAULT_VOICE : voiceId;
        if (!SUPPORTED_VOICES.contains(resolvedVoiceId)) {
            throw new IllegalArgumentException("Unsupported voice. Supported voices: Matthew, Joanna, Liam, Ruth");
        }

        // Build and execute the Polly synthesis request
        try {
            SynthesizeSpeechRequest request = SynthesizeSpeechRequest.builder()
                    .engine(Engine.NEURAL)
                    .outputFormat(OutputFormat.MP3)
                    .sampleRate("24000")
                    .textType(TextType.TEXT)
                    .voiceId(VoiceId.fromValue(resolvedVoiceId))
                    .text(text)
                    .build();

            try (ResponseInputStream<SynthesizeSpeechResponse> response = pollyClient.synthesizeSpeech(request)) {
                return response.readAllBytes();
            }
        } catch (PollyException e) {
            log.error("Polly service error during speech synthesis: {}", e.getMessage(), e);
            throw new TtsSynthesisException("Speech synthesis is temporarily unavailable", e);
        } catch (SdkClientException e) {
            log.error("AWS SDK client error during speech synthesis: {}", e.getMessage(), e);
            throw new TtsSynthesisException("Speech synthesis is temporarily unavailable", e);
        } catch (IOException e) {
            log.error("Error reading Polly response stream: {}", e.getMessage(), e);
            throw new TtsSynthesisException("Speech synthesis is temporarily unavailable", e);
        }
    }
}
