package live.gameshift.api.controller;

import live.gameshift.api.config.SecurityConfig;
import live.gameshift.api.service.SseEmitterService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

@WebMvcTest(EventController.class)
@Import(SecurityConfig.class)
class EventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SseEmitterService sseEmitterService;

    @Test
    void streamEvents_shouldReturnSseEmitter() throws Exception {
        SseEmitter mockEmitter = new SseEmitter(0L);
        when(sseEmitterService.createEmitter(any(String.class), any())).thenReturn(mockEmitter);

        mockMvc.perform(get("/api/events/stream")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());

        verify(sseEmitterService).createEmitter("ALL", null);
    }

    @Test
    void streamEvents_withFixtureId_shouldPassToService() throws Exception {
        SseEmitter mockEmitter = new SseEmitter(0L);
        when(sseEmitterService.createEmitter(any(String.class), any(String.class))).thenReturn(mockEmitter);

        mockMvc.perform(get("/api/events/stream")
                        .param("fixtureId", "fixture-42")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());

        verify(sseEmitterService).createEmitter("ALL", "fixture-42");
    }

    @Test
    void streamEvents_withSportParam_shouldPassToService() throws Exception {
        SseEmitter mockEmitter = new SseEmitter(0L);
        when(sseEmitterService.createEmitter(any(String.class), any())).thenReturn(mockEmitter);

        mockMvc.perform(get("/api/events/stream")
                        .param("sport", "SOCCER")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());

        verify(sseEmitterService).createEmitter("SOCCER", null);
    }

    @Test
    void streamEvents_withSportAndFixture_shouldPassBothToService() throws Exception {
        SseEmitter mockEmitter = new SseEmitter(0L);
        when(sseEmitterService.createEmitter(any(String.class), any(String.class))).thenReturn(mockEmitter);

        mockMvc.perform(get("/api/events/stream")
                        .param("sport", "BASKETBALL")
                        .param("fixtureId", "fixture-99")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());

        verify(sseEmitterService).createEmitter("BASKETBALL", "fixture-99");
    }
}
