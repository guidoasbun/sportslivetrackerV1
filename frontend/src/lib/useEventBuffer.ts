// frontend/src/lib/useEventBuffer.ts
import { useState, useEffect, useMemo } from 'react';
import { SportEvent } from '@/types/event';
import { API_BASE_URL } from '@/lib/constants';

// We limit the buffer so we don't crash the user's browser if the stream runs for 10 hours
const MAX_BUFFER_SIZE = 500;

export function useEventBuffer(offsetSeconds: number, fixtureId: string | null = null) {
    // Store all events we've received from the backend
    const [events, setEvents] = useState<SportEvent[]>([]);
    const [isConnected, setIsConnected] = useState(false);

    useEffect(() => {
        // If no fixtureId is provided, don't open the SSE connection
        if (!fixtureId) {
            setEvents([]);
            setIsConnected(false);
            return;
        }

        // Clear events when fixtureId changes (fresh start for new fixture)
        setEvents([]);

        // 1. Establish the Server-Sent Events (SSE) open connection with fixtureId
        const url = `${API_BASE_URL}/events/stream?fixtureId=${encodeURIComponent(fixtureId)}`;
        const eventSource = new EventSource(url);

        eventSource.onopen = () => {
            setIsConnected(true);
            console.log(`Connected to live event stream for fixture ${fixtureId}!`);
        };

        // 2. Listen for incoming data
        eventSource.onmessage = (message) => {
            try {
                const newEvent: SportEvent = JSON.parse(message.data);

                setEvents((prevEvents) => {
                    // Add the new event to the top of the list, and chop off the oldest ones
                    const updatedEvents = [newEvent, ...prevEvents];
                    return updatedEvents.slice(0, MAX_BUFFER_SIZE);
                });
            } catch (error) {
                console.error('Failed to parse event:', error);
            }
        };

        eventSource.onerror = (error) => {
            setIsConnected(false);
            console.error('SSE Connection Error:', error);
            eventSource.close();
        };

        // 3. Cleanup: close connection when the user leaves the page or fixtureId changes
        return () => {
            eventSource.close();
        };
    }, [fixtureId]); // Re-open the connection when fixtureId changes

    // 4. The Time-Travel Logic!
    const visibleEvents = useMemo(() => {
        const now = Date.now();
        return events.filter((event) => {
            // Calculate how many seconds ago this event ACTUALLY happened
            const secondsSinceEvent = (now - event.eventTimestamp) / 1000;

            // Only show the event if it's older than our broadcast delay offset
            return secondsSinceEvent >= offsetSeconds;
        });
    }, [events, offsetSeconds]); // Recalculate only if the events list or the slider offset changes

    return {
        visibleEvents,
        isConnected,
        totalBuffered: events.length
    };
}
