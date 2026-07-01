// frontend/src/lib/useEventBuffer.ts
import { useState, useEffect, useMemo, useCallback, useRef } from 'react';
import { SportEvent } from '@/types/event';
import { API_BASE_URL } from '@/lib/constants';

// We limit the buffer so we don't crash the user's browser if the stream runs for 10 hours
const MAX_BUFFER_SIZE = 500;

const MAX_RECONNECT_ATTEMPTS = 10;
const INITIAL_BACKOFF_MS = 1000;
const MAX_BACKOFF_MS = 30000;

export type ReconnectionStatus = 'connected' | 'reconnecting' | 'failed';

export interface ReconnectionState {
    status: ReconnectionStatus;
    attempt: number;
}

function getBackoffDelay(attempt: number): number {
    // delay = Math.min(1000 * 2^(attempt-1), 30000)
    return Math.min(INITIAL_BACKOFF_MS * Math.pow(2, attempt - 1), MAX_BACKOFF_MS);
}

export function useEventBuffer(offsetSeconds: number, fixtureId: string | null = null) {
    // Store all events we've received from the backend
    const [events, setEvents] = useState<SportEvent[]>([]);
    const [isConnected, setIsConnected] = useState(false);
    const [reconnectionState, setReconnectionState] = useState<ReconnectionState>({
        status: 'connected',
        attempt: 0,
    });

    // Refs for managing reconnection
    const reconnectTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
    const attemptRef = useRef(0);
    const eventSourceRef = useRef<EventSource | null>(null);
    const fixtureIdRef = useRef<string | null>(fixtureId);
    const mountedRef = useRef(true);

    // Keep fixtureIdRef in sync
    fixtureIdRef.current = fixtureId;

    const clearReconnectTimer = useCallback(() => {
        if (reconnectTimerRef.current !== null) {
            clearTimeout(reconnectTimerRef.current);
            reconnectTimerRef.current = null;
        }
    }, []);

    const connect = useCallback((fId: string) => {
        // Close any existing connection
        if (eventSourceRef.current) {
            eventSourceRef.current.close();
            eventSourceRef.current = null;
        }

        const url = `${API_BASE_URL}/events/stream?fixtureId=${encodeURIComponent(fId)}`;
        const eventSource = new EventSource(url);
        eventSourceRef.current = eventSource;

        eventSource.onopen = () => {
            if (!mountedRef.current) return;
            setIsConnected(true);
            // Reset reconnection state on successful connection
            attemptRef.current = 0;
            setReconnectionState({ status: 'connected', attempt: 0 });
            console.log(`Connected to live event stream for fixture ${fId}!`);
        };

        eventSource.onmessage = (message) => {
            if (!mountedRef.current) return;
            try {
                const newEvent: SportEvent = JSON.parse(message.data);

                setEvents((prevEvents) => {
                    const updatedEvents = [newEvent, ...prevEvents];
                    return updatedEvents.slice(0, MAX_BUFFER_SIZE);
                });
            } catch (error) {
                console.error('Failed to parse event:', error);
            }
        };

        eventSource.onerror = () => {
            if (!mountedRef.current) return;
            setIsConnected(false);
            eventSource.close();
            eventSourceRef.current = null;
            console.error('SSE Connection Error');

            // Check if fixture is still the same (it may have changed)
            if (fixtureIdRef.current !== fId) return;

            // Attempt reconnection with exponential backoff
            attemptRef.current += 1;
            const currentAttempt = attemptRef.current;

            if (currentAttempt > MAX_RECONNECT_ATTEMPTS) {
                setReconnectionState({ status: 'failed', attempt: currentAttempt - 1 });
                return;
            }

            setReconnectionState({ status: 'reconnecting', attempt: currentAttempt });

            const delay = getBackoffDelay(currentAttempt);
            reconnectTimerRef.current = setTimeout(() => {
                if (!mountedRef.current) return;
                if (fixtureIdRef.current === fId) {
                    connect(fId);
                }
            }, delay);
        };
    }, []);

    // Manual reconnect function — resets counter and immediately connects
    const reconnect = useCallback(() => {
        if (!fixtureIdRef.current) return;
        clearReconnectTimer();
        attemptRef.current = 0;
        setReconnectionState({ status: 'reconnecting', attempt: 1 });
        connect(fixtureIdRef.current);
    }, [connect, clearReconnectTimer]);

    useEffect(() => {
        mountedRef.current = true;

        // If no fixtureId is provided, don't open the SSE connection
        if (!fixtureId) {
            setEvents([]);
            setIsConnected(false);
            setReconnectionState({ status: 'connected', attempt: 0 });
            attemptRef.current = 0;
            clearReconnectTimer();
            if (eventSourceRef.current) {
                eventSourceRef.current.close();
                eventSourceRef.current = null;
            }
            return;
        }

        // Clear events when fixtureId changes (fresh start for new fixture)
        setEvents([]);
        attemptRef.current = 0;
        setReconnectionState({ status: 'connected', attempt: 0 });
        clearReconnectTimer();

        // Establish the SSE connection
        connect(fixtureId);

        // Cleanup: close connection when the user leaves the page or fixtureId changes
        return () => {
            mountedRef.current = false;
            clearReconnectTimer();
            if (eventSourceRef.current) {
                eventSourceRef.current.close();
                eventSourceRef.current = null;
            }
        };
    }, [fixtureId, connect, clearReconnectTimer]);

    // The Time-Travel Logic!
    const visibleEvents = useMemo(() => {
        const now = Date.now();
        return events.filter((event) => {
            const secondsSinceEvent = (now - event.eventTimestamp) / 1000;
            return secondsSinceEvent >= offsetSeconds;
        });
    }, [events, offsetSeconds]);

    return {
        visibleEvents,
        isConnected,
        totalBuffered: events.length,
        reconnectionState,
        reconnect,
    };
}
