import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { renderHook, act } from "@testing-library/react";
import fc from "fast-check";
import { useEventBuffer } from "../useEventBuffer";

/**
 * Property 4: Event buffer time-travel filtering
 *
 * For any list of events with timestamps and any non-negative offset value in seconds,
 * the visible events returned by useEventBuffer SHALL include only events whose
 * eventTimestamp is at least offsetSeconds older than the current time, and SHALL
 * exclude all events newer than that threshold.
 *
 * **Validates: Requirements 7.2**
 */

// Mock EventSource since jsdom doesn't provide it
class MockEventSource {
  static instances: MockEventSource[] = [];
  onopen: (() => void) | null = null;
  onmessage: ((event: { data: string }) => void) | null = null;
  onerror: ((error: unknown) => void) | null = null;
  readyState = 0;
  url: string;

  constructor(url: string) {
    this.url = url;
    MockEventSource.instances.push(this);
    // Open connection synchronously — these tests don't assert async connection behavior
    this.readyState = 1;
    this.onopen?.();
  }

  close() {
    this.readyState = 2;
  }

  // Helper to simulate receiving a message
  simulateMessage(data: string) {
    this.onmessage?.({ data });
  }
}

// Arbitrary for generating sport events with controlled timestamps
const sportTypes = [
  "SOCCER",
  "FOOTBALL",
  "BASKETBALL",
  "BASEBALL",
  "HOCKEY",
  "FORMULA_1",
] as const;

const sportEventArb = (timestampRange: { min: number; max: number }) =>
  fc.record({
    eventId: fc.uuid(),
    sportType: fc.constantFrom(...sportTypes),
    action: fc.string({ minLength: 1, maxLength: 20 }),
    participants: fc.dictionary(
      fc.string({ minLength: 1, maxLength: 10 }),
      fc.string({ minLength: 1, maxLength: 10 }),
      { minKeys: 1, maxKeys: 3 }
    ),
    eventTimestamp: fc.integer({ min: timestampRange.min, max: timestampRange.max }),
  });

describe("useEventBuffer - Property Tests", () => {
  let originalEventSource: typeof globalThis.EventSource;

  beforeEach(() => {
    MockEventSource.instances = [];
    originalEventSource = globalThis.EventSource as typeof globalThis.EventSource;
    (globalThis as unknown as Record<string, unknown>).EventSource = MockEventSource;
  });

  afterEach(() => {
    (globalThis as unknown as Record<string, unknown>).EventSource = originalEventSource;
    vi.restoreAllMocks();
  });

  it("Property 4: visible events are exactly those older than offsetSeconds", () => {
    fc.assert(
      fc.property(
        // Generate a fixed "now" timestamp (somewhere around recent epoch)
        fc.integer({ min: 1_700_000_000_000, max: 1_800_000_000_000 }),
        // Generate offset in seconds (0 to 120 seconds)
        fc.integer({ min: 0, max: 120 }),
        // Generate a list of events (1 to 20 events)
        fc.integer({ min: 1_700_000_000_000, max: 1_800_000_000_000 }).chain(
          () =>
            fc.array(
              sportEventArb({ min: 1_600_000_000_000, max: 1_900_000_000_000 }),
              { minLength: 1, maxLength: 20 }
            )
        ),
        (fixedNow, offsetSeconds, events) => {
          // Mock Date.now to return our fixed value
          const dateNowSpy = vi.spyOn(Date, "now").mockReturnValue(fixedNow);

          // Render the hook with the given offset
          const { result } = renderHook(() => useEventBuffer(offsetSeconds));

          // Get the EventSource instance and simulate sending all events
          const eventSource = MockEventSource.instances[MockEventSource.instances.length - 1];

          act(() => {
            for (const event of events) {
              eventSource.simulateMessage(JSON.stringify(event));
            }
          });

          // Get visible events from the hook
          const visibleEvents = result.current.visibleEvents;

          // Compute expected partition
          const expectedVisible = events.filter((event) => {
            const secondsSinceEvent = (fixedNow - event.eventTimestamp) / 1000;
            return secondsSinceEvent >= offsetSeconds;
          });

          const expectedHidden = events.filter((event) => {
            const secondsSinceEvent = (fixedNow - event.eventTimestamp) / 1000;
            return secondsSinceEvent < offsetSeconds;
          });

          // All visible events must satisfy the threshold condition
          for (const event of visibleEvents) {
            const secondsSinceEvent = (fixedNow - event.eventTimestamp) / 1000;
            expect(secondsSinceEvent).toBeGreaterThanOrEqual(offsetSeconds);
          }

          // Visible events count must match expected
          expect(visibleEvents).toHaveLength(expectedVisible.length);

          // Verify no hidden event sneaked through
          const visibleIds = new Set(visibleEvents.map((e) => e.eventId));
          for (const hiddenEvent of expectedHidden) {
            expect(visibleIds.has(hiddenEvent.eventId)).toBe(false);
          }

          // Cleanup
          dateNowSpy.mockRestore();
        }
      ),
      { numRuns: 100 }
    );
  });
});
