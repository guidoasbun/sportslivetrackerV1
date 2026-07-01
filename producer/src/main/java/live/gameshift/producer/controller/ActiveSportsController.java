package live.gameshift.producer.controller;

import live.gameshift.producer.model.SportType;
import live.gameshift.producer.service.SeasonFilterService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Internal endpoint exposing the producer's active sports state.
 * Called by the API service to determine which sports are currently in-season.
 */
@RestController
@RequestMapping("/internal")
public class ActiveSportsController {

    private final SeasonFilterService seasonFilterService;

    public ActiveSportsController(SeasonFilterService seasonFilterService) {
        this.seasonFilterService = seasonFilterService;
    }

    @GetMapping("/sports/active")
    public List<SportType> getActiveSports() {
        return seasonFilterService.getActiveSports();
    }
}
