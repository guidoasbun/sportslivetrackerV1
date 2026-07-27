/**
 * Fixture grouping utility for organizing soccer fixtures by region/league.
 *
 * Groups fixtures by their country and league name, with sensible defaults
 * for missing metadata. Produces alphabetically sorted groups with
 * time-sorted fixtures within each group.
 */

export interface Fixture {
  fixtureId: string;
  sportType: string;
  participants: Record<string, string>;
  status: string;
  startTime: number;
  leagueName?: string;
  country?: string;
}

export interface RegionGroup {
  label: string;
  fixtures: Fixture[];
}

/**
 * Groups fixtures by region label ("Country - League Name").
 *
 * - Defaults missing/null/undefined/empty `country` to "International"
 * - Defaults missing/null/undefined/empty `leagueName` to "Unknown League"
 * - Sorts groups alphabetically (case-insensitive) by label
 * - Sorts fixtures within each group by startTime ascending
 */
export function groupFixturesByRegion(fixtures: Fixture[]): RegionGroup[] {
  const groupMap = new Map<string, Fixture[]>();

  for (const fixture of fixtures) {
    const effectiveCountry = isNonEmpty(fixture.country) ? fixture.country! : "International";
    const effectiveLeagueName = isNonEmpty(fixture.leagueName) ? fixture.leagueName! : "Unknown League";
    const label = `${effectiveCountry} - ${effectiveLeagueName}`;

    const group = groupMap.get(label);
    if (group) {
      group.push(fixture);
    } else {
      groupMap.set(label, [fixture]);
    }
  }

  const groups: RegionGroup[] = Array.from(groupMap.entries()).map(([label, fixtures]) => ({
    label,
    fixtures: fixtures.slice().sort((a, b) => a.startTime - b.startTime),
  }));

  groups.sort((a, b) => a.label.toLowerCase().localeCompare(b.label.toLowerCase()));

  return groups;
}

function isNonEmpty(value: string | undefined | null): boolean {
  return value !== undefined && value !== null && value.trim().length > 0;
}
