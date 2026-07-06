package live.gameshift.api.controller;

import live.gameshift.api.service.TtsService;
import live.gameshift.api.service.TtsSynthesisException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TtsController.class)
@AutoConfigureMockMvc(addFilters = false)
class TtsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TtsService ttsService;

    @Test
    void synthesize_validRequest_returns200WithAudioMpeg() throws Exception {
        byte[] audioBytes = new byte[]{0x01, 0x02, 0x03, 0x04};
        when(ttsService.synthesize("Goal scored!", "Matthew")).thenReturn(audioBytes);

        mockMvc.perform(post("/api/tts/synthesize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Goal scored!\",\"voiceId\":\"Matthew\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("audio/mpeg"));
    }

    @Test
    void synthesize_emptyText_returns400() throws Exception {
        when(ttsService.synthesize("", "Matthew"))
                .thenThrow(new IllegalArgumentException("Commentary text is required"));

        mockMvc.perform(post("/api/tts/synthesize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"\",\"voiceId\":\"Matthew\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Commentary text is required"));
    }

    @Test
    void synthesize_textExceeds3000Chars_returns400() throws Exception {
        String longText = "a".repeat(3001);
        when(ttsService.synthesize(longText, "Matthew"))
                .thenThrow(new IllegalArgumentException("Text exceeds maximum length of 3000 characters"));

        mockMvc.perform(post("/api/tts/synthesize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"" + longText + "\",\"voiceId\":\"Matthew\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Text exceeds maximum length of 3000 characters"));
    }

    @Test
    void synthesize_invalidVoiceId_returns400() throws Exception {
        when(ttsService.synthesize("Hello", "InvalidVoice"))
                .thenThrow(new IllegalArgumentException("Unsupported voice. Supported voices: Matthew, Joanna, Liam, Ruth"));

        mockMvc.perform(post("/api/tts/synthesize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Hello\",\"voiceId\":\"InvalidVoice\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Unsupported voice. Supported voices: Matthew, Joanna, Liam, Ruth"));
    }

    @Test
    void synthesize_pollyFailure_returns503() throws Exception {
        when(ttsService.synthesize(anyString(), anyString()))
                .thenThrow(new TtsSynthesisException("Speech synthesis is temporarily unavailable", new RuntimeException()));

        mockMvc.perform(post("/api/tts/synthesize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Hello\",\"voiceId\":\"Matthew\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("Speech synthesis is temporarily unavailable"));
    }

    @Test
    void synthesize_malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/api/tts/synthesize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Request body is malformed"));
    }
}
