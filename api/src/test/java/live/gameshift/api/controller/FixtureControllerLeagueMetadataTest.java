package live.gameshift.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import live.gameshift.api.dto.FixtureDto;
import live.gameshift.api.model.enums.SportType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for backend league metadata extraction in FixtureController.
 * Validates Requirements 1.1, 1.2, 1.3, 1.4, 1.5, 2.3.
 */
class FixtureControllerLeagueMetadataTest {

    private FixtureController controller;

    @BeforeEach
    void setUp() {
        controller = new FixtureController(false, "", new ObjectMapper());
    }

    private String wrapInResponse(String fixtureJson) {
        return """
            {"response": [%s]}
            """.formatted(fixtureJson);
    }

    private String buildFixtureJson(String leagueBlock) {
        return """
            {
                "fixture": { "id": 12345, "timestamp": 1700000000, "status": { "long": "First Half", "elapsed": 32 } },
                %s
                "teams": { "home": { "name": "Arsenal" }, "away": { "name": "Chelsea" } }
            }
            """.formatted(leagueBlock);
    }

    @Nested
    @DisplayName("Missing league object → defaults applied")
    class MissingLeagueObject {

        @Test
        @DisplayName("No league field at all → leagueName='Unknown League', country='International'")
        void noLeagueField() {
            String fixture = """
                {
                    "fixture": { "id": 12345, "timestamp": 1700000000, "status": { "long": "First Half", "elapsed": 32 } },
                    "teams": { "home": { "name": "Arsenal" }, "away": { "name": "Chelsea" } }
                }
                """;
            String json = wrapInResponse(fixture);

            List<FixtureDto> result = controller.parseFixturesResponse(json, SportType.SOCCER);

            assertEquals(1, result.size());
            assertEquals("Unknown League", result.get(0).leagueName());
            assertEquals("International", result.get(0).country());
        }
    }

    @Nested
    @DisplayName("Null/blank/whitespace league.country → 'International'")
    class BlankCountry {

        @Test
        @DisplayName("Empty string country → 'International'")
        void emptyCountry() {
            String fixture = buildFixtureJson("""
                "league": { "id": 39, "name": "Premier League", "country": "" },
                """);
            String json = wrapInResponse(fixture);

            List<FixtureDto> result = controller.parseFixturesResponse(json, SportType.SOCCER);

            assertEquals(1, result.size());
            assertEquals("Premier League", result.get(0).leagueName());
            assertEquals("International", result.get(0).country());
        }

        @Test
        @DisplayName("Whitespace-only country → 'International'")
        void whitespaceCountry() {
            String fixture = buildFixtureJson("""
                "league": { "id": 39, "name": "La Liga", "country": "   " },
                """);
            String json = wrapInResponse(fixture);

            List<FixtureDto> result = controller.parseFixturesResponse(json, SportType.SOCCER);

            assertEquals(1, result.size());
            assertEquals("La Liga", result.get(0).leagueName());
            assertEquals("International", result.get(0).country());
        }

        @Test
        @DisplayName("Null country (JSON null) → 'International'")
        void nullCountry() {
            String fixture = buildFixtureJson("""
                "league": { "id": 39, "name": "Serie A", "country": null },
                """);
            String json = wrapInResponse(fixture);

            List<FixtureDto> result = controller.parseFixturesResponse(json, SportType.SOCCER);

            assertEquals(1, result.size());
            assertEquals("Serie A", result.get(0).leagueName());
            assertEquals("International", result.get(0).country());
        }

        @Test
        @DisplayName("Missing country field entirely → 'International'")
        void missingCountryField() {
            String fixture = buildFixtureJson("""
                "league": { "id": 39, "name": "Bundesliga" },
                """);
            String json = wrapInResponse(fixture);

            List<FixtureDto> result = controller.parseFixturesResponse(json, SportType.SOCCER);

            assertEquals(1, result.size());
            assertEquals("Bundesliga", result.get(0).leagueName());
            assertEquals("International", result.get(0).country());
        }
    }

    @Nested
    @DisplayName("Null/blank/whitespace league.name → 'Unknown League'")
    class BlankLeagueName {

        @Test
        @DisplayName("Empty string league name → 'Unknown League'")
        void emptyLeagueName() {
            String fixture = buildFixtureJson("""
                "league": { "id": 39, "name": "", "country": "England" },
                """);
            String json = wrapInResponse(fixture);

            List<FixtureDto> result = controller.parseFixturesResponse(json, SportType.SOCCER);

            assertEquals(1, result.size());
            assertEquals("Unknown League", result.get(0).leagueName());
            assertEquals("England", result.get(0).country());
        }

        @Test
        @DisplayName("Whitespace-only league name → 'Unknown League'")
        void whitespaceLeagueName() {
            String fixture = buildFixtureJson("""
                "league": { "id": 39, "name": "   ", "country": "Spain" },
                """);
            String json = wrapInResponse(fixture);

            List<FixtureDto> result = controller.parseFixturesResponse(json, SportType.SOCCER);

            assertEquals(1, result.size());
            assertEquals("Unknown League", result.get(0).leagueName());
            assertEquals("Spain", result.get(0).country());
        }

        @Test
        @DisplayName("Null league name (JSON null) → 'Unknown League'")
        void nullLeagueName() {
            String fixture = buildFixtureJson("""
                "league": { "id": 39, "name": null, "country": "France" },
                """);
            String json = wrapInResponse(fixture);

            List<FixtureDto> result = controller.parseFixturesResponse(json, SportType.SOCCER);

            assertEquals(1, result.size());
            assertEquals("Unknown League", result.get(0).leagueName());
            assertEquals("France", result.get(0).country());
        }

        @Test
        @DisplayName("Missing name field entirely → 'Unknown League'")
        void missingNameField() {
            String fixture = buildFixtureJson("""
                "league": { "id": 39, "country": "Germany" },
                """);
            String json = wrapInResponse(fixture);

            List<FixtureDto> result = controller.parseFixturesResponse(json, SportType.SOCCER);

            assertEquals(1, result.size());
            assertEquals("Unknown League", result.get(0).leagueName());
            assertEquals("Germany", result.get(0).country());
        }
    }

    @Nested
    @DisplayName("Values exceeding 100 characters → truncated to 100 chars")
    class Truncation {

        @Test
        @DisplayName("League name over 100 chars is truncated")
        void longLeagueNameTruncated() {
            String longName = "A".repeat(150);
            String fixture = buildFixtureJson("""
                "league": { "id": 39, "name": "%s", "country": "England" },
                """.formatted(longName));
            String json = wrapInResponse(fixture);

            List<FixtureDto> result = controller.parseFixturesResponse(json, SportType.SOCCER);

            assertEquals(1, result.size());
            assertEquals(100, result.get(0).leagueName().length());
            assertEquals("A".repeat(100), result.get(0).leagueName());
        }

        @Test
        @DisplayName("Country over 100 chars is truncated")
        void longCountryTruncated() {
            String longCountry = "B".repeat(200);
            String fixture = buildFixtureJson("""
                "league": { "id": 39, "name": "Premier League", "country": "%s" },
                """.formatted(longCountry));
            String json = wrapInResponse(fixture);

            List<FixtureDto> result = controller.parseFixturesResponse(json, SportType.SOCCER);

            assertEquals(1, result.size());
            assertEquals(100, result.get(0).country().length());
            assertEquals("B".repeat(100), result.get(0).country());
        }

        @Test
        @DisplayName("Exactly 100 chars is NOT truncated")
        void exactly100CharsNotTruncated() {
            String exact100 = "C".repeat(100);
            String fixture = buildFixtureJson("""
                "league": { "id": 39, "name": "%s", "country": "Spain" },
                """.formatted(exact100));
            String json = wrapInResponse(fixture);

            List<FixtureDto> result = controller.parseFixturesResponse(json, SportType.SOCCER);

            assertEquals(1, result.size());
            assertEquals(100, result.get(0).leagueName().length());
            assertEquals(exact100, result.get(0).leagueName());
        }
    }

    @Nested
    @DisplayName("Unicode and special characters in league names")
    class UnicodeAndSpecialCharacters {

        @Test
        @DisplayName("Turkish: Süper Lig is preserved")
        void turkishLeagueName() {
            String fixture = buildFixtureJson("""
                "league": { "id": 203, "name": "Süper Lig", "country": "Turkey" },
                """);
            String json = wrapInResponse(fixture);

            List<FixtureDto> result = controller.parseFixturesResponse(json, SportType.SOCCER);

            assertEquals(1, result.size());
            assertEquals("Süper Lig", result.get(0).leagueName());
            assertEquals("Turkey", result.get(0).country());
        }

        @Test
        @DisplayName("German umlaut: Ligue 1 Über Alles is preserved")
        void germanUmlautLeagueName() {
            String fixture = buildFixtureJson("""
                "league": { "id": 99, "name": "Ligue 1 Über Alles", "country": "France" },
                """);
            String json = wrapInResponse(fixture);

            List<FixtureDto> result = controller.parseFixturesResponse(json, SportType.SOCCER);

            assertEquals(1, result.size());
            assertEquals("Ligue 1 Über Alles", result.get(0).leagueName());
            assertEquals("France", result.get(0).country());
        }

        @Test
        @DisplayName("Emoji in league name is preserved")
        void emojiLeagueName() {
            String fixture = buildFixtureJson("""
                "league": { "id": 100, "name": "⚽ Super Cup 🏆", "country": "🇧🇷 Brazil" },
                """);
            String json = wrapInResponse(fixture);

            List<FixtureDto> result = controller.parseFixturesResponse(json, SportType.SOCCER);

            assertEquals(1, result.size());
            assertEquals("⚽ Super Cup 🏆", result.get(0).leagueName());
            assertEquals("🇧🇷 Brazil", result.get(0).country());
        }

        @Test
        @DisplayName("Japanese characters: J1リーグ is preserved")
        void japaneseLeagueName() {
            String fixture = buildFixtureJson("""
                "league": { "id": 101, "name": "J1リーグ", "country": "日本" },
                """);
            String json = wrapInResponse(fixture);

            List<FixtureDto> result = controller.parseFixturesResponse(json, SportType.SOCCER);

            assertEquals(1, result.size());
            assertEquals("J1リーグ", result.get(0).leagueName());
            assertEquals("日本", result.get(0).country());
        }

        @Test
        @DisplayName("Special characters: ampersand, apostrophe, quotes")
        void specialCharactersPreserved() {
            String fixture = buildFixtureJson("""
                "league": { "id": 102, "name": "Cup & League's \\\"Final\\\"", "country": "Côte d'Ivoire" },
                """);
            String json = wrapInResponse(fixture);

            List<FixtureDto> result = controller.parseFixturesResponse(json, SportType.SOCCER);

            assertEquals(1, result.size());
            assertEquals("Cup & League's \"Final\"", result.get(0).leagueName());
            assertEquals("Côte d'Ivoire", result.get(0).country());
        }
    }

    @Nested
    @DisplayName("Mock mode returns realistic league/country combos")
    class MockMode {

        private FixtureController mockController;

        @BeforeEach
        void setUp() {
            mockController = new FixtureController(true, "", new ObjectMapper());
        }

        @Test
        @DisplayName("Soccer mock fixtures have non-empty league names")
        void soccerMockFixturesHaveLeagueNames() {
            List<FixtureDto> fixtures = mockController.getMockFixtures(SportType.SOCCER);

            assertFalse(fixtures.isEmpty(), "Soccer mock fixtures should not be empty");
            for (FixtureDto fixture : fixtures) {
                assertNotNull(fixture.leagueName());
                assertFalse(fixture.leagueName().isBlank(),
                    "Mock fixture leagueName should not be blank: " + fixture.fixtureId());
            }
        }

        @Test
        @DisplayName("Soccer mock fixtures have non-empty country")
        void soccerMockFixturesHaveCountry() {
            List<FixtureDto> fixtures = mockController.getMockFixtures(SportType.SOCCER);

            assertFalse(fixtures.isEmpty());
            for (FixtureDto fixture : fixtures) {
                assertNotNull(fixture.country());
                assertFalse(fixture.country().isBlank(),
                    "Mock fixture country should not be blank: " + fixture.fixtureId());
            }
        }

        @Test
        @DisplayName("Soccer mock fixtures have realistic league/country pairs")
        void soccerMockFixturesHaveRealisticPairs() {
            List<FixtureDto> fixtures = mockController.getMockFixtures(SportType.SOCCER);

            // At least one fixture should have a recognizable league
            boolean hasRecognizableLeague = fixtures.stream()
                .anyMatch(f -> f.leagueName().equals("Champions League")
                    || f.leagueName().equals("Premier League")
                    || f.leagueName().equals("La Liga")
                    || f.leagueName().equals("Bundesliga")
                    || f.leagueName().equals("Serie A")
                    || f.leagueName().equals("Ligue 1"));

            assertTrue(hasRecognizableLeague,
                "At least one soccer mock fixture should have a recognizable league name");
        }

        @Test
        @DisplayName("Non-soccer mock fixtures also have league/country values")
        void nonSoccerMockFixturesHaveMetadata() {
            List<FixtureDto> basketballFixtures = mockController.getMockFixtures(SportType.BASKETBALL);

            assertFalse(basketballFixtures.isEmpty());
            for (FixtureDto fixture : basketballFixtures) {
                assertNotNull(fixture.leagueName());
                assertFalse(fixture.leagueName().isBlank());
                assertNotNull(fixture.country());
                assertFalse(fixture.country().isBlank());
            }
        }
    }
}
