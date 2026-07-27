"use client";

import { KeyboardEvent } from 'react';

interface RegionGroupHeaderProps {
    label: string;
    fixtureCount: number;
    isExpanded: boolean;
    onToggle: () => void;
    groupId: string;
}

export default function RegionGroupHeader({
    label,
    fixtureCount,
    isExpanded,
    onToggle,
    groupId,
}: RegionGroupHeaderProps) {
    function handleKeyDown(e: KeyboardEvent<HTMLDivElement>) {
        if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            onToggle();
        }
    }

    return (
        <div
            role="button"
            tabIndex={0}
            aria-expanded={isExpanded}
            aria-controls={groupId}
            onClick={onToggle}
            onKeyDown={handleKeyDown}
            style={{
                display: 'flex',
                alignItems: 'center',
                gap: '8px',
                padding: '10px 16px',
                cursor: 'pointer',
                borderRadius: '8px',
                background: 'rgba(255,255,255,0.03)',
                border: '1px solid rgba(255,255,255,0.08)',
                userSelect: 'none',
                transition: 'background 0.15s ease',
            }}
        >
            {/* Chevron icon */}
            <svg
                width="16"
                height="16"
                viewBox="0 0 16 16"
                fill="none"
                style={{
                    transform: isExpanded ? 'rotate(90deg)' : 'rotate(0deg)',
                    transition: 'transform 0.2s ease',
                    flexShrink: 0,
                }}
                aria-hidden="true"
            >
                <path
                    d="M6 4L10 8L6 12"
                    stroke="#94a3b8"
                    strokeWidth="2"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                />
            </svg>

            {/* Group label */}
            <span style={{ color: '#e2e8f0', fontSize: '13px', fontWeight: 600 }}>
                {label}
            </span>

            {/* Fixture count shown when collapsed */}
            {!isExpanded && (
                <span style={{ color: '#64748b', fontSize: '12px', fontWeight: 500 }}>
                    ({fixtureCount})
                </span>
            )}
        </div>
    );
}
