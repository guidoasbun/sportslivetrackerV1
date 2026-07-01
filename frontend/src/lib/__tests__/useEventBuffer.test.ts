import { renderHook, act } from '@testing-library/react';
import { useEventBuffer } from '@/lib/useEventBuffer';
import { SportEvent } from '@/types/event';

// Mock EventSource — track all instances for reconnection tests
let mockEventSourceInstance: {
    onopen: (() => void) | null;
    onmessage: ((event: { data: string }) => void) | null;
    onerror: ((error: Event) => void) | null;
    close: ReturnType<typeof vi.fn>;
};
let mockEventSourceInstances: typeof mockEventSourceInstance[] = [];

class MockEventSource {
    onopen: (() => void) | null = null;
    onmessage: ((event: { data: string }) => void) | null = null;
    onerror: ((error: Event) => void) | null = null;
    close = vi.fn();

    constructor(_url: string) {
        mockEventSourceInstance = this;
        mockEventSourceInstances.push(this);
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
        mockEventSourceInstances = [];
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

describe('useEventBuffer reconnection', () => {
    beforeEach(() => {
        vi.useFakeTimers();
        vi.stubGlobal('EventSource', MockEventSource);
        mockEventSourceInstances = [];
    });

    afterEach(() => {
        vi.useRealTimers();
        vi.unstubAllGlobals();
        vi.restoreAllMocks();
    });

    it('transitions to reconnecting state on SSE error and increments attempt', () => {
        const { result } = renderHook(() => useEventBuffer(0, TEST_FIXTURE_ID));

        // Initial connection established
        act(() => {
            mockEventSourceInstance.onopen?.();
        });
        expect(result.current.reconnectionState).toEqual({ status: 'connected', attempt: 0 });

        // Simulate connection error
        act(() => {
            mockEventSourceInstance.onerror?.(new Event('error'));
        });

        expect(result.current.isConnected).toBe(false);
        expect(result.current.reconnectionState).toEqual({ status: 'reconnecting', attempt: 1 });
    });

    it('schedules reconnection with exponential backoff delays', () => {
        const { result } = renderHook(() => useEventBuffer(0, TEST_FIXTURE_ID));

        act(() => {
            mockEventSourceInstance.onopen?.();
        });

        // 1st error → attempt 1, delay 1000ms
        act(() => {
            mockEventSourceInstance.onerror?.(new Event('error'));
        });
        expect(result.current.reconnectionState.attempt).toBe(1);
        expect(mockEventSourceInstances).toHaveLength(1);

        // Advance 1000ms — should create a new EventSource (attempt 1 backoff)
        act(() => {
            vi.advanceTimersByTime(1000);
        });
        expect(mockEventSourceInstances).toHaveLength(2);

        // 2nd error → attempt 2, delay 2000ms
        act(() => {
            mockEventSourceInstance.onerror?.(new Event('error'));
        });
        expect(result.current.reconnectionState.attempt).toBe(2);

        // Advance 1999ms — not enough yet
        act(() => {
            vi.advanceTimersByTime(1999);
        });
        expect(mockEventSourceInstances).toHaveLength(2);

        // Advance 1 more ms — now should reconnect
        act(() => {
            vi.advanceTimersByTime(1);
        });
        expect(mockEventSourceInstances).toHaveLength(3);
    });

    it('caps backoff delay at 30 seconds', () => {
        const { result } = renderHook(() => useEventBuffer(0, TEST_FIXTURE_ID));

        act(() => {
            mockEventSourceInstance.onopen?.();
        });

        // Fail multiple times to reach high attempt numbers
        // Attempt 6: delay = min(1000 * 2^5, 30000) = 32000 → capped at 30000
        for (let i = 0; i < 5; i++) {
            act(() => {
                mockEventSourceInstance.onerror?.(new Event('error'));
            });
            act(() => {
                vi.advanceTimersByTime(30000); // always enough to trigger next
            });
        }

        // Now at attempt 6 after the 5th error + reconnect
        act(() => {
            mockEventSourceInstance.onerror?.(new Event('error'));
        });
        expect(result.current.reconnectionState.attempt).toBe(6);

        const instancesBefore = mockEventSourceInstances.length;

        // Advance 29999ms — should NOT reconnect yet (cap is 30000)
        act(() => {
            vi.advanceTimersByTime(29999);
        });
        expect(mockEventSourceInstances).toHaveLength(instancesBefore);

        // Advance 1 more ms — should reconnect
        act(() => {
            vi.advanceTimersByTime(1);
        });
        expect(mockEventSourceInstances).toHaveLength(instancesBefore + 1);
    });

    it('transitions to failed after 10 consecutive attempts', () => {
        const { result } = renderHook(() => useEventBuffer(0, TEST_FIXTURE_ID));

        act(() => {
            mockEventSourceInstance.onopen?.();
        });

        // Fail 10 times with timer advances between each
        for (let i = 1; i <= 10; i++) {
            act(() => {
                mockEventSourceInstance.onerror?.(new Event('error'));
            });

            if (i < 10) {
                expect(result.current.reconnectionState.status).toBe('reconnecting');
                act(() => {
                    vi.advanceTimersByTime(30000);
                });
            }
        }

        // After 10th error + advance, the 11th error triggers 'failed'
        act(() => {
            vi.advanceTimersByTime(30000);
        });
        act(() => {
            mockEventSourceInstance.onerror?.(new Event('error'));
        });

        expect(result.current.reconnectionState).toEqual({ status: 'failed', attempt: 10 });
    });

    it('resets attempts and reconnects immediately on manual reconnect()', () => {
        const { result } = renderHook(() => useEventBuffer(0, TEST_FIXTURE_ID));

        act(() => {
            mockEventSourceInstance.onopen?.();
        });

        // Fail a few times
        for (let i = 0; i < 3; i++) {
            act(() => {
                mockEventSourceInstance.onerror?.(new Event('error'));
            });
            act(() => {
                vi.advanceTimersByTime(30000);
            });
        }
        expect(result.current.reconnectionState.attempt).toBe(3);

        const instancesBefore = mockEventSourceInstances.length;

        // Call reconnect() manually
        act(() => {
            result.current.reconnect();
        });

        // Should immediately create a new EventSource (no timer needed)
        expect(mockEventSourceInstances).toHaveLength(instancesBefore + 1);
        expect(result.current.reconnectionState).toEqual({ status: 'reconnecting', attempt: 1 });

        // Simulate successful connection
        act(() => {
            mockEventSourceInstance.onopen?.();
        });
        expect(result.current.reconnectionState).toEqual({ status: 'connected', attempt: 0 });
        expect(result.current.isConnected).toBe(true);
    });

    it('resets reconnection state on successful connection after errors', () => {
        const { result } = renderHook(() => useEventBuffer(0, TEST_FIXTURE_ID));

        act(() => {
            mockEventSourceInstance.onopen?.();
        });

        // Error occurs
        act(() => {
            mockEventSourceInstance.onerror?.(new Event('error'));
        });
        expect(result.current.reconnectionState.status).toBe('reconnecting');

        // Timer fires, new connection established
        act(() => {
            vi.advanceTimersByTime(1000);
        });
        act(() => {
            mockEventSourceInstance.onopen?.();
        });

        expect(result.current.reconnectionState).toEqual({ status: 'connected', attempt: 0 });
        expect(result.current.isConnected).toBe(true);
    });
});
