// frontend/src/lib/api.ts
import { API_BASE_URL } from '@/lib/constants';
import { SportSummary } from '@/types/summary';

export async function fetchEventSummary(eventId: string): Promise<SportSummary | null> {
    try {
        const response = await fetch(`${API_BASE_URL}/summaries/event/${eventId}`);

        if (response.status === 404) {
            // The Lambda function and Amazon Bedrock (AI) might take 2-3 seconds to generate
            // the commentary. If we ask for it too quickly, the backend returns a 404 Not Found.
            // We return null so the UI knows to show a "Loading commentary..." state.
            return null;
        }

        if (!response.ok) {
            throw new Error(`Failed to fetch summary: ${response.statusText}`);
        }

        return await response.json() as SportSummary;
    } catch (error) {
        console.error(`Error fetching summary for event ${eventId}:`, error);
        return null;
    }
}
