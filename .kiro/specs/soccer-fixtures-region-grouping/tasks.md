# Implementation Plan: Soccer Fixtures Region Grouping

## Overview

This plan implements region-based grouping for soccer fixtures. The backend is extended to extract league/country metadata from API-Sports responses and include it in `FixtureDto`. The frontend implements a pure grouping utility and a collapsible region-grouped layout for soccer, while preserving the existing flat layout for other sports.

## Tasks

- [x] 1. Extend backend FixtureDto and parsing logic
  - [x] 1.1 Add `leagueName` and `country` fields to FixtureDto
    - Modify `FixtureDto.java` to add two new `String` fields: `leagueName` and `country`
    - The record becomes: `FixtureDto(String fixtureId, SportType sportType, Map<String, String> participants, String status, Long startTime, String leagueName, String country)`
    - Update all existing call sites that construct `FixtureDto` instances to include the new fields
    - _Requirements: 2.1, 2.2_

  - [x] 1.2 Extract league metadata in `parseFixturesResponse`
    - In `FixtureController.parseFixturesResponse()`, extract `league.name` and `league.country` from each fixture JSON node
    - If `league.country` is null, empty, or whitespace-only, default to `"International"`
    - If `league.name` is null, empty, or whitespace-only, default to `"Unknown League"`
    - If the `league` object is entirely missing, use both defaults
    - Truncate values exceeding 100 characters to 100 characters
    - Pass extracted values to the `FixtureDto` constructor
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 2.1, 2.2_

  - [x] 1.3 Update mock fixtures with realistic league/country data
    - In `FixtureController.getMockFixtures()`, update all soccer mock fixtures to include realistic league/country values (e.g., "Champions League"/"World", "Premier League"/"England")
    - Update non-soccer mock fixtures with appropriate default values (e.g., "NBA"/"USA" for basketball)
    - _Requirements: 2.3_

  - [x] 1.4 Write property tests for league metadata extraction (jqwik)
    - **Property 1: League metadata extraction preserves source data**
    - Generate random fixture JSON nodes with varying `league.name` and `league.country` values; verify parsed DTO fields match source
    - **Property 2: FixtureDto league fields are always non-empty and bounded**
    - Generate fixtures with null, blank, whitespace, empty, and very long league/country values; verify DTO fields are always non-empty and ≤100 chars
    - Tag: `@Tag("Feature: soccer-fixtures-region-grouping, Property 1: ...")` and `@Tag("Feature: soccer-fixtures-region-grouping, Property 2: ...")`
    - Minimum 100 iterations per property
    - **Validates: Requirements 1.1, 1.2, 1.3, 1.4, 1.5, 2.1, 2.2**

  - [x] 1.5 Write unit tests for backend league metadata extraction
    - Test missing `league` object → defaults applied
    - Test null/blank/whitespace `league.country` → "International"
    - Test null/blank/whitespace `league.name` → "Unknown League"
    - Test values exceeding 100 characters → truncated
    - Test unicode and special characters in league names
    - Test mock mode returns realistic league/country combos
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 2.3_

- [~] 2. Checkpoint - Backend complete
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 3. Implement frontend grouping utility
  - [~] 3.1 Create `groupFixturesByRegion` utility function
    - Create `frontend/src/lib/fixtureGrouping.ts`
    - Define `Fixture` interface (extending existing with optional `leagueName` and `country` fields)
    - Define `RegionGroup` interface with `label: string` and `fixtures: Fixture[]`
    - Implement `groupFixturesByRegion(fixtures: Fixture[]): RegionGroup[]` as a pure function
    - Group fixtures by `"${effectiveCountry} - ${effectiveLeagueName}"` label
    - Default missing/null/undefined/empty `country` to `"International"` and `leagueName` to `"Unknown League"`
    - Sort groups alphabetically (case-insensitive) by label
    - Sort fixtures within each group by `startTime` ascending
    - _Requirements: 3.1, 3.3, 3.4, 3.6_

  - [~] 3.2 Write property tests for `groupFixturesByRegion` (fast-check)
    - **Property 3: Grouping correctness with default handling**
    - Generate random fixture arrays with varying country/leagueName (including null/undefined/empty); verify every fixture appears in exactly one group with correct label
    - **Property 4: Region groups are sorted alphabetically**
    - Generate random fixtures; verify groups are in case-insensitive ascending order
    - **Property 5: Fixtures within a group are sorted by start time**
    - Generate fixtures with random startTimes; verify intra-group ascending sort
    - `numRuns: 100` minimum per property
    - Describe block: `"Feature: soccer-fixtures-region-grouping, Property N: ..."`
    - **Validates: Requirements 3.1, 3.3, 3.4, 3.6**

  - [~] 3.3 Write unit tests for `groupFixturesByRegion`
    - Test empty array returns empty array
    - Test single fixture produces single group
    - Test multiple fixtures in same league grouped together
    - Test fixtures from different leagues produce separate groups
    - Test null/undefined/empty country defaults to "International"
    - Test null/undefined/empty leagueName defaults to "Unknown League"
    - Test case-insensitive alphabetical sorting of groups
    - Test startTime ascending sorting within groups
    - _Requirements: 3.1, 3.3, 3.4, 3.6_

- [ ] 4. Implement collapsible region group UI
  - [~] 4.1 Create `RegionGroupHeader` component
    - Create `frontend/src/components/dashboard/RegionGroupHeader.tsx`
    - Render group label text and a chevron icon indicating expand/collapse state
    - When collapsed, display parenthetical fixture count (e.g., "(3)")
    - Handle click to toggle expand/collapse
    - Handle Enter and Space keypress to toggle (keyboard accessibility)
    - Add ARIA attributes: `aria-expanded`, `aria-controls`, `role="button"`, `tabIndex={0}`
    - Style: consistent with existing dark-themed UI in the project
    - _Requirements: 4.1, 4.2, 4.3, 4.5_

  - [~] 4.2 Write unit tests for `RegionGroupHeader`
    - Test renders group label and fixture count when collapsed
    - Test click toggles expanded state
    - Test Enter key toggles expanded state
    - Test Space key toggles expanded state
    - Test ARIA attributes update correctly
    - Test chevron icon rotates on toggle
    - _Requirements: 4.1, 4.2, 4.3, 4.5_

- [ ] 5. Integrate region grouping into FixtureList
  - [~] 5.1 Update `Fixture` interface in `FixtureList.tsx`
    - Add optional `leagueName?: string` and `country?: string` fields to the existing `Fixture` interface
    - _Requirements: 2.1, 2.2_

  - [~] 5.2 Implement grouped layout for soccer in `FixtureList.tsx`
    - Import `groupFixturesByRegion` from `@/lib/fixtureGrouping`
    - Import `RegionGroupHeader` component
    - When `sport === "SOCCER"`: call `groupFixturesByRegion(fixtures)`, render vertical stack of region groups
    - Each region group: `RegionGroupHeader` + horizontal scrollable row of fixture cards (when expanded)
    - When non-soccer: render existing flat horizontal list unchanged
    - Constrain overall fixture area to max-height 600px with vertical overflow scroll
    - Maintain horizontal scroll within each group's fixture row
    - _Requirements: 3.1, 3.2, 3.5, 5.1, 5.2, 5.3, 5.4_

  - [~] 5.3 Implement session storage persistence for collapse state
    - Store collapse state per group using key format: `fixture-group-state-${groupLabel}`
    - Values: `"collapsed"` or `"expanded"`
    - On mount, read persisted state for each group; default to expanded if no key exists
    - On toggle, write new state to sessionStorage
    - Gracefully handle `sessionStorage` unavailability (e.g., private browsing) by falling back to all-expanded with no persistence
    - _Requirements: 4.1, 4.4_

  - [~] 5.4 Write property test for collapsed group count
    - **Property 6: Collapsed group count matches actual fixture count**
    - Generate fixture arrays, verify count displayed for each group equals the number of fixtures in that group
    - `numRuns: 100` minimum
    - Describe block: `"Feature: soccer-fixtures-region-grouping, Property 6: ..."`
    - **Validates: Requirements 4.3**

  - [~] 5.5 Write unit tests for FixtureList integration
    - Test soccer sport renders grouped vertical layout
    - Test non-soccer sport renders flat horizontal list
    - Test session storage read on mount
    - Test session storage write on toggle
    - Test max-height 600px container with vertical scroll
    - Test horizontal scroll within fixture rows
    - Test fallback when sessionStorage is unavailable
    - Test expanded by default when no persisted state
    - _Requirements: 3.2, 3.5, 4.1, 4.4, 5.1, 5.2, 5.3, 5.4_

- [~] 6. Final checkpoint
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties from the design document
- Unit tests validate specific examples and edge cases
- Backend uses Java (Spring Boot, jqwik for property tests, JUnit 5 for unit tests)
- Frontend uses TypeScript (Next.js/React, fast-check for property tests, Vitest + Testing Library for unit tests)

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1"] },
    { "id": 1, "tasks": ["1.2", "1.3"] },
    { "id": 2, "tasks": ["1.4", "1.5", "3.1"] },
    { "id": 3, "tasks": ["3.2", "3.3", "4.1"] },
    { "id": 4, "tasks": ["4.2", "5.1"] },
    { "id": 5, "tasks": ["5.2", "5.3"] },
    { "id": 6, "tasks": ["5.4", "5.5"] }
  ]
}
```
