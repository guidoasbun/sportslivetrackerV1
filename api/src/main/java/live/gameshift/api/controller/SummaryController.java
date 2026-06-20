package live.gameshift.api.controller;

import live.gameshift.api.dto.SummaryDto;
import live.gameshift.api.service.SummaryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/summaries")
public class SummaryController {

    private final SummaryService summaryService;

    public SummaryController(SummaryService summaryService) {
        this.summaryService = summaryService;
    }

    @GetMapping("/event/{eventId}")
    public ResponseEntity<SummaryDto> getSummary(@PathVariable String eventId) {
        // If the summary exists, return HTTP 200 OK with the JSON body.
        // If the AI hasn't written it yet (Optional is empty), return HTTP 404 Not Found.
        return summaryService.getSummaryForEvent(eventId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
