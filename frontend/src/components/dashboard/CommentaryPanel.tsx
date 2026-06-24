// frontend/src/components/dashboard/CommentaryPanel.tsx
import { useState, useEffect } from 'react';
import { fetchEventSummary } from '@/lib/api';
import { SportSummary } from '@/types/summary';

interface CommentaryPanelProps {
    eventId: string;
}

export default function CommentaryPanel({ eventId }: CommentaryPanelProps) {
    const [summary, setSummary] = useState<SportSummary | null>(null);
    const [isGenerating, setIsGenerating] = useState(true);

    useEffect(() => {
        let interval: NodeJS.Timeout | undefined;

        // We create a function to ask the backend for the AI summary
        const checkSummary = async () => {
            const data = await fetchEventSummary(eventId);
            if (data) {
                setSummary(data);
                setIsGenerating(false);
                // Once we have the data, we stop asking!
                if (interval) clearInterval(interval);
            }
        };

        // Try immediately
        checkSummary();

        // If we didn't get it on the first try, set up a loop to ask every 2.5 seconds
        interval = setInterval(checkSummary, 2500);

        // Cleanup the interval if the user leaves the page before the AI finishes
        return () => {
            if (interval) clearInterval(interval);
        };
    }, [eventId]);


    // While waiting for Bedrock, show a nice loading state
    if (isGenerating) {
        return (
            <div className="mt-4 p-3 bg-slate-900/50 rounded-lg border border-slate-700/30 flex items-center gap-3">
                <div className="w-4 h-4 border-2 border-indigo-500 border-t-transparent rounded-full animate-spin" />
                <span className="text-xs text-slate-400 italic">AI is analyzing the play...</span>
            </div>
        );
    }

    // Once the AI finishes, display the text!
    return (
        <div className="mt-4 p-4 bg-indigo-900/20 rounded-lg border border-indigo-500/30">
            <div className="flex items-center gap-2 mb-2">
                <span className="text-xs font-bold text-indigo-400 uppercase tracking-wider">AI Commentary</span>
                <svg className="w-4 h-4 text-indigo-400" fill="currentColor" viewBox="0 0 24 24">
                    <path d="M12 2L15.09 8.26L22 9.27L17 14.14L18.18 21.02L12 17.77L5.82 21.02L7 14.14L2 9.27L8.91 8.26L12 2Z" />
                </svg>
            </div>
            <p className="text-slate-300 text-sm leading-relaxed">
                {summary?.commentary}
            </p>
        </div>
    );
}
