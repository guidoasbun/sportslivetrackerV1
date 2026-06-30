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

vi.stubGlobal('EventSource', MockEventSource);

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

describe('useEventBuffer', () => {
    beforeEach(() => {
        vi.restoreAllMocks();
    });

    it('includes events older than offsetSeconds in visibleEvents', () => {
        const now = 1000000;
        vi.spyOn(Date, 'now').mockReturnValue(now);

        const offsetSeconds = 10;
        // Event happened 15 seconds ago — should be visible
        const oldEvent = createEvent({ eventId: 'old-1', eventTimestamp: now - 15000 });

        const { result } = renderHook(() => useEventBuffer(offsetSeconds));

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

        const { result } = renderHook(() => useEventBuffer(offsetSeconds));

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

        const { result } = renderHook(() => useEventBuffer(0));

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
});
