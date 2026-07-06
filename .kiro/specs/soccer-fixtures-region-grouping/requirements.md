# Requirements Document

## Introduction

Soccer fixtures currently load as a flat horizontal list with no organizational structure. This feature groups soccer fixtures by their league/region (e.g., "England - Premier League", "Spain - La Liga") so users can quickly locate games from competitions they care about. The grouping requires extracting league and country metadata from the API-Sports response on the backend, passing it to the frontend via the FixtureDto, and rendering fixtures in collapsible region-based groups in the UI.

## Glossary

- **Fixture_API**: The Spring Boot REST endpoint at `/api/fixtures` that queries API-Sports for live fixture data and returns `FixtureDto` objects to the frontend.
- **FixtureDto**: The data transfer object representing a single fixture, transmitted from backend to frontend as JSON.
- **Region_Group**: A visual section in the fixture list UI that contains all fixtures belonging to the same league and country combination (e.g., "England - Premier League").
- **Fixture_List_Component**: The React component (`FixtureList.tsx`) that fetches and renders available soccer fixtures.
- **League_Metadata**: The country name and league name associated with a fixture, sourced from the API-Sports response `league` object.
- **Grouped_Fixture_Response**: A response structure that organizes fixtures into groups keyed by region/league, rather than a flat array.

## Requirements

### Requirement 1: Backend Extracts League Metadata from API-Sports Response

**User Story:** As a developer, I want the backend to extract league and country information from the API-Sports fixture response, so that the frontend can group fixtures by region.

#### Acceptance Criteria

1. WHEN a soccer fixture response is received from API-Sports, THE Fixture_API SHALL extract the country name from the `league.country` field of each fixture and populate the corresponding FixtureDto country field.
2. WHEN a soccer fixture response is received from API-Sports, THE Fixture_API SHALL extract the league name from the `league.name` field of each fixture and populate the corresponding FixtureDto leagueName field.
3. IF the `league.country` field is null, empty, or contains only whitespace, THEN THE Fixture_API SHALL use "International" as the default country name.
4. IF the `league.name` field is null, empty, or contains only whitespace, THEN THE Fixture_API SHALL use "Unknown League" as the default league name.
5. IF the `league` object is entirely missing from a fixture entry in the API-Sports response, THEN THE Fixture_API SHALL use "International" as the country name and "Unknown League" as the league name.

### Requirement 2: FixtureDto Includes League and Country Fields

**User Story:** As a frontend developer, I want each fixture to include league and country metadata, so that I can group fixtures in the UI.

#### Acceptance Criteria

1. THE FixtureDto SHALL include a `leagueName` field of type String representing the league name, with a maximum length of 100 characters and a non-empty value.
2. THE FixtureDto SHALL include a `country` field of type String representing the country or region, with a maximum length of 100 characters and a non-empty value.
3. WHEN running in mock mode, THE Fixture_API SHALL provide league and country values that correspond to real-world soccer leagues and their associated countries for each mock soccer fixture (e.g., "Premier League" / "England", "La Liga" / "Spain").
4. IF league or country metadata is unavailable from the external data source, THEN THE Fixture_API SHALL return a default value of "Unknown" for the missing field.

### Requirement 3: Frontend Groups Soccer Fixtures by Region

**User Story:** As a user, I want soccer fixtures grouped by region/league, so that I can quickly find games from the competitions I follow.

#### Acceptance Criteria

1. WHEN soccer fixtures are loaded, THE Fixture_List_Component SHALL group fixtures by their combined country and league name (formatted as "Country - League Name") using the `country` and `leagueName` fields from each FixtureDto.
2. WHILE the sport is soccer, THE Fixture_List_Component SHALL display each Region_Group as a visually separated section with a header showing the group label text.
3. WHILE the sport is soccer, THE Fixture_List_Component SHALL sort Region_Groups in ascending alphabetical order (case-insensitive) by their group label.
4. WHEN a Region_Group contains multiple fixtures, THE Fixture_List_Component SHALL sort fixtures within the group by start time in ascending order.
5. WHEN fixtures are loaded for non-soccer sports, THE Fixture_List_Component SHALL display fixtures in the existing flat horizontal list format without region grouping.
6. IF a fixture's `country` or `leagueName` field is null, undefined, or empty, THEN THE Fixture_List_Component SHALL assign that fixture to a group labeled "International - Unknown League" using "International" for missing country and "Unknown League" for missing league name.

### Requirement 4: Region Groups Are Collapsible

**User Story:** As a user, I want to collapse region groups I'm not interested in, so that I can reduce visual clutter and focus on relevant leagues.

#### Acceptance Criteria

1. THE Fixture_List_Component SHALL display each Region_Group in an expanded state by default when no persisted state exists for that group in the current browser session.
2. WHEN a user clicks or activates a Region_Group header, THE Fixture_List_Component SHALL toggle the visibility of fixtures within that group and update a visual indicator to reflect the current expanded or collapsed state.
3. WHILE a Region_Group is collapsed, THE Fixture_List_Component SHALL display the group header with a parenthetical count of fixtures in that group (e.g., "(3)") that updates when the fixture data changes.
4. THE Fixture_List_Component SHALL persist the expanded/collapsed state of each group using session storage so that the state is retained across in-app navigation but resets when the browser tab is closed.
5. WHEN a user presses the Enter or Space key while a Region_Group header has focus, THE Fixture_List_Component SHALL toggle the visibility of fixtures within that group identically to a click interaction.

### Requirement 5: Grouped Layout Uses Vertical Sections

**User Story:** As a user, I want the grouped fixture view to use a vertical layout with sections, so that I can scan through multiple leagues naturally.

#### Acceptance Criteria

1. WHEN soccer fixtures are grouped, THE Fixture_List_Component SHALL render Region_Groups in a vertical stack layout where each Region_Group is positioned below the previous one.
2. THE Fixture_List_Component SHALL render fixtures within each Region_Group in a horizontal scrollable row that clips overflowing fixture cards and allows the user to scroll horizontally to reveal them.
3. THE Fixture_List_Component SHALL constrain the overall fixture area to a maximum height of 600px, and IF the combined height of all rendered Region_Groups exceeds 600px, THEN THE Fixture_List_Component SHALL enable vertical scrolling within the fixture area.
4. WHEN fixtures are loaded for non-soccer sports, THE Fixture_List_Component SHALL render fixtures in a single horizontal scrollable row without vertical Region_Group sections.
