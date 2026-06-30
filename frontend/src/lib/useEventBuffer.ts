// frontend/src/lib/useEventBuffer.ts
import { useState, useEffect, useMemo } from 'react';
import { SportEvent } from '@/types/event';
import { API_BASE_URL } from '@/lib/constants';

// We limit the buffer so we don't crash the user's browser if the stream runs for 10 hours
const MAX_BUFFER_SIZE = 500;

export function useEventBuffer(offsetSeconds: number) {
    // Store all events we've received from the backend
    const [events, setEvents] = useState<SportEvent[]>([]);
    const [isConnected, setIsConnected] = useState(false);

    useEffect(() => {
        // 1. Establish the Server-Sent Events (SSE) open connection
        const eventSource = new EventSource(`${API_BASE_URL}/events/stream`);

        eventSource.onopen = () => {
            setIsConnected(true);
            console.log('Connected to live event stream!');
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

        // 3. Cleanup: close connection when the user leaves the page
        return () => {
            eventSource.close();
        };
    }, []); // The empty array [] means this connection code only runs ONCE when the dashboard loads

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
