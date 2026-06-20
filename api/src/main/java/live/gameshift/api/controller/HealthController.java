package live.gameshift.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/api/health")
    public ResponseEntity<String> healthCheck() {
        // As long as we return HTTP 200 OK, AWS knows we are alive!
        return ResponseEntity.ok("OK");
    }
}
