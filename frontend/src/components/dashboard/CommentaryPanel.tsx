"use client";

// frontend/src/components/dashboard/CommentaryPanel.tsx
import { useState, useEffect, useRef } from 'react';
import { fetchEventSummary } from '@/lib/api';
import { SportSummary } from '@/types/summary';
import { useTtsPreference } from '@/lib/useTtsPreference';
import { useAudioQueue } from '@/lib/useAudioQueue';
import TtsToggle from '@/components/dashboard/TtsToggle';
import PlaybackControls from '@/components/dashboard/PlaybackControls';

interface CommentaryPanelProps {
    eventId: string;
}

const MAX_POLL_ATTEMPTS = 20; // Stop after ~50 seconds (20 * 2.5s)

export default function CommentaryPanel({ eventId }: CommentaryPanelProps) {
    const [summary, setSummary] = useState<SportSummary | null>(null);
    const [isGenerating, setIsGenerating] = useState(true);
    const [gaveUp, setGaveUp] = useState(false);

    const { enabled } = useTtsPreference();
    const { enqueue, skip, stopAll, state } = useAudioQueue(enabled);

    // Track which commentary texts have already been sent for synthesis
    const synthesizedRef = useRef<Set<string>>(new Set());

    useEffect(() => {
        let interval: NodeJS.Timeout | undefined;
        let attempts = 0;

        const checkSummary = async () => {
            attempts++;
            const data = await fetchEventSummary(eventId);
            if (data) {
                setSummary(data);
                setIsGenerating(false);
                if (interval) clearInterval(interval);
            } else if (attempts >= MAX_POLL_ATTEMPTS) {
                setIsGenerating(false);
                setGaveUp(true);
                if (interval) clearInterval(interval);
            }
        };

        // Reset state when eventId changes
        setSummary(null);
        setIsGenerating(true);
        setGaveUp(false);
        synthesizedRef.current = new Set();

        checkSummary();
        interval = setInterval(checkSummary, 2500);

        return () => {
            if (interval) clearInterval(interval);
        };
    }, [eventId]);

    // TTS synthesis effect: when commentary arrives and TTS is enabled, synthesize
    useEffect(() => {
        const commentary = summary?.commentary;
        if (!commentary || !enabled) return;

        // Don't re-synthesize text we've already sent
        if (synthesizedRef.current.has(commentary)) return;
        synthesizedRef.current.add(commentary);

        const controller = new AbortController();

        (async () => {
            try {
                const response = await fetch('/api/auth/tts/synthesize', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    credentials: 'include',
                    body: JSON.stringify({ text: commentary }),
                    signal: AbortSignal.timeout(10000),
                });

                if (!response.ok) {
                    console.warn(`TTS synthesis failed with status ${response.status}`);
                    return;
                }

                const blob = await response.blob();
                enqueue(commentary, blob);
            } catch (error) {
                console.warn('TTS synthesis error, skipping commentary:', error);
            }
        })();

        return () => {
            controller.abort();
        };
    }, [summary?.commentary, enabled, enqueue]);

    if (gaveUp) {
        return (
            <div className="mt-4 p-3 bg-slate-900/50 rounded-lg border border-slate-700/30">
                <span className="text-xs text-slate-500 italic">Commentary unavailable for this event.</span>
            </div>
        );
    }

    if (isGenerating) {
        return (
            <div className="mt-4 p-3 bg-slate-900/50 rounded-lg border border-slate-700/30 flex items-center gap-3">
                <div className="w-4 h-4 border-2 border-indigo-500 border-t-transparent rounded-full animate-spin" />
                <span className="text-xs text-slate-400 italic">AI is analyzing the play...</span>
            </div>
        );
    }

    return (
        <div className="mt-4 p-4 bg-indigo-900/20 rounded-lg border border-indigo-500/30">
            <div className="flex items-center gap-2 mb-2">
                <span className="text-xs font-bold text-indigo-400 uppercase tracking-wider">AI Commentary</span>
                <svg className="w-4 h-4 text-indigo-400" fill="currentColor" viewBox="0 0 24 24">
                    <path d="M12 2L15.09 8.26L22 9.27L17 14.14L18.18 21.02L12 17.77L5.82 21.02L7 14.14L2 9.27L8.91 8.26L12 2Z" />
                </svg>
                <TtsToggle />
            </div>
            <p className="text-slate-300 text-sm leading-relaxed">
                {summary?.commentary}
            </p>
            <div className="mt-2">
                <PlaybackControls
                    isPlaying={state.isPlaying}
                    queueLength={state.queue.length}
                    onSkip={skip}
                    onStopAll={stopAll}
                    onPlayNext={skip}
                />
            </div>
        </div>
    );
}
