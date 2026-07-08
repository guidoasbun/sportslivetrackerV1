import { describe, it, expect } from "vitest";
import * as fc from "fast-check";
import { groupFixturesByRegion, Fixture } from "../fixtureGrouping";

/**
 * Property-based tests for fixture grouping utility.
 * Feature: soccer-fixtures-region-grouping
 */

// --- Arbitraries (generators) ---

/** Generate a random country value: string, empty string, or undefined */
function arbitraryCountry(): fc.Arbitrary<string | undefined> {
  return fc.oneof(
    fc.constant(undefined),
    fc.constant(""),
    fc.constant("   "),
    fc.stringMatching(/^[A-Za-z ]+$/, { minLength: 1, maxLength: 30 })
  );
}

/** Generate a random league name value: string, empty string, or undefined */
function arbitraryLeagueName(): fc.Arbitrary<string | undefined> {
  return fc.oneof(
    fc.constant(undefined),
    fc.constant(""),
    fc.constant("   "),
    fc.stringMatching(/^[A-Za-z0-9 ]+$/, { minLength: 1, maxLength: 30 })
  );
}

/** Generate a single valid fixture with optional country/leagueName */
function arbitraryFixture(): fc.Arbitrary<Fixture> {
  return fc.record({
    fixtureId: fc.uuid(),
    sportType: fc.constant("SOCCER"),
    participants: fc.constant({ home: "TeamA", away: "TeamB" }),
    status: fc.constant("Not Started"),
    startTime: fc.integer({ min: 1_000_000_000_000, max: 2_000_000_000_000 }),
    leagueName: arbitraryLeagueName(),
    country: arbitraryCountry(),
  });
}

/** Generate an array of fixtures */
function arbitraryFixtures(): fc.Arbitrary<Fixture[]> {
  return fc.array(arbitraryFixture(), { minLength: 0, maxLength: 30 });
}

// --- Helper functions ---

function effectiveCountry(fixture: Fixture): string {
  const val = fixture.country;
  if (val === undefined || val === null || val.trim().length === 0) {
    return "International";
  }
  return val;
}

function effectiveLeagueName(fixture: Fixture): string {
  const val = fixture.leagueName;
  if (val === undefined || val === null || val.trim().length === 0) {
    return "Unknown League";
  }
  return val;
}

function expectedLabel(fixture: Fixture): string {
  return `${effectiveCountry(fixture)} - ${effectiveLeagueName(fixture)}`;
}

// --- Property Tests ---

describe("Feature: soccer-fixtures-region-grouping, Property 3: Grouping correctness with default handling", () => {
  /**
   * **Validates: Requirements 3.1, 3.6**
   *
   * For any list of fixtures, after calling groupFixturesByRegion, every fixture
   * SHALL appear in exactly one group whose label equals
   * "${effectiveCountry} - ${effectiveLeagueName}" where effectiveCountry is
   * "International" if the fixture's country is null/undefined/empty and
   * effectiveLeagueName is "Unknown League" if the fixture's leagueName is null/undefined/empty.
   */
  it("every fixture appears in exactly one group with the correct label", () => {
    fc.assert(
      fc.property(arbitraryFixtures(), (fixtures) => {
        const groups = groupFixturesByRegion(fixtures);

        // Collect all fixtures from all groups
        const allGroupedFixtures = groups.flatMap((g) => g.fixtures);

        // Total count must match input
        expect(allGroupedFixtures).toHaveLength(fixtures.length);

        // Every input fixture must appear in exactly one group with the correct label
        for (const fixture of fixtures) {
          const expected = expectedLabel(fixture);

          // Find the group this fixture belongs to
          const matchingGroups = groups.filter((g) =>
            g.fixtures.some((f) => f.fixtureId === fixture.fixtureId && f.startTime === fixture.startTime)
          );

          // Fixture appears in exactly one group
          expect(matchingGroups).toHaveLength(1);

          // That group has the correct label
          expect(matchingGroups[0].label).toBe(expected);
        }
      }),
      { numRuns: 100 }
    );
  });

  it("no fixture is duplicated across groups", () => {
    fc.assert(
      fc.property(arbitraryFixtures(), (fixtures) => {
        const groups = groupFixturesByRegion(fixtures);
        const allGroupedFixtures = groups.flatMap((g) => g.fixtures);

        // Total count matches — no duplicates, no losses
        expect(allGroupedFixtures).toHaveLength(fixtures.length);
      }),
      { numRuns: 100 }
    );
  });
});

describe("Feature: soccer-fixtures-region-grouping, Property 4: Region groups are sorted alphabetically", () => {
  /**
   * **Validates: Requirements 3.3**
   *
   * For any list of fixtures, the array of RegionGroup objects returned SHALL have
   * labels in case-insensitive ascending alphabetical order — for all consecutive
   * pairs (groups[i], groups[i+1]), groups[i].label.toLowerCase() <= groups[i+1].label.toLowerCase().
   */
  it("groups are in case-insensitive ascending alphabetical order", () => {
    fc.assert(
      fc.property(arbitraryFixtures(), (fixtures) => {
        const groups = groupFixturesByRegion(fixtures);

        for (let i = 0; i < groups.length - 1; i++) {
          const current = groups[i].label.toLowerCase();
          const next = groups[i + 1].label.toLowerCase();
          expect(current <= next).toBe(true);
        }
      }),
      { numRuns: 100 }
    );
  });
});

describe("Feature: soccer-fixtures-region-grouping, Property 5: Fixtures within a group are sorted by start time", () => {
  /**
   * **Validates: Requirements 3.4**
   *
   * For any group returned that contains more than one fixture, the fixtures SHALL
   * be in ascending order by startTime — for all consecutive pairs
   * (fixtures[j], fixtures[j+1]), fixtures[j].startTime <= fixtures[j+1].startTime.
   */
  it("fixtures within each group are in ascending startTime order", () => {
    fc.assert(
      fc.property(arbitraryFixtures(), (fixtures) => {
        const groups = groupFixturesByRegion(fixtures);

        for (const group of groups) {
          for (let j = 0; j < group.fixtures.length - 1; j++) {
            expect(group.fixtures[j].startTime).toBeLessThanOrEqual(
              group.fixtures[j + 1].startTime
            );
          }
        }
      }),
      { numRuns: 100 }
    );
  });
});


describe("Feature: soccer-fixtures-region-grouping, Property 6: Collapsed group count matches actual fixture count", () => {
  /**
   * **Validates: Requirements 4.3**
   *
   * For any RegionGroup with N fixtures (N ≥ 1), the count value displayed when
   * collapsed (fixtureCount prop passed to RegionGroupHeader) SHALL equal N.
   * Furthermore, N SHALL equal the number of fixtures in the input whose effective
   * group label matches this group's label.
   */
  it("each group's fixture count equals the number of input fixtures belonging to that group", () => {
    fc.assert(
      fc.property(
        fc.array(arbitraryFixture(), { minLength: 1, maxLength: 30 }),
        (fixtures) => {
          const groups = groupFixturesByRegion(fixtures);

          for (const group of groups) {
            // The fixtureCount that would be passed to RegionGroupHeader
            const fixtureCount = group.fixtures.length;

            // Count fixtures from input that should belong to this group
            const expectedCount = fixtures.filter(
              (f) => expectedLabel(f) === group.label
            ).length;

            // The displayed count must equal the actual number of fixtures in the group
            expect(fixtureCount).toBeGreaterThanOrEqual(1);
            expect(fixtureCount).toBe(expectedCount);
          }
        }
      ),
      { numRuns: 100 }
    );
  });
});
