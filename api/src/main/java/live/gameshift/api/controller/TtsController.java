package live.gameshift.api.controller;

import live.gameshift.api.dto.TtsErrorResponse;
import live.gameshift.api.dto.TtsSynthesizeRequest;
import live.gameshift.api.service.TtsService;
import live.gameshift.api.service.TtsSynthesisException;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tts")
public class TtsController {

    private final TtsService ttsService;

    public TtsController(TtsService ttsService) {
        this.ttsService = ttsService;
    }

    @PostMapping(value = "/synthesize", produces = "audio/mpeg")
    public ResponseEntity<byte[]> synthesize(@RequestBody TtsSynthesizeRequest request) {
        byte[] audioBytes = ttsService.synthesize(request.text(), request.voiceId());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("audio/mpeg"))
                .body(audioBytes);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<TtsErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(new TtsErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(TtsSynthesisException.class)
    public ResponseEntity<TtsErrorResponse> handleTtsSynthesis(TtsSynthesisException ex) {
        return ResponseEntity.status(503).body(new TtsErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<TtsErrorResponse> handleMalformedBody(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(new TtsErrorResponse("Request body is malformed"));
    }
}
