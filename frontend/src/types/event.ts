// frontend/src/types/event.ts
import { SportType } from '@/lib/constants';

export interface SportEvent {
    eventId: string;
    sportType: SportType;
    action: string;
    participants: Record<string, string>;
    eventTimestamp: number;
    rawPayload: string;
}
export type { SportType };

