package live.gameshift.producer.service;

import live.gameshift.producer.model.SportEvent;
import live.gameshift.producer.model.SportType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * [MOCK] Simulates live API-Sports fixture data with realistic game progression.
 *
 * Each sport has 2-3 concurrent fixtures that evolve over time:
 * - Game clock advances each poll
 * - Scores change based on weighted random events
 * - Events are sport-specific and realistic
 * - Games restart when finished (continuous simulation)
 *
 * All mock events are clearly tagged with [MOCK] in the action field
 * and "mock": true in the rawPayload so they are immediately identifiable.
 *
 * Only active when app.api.sports.mock-mode=true.
 */
@Service
public class MockDataService {

    private static final Logger log = LoggerFactory.getLogger(MockDataService.class);
    private static final String MOCK_TAG = "[MOCK] ";

    private final Map<String, MockFixture> activeFixtures = new ConcurrentHashMap<>();

    public MockDataService() {
        initializeFixtures();
    }

    /**
     * Generates a realistic mock event for the given sport, simulating a live game.
     * The returned event is tagged with [MOCK] in the action so it's obvious in logs/UI.
     */
    public Optional<SportEvent> generateEvent(SportType sportType) {
        List<MockFixture> fixtures = getFixturesForSport(sportType);
        if (fixtures.isEmpty()) {
            return Optional.empty();
        }

        // Pick a random active fixture for this sport
        MockFixture fixture = fixtures.get(ThreadLocalRandom.current().nextInt(fixtures.size()));
        fixture.advanceTime();

        SportEvent event = new SportEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setSportType(sportType);
        event.setEventTimestamp(System.currentTimeMillis());
        event.setFixtureId(fixture.fixtureId);

        Map<String, String> participants = new LinkedHashMap<>();
        participants.put("home", fixture.homeTeam);
        participants.put("away", fixture.awayTeam);

        switch (sportType) {
            case SOCCER -> buildSoccerEvent(event, participants, fixture);
            case BASKETBALL -> buildBasketballEvent(event, participants, fixture);
            case FOOTBALL -> buildFootballEvent(event, participants, fixture);
            case BASEBALL -> buildBaseballEvent(event, participants, fixture);
            case HOCKEY -> buildHockeyEvent(event, participants, fixture);
            case FORMULA_1 -> buildFormula1Event(event, participants, fixture);
        }

        // Tag action with [MOCK] prefix
        event.setAction(MOCK_TAG + event.getAction());
        event.setParticipants(participants);
        event.setRawPayload(buildRawPayload(fixture, event.getAction()));

        log.debug("[MOCK] Generated event: sport={} fixture={} action={} score={}-{} minute={}",
                sportType, fixture.fixtureId, event.getAction(),
                fixture.homeScore, fixture.awayScore, fixture.gameMinute);

        return Optional.of(event);
    }

    /**
     * Returns all mock fixtures for a given sport type.
     */
    public List<MockFixture> getFixturesForSport(SportType sportType) {
        return activeFixtures.values().stream()
                .filter(f -> f.sportType == sportType)
                .toList();
    }

    public List<MockFixture> getAllFixtures() {
        return new ArrayList<>(activeFixtures.values());
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // SOCCER — 90 minutes, low scoring, lots of midfield events
    // ─────────────────────────────────────────────────────────────────────────────

    private void buildSoccerEvent(SportEvent event, Map<String, String> participants, MockFixture fixture) {
        // Realistic distribution: most events are NOT goals
        GameEvent ge = pickEvent(fixture, new GameEvent[]{
            new GameEvent("GOAL",          0.06, true,  1),
            new GameEvent("SHOT_ON_TARGET",0.12, false, 0),
            new GameEvent("SHOT_OFF_TARGET",0.10, false, 0),
            new GameEvent("CORNER_KICK",   0.12, false, 0),
            new GameEvent("FREE_KICK",     0.10, false, 0),
            new GameEvent("YELLOW_CARD",   0.08, false, 0),
            new GameEvent("RED_CARD",      0.01, false, 0),
            new GameEvent("OFFSIDE",       0.08, false, 0),
            new GameEvent("SUBSTITUTION",  0.07, false, 0),
            new GameEvent("FOUL",          0.14, false, 0),
            new GameEvent("THROW_IN",      0.08, false, 0),
            new GameEvent("GOAL_KICK",     0.04, false, 0),
        });

        event.setAction(ge.name);

        String[] homePlayers = fixture.homePlayers;
        String[] awayPlayers = fixture.awayPlayers;
        boolean isHome = ThreadLocalRandom.current().nextBoolean();
        String player = isHome
                ? homePlayers[ThreadLocalRandom.current().nextInt(homePlayers.length)]
                : awayPlayers[ThreadLocalRandom.current().nextInt(awayPlayers.length)];

        participants.put("player", player);
        participants.put("team", isHome ? fixture.homeTeam : fixture.awayTeam);
        participants.put("minute", fixture.gameMinute + "'");
        participants.put("score", fixture.homeScore + " - " + fixture.awayScore);

        if (fixture.gameMinute == 45) {
            participants.put("period", "HT");
        } else if (fixture.gameMinute > 45) {
            participants.put("period", "2H");
        } else {
            participants.put("period", "1H");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // BASKETBALL — 48 minutes (4x12), high scoring
    // ─────────────────────────────────────────────────────────────────────────────

    private void buildBasketballEvent(SportEvent event, Map<String, String> participants, MockFixture fixture) {
        GameEvent ge = pickEvent(fixture, new GameEvent[]{
            new GameEvent("3_POINTER",     0.14, true,  3),
            new GameEvent("2_POINTER",     0.22, true,  2),
            new GameEvent("FREE_THROW",    0.10, true,  1),
            new GameEvent("DUNK",          0.06, true,  2),
            new GameEvent("BLOCK",         0.08, false, 0),
            new GameEvent("STEAL",         0.07, false, 0),
            new GameEvent("TURNOVER",      0.09, false, 0),
            new GameEvent("OFFENSIVE_REBOUND", 0.06, false, 0),
            new GameEvent("DEFENSIVE_REBOUND", 0.08, false, 0),
            new GameEvent("FOUL",          0.07, false, 0),
            new GameEvent("TIMEOUT",       0.03, false, 0),
        });

        event.setAction(ge.name);

        boolean isHome = ThreadLocalRandom.current().nextBoolean();
        String[] players = isHome ? fixture.homePlayers : fixture.awayPlayers;
        String player = players[ThreadLocalRandom.current().nextInt(players.length)];

        int quarter = Math.min(4, (fixture.gameMinute / 12) + 1);
        participants.put("player", player);
        participants.put("team", isHome ? fixture.homeTeam : fixture.awayTeam);
        participants.put("quarter", "Q" + quarter);
        participants.put("score", fixture.homeScore + " - " + fixture.awayScore);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // FOOTBALL (American) — 60 minutes (4x15), medium scoring
    // ─────────────────────────────────────────────────────────────────────────────

    private void buildFootballEvent(SportEvent event, Map<String, String> participants, MockFixture fixture) {
        GameEvent ge = pickEvent(fixture, new GameEvent[]{
            new GameEvent("TOUCHDOWN",     0.08, true,  7),
            new GameEvent("FIELD_GOAL",    0.06, true,  3),
            new GameEvent("FIRST_DOWN",    0.18, false, 0),
            new GameEvent("INCOMPLETE_PASS",0.12, false, 0),
            new GameEvent("RUSH",          0.14, false, 0),
            new GameEvent("SACK",          0.06, false, 0),
            new GameEvent("INTERCEPTION",  0.04, false, 0),
            new GameEvent("FUMBLE",        0.03, false, 0),
            new GameEvent("PUNT",          0.10, false, 0),
            new GameEvent("PENALTY",       0.09, false, 0),
            new GameEvent("TWO_MINUTE_WARNING", 0.02, false, 0),
            new GameEvent("KICKOFF",       0.08, false, 0),
        });

        event.setAction(ge.name);

        boolean isHome = ThreadLocalRandom.current().nextBoolean();
        String[] players = isHome ? fixture.homePlayers : fixture.awayPlayers;
        String player = players[ThreadLocalRandom.current().nextInt(players.length)];

        int quarter = Math.min(4, (fixture.gameMinute / 15) + 1);
        int down = ThreadLocalRandom.current().nextInt(1, 5);
        int yardsToGo = ThreadLocalRandom.current().nextInt(1, 15);

        participants.put("player", player);
        participants.put("team", isHome ? fixture.homeTeam : fixture.awayTeam);
        participants.put("quarter", "Q" + quarter);
        participants.put("down", down + "&" + yardsToGo);
        participants.put("score", fixture.homeScore + " - " + fixture.awayScore);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // BASEBALL — 9 innings, low scoring
    // ─────────────────────────────────────────────────────────────────────────────

    private void buildBaseballEvent(SportEvent event, Map<String, String> participants, MockFixture fixture) {
        GameEvent ge = pickEvent(fixture, new GameEvent[]{
            new GameEvent("HOME_RUN",      0.04, true,  1),
            new GameEvent("SINGLE",        0.16, false, 0),
            new GameEvent("DOUBLE",        0.06, false, 0),
            new GameEvent("TRIPLE",        0.02, false, 0),
            new GameEvent("STRIKEOUT",     0.18, false, 0),
            new GameEvent("WALK",          0.09, false, 0),
            new GameEvent("FLY_OUT",       0.14, false, 0),
            new GameEvent("GROUND_OUT",    0.13, false, 0),
            new GameEvent("STOLEN_BASE",   0.04, false, 0),
            new GameEvent("DOUBLE_PLAY",   0.05, false, 0),
            new GameEvent("RBI_SINGLE",    0.05, true,  1),
            new GameEvent("SACRIFICE_FLY", 0.04, true,  1),
        });

        event.setAction(ge.name);

        boolean isHome = ThreadLocalRandom.current().nextBoolean();
        String[] players = isHome ? fixture.homePlayers : fixture.awayPlayers;
        String player = players[ThreadLocalRandom.current().nextInt(players.length)];

        int inning = Math.min(9, (fixture.gameMinute / 5) + 1);
        String half = ThreadLocalRandom.current().nextBoolean() ? "Top" : "Bot";
        int outs = ThreadLocalRandom.current().nextInt(0, 3);

        participants.put("player", player);
        participants.put("team", isHome ? fixture.homeTeam : fixture.awayTeam);
        participants.put("inning", half + " " + inning);
        participants.put("outs", outs + " out");
        participants.put("score", fixture.homeScore + " - " + fixture.awayScore);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // HOCKEY — 60 minutes (3x20), low-medium scoring
    // ─────────────────────────────────────────────────────────────────────────────

    private void buildHockeyEvent(SportEvent event, Map<String, String> participants, MockFixture fixture) {
        GameEvent ge = pickEvent(fixture, new GameEvent[]{
            new GameEvent("GOAL",          0.07, true,  1),
            new GameEvent("SHOT_ON_GOAL",  0.18, false, 0),
            new GameEvent("SAVE",          0.15, false, 0),
            new GameEvent("PENALTY",       0.08, false, 0),
            new GameEvent("POWER_PLAY_START", 0.06, false, 0),
            new GameEvent("FACEOFF_WIN",   0.12, false, 0),
            new GameEvent("HIT",           0.10, false, 0),
            new GameEvent("ICING",         0.07, false, 0),
            new GameEvent("OFFSIDE",       0.05, false, 0),
            new GameEvent("BLOCKED_SHOT",  0.08, false, 0),
            new GameEvent("FIGHT",         0.02, false, 0),
            new GameEvent("EMPTY_NET",     0.02, true,  1),
        });

        event.setAction(ge.name);

        boolean isHome = ThreadLocalRandom.current().nextBoolean();
        String[] players = isHome ? fixture.homePlayers : fixture.awayPlayers;
        String player = players[ThreadLocalRandom.current().nextInt(players.length)];

        int period = Math.min(3, (fixture.gameMinute / 20) + 1);

        participants.put("player", player);
        participants.put("team", isHome ? fixture.homeTeam : fixture.awayTeam);
        participants.put("period", "P" + period);
        participants.put("score", fixture.homeScore + " - " + fixture.awayScore);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // FORMULA 1 — ~57 laps, position-based
    // ─────────────────────────────────────────────────────────────────────────────

    private void buildFormula1Event(SportEvent event, Map<String, String> participants, MockFixture fixture) {
        GameEvent ge = pickEvent(fixture, new GameEvent[]{
            new GameEvent("OVERTAKE",      0.15, false, 0),
            new GameEvent("PIT_STOP",      0.10, false, 0),
            new GameEvent("FASTEST_LAP",   0.08, false, 0),
            new GameEvent("YELLOW_FLAG",   0.06, false, 0),
            new GameEvent("DRS_ENABLED",   0.10, false, 0),
            new GameEvent("COLLISION",     0.04, false, 0),
            new GameEvent("RETIREMENT",    0.03, false, 0),
            new GameEvent("SAFETY_CAR",    0.04, false, 0),
            new GameEvent("LAP_COMPLETED", 0.30, false, 0),
            new GameEvent("PENALTY",       0.05, false, 0),
            new GameEvent("TEAM_RADIO",    0.05, false, 0),
        });

        event.setAction(ge.name);

        String[] drivers = {"M. Verstappen", "L. Norris", "C. Leclerc", "L. Hamilton", "C. Sainz", "O. Piastri", "F. Alonso", "G. Russell"};
        String[] driverTeams = {"Red Bull", "McLaren", "Ferrari", "Mercedes", "Ferrari", "McLaren", "Aston Martin", "Mercedes"};
        int driverIdx = ThreadLocalRandom.current().nextInt(drivers.length);

        participants.put("driver", drivers[driverIdx]);
        participants.put("team", driverTeams[driverIdx]);
        participants.put("lap", fixture.gameMinute + "/" + 57);
        participants.put("position", "P" + ThreadLocalRandom.current().nextInt(1, 21));

        if ("FASTEST_LAP".equals(ge.name)) {
            int mins = ThreadLocalRandom.current().nextInt(1, 2);
            int secs = ThreadLocalRandom.current().nextInt(10, 40);
            int ms = ThreadLocalRandom.current().nextInt(100, 999);
            participants.put("lapTime", mins + ":" + secs + "." + ms);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Event selection with weighted random
    // ─────────────────────────────────────────────────────────────────────────────

    private GameEvent pickEvent(MockFixture fixture, GameEvent[] events) {
        double total = 0;
        for (GameEvent e : events) total += e.weight;
        double r = ThreadLocalRandom.current().nextDouble() * total;
        double cumulative = 0;
        GameEvent picked = events[events.length - 1];
        for (GameEvent e : events) {
            cumulative += e.weight;
            if (r <= cumulative) {
                picked = e;
                break;
            }
        }
        // Apply score change
        if (picked.isScoring) {
            boolean isHome = ThreadLocalRandom.current().nextBoolean();
            fixture.incrementScoreBy(isHome, picked.points);
        }
        return picked;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Raw payload — mimics actual API-Sports JSON structure
    // ─────────────────────────────────────────────────────────────────────────────

    private String buildRawPayload(MockFixture fixture, String action) {
        return String.format(
            "{\"mock\":true,\"response\":[{" +
                "\"fixture\":{\"id\":%s,\"status\":{\"short\":\"%s\",\"elapsed\":%d}}," +
                "\"teams\":{\"home\":{\"name\":\"%s\"},\"away\":{\"name\":\"%s\"}}," +
                "\"goals\":{\"home\":%d,\"away\":%d}" +
            "}]}",
            fixture.fixtureId, fixture.getStatus(), fixture.gameMinute,
            fixture.homeTeam, fixture.awayTeam,
            fixture.homeScore, fixture.awayScore
        );
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Fixture initialization — 2-3 per sport with real team names
    // ─────────────────────────────────────────────────────────────────────────────

    private void initializeFixtures() {
        // SOCCER — 3 fixtures (Champions League feel)
        addFixture("MOCK-S01", SportType.SOCCER,
                "Manchester City", "Real Madrid",
                new String[]{"Haaland", "De Bruyne", "Foden", "Rodri", "Grealish"},
                new String[]{"Vinícius Jr.", "Bellingham", "Mbappé", "Valverde", "Rüdiger"});
        addFixture("MOCK-S02", SportType.SOCCER,
                "Barcelona", "Bayern Munich",
                new String[]{"Lewandowski", "Pedri", "Gavi", "Raphinha", "Araujo"},
                new String[]{"Musiala", "Sané", "Kane", "Kimmich", "Müller"});
        addFixture("MOCK-S03", SportType.SOCCER,
                "Liverpool", "Inter Milan",
                new String[]{"Salah", "Núñez", "Szoboszlai", "Mac Allister", "Van Dijk"},
                new String[]{"Lautaro", "Thuram", "Barella", "Çalhanoğlu", "Bastoni"});

        // BASKETBALL — 2 fixtures (NBA Playoffs feel)
        addFixture("MOCK-B01", SportType.BASKETBALL,
                "Lakers", "Celtics",
                new String[]{"L. James", "A. Davis", "A. Reaves", "D. Russell", "R. Hachimura"},
                new String[]{"J. Tatum", "J. Brown", "D. White", "K. Porzingis", "J. Holiday"});
        addFixture("MOCK-B02", SportType.BASKETBALL,
                "Warriors", "Nuggets",
                new String[]{"S. Curry", "K. Thompson", "D. Green", "A. Wiggins", "C. Paul"},
                new String[]{"N. Jokić", "J. Murray", "M. Porter Jr.", "A. Gordon", "K. Caldwell-Pope"});

        // FOOTBALL — 2 fixtures (NFL feel)
        addFixture("MOCK-F01", SportType.FOOTBALL,
                "Chiefs", "Eagles",
                new String[]{"P. Mahomes", "T. Kelce", "I. Pacheco", "R. Rice", "C. Jones"},
                new String[]{"J. Hurts", "D. Smith", "A. Brown", "S. Barkley", "J. Johnson"});
        addFixture("MOCK-F02", SportType.FOOTBALL,
                "49ers", "Cowboys",
                new String[]{"B. Purdy", "C. McCaffrey", "B. Aiyuk", "G. Kittle", "N. Bosa"},
                new String[]{"D. Prescott", "C. Lamb", "M. Parsons", "Z. Martin", "T. Diggs"});

        // BASEBALL — 2 fixtures (MLB feel)
        addFixture("MOCK-X01", SportType.BASEBALL,
                "Yankees", "Dodgers",
                new String[]{"A. Judge", "J. Soto", "G. Torres", "G. Cole", "A. Volpe"},
                new String[]{"S. Ohtani", "M. Betts", "F. Freeman", "W. Smith", "J. Outman"});
        addFixture("MOCK-X02", SportType.BASEBALL,
                "Astros", "Braves",
                new String[]{"J. Altuve", "Y. Alvarez", "K. Tucker", "A. Bregman", "F. Valdez"},
                new String[]{"R. Acuña Jr.", "M. Olson", "A. Riley", "O. Albies", "S. Strider"});

        // HOCKEY — 2 fixtures (NHL Playoffs feel)
        addFixture("MOCK-H01", SportType.HOCKEY,
                "Oilers", "Panthers",
                new String[]{"C. McDavid", "L. Draisaitl", "E. Bouchard", "R. Nugent-Hopkins", "Z. Hyman"},
                new String[]{"A. Barkov", "M. Tkachuk", "S. Reinhart", "B. Bobrovsky", "G. Forsling"});
        addFixture("MOCK-H02", SportType.HOCKEY,
                "Avalanche", "Rangers",
                new String[]{"N. MacKinnon", "C. Makar", "M. Rantanen", "V. Lehkonen", "A. Georgiev"},
                new String[]{"A. Panarin", "M. Zibanejad", "C. Kreider", "A. Fox", "I. Shesterkin"});

        // FORMULA 1 — 2 races
        addFixture("MOCK-R01", SportType.FORMULA_1,
                "Monaco Grand Prix", "Race",
                new String[]{"Verstappen", "Norris", "Leclerc", "Hamilton"},
                new String[]{"Sainz", "Piastri", "Alonso", "Russell"});
        addFixture("MOCK-R02", SportType.FORMULA_1,
                "Silverstone Grand Prix", "Race",
                new String[]{"Hamilton", "Norris", "Verstappen", "Russell"},
                new String[]{"Leclerc", "Sainz", "Piastri", "Alonso"});

        log.info("[MOCK] Fixtures initialized: {} total across {} sports",
                activeFixtures.size(), SportType.values().length);
    }

    private void addFixture(String fixtureId, SportType sportType, String home, String away,
                            String[] homePlayers, String[] awayPlayers) {
        MockFixture fixture = new MockFixture(fixtureId, sportType, home, away, homePlayers, awayPlayers);
        activeFixtures.put(fixtureId, fixture);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Inner classes
    // ─────────────────────────────────────────────────────────────────────────────

    private record GameEvent(String name, double weight, boolean isScoring, int points) {}

    /**
     * Represents a mock live fixture with evolving game state.
     * The game clock advances each poll, scores accumulate, and games restart when done.
     */
    public static class MockFixture {
        public final String fixtureId;
        public final SportType sportType;
        public final String homeTeam;
        public final String awayTeam;
        public final String[] homePlayers;
        public final String[] awayPlayers;
        public int homeScore = 0;
        public int awayScore = 0;
        public int gameMinute = 0;
        public final long startTime;

        public MockFixture(String fixtureId, SportType sportType, String homeTeam, String awayTeam,
                           String[] homePlayers, String[] awayPlayers) {
            this.fixtureId = fixtureId;
            this.sportType = sportType;
            this.homeTeam = homeTeam;
            this.awayTeam = awayTeam;
            this.homePlayers = homePlayers;
            this.awayPlayers = awayPlayers;
            this.startTime = System.currentTimeMillis();
            // Start at a random minute to simulate joining a game already in progress
            this.gameMinute = ThreadLocalRandom.current().nextInt(1, getMaxMinutes() / 2);
        }

        public void advanceTime() {
            // Advance 1-3 minutes per poll cycle (30s real time ≈ 1-3 game minutes)
            gameMinute += ThreadLocalRandom.current().nextInt(1, 4);
            if (gameMinute > getMaxMinutes()) {
                // Game over — reset for a new game
                gameMinute = 1;
                homeScore = 0;
                awayScore = 0;
                log.debug("[MOCK] Game reset: {} vs {} (fixture {})", homeTeam, awayTeam, fixtureId);
            }
        }

        public void incrementScoreBy(boolean isHome, int points) {
            if (isHome) homeScore += points;
            else awayScore += points;
        }

        public String getStatus() {
            if (gameMinute <= 0) return "NS";
            if (gameMinute >= getMaxMinutes()) return "FT";
            // Sport-specific live status codes (matching API-Sports conventions)
            return switch (sportType) {
                case SOCCER -> gameMinute <= 45 ? "1H" : "2H";
                case BASKETBALL -> "Q" + Math.min(4, (gameMinute / 12) + 1);
                case FOOTBALL -> "Q" + Math.min(4, (gameMinute / 15) + 1);
                case BASEBALL -> "IN" + Math.min(9, (gameMinute / 5) + 1);
                case HOCKEY -> "P" + Math.min(3, (gameMinute / 20) + 1);
                case FORMULA_1 -> "LAP" + gameMinute;
            };
        }

        private int getMaxMinutes() {
            return switch (sportType) {
                case SOCCER -> 90;
                case BASKETBALL -> 48;
                case FOOTBALL -> 60;
                case BASEBALL -> 45;
                case HOCKEY -> 60;
                case FORMULA_1 -> 57;
            };
        }

        private static final Logger log = LoggerFactory.getLogger(MockFixture.class);
    }
}
