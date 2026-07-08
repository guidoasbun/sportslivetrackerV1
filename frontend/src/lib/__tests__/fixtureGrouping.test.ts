import { describe, it, expect } from "vitest";
import { groupFixturesByRegion, Fixture } from "../fixtureGrouping";

function makeFixture(overrides: Partial<Fixture> = {}): Fixture {
  return {
    fixtureId: "1",
    sportType: "SOCCER",
    participants: { home: "Team A", away: "Team B" },
    status: "Not Started",
    startTime: 1700000000,
    ...overrides,
  };
}

describe("groupFixturesByRegion", () => {
  it("returns empty array when given empty array", () => {
    const result = groupFixturesByRegion([]);
    expect(result).toEqual([]);
  });

  it("produces a single group for a single fixture", () => {
    const fixture = makeFixture({ country: "England", leagueName: "Premier League" });
    const result = groupFixturesByRegion([fixture]);

    expect(result).toHaveLength(1);
    expect(result[0].label).toBe("England - Premier League");
    expect(result[0].fixtures).toHaveLength(1);
    expect(result[0].fixtures[0]).toEqual(fixture);
  });

  it("groups multiple fixtures in the same league together", () => {
    const fixtures: Fixture[] = [
      makeFixture({ fixtureId: "1", country: "England", leagueName: "Premier League", startTime: 1700000100 }),
      makeFixture({ fixtureId: "2", country: "England", leagueName: "Premier League", startTime: 1700000200 }),
      makeFixture({ fixtureId: "3", country: "England", leagueName: "Premier League", startTime: 1700000300 }),
    ];

    const result = groupFixturesByRegion(fixtures);

    expect(result).toHaveLength(1);
    expect(result[0].label).toBe("England - Premier League");
    expect(result[0].fixtures).toHaveLength(3);
  });

  it("produces separate groups for fixtures from different leagues", () => {
    const fixtures: Fixture[] = [
      makeFixture({ fixtureId: "1", country: "England", leagueName: "Premier League" }),
      makeFixture({ fixtureId: "2", country: "Spain", leagueName: "La Liga" }),
      makeFixture({ fixtureId: "3", country: "Germany", leagueName: "Bundesliga" }),
    ];

    const result = groupFixturesByRegion(fixtures);

    expect(result).toHaveLength(3);
    const labels = result.map((g) => g.label);
    expect(labels).toContain("England - Premier League");
    expect(labels).toContain("Spain - La Liga");
    expect(labels).toContain("Germany - Bundesliga");
  });

  it("defaults null/undefined/empty country to 'International'", () => {
    const fixtures: Fixture[] = [
      makeFixture({ fixtureId: "1", country: null as unknown as string, leagueName: "Champions League" }),
      makeFixture({ fixtureId: "2", country: undefined, leagueName: "Champions League" }),
      makeFixture({ fixtureId: "3", country: "", leagueName: "Champions League" }),
      makeFixture({ fixtureId: "4", country: "   ", leagueName: "Champions League" }),
    ];

    const result = groupFixturesByRegion(fixtures);

    expect(result).toHaveLength(1);
    expect(result[0].label).toBe("International - Champions League");
    expect(result[0].fixtures).toHaveLength(4);
  });

  it("defaults null/undefined/empty leagueName to 'Unknown League'", () => {
    const fixtures: Fixture[] = [
      makeFixture({ fixtureId: "1", country: "France", leagueName: null as unknown as string }),
      makeFixture({ fixtureId: "2", country: "France", leagueName: undefined }),
      makeFixture({ fixtureId: "3", country: "France", leagueName: "" }),
      makeFixture({ fixtureId: "4", country: "France", leagueName: "  " }),
    ];

    const result = groupFixturesByRegion(fixtures);

    expect(result).toHaveLength(1);
    expect(result[0].label).toBe("France - Unknown League");
    expect(result[0].fixtures).toHaveLength(4);
  });

  it("sorts groups alphabetically by label (case-insensitive)", () => {
    const fixtures: Fixture[] = [
      makeFixture({ fixtureId: "1", country: "spain", leagueName: "La Liga" }),
      makeFixture({ fixtureId: "2", country: "England", leagueName: "Premier League" }),
      makeFixture({ fixtureId: "3", country: "germany", leagueName: "Bundesliga" }),
    ];

    const result = groupFixturesByRegion(fixtures);

    expect(result).toHaveLength(3);
    expect(result[0].label).toBe("England - Premier League");
    expect(result[1].label).toBe("germany - Bundesliga");
    expect(result[2].label).toBe("spain - La Liga");
  });

  it("sorts fixtures within a group by startTime ascending", () => {
    const fixtures: Fixture[] = [
      makeFixture({ fixtureId: "3", country: "England", leagueName: "Premier League", startTime: 1700000300 }),
      makeFixture({ fixtureId: "1", country: "England", leagueName: "Premier League", startTime: 1700000100 }),
      makeFixture({ fixtureId: "2", country: "England", leagueName: "Premier League", startTime: 1700000200 }),
    ];

    const result = groupFixturesByRegion(fixtures);

    expect(result).toHaveLength(1);
    expect(result[0].fixtures[0].fixtureId).toBe("1");
    expect(result[0].fixtures[1].fixtureId).toBe("2");
    expect(result[0].fixtures[2].fixtureId).toBe("3");
  });
});
