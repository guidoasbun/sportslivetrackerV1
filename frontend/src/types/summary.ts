// frontend/src/types/summary.ts
import { SportType } from '@/lib/constants';

export interface SportSummary {
    summaryId: string;
    eventId: string;
    sportType: SportType;
    commentary: string;
    timestamp: number;
}
