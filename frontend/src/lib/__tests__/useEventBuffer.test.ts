import { renderHook, act } from '@testing-library/react';
import { useEventBuffer } from '@/lib/useEventBuffer';
import { SportEvent } from '@/types/event';

// Mock EventSource
let mockEventSourceInstance: {
    onopen: (() => void) | null;
    onmessage: ((event: { data: string }) => void) | null;
    onerror: ((error: Event) => void) | null;
    close: ReturnType<typeof vi.fn>;
};

class MockEventSource {
    onopen: (() => void) | null = null;
    onmessage: ((event: { data: string }) => void) | null = null;
    onerror: ((error: Event) => void) | null = null;
    close = vi.fn();

    constructor(_url: string) {
        mockEventSourceInstance = this;
    }
}

function createEvent(overrides: Partial<SportEvent> = {}): SportEvent {
    return {
        eventId: crypto.randomUUID(),
        sportType: 'SOCCER',
        action: 'GOAL',
        participants: { home: 'Team A', away: 'Team B' },
        eventTimestamp: Date.now(),
        ...overrides,
    };
}

const TEST_FIXTURE_ID = 'fixture-123';

describe('useEventBuffer', () => {
    beforeEach(() => {
        vi.stubGlobal('EventSource', MockEventSource);
    });

    afterEach(() => {
        vi.unstubAllGlobals();
        vi.restoreAllMocks();
    });

    it('includes events older than offsetSeconds in visibleEvents', () => {
        const now = 1000000;
        vi.spyOn(Date, 'now').mockReturnValue(now);

        const offsetSeconds = 10;
        // Event happened 15 seconds ago — should be visible
        const oldEvent = createEvent({ eventId: 'old-1', eventTimestamp: now - 15000 });

        const { result } = renderHook(() => useEventBuffer(offsetSeconds, TEST_FIXTURE_ID));

        // Simulate connection open and receiving a message
        act(() => {
            mockEventSourceInstance.onopen?.();
            mockEventSourceInstance.onmessage?.({ data: JSON.stringify(oldEvent) });
        });

        expect(result.current.visibleEvents).toHaveLength(1);
        expect(result.current.visibleEvents[0].eventId).toBe('old-1');
        expect(result.current.isConnected).toBe(true);
    });

    it('excludes events newer than the offset threshold from visibleEvents', () => {
        const now = 1000000;
        vi.spyOn(Date, 'now').mockReturnValue(now);

        const offsetSeconds = 10;
        // Event happened 3 seconds ago — too recent, should be excluded
        const recentEvent = createEvent({ eventId: 'recent-1', eventTimestamp: now - 3000 });

        const { result } = renderHook(() => useEventBuffer(offsetSeconds, TEST_FIXTURE_ID));

        act(() => {
            mockEventSourceInstance.onopen?.();
            mockEventSourceInstance.onmessage?.({ data: JSON.stringify(recentEvent) });
        });

        expect(result.current.visibleEvents).toHaveLength(0);
        // Event is still buffered even though not visible
        expect(result.current.totalBuffered).toBe(1);
    });

    it('caps the buffer at 500 stored events', () => {
        const now = 1000000;
        vi.spyOn(Date, 'now').mockReturnValue(now);

        const { result } = renderHook(() => useEventBuffer(0, TEST_FIXTURE_ID));

        act(() => {
            mockEventSourceInstance.onopen?.();
            // Send 510 events — buffer should cap at 500
            for (let i = 0; i < 510; i++) {
                const event = createEvent({
                    eventId: `event-${i}`,
                    eventTimestamp: now - (i + 1) * 1000,
                });
                mockEventSourceInstance.onmessage?.({ data: JSON.stringify(event) });
            }
        });

        expect(result.current.totalBuffered).toBe(500);
        // The most recent events should be kept (they're prepended)
        expect(result.current.visibleEvents[0].eventId).toBe('event-509');
    });

    it('does not open EventSource when fixtureId is null', () => {
        const { result } = renderHook(() => useEventBuffer(0, null));

        expect(result.current.visibleEvents).toHaveLength(0);
        expect(result.current.isConnected).toBe(false);
        expect(result.current.totalBuffered).toBe(0);
    });

    it('clears events and reconnects when fixtureId changes', () => {
        const now = 1000000;
        vi.spyOn(Date, 'now').mockReturnValue(now);

        let fixtureId = 'fixture-A';
        const { result, rerender } = renderHook(
            ({ offset, fId }) => useEventBuffer(offset, fId),
            { initialProps: { offset: 0, fId: fixtureId } }
        );

        // Receive an event on fixture-A
        act(() => {
            mockEventSourceInstance.onopen?.();
            mockEventSourceInstance.onmessage?.({
                data: JSON.stringify(createEvent({ eventId: 'ev-A', eventTimestamp: now - 5000 }))
            });
        });
        expect(result.current.totalBuffered).toBe(1);

        // Change to fixture-B — events should clear
        fixtureId = 'fixture-B';
        rerender({ offset: 0, fId: fixtureId });

        expect(result.current.totalBuffered).toBe(0);
    });
});
