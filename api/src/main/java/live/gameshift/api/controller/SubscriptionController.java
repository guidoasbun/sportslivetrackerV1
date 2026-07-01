package live.gameshift.api.controller;

import live.gameshift.api.service.SubscriptionRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/subscriptions")
public class SubscriptionController {

    private final SubscriptionRegistry subscriptionRegistry;

    public SubscriptionController(SubscriptionRegistry subscriptionRegistry) {
        this.subscriptionRegistry = subscriptionRegistry;
    }

    public record ActiveSubscriptionDto(String sportType, String fixtureId, int subscriberCount) {}

    @GetMapping("/active")
    public List<ActiveSubscriptionDto> getActiveSubscriptions() {
        Map<String, Integer> active = subscriptionRegistry.getActiveSubscriptions();

        return active.entrySet().stream()
                .map(entry -> {
                    String[] parts = entry.getKey().split(":", 2);
                    String sportType = parts[0];
                    String fixtureId = parts.length > 1 ? parts[1] : "";
                    return new ActiveSubscriptionDto(sportType, fixtureId, entry.getValue());
                })
                .toList();
    }
}
