"use client";

import { useState, useEffect } from 'react';
import OffsetSlider from '@/components/dashboard/OffsetSlider';
import EventFeed from '@/components/dashboard/EventFeed';
import CommentaryPanel from '@/components/dashboard/CommentaryPanel';
import FixtureList from '@/components/dashboard/FixtureList';
import EventFeedSkeleton from '@/components/dashboard/EventFeedSkeleton';
import CommentaryPanelSkeleton from '@/components/dashboard/CommentaryPanelSkeleton';
import { useEventBuffer } from '@/lib/useEventBuffer';
import { API_BASE_URL } from '@/lib/constants';

const SPORTS = ['SOCCER', 'FOOTBALL', 'BASKETBALL', 'BASEBALL', 'HOCKEY', 'FORMULA_1'];

export default function DashboardPage() {
    const [selectedSport, setSelectedSport] = useState<string>('SOCCER');
    const [selectedFixtureId, setSelectedFixtureId] = useState<string | null>(null);
    const [activeSports, setActiveSports] = useState<string[]>(SPORTS);
    const [liveSports, setLiveSports] = useState<string[]>([]);

    useEffect(() => {
        async function fetchActiveSports() {
            try {
                const response = await fetch(`${API_BASE_URL}/sports/active`);
                if (!response.ok) return;
                const data: string[] = await response.json();
                setLiveSports(data);
                if (Array.isArray(data) && data.length > 0) {
                    setActiveSports(data);
                    // If the currently selected sport is no longer active, reset to first active
                    setSelectedSport(prev => data.includes(prev) ? prev : data[0]);
                }
            } catch {
                // On failure, keep default (all sports) — graceful degradation
            }
        }
        fetchActiveSports();
    }, []);

    // Reset fixture selection when sport changes
    useEffect(() => {
        setSelectedFixtureId(null);
    }, [selectedSport]);

    // Start at 0 seconds delay (real-time)
    const [offsetSeconds, setOffsetSeconds] = useState<number>(0);

    // Pass fixtureId to useEventBuffer — no SSE connection when null
    const { visibleEvents, isConnected, reconnectionState, reconnect } = useEventBuffer(offsetSeconds, selectedFixtureId);

    // Filter the events locally so we only see the sport we clicked on!
    const filteredEvents = visibleEvents.filter(e => e.sportType === selectedSport);

    // Determine if we should show loading skeletons:
    // initial connection phase (not connected yet and not in failed state)
    const showSkeletons = selectedFixtureId && !isConnected && reconnectionState.status !== 'failed';

    return (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '32px' }}>

            {/* Reconnection Banner */}
            {reconnectionState.status === 'reconnecting' && (
                <div
                    role="alert"
                    style={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        padding: '12px 20px',
                        background: 'rgba(234, 179, 8, 0.15)',
                        border: '1px solid rgba(234, 179, 8, 0.4)',
                        borderRadius: '12px',
                        color: '#fbbf24',
                        fontSize: '14px',
                        fontWeight: 500,
                    }}
                >
                    <span>
                        ⚠️ Connection lost. Reconnecting... (attempt {reconnectionState.attempt})
                    </span>
                </div>
            )}

            {/* Persistent Error Banner with Reconnect Button */}
            {reconnectionState.status === 'failed' && (
                <div
                    role="alert"
                    style={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        padding: '12px 20px',
                        background: 'rgba(239, 68, 68, 0.15)',
                        border: '1px solid rgba(239, 68, 68, 0.4)',
                        borderRadius: '12px',
                        color: '#f87171',
                        fontSize: '14px',
                        fontWeight: 500,
                    }}
                >
                    <span>
                        ❌ Unable to connect to live event stream after {reconnectionState.attempt} attempts.
                    </span>
                    <button
                        onClick={reconnect}
                        style={{
                            padding: '8px 16px',
                            borderRadius: '8px',
                            border: '1px solid rgba(239, 68, 68, 0.5)',
                            background: 'rgba(239, 68, 68, 0.2)',
                            color: '#f87171',
                            fontWeight: 'bold',
                            cursor: 'pointer',
                            transition: 'all 0.2s ease',
                            fontSize: '13px',
                        }}
                    >
                        Reconnect
                    </button>
                </div>
            )}

            {/* Top Controls: Sport Selector and TV Delay Slider */}
            <div style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                flexWrap: 'wrap',
                gap: '24px',
                background: 'rgba(255,255,255,0.03)',
                padding: '20px',
                borderRadius: '16px',
                border: '1px solid rgba(255,255,255,0.05)'
            }}>
                <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap' }}>
                    {SPORTS.map(sport => {
                        const isSelected = selectedSport === sport;
                        const isLive = liveSports.includes(sport);
                        return (
                            <button
                                key={sport}
                                onClick={() => setSelectedSport(sport)}
                                style={{
                                    padding: '10px 20px',
                                    borderRadius: '24px',
                                    border: isSelected
                                        ? '2px solid transparent'
                                        : isLive
                                            ? '2px solid #10b981'
                                            : '1px solid rgba(255,255,255,0.1)',
                                    background: isSelected ? 'linear-gradient(90deg, #00b4db, #0083b0)' : 'transparent',
                                    color: 'white',
                                    fontWeight: isSelected ? 'bold' : 'normal',
                                    cursor: 'pointer',
                                    transition: 'all 0.2s ease',
                                    boxShadow: isSelected
                                        ? '0 4px 12px rgba(0, 180, 219, 0.3)'
                                        : isLive
                                            ? '0 0 8px rgba(16, 185, 129, 0.3)'
                                            : 'none',
                                    opacity: isLive || isSelected ? 1 : 0.5
                                }}
                            >
                                {sport}
                            </button>
                        );
                    })}
                </div>

                <OffsetSlider offsetSeconds={offsetSeconds} onChange={setOffsetSeconds} />
            </div>

            {/* Fixture List */}
            <FixtureList
                sport={selectedSport}
                selectedFixtureId={selectedFixtureId}
                onSelectFixture={setSelectedFixtureId}
            />

            {/* Main Content Grid — only shown when a fixture is selected */}
            {selectedFixtureId ? (
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 350px', gap: '24px', alignItems: 'start' }}>

                    {/* Left Side: Live Feed */}
                    <div style={{
                        background: 'rgba(255,255,255,0.02)',
                        border: '1px solid rgba(255,255,255,0.05)',
                        borderRadius: '16px',
                        padding: '24px'
                    }}>
                        <h3 style={{ fontSize: '22px', fontWeight: 'bold', marginBottom: '24px', color: '#00b4db' }}>
                            Live Feed
                        </h3>
                        {showSkeletons ? (
                            <EventFeedSkeleton />
                        ) : (
                            <EventFeed events={filteredEvents} />
                        )}
                    </div>

                    {/* Right Side: AI Commentary */}
                    <div style={{
                        background: 'rgba(255,255,255,0.02)',
                        border: '1px solid rgba(255,255,255,0.05)',
                        borderRadius: '16px',
                        padding: '24px',
                        position: 'sticky',
                        top: '24px'
                    }}>
                        <h3 style={{ fontSize: '22px', fontWeight: 'bold', marginBottom: '24px', color: '#00b4db' }}>
                            Color Commentary
                        </h3>

                        {showSkeletons ? (
                            <CommentaryPanelSkeleton />
                        ) : filteredEvents.length > 0 ? (
                            <CommentaryPanel eventId={filteredEvents[0].eventId} />
                        ) : (
                            <p style={{ color: '#888', fontStyle: 'italic', fontSize: '14px' }}>
                                Waiting for live events to generate AI commentary...
                            </p>
                        )}
                    </div>

                </div>
            ) : (
                <div style={{
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'center',
                    justifyContent: 'center',
                    padding: '48px 24px',
                    background: 'rgba(255,255,255,0.02)',
                    borderRadius: '16px',
                    border: '1px solid rgba(255,255,255,0.05)',
                    textAlign: 'center'
                }}>
                    <p style={{ color: '#94a3b8', fontSize: '16px', fontWeight: 500 }}>
                        Select a fixture above to start streaming events
                    </p>
                    <p style={{ color: '#64748b', fontSize: '13px', marginTop: '8px' }}>
                        Choose a sport and pick a match to see the live feed and AI commentary.
                    </p>
                </div>
            )}
        </div>
    );
}
