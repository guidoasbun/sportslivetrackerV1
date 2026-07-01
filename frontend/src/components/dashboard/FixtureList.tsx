"use client";

import { useState, useEffect, useCallback } from 'react';
import { API_BASE_URL } from '@/lib/constants';

interface Fixture {
    fixtureId: string;
    sportType: string;
    participants: Record<string, string>;
    status: string;
    startTime: number;
}

interface FixtureListProps {
    sport: string;
    selectedFixtureId: string | null;
    onSelectFixture: (fixtureId: string) => void;
}

export default function FixtureList({ sport, selectedFixtureId, onSelectFixture }: FixtureListProps) {
    const [fixtures, setFixtures] = useState<Fixture[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    const fetchFixtures = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            const response = await fetch(`${API_BASE_URL}/fixtures?sport=${sport}`);
            if (!response.ok) {
                throw new Error(`Failed to fetch fixtures (${response.status})`);
            }
            const data: Fixture[] = await response.json();
            setFixtures(data);
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Failed to load fixtures');
        } finally {
            setLoading(false);
        }
    }, [sport]);

    useEffect(() => {
        fetchFixtures();
    }, [fetchFixtures]);

    function getStatusIndicator(status: string) {
        const normalized = status.toLowerCase();
        if (normalized === 'live' || normalized === 'in_progress') {
            return { color: '#10b981', label: 'Live', bgColor: 'rgba(16, 185, 129, 0.15)' };
        }
        if (normalized === 'scheduled' || normalized === 'upcoming') {
            return { color: '#f59e0b', label: 'Scheduled', bgColor: 'rgba(245, 158, 11, 0.15)' };
        }
        return { color: '#6b7280', label: 'Finished', bgColor: 'rgba(107, 114, 128, 0.15)' };
    }

    function formatStartTime(timestamp: number) {
        return new Date(timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    }

    if (loading) {
        return (
            <div style={{
                display: 'flex',
                gap: '12px',
                overflowX: 'auto',
                padding: '16px 20px',
                background: 'rgba(255,255,255,0.02)',
                borderRadius: '12px',
                border: '1px solid rgba(255,255,255,0.05)'
            }}>
                {[1, 2, 3].map(i => (
                    <div key={i} style={{
                        minWidth: '200px',
                        height: '80px',
                        background: 'rgba(255,255,255,0.05)',
                        borderRadius: '12px',
                        animation: 'pulse 1.5s ease-in-out infinite'
                    }} />
                ))}
            </div>
        );
    }

    if (error) {
        return (
            <div style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                gap: '12px',
                padding: '20px',
                background: 'rgba(239, 68, 68, 0.1)',
                borderRadius: '12px',
                border: '1px solid rgba(239, 68, 68, 0.2)'
            }}>
                <span style={{ color: '#ef4444', fontSize: '14px' }}>{error}</span>
                <button
                    onClick={fetchFixtures}
                    style={{
                        padding: '6px 16px',
                        borderRadius: '8px',
                        border: '1px solid rgba(239, 68, 68, 0.4)',
                        background: 'rgba(239, 68, 68, 0.2)',
                        color: '#ef4444',
                        cursor: 'pointer',
                        fontSize: '13px',
                        fontWeight: 500
                    }}
                >
                    Retry
                </button>
            </div>
        );
    }

    if (fixtures.length === 0) {
        return (
            <div style={{
                padding: '20px',
                textAlign: 'center',
                color: '#64748b',
                fontSize: '14px',
                background: 'rgba(255,255,255,0.02)',
                borderRadius: '12px',
                border: '1px solid rgba(255,255,255,0.05)'
            }}>
                No fixtures available for {sport}
            </div>
        );
    }

    return (
        <div style={{
            display: 'flex',
            gap: '12px',
            overflowX: 'auto',
            padding: '16px 20px',
            background: 'rgba(255,255,255,0.02)',
            borderRadius: '12px',
            border: '1px solid rgba(255,255,255,0.05)'
        }}>
            {fixtures.map(fixture => {
                const isSelected = fixture.fixtureId === selectedFixtureId;
                const statusInfo = getStatusIndicator(fixture.status);
                const participants = Object.values(fixture.participants);

                return (
                    <button
                        key={fixture.fixtureId}
                        onClick={() => onSelectFixture(fixture.fixtureId)}
                        style={{
                            minWidth: '220px',
                            padding: '14px 18px',
                            borderRadius: '12px',
                            border: isSelected
                                ? '2px solid #00b4db'
                                : '1px solid rgba(255,255,255,0.1)',
                            background: isSelected
                                ? 'rgba(0, 180, 219, 0.1)'
                                : 'rgba(255,255,255,0.03)',
                            cursor: 'pointer',
                            textAlign: 'left',
                            transition: 'all 0.2s ease',
                            boxShadow: isSelected ? '0 4px 12px rgba(0, 180, 219, 0.2)' : 'none'
                        }}
                    >
                        {/* Participants */}
                        <div style={{ color: 'white', fontSize: '14px', fontWeight: 600, marginBottom: '8px' }}>
                            {participants.length >= 2
                                ? `${participants[0]} vs ${participants[1]}`
                                : participants.join(', ') || 'TBD'}
                        </div>

                        {/* Status and time */}
                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                            <span style={{
                                display: 'inline-flex',
                                alignItems: 'center',
                                gap: '4px',
                                padding: '2px 8px',
                                borderRadius: '6px',
                                fontSize: '11px',
                                fontWeight: 500,
                                color: statusInfo.color,
                                background: statusInfo.bgColor
                            }}>
                                <span style={{
                                    width: '6px',
                                    height: '6px',
                                    borderRadius: '50%',
                                    background: statusInfo.color
                                }} />
                                {statusInfo.label}
                            </span>
                            <span style={{ color: '#94a3b8', fontSize: '12px' }}>
                                {formatStartTime(fixture.startTime)}
                            </span>
                        </div>
                    </button>
                );
            })}
        </div>
    );
}
