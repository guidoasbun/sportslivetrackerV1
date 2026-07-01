package live.gameshift.api.service;

import live.gameshift.api.dto.EventDto;
import live.gameshift.api.model.enums.SportType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * [MOCK] Generates and broadcasts mock events directly to SSE emitters,
 * bypassing DynamoDB. Only active when app.mock-mode=true.
 *
 * Simulates the full pipeline: producer → Kinesis → Lambda → DynamoDB → API polling.
 * Events match the fixture IDs from the FixtureController mock data.
 */
@Service
@ConditionalOnProperty(name = "app.mock-mode", havingValue = "true")
public class MockEventGenerator {

    private static final Logger log = LoggerFactory.getLogger(MockEventGenerator.class);
    private static final String MOCK_TAG = "[MOCK] ";

    private final SseEmitterService sseEmitterService;

    // Track game state per fixture
    private final Map<String, FixtureState> fixtures = new LinkedHashMap<>();

    public MockEventGenerator(SseEmitterService sseEmitterService) {
        this.sseEmitterService = sseEmitterService;
        initializeFixtures();
        log.info("[MOCK] MockEventGenerator active — broadcasting simulated events every 5s");
    }

    /**
     * Generates mock events every 5 seconds (same cadence as the real EventService poll).
     * Picks a random fixture and broadcasts a realistic event for that sport.
     */
    @Scheduled(fixedDelay = 5000, initialDelay = 3000)
    public void generateAndBroadcast() {
        // Generate 1-3 events per cycle across random fixtures
        int eventsThisCycle = ThreadLocalRandom.current().nextInt(1, 4);

        List<FixtureState> fixtureList = new ArrayList<>(fixtures.values());
        for (int i = 0; i < eventsThisCycle; i++) {
            FixtureState fixture = fixtureList.get(ThreadLocalRandom.current().nextInt(fixtureList.size()));
            fixture.advanceTime();

            EventDto event = generateEvent(fixture);
            sseEmitterService.broadcast(event);

            log.debug("[MOCK] Broadcast: {} {} {} | {}-{} min={}",
                    fixture.sportType, fixture.fixtureId, event.action(),
                    fixture.homeScore, fixture.awayScore, fixture.gameMinute);
        }
    }

    private EventDto generateEvent(FixtureState fixture) {
        Map<String, String> participants = new LinkedHashMap<>();
        participants.put("home", fixture.homeTeam);
        participants.put("away", fixture.awayTeam);

        String action = switch (fixture.sportType) {
            case SOCCER -> generateSoccerAction(fixture, participants);
            case BASKETBALL -> generateBasketballAction(fixture, participants);
            case FOOTBALL -> generateFootballAction(fixture, participants);
            case BASEBALL -> generateBaseballAction(fixture, participants);
            case HOCKEY -> generateHockeyAction(fixture, participants);
            case FORMULA_1 -> generateF1Action(fixture, participants);
        };

        return new EventDto(
                UUID.randomUUID().toString(),
                fixture.sportType,
                MOCK_TAG + action,
                participants,
                System.currentTimeMillis(),
                fixture.fixtureId
        );
    }

    // ─── Soccer ──────────────────────────────────────────────────────────────────

    private String generateSoccerAction(FixtureState f, Map<String, String> p) {
        String[] actions = {"GOAL", "SHOT_ON_TARGET", "CORNER_KICK", "FREE_KICK", "YELLOW_CARD", "FOUL", "OFFSIDE", "SUBSTITUTION", "THROW_IN"};
        double[] weights = {0.06, 0.14, 0.12, 0.10, 0.08, 0.18, 0.08, 0.07, 0.17};
        String action = weighted(actions, weights);

        String[] homePlayers = {"Haaland", "De Bruyne", "Foden", "Rodri", "Grealish"};
        String[] awayPlayers = {"Vinícius Jr.", "Bellingham", "Mbappé", "Valverde", "Rüdiger"};
        boolean isHome = ThreadLocalRandom.current().nextBoolean();
        String player = isHome
                ? homePlayers[ThreadLocalRandom.current().nextInt(homePlayers.length)]
                : awayPlayers[ThreadLocalRandom.current().nextInt(awayPlayers.length)];

        p.put("player", player);
        p.put("team", isHome ? f.homeTeam : f.awayTeam);
        p.put("minute", f.gameMinute + "'");

        if ("GOAL".equals(action)) {
            if (isHome) f.homeScore++; else f.awayScore++;
        }
        p.put("score", f.homeScore + " - " + f.awayScore);
        return action;
    }

    // ─── Basketball ──────────────────────────────────────────────────────────────

    private String generateBasketballAction(FixtureState f, Map<String, String> p) {
        String[] actions = {"3_POINTER", "2_POINTER", "FREE_THROW", "DUNK", "BLOCK", "STEAL", "TURNOVER", "FOUL", "REBOUND"};
        double[] weights = {0.14, 0.22, 0.10, 0.06, 0.08, 0.08, 0.09, 0.10, 0.13};
        String action = weighted(actions, weights);

        String[] homePlayers = {"L. James", "A. Davis", "A. Reaves", "D. Russell"};
        String[] awayPlayers = {"J. Tatum", "J. Brown", "D. White", "K. Porzingis"};
        boolean isHome = ThreadLocalRandom.current().nextBoolean();
        String player = isHome
                ? homePlayers[ThreadLocalRandom.current().nextInt(homePlayers.length)]
                : awayPlayers[ThreadLocalRandom.current().nextInt(awayPlayers.length)];

        p.put("player", player);
        p.put("team", isHome ? f.homeTeam : f.awayTeam);
        p.put("quarter", "Q" + Math.min(4, (f.gameMinute / 12) + 1));

        if ("3_POINTER".equals(action)) { if (isHome) f.homeScore += 3; else f.awayScore += 3; }
        else if ("2_POINTER".equals(action) || "DUNK".equals(action)) { if (isHome) f.homeScore += 2; else f.awayScore += 2; }
        else if ("FREE_THROW".equals(action)) { if (isHome) f.homeScore++; else f.awayScore++; }

        p.put("score", f.homeScore + " - " + f.awayScore);
        return action;
    }

    // ─── Football ────────────────────────────────────────────────────────────────

    private String generateFootballAction(FixtureState f, Map<String, String> p) {
        String[] actions = {"TOUCHDOWN", "FIELD_GOAL", "FIRST_DOWN", "INCOMPLETE_PASS", "RUSH", "SACK", "INTERCEPTION", "PUNT", "PENALTY"};
        double[] weights = {0.08, 0.06, 0.20, 0.14, 0.16, 0.07, 0.05, 0.12, 0.12};
        String action = weighted(actions, weights);

        String[] homePlayers = {"P. Mahomes", "T. Kelce", "I. Pacheco", "R. Rice"};
        String[] awayPlayers = {"J. Hurts", "D. Smith", "A. Brown", "S. Barkley"};
        boolean isHome = ThreadLocalRandom.current().nextBoolean();
        String player = isHome
                ? homePlayers[ThreadLocalRandom.current().nextInt(homePlayers.length)]
                : awayPlayers[ThreadLocalRandom.current().nextInt(awayPlayers.length)];

        p.put("player", player);
        p.put("team", isHome ? f.homeTeam : f.awayTeam);
        p.put("quarter", "Q" + Math.min(4, (f.gameMinute / 15) + 1));

        if ("TOUCHDOWN".equals(action)) { if (isHome) f.homeScore += 7; else f.awayScore += 7; }
        else if ("FIELD_GOAL".equals(action)) { if (isHome) f.homeScore += 3; else f.awayScore += 3; }

        p.put("score", f.homeScore + " - " + f.awayScore);
        return action;
    }

    // ─── Baseball ────────────────────────────────────────────────────────────────

    private String generateBaseballAction(FixtureState f, Map<String, String> p) {
        String[] actions = {"SINGLE", "DOUBLE", "HOME_RUN", "STRIKEOUT", "WALK", "FLY_OUT", "GROUND_OUT", "STOLEN_BASE", "DOUBLE_PLAY"};
        double[] weights = {0.16, 0.07, 0.05, 0.18, 0.10, 0.15, 0.14, 0.05, 0.10};
        String action = weighted(actions, weights);

        String[] homePlayers = {"A. Judge", "J. Soto", "G. Torres", "G. Cole"};
        String[] awayPlayers = {"S. Ohtani", "M. Betts", "F. Freeman", "W. Smith"};
        boolean isHome = ThreadLocalRandom.current().nextBoolean();
        String player = isHome
                ? homePlayers[ThreadLocalRandom.current().nextInt(homePlayers.length)]
                : awayPlayers[ThreadLocalRandom.current().nextInt(awayPlayers.length)];

        p.put("player", player);
        p.put("team", isHome ? f.homeTeam : f.awayTeam);
        p.put("inning", "Top " + Math.min(9, (f.gameMinute / 5) + 1));

        if ("HOME_RUN".equals(action)) { if (isHome) f.homeScore++; else f.awayScore++; }

        p.put("score", f.homeScore + " - " + f.awayScore);
        return action;
    }

    // ─── Hockey ──────────────────────────────────────────────────────────────────

    private String generateHockeyAction(FixtureState f, Map<String, String> p) {
        String[] actions = {"GOAL", "SHOT_ON_GOAL", "SAVE", "PENALTY", "POWER_PLAY", "FACEOFF_WIN", "HIT", "ICING", "BLOCKED_SHOT"};
        double[] weights = {0.07, 0.20, 0.15, 0.08, 0.07, 0.12, 0.12, 0.08, 0.11};
        String action = weighted(actions, weights);

        String[] homePlayers = {"C. McDavid", "L. Draisaitl", "E. Bouchard", "Z. Hyman"};
        String[] awayPlayers = {"A. Barkov", "M. Tkachuk", "S. Reinhart", "B. Bobrovsky"};
        boolean isHome = ThreadLocalRandom.current().nextBoolean();
        String player = isHome
                ? homePlayers[ThreadLocalRandom.current().nextInt(homePlayers.length)]
                : awayPlayers[ThreadLocalRandom.current().nextInt(awayPlayers.length)];

        p.put("player", player);
        p.put("team", isHome ? f.homeTeam : f.awayTeam);
        p.put("period", "P" + Math.min(3, (f.gameMinute / 20) + 1));

        if ("GOAL".equals(action)) { if (isHome) f.homeScore++; else f.awayScore++; }

        p.put("score", f.homeScore + " - " + f.awayScore);
        return action;
    }

    // ─── Formula 1 ───────────────────────────────────────────────────────────────

    private String generateF1Action(FixtureState f, Map<String, String> p) {
        String[] actions = {"OVERTAKE", "PIT_STOP", "FASTEST_LAP", "DRS_ENABLED", "LAP_COMPLETED", "YELLOW_FLAG", "COLLISION", "SAFETY_CAR"};
        double[] weights = {0.15, 0.10, 0.08, 0.12, 0.30, 0.08, 0.05, 0.07, 0.05};
        String action = weighted(actions, weights);

        String[] drivers = {"Verstappen", "Norris", "Leclerc", "Hamilton", "Sainz", "Piastri", "Alonso", "Russell"};
        String[] teams = {"Red Bull", "McLaren", "Ferrari", "Mercedes", "Ferrari", "McLaren", "Aston Martin", "Mercedes"};
        int idx = ThreadLocalRandom.current().nextInt(drivers.length);

        p.put("driver", drivers[idx]);
        p.put("team", teams[idx]);
        p.put("lap", f.gameMinute + "/57");
        p.put("position", "P" + ThreadLocalRandom.current().nextInt(1, 21));
        return action;
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    private String weighted(String[] items, double[] weights) {
        double total = 0;
        for (double w : weights) total += w;
        double r = ThreadLocalRandom.current().nextDouble() * total;
        double cumulative = 0;
        for (int i = 0; i < items.length; i++) {
            cumulative += weights[i];
            if (r <= cumulative) return items[i];
        }
        return items[items.length - 1];
    }

    private void initializeFixtures() {
        // Match the fixture IDs from FixtureController mock data
        fixtures.put("MOCK-S01", new FixtureState("MOCK-S01", SportType.SOCCER, "Manchester City", "Real Madrid"));
        fixtures.put("MOCK-S02", new FixtureState("MOCK-S02", SportType.SOCCER, "Barcelona", "Bayern Munich"));
        fixtures.put("MOCK-S03", new FixtureState("MOCK-S03", SportType.SOCCER, "Liverpool", "Inter Milan"));

        fixtures.put("MOCK-B01", new FixtureState("MOCK-B01", SportType.BASKETBALL, "Lakers", "Celtics"));
        fixtures.put("MOCK-B02", new FixtureState("MOCK-B02", SportType.BASKETBALL, "Warriors", "Nuggets"));

        fixtures.put("MOCK-F01", new FixtureState("MOCK-F01", SportType.FOOTBALL, "Chiefs", "Eagles"));
        fixtures.put("MOCK-F02", new FixtureState("MOCK-F02", SportType.FOOTBALL, "49ers", "Cowboys"));

        fixtures.put("MOCK-X01", new FixtureState("MOCK-X01", SportType.BASEBALL, "Yankees", "Dodgers"));
        fixtures.put("MOCK-X02", new FixtureState("MOCK-X02", SportType.BASEBALL, "Astros", "Braves"));

        fixtures.put("MOCK-H01", new FixtureState("MOCK-H01", SportType.HOCKEY, "Oilers", "Panthers"));
        fixtures.put("MOCK-H02", new FixtureState("MOCK-H02", SportType.HOCKEY, "Avalanche", "Rangers"));

        fixtures.put("MOCK-R01", new FixtureState("MOCK-R01", SportType.FORMULA_1, "Monaco Grand Prix", "Race"));
        fixtures.put("MOCK-R02", new FixtureState("MOCK-R02", SportType.FORMULA_1, "Silverstone Grand Prix", "Race"));
    }

    private static class FixtureState {
        final String fixtureId;
        final SportType sportType;
        final String homeTeam;
        final String awayTeam;
        int homeScore = 0;
        int awayScore = 0;
        int gameMinute;

        FixtureState(String fixtureId, SportType sportType, String homeTeam, String awayTeam) {
            this.fixtureId = fixtureId;
            this.sportType = sportType;
            this.homeTeam = homeTeam;
            this.awayTeam = awayTeam;
            this.gameMinute = ThreadLocalRandom.current().nextInt(5, 30);
        }

        void advanceTime() {
            gameMinute += ThreadLocalRandom.current().nextInt(1, 3);
            int max = switch (sportType) {
                case SOCCER -> 90;
                case BASKETBALL -> 48;
                case FOOTBALL -> 60;
                case BASEBALL -> 45;
                case HOCKEY -> 60;
                case FORMULA_1 -> 57;
            };
            if (gameMinute > max) {
                gameMinute = 1;
                homeScore = 0;
                awayScore = 0;
            }
        }
    }
}
