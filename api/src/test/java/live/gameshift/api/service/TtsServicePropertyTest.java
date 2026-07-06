package live.gameshift.api.service;

import net.jqwik.api.*;
import net.jqwik.api.constraints.*;
import org.mockito.ArgumentCaptor;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.polly.PollyClient;
import software.amazon.awssdk.services.polly.model.*;

import java.io.ByteArrayInputStream;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for TtsService.
 *
 * Validates: Requirements 1.2, 1.3, 1.4, 1.5, 1.6, 1.8, 6.1, 6.2, 6.3, 6.4, 6.5, 6.6
 */
class TtsServicePropertyTest {

    private static final Set<String> SUPPORTED_VOICES = Set.of("Matthew", "Joanna", "Liam", "Ruth");
    private static final String DEFAULT_VOICE = "Matthew";

    /**
     * Property 1: Valid request produces correct Polly invocation
     *
     * For any valid commentary text (1 to 3000 characters, non-blank) and for any voiceId
     * in {Matthew, Joanna, Liam, Ruth, null}, the TtsService SHALL invoke Polly with
     * engine=NEURAL, outputFormat=MP3, sampleRate=24000, textType=TEXT, and voiceId equal
     * to the provided value (or "Matthew" if null), and return the Polly audio bytes.
     *
     * Validates: Requirements 1.2, 1.3, 6.1, 6.2, 6.4, 6.5
     */
    @Property(tries = 100)
    @Tag("Feature: polly-commentary-tts, Property 1: Valid request produces correct Polly invocation")
    void validRequestProducesCorrectPollyInvocation(
            @ForAll("validText") String text,
            @ForAll("validOrNullVoiceId") String voiceId) throws Exception {

        // Arrange
        PollyClient mockPollyClient = mock(PollyClient.class);
        byte[] expectedAudioBytes = new byte[]{0x01, 0x02, 0x03, 0x04};

        SynthesizeSpeechResponse response = SynthesizeSpeechResponse.builder().build();
        ResponseInputStream<SynthesizeSpeechResponse> responseStream =
                new ResponseInputStream<>(response,
                        AbortableInputStream.create(new ByteArrayInputStream(expectedAudioBytes)));

        when(mockPollyClient.synthesizeSpeech(any(SynthesizeSpeechRequest.class)))
                .thenReturn(responseStream);

        TtsService service = new TtsService(mockPollyClient);

        // Act
        byte[] result = service.synthesize(text, voiceId);

        // Assert - verify correct Polly invocation parameters
        ArgumentCaptor<SynthesizeSpeechRequest> requestCaptor =
                ArgumentCaptor.forClass(SynthesizeSpeechRequest.class);
        verify(mockPollyClient).synthesizeSpeech(requestCaptor.capture());

        SynthesizeSpeechRequest capturedRequest = requestCaptor.getValue();
        assertEquals(Engine.NEURAL, capturedRequest.engine());
        assertEquals(OutputFormat.MP3, capturedRequest.outputFormat());
        assertEquals("24000", capturedRequest.sampleRate());
        assertEquals(TextType.TEXT, capturedRequest.textType());
        assertEquals(text, capturedRequest.text());

        String expectedVoice = voiceId == null ? DEFAULT_VOICE : voiceId;
        assertEquals(VoiceId.fromValue(expectedVoice), capturedRequest.voiceId());

        // Assert - verify audio bytes are returned
        assertArrayEquals(expectedAudioBytes, result);
    }

    /**
     * Property 2: Invalid text is rejected
     *
     * For any text string that is null, empty, composed entirely of whitespace, or exceeds
     * 3000 characters, the TtsService SHALL reject the request with an IllegalArgumentException
     * without invoking the Polly client.
     *
     * Validates: Requirements 1.4, 1.5
     */
    @Property(tries = 100)
    @Tag("Feature: polly-commentary-tts, Property 2: Invalid text is rejected")
    void invalidTextIsRejected(@ForAll("invalidText") String text) {
        // Arrange
        PollyClient mockPollyClient = mock(PollyClient.class);
        TtsService service = new TtsService(mockPollyClient);

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> service.synthesize(text, "Matthew"));

        // Verify Polly was never called
        verify(mockPollyClient, never()).synthesizeSpeech(any(SynthesizeSpeechRequest.class));
    }

    /**
     * Property 3: Invalid voiceId is rejected
     *
     * For any voiceId string that is not in the set {Matthew, Joanna, Liam, Ruth}, the TtsService
     * SHALL reject the request with an IllegalArgumentException without invoking the Polly client.
     *
     * Validates: Requirements 1.8, 6.3
     */
    @Property(tries = 100)
    @Tag("Feature: polly-commentary-tts, Property 3: Invalid voiceId is rejected")
    void invalidVoiceIdIsRejected(
            @ForAll("validText") String text,
            @ForAll("invalidVoiceId") String voiceId) {

        // Arrange
        PollyClient mockPollyClient = mock(PollyClient.class);
        TtsService service = new TtsService(mockPollyClient);

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> service.synthesize(text, voiceId));

        // Verify Polly was never called
        verify(mockPollyClient, never()).synthesizeSpeech(any(SynthesizeSpeechRequest.class));
    }

    /**
     * Property 4: Polly failure produces service unavailable
     *
     * For any valid synthesis request, if the Polly client throws any exception (PollyException,
     * SdkClientException), the TtsService SHALL throw a TtsSynthesisException without propagating
     * the original exception.
     *
     * Validates: Requirements 1.6, 6.6
     */
    @Property(tries = 100)
    @Tag("Feature: polly-commentary-tts, Property 4: Polly failure produces service unavailable")
    void pollyFailureProducesServiceUnavailable(
            @ForAll("validText") String text,
            @ForAll("validOrNullVoiceId") String voiceId,
            @ForAll("pollyException") RuntimeException pollyException) {

        // Arrange
        PollyClient mockPollyClient = mock(PollyClient.class);
        when(mockPollyClient.synthesizeSpeech(any(SynthesizeSpeechRequest.class)))
                .thenThrow(pollyException);

        TtsService service = new TtsService(mockPollyClient);

        // Act & Assert
        TtsSynthesisException thrown = assertThrows(TtsSynthesisException.class,
                () -> service.synthesize(text, voiceId));

        // Verify TtsSynthesisException wraps the cause and does not propagate the original
        assertEquals("Speech synthesis is temporarily unavailable", thrown.getMessage());
        assertSame(pollyException, thrown.getCause());
    }

    // --- Arbitrary Providers ---

    @Provide
    Arbitrary<String> validText() {
        // Generate non-blank text between 1 and 3000 characters
        // Use alphanumeric + spaces to ensure non-blank content
        return Arbitraries.strings()
                .ofMinLength(1)
                .ofMaxLength(3000)
                .alpha()
                .ofMinLength(1);
    }

    @Provide
    Arbitrary<String> validOrNullVoiceId() {
        // Generate either a valid voice or null
        Arbitrary<String> validVoice = Arbitraries.of("Matthew", "Joanna", "Liam", "Ruth");
        Arbitrary<String> nullVoice = Arbitraries.just(null);
        return Arbitraries.oneOf(validVoice, nullVoice);
    }

    @Provide
    Arbitrary<String> invalidText() {
        // Generate text that should be rejected: null, empty, whitespace-only, or > 3000 chars
        Arbitrary<String> nullText = Arbitraries.just(null);
        Arbitrary<String> emptyText = Arbitraries.just("");
        Arbitrary<String> whitespaceOnly = Arbitraries.of(" ", "  ", "\t", "\n", " \t\n ", "   \n\t  ");
        Arbitrary<String> tooLong = Arbitraries.strings()
                .alpha()
                .ofMinLength(3001)
                .ofMaxLength(4000);

        return Arbitraries.oneOf(nullText, emptyText, whitespaceOnly, tooLong);
    }

    @Provide
    Arbitrary<String> invalidVoiceId() {
        // Generate strings that are NOT in {Matthew, Joanna, Liam, Ruth}
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(1)
                .ofMaxLength(20)
                .filter(s -> !SUPPORTED_VOICES.contains(s));
    }

    @Provide
    Arbitrary<RuntimeException> pollyException() {
        // Generate different types of Polly/AWS exceptions
        Arbitrary<RuntimeException> pollyEx = Arbitraries.of(
                PollyException.builder().message("Service error").build(),
                PollyException.builder().message("Throttling").build(),
                PollyException.builder().message("Internal server error").build()
        );

        Arbitrary<RuntimeException> sdkClientEx = Arbitraries.of(
                SdkClientException.builder().message("Connection timeout").build(),
                SdkClientException.builder().message("Network unreachable").build(),
                SdkClientException.builder().message("Request timed out").build()
        );

        return Arbitraries.oneOf(pollyEx, sdkClientEx);
    }
}
