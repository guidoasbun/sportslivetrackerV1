package live.gameshift.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import live.gameshift.api.dto.FixtureDto;
import live.gameshift.api.model.enums.SportType;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for league metadata extraction in FixtureController.
 *
 * Validates: Requirements 1.1, 1.2, 1.3, 1.4, 1.5, 2.1, 2.2
 */
class FixtureControllerLeagueMetadataPropertyTest {

    private final FixtureController controller = new FixtureController(
            false, "", new ObjectMapper());

    /**
     * Property 1: League metadata extraction preserves source data.
     *
     * For any valid API-Sports fixture JSON containing a league object with non-blank
     * name and country fields, the parsed FixtureDto SHALL have leagueName equal to
     * the source league.name and country equal to the source league.country.
     *
     * Validates: Requirements 1.1, 1.2
     */
    @Property(tries = 100)
    @Tag("Feature-soccer-fixtures-region-grouping")
    @Tag("Property-1-League-metadata-extraction-preserves-source-data")
    void leagueMetadataExtractionPreservesSourceData(
            @ForAll("nonBlankLeagueName") String leagueName,
            @ForAll("nonBlankCountry") String country,
            @ForAll @IntRange(min = 1, max = 999999) int fixtureId,
            @ForAll @IntRange(min = 1000000000, max = 2000000000) int timestamp) {

        String json = buildFixtureJson(fixtureId, timestamp, leagueName, country,
                "Team A", "Team B");

        List<FixtureDto> result = controller.parseFixturesResponse(json, SportType.SOCCER);

        assertThat(result).hasSize(1);
        FixtureDto dto = result.get(0);
        // Since values are non-blank and ≤100 chars, they should be preserved exactly
        assertThat(dto.leagueName()).isEqualTo(leagueName);
        assertThat(dto.country()).isEqualTo(country);
    }

    /**
     * Property 2: FixtureDto league fields are always non-empty and bounded.
     *
     * For any fixture input (including null, blank, whitespace-only, very long, or
     * missing league data), the resulting FixtureDto SHALL have both leagueName and
     * country fields that are non-empty (length >= 1) and at most 100 characters long.
     *
     * Validates: Requirements 2.1, 2.2
     */
    @Property(tries = 100)
    @Tag("Feature-soccer-fixtures-region-grouping")
    @Tag("Property-2-FixtureDto-league-fields-are-always-non-empty-and-bounded")
    void leagueFieldsAreAlwaysNonEmptyAndBounded(
            @ForAll("problematicLeagueValue") String leagueName,
            @ForAll("problematicLeagueValue") String country,
            @ForAll @IntRange(min = 1, max = 999999) int fixtureId,
            @ForAll @IntRange(min = 1000000000, max = 2000000000) int timestamp) {

        String json = buildFixtureJsonWithNullableFields(fixtureId, timestamp,
                leagueName, country, "Home Team", "Away Team");

        List<FixtureDto> result = controller.parseFixturesResponse(json, SportType.SOCCER);

        assertThat(result).hasSize(1);
        FixtureDto dto = result.get(0);

        // leagueName must be non-empty and ≤100 chars
        assertThat(dto.leagueName()).isNotNull();
        assertThat(dto.leagueName()).isNotBlank();
        assertThat(dto.leagueName().length()).isGreaterThanOrEqualTo(1);
        assertThat(dto.leagueName().length()).isLessThanOrEqualTo(100);

        // country must be non-empty and ≤100 chars
        assertThat(dto.country()).isNotNull();
        assertThat(dto.country()).isNotBlank();
        assertThat(dto.country().length()).isGreaterThanOrEqualTo(1);
        assertThat(dto.country().length()).isLessThanOrEqualTo(100);
    }

    // --- Arbitraries ---

    @Provide
    Arbitrary<String> nonBlankLeagueName() {
        // Generate non-blank strings between 1-100 characters with printable chars
        return Arbitraries.strings()
                .withCharRange('A', 'Z')
                .withCharRange('a', 'z')
                .withCharRange('0', '9')
                .withChars(' ', '-', '.', '\'', 'é', 'ñ', 'ü')
                .ofMinLength(1)
                .ofMaxLength(100)
                .filter(s -> !s.isBlank());
    }

    @Provide
    Arbitrary<String> nonBlankCountry() {
        // Generate non-blank strings between 1-100 characters with printable chars
        return Arbitraries.strings()
                .withCharRange('A', 'Z')
                .withCharRange('a', 'z')
                .withCharRange('0', '9')
                .withChars(' ', '-', '.', '\'', 'é', 'ñ', 'ü')
                .ofMinLength(1)
                .ofMaxLength(100)
                .filter(s -> !s.isBlank());
    }

    @Provide
    Arbitrary<String> problematicLeagueValue() {
        // Domain includes: null-sentinel, empty, whitespace-only, very long, and valid strings
        return Arbitraries.oneOf(
                // null represented as a sentinel (we handle in JSON building)
                Arbitraries.just("__NULL__"),
                // empty string
                Arbitraries.just(""),
                // whitespace-only
                Arbitraries.of("   ", "\t", " \t ", "  \n  "),
                // very long strings (> 100 characters)
                Arbitraries.strings()
                        .withCharRange('A', 'Z')
                        .withCharRange('a', 'z')
                        .ofMinLength(101)
                        .ofMaxLength(200),
                // valid non-blank strings (1-100 chars)
                Arbitraries.strings()
                        .withCharRange('A', 'Z')
                        .withCharRange('a', 'z')
                        .withChars(' ', '-')
                        .ofMinLength(1)
                        .ofMaxLength(100)
                        .filter(s -> !s.isBlank())
        );
    }

    // --- Helper methods ---

    private String buildFixtureJson(int fixtureId, int timestamp,
                                    String leagueName, String country,
                                    String homeTeam, String awayTeam) {
        return String.format("""
                {
                  "response": [
                    {
                      "fixture": { "id": %d, "timestamp": %d, "status": { "long": "First Half", "elapsed": 32 } },
                      "league": { "id": 1, "name": %s, "country": %s },
                      "teams": { "home": { "name": %s }, "away": { "name": %s } }
                    }
                  ]
                }
                """,
                fixtureId, timestamp,
                jsonString(leagueName), jsonString(country),
                jsonString(homeTeam), jsonString(awayTeam));
    }

    private String buildFixtureJsonWithNullableFields(int fixtureId, int timestamp,
                                                      String leagueName, String country,
                                                      String homeTeam, String awayTeam) {
        String leagueNameField;
        String countryField;

        if ("__NULL__".equals(leagueName)) {
            leagueNameField = "null";
        } else {
            leagueNameField = jsonString(leagueName);
        }

        if ("__NULL__".equals(country)) {
            countryField = "null";
        } else {
            countryField = jsonString(country);
        }

        return String.format("""
                {
                  "response": [
                    {
                      "fixture": { "id": %d, "timestamp": %d, "status": { "long": "First Half", "elapsed": 32 } },
                      "league": { "id": 1, "name": %s, "country": %s },
                      "teams": { "home": { "name": %s }, "away": { "name": %s } }
                    }
                  ]
                }
                """,
                fixtureId, timestamp,
                leagueNameField, countryField,
                jsonString(homeTeam), jsonString(awayTeam));
    }

    /**
     * Escapes a string for safe embedding in JSON.
     */
    private String jsonString(String value) {
        if (value == null) {
            return "null";
        }
        // Escape backslashes, quotes, and control characters for valid JSON
        String escaped = value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
        return "\"" + escaped + "\"";
    }
}
