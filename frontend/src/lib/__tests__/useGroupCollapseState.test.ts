import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useGroupCollapseState } from '../useGroupCollapseState';

describe('useGroupCollapseState', () => {
    beforeEach(() => {
        sessionStorage.clear();
    });

    afterEach(() => {
        sessionStorage.clear();
    });

    it('defaults all groups to expanded when no persisted state exists', () => {
        const { result } = renderHook(() =>
            useGroupCollapseState(['England - Premier League', 'Spain - La Liga'])
        );

        expect(result.current.isExpanded('England - Premier League')).toBe(true);
        expect(result.current.isExpanded('Spain - La Liga')).toBe(true);
    });

    it('reads persisted collapsed state from sessionStorage on mount', () => {
        sessionStorage.setItem('fixture-group-state-England - Premier League', 'collapsed');
        sessionStorage.setItem('fixture-group-state-Spain - La Liga', 'expanded');

        const { result } = renderHook(() =>
            useGroupCollapseState(['England - Premier League', 'Spain - La Liga'])
        );

        expect(result.current.isExpanded('England - Premier League')).toBe(false);
        expect(result.current.isExpanded('Spain - La Liga')).toBe(true);
    });

    it('toggles a group from expanded to collapsed', () => {
        const { result } = renderHook(() =>
            useGroupCollapseState(['England - Premier League'])
        );

        expect(result.current.isExpanded('England - Premier League')).toBe(true);

        act(() => {
            result.current.toggle('England - Premier League');
        });

        expect(result.current.isExpanded('England - Premier League')).toBe(false);
    });

    it('toggles a group from collapsed to expanded', () => {
        sessionStorage.setItem('fixture-group-state-England - Premier League', 'collapsed');

        const { result } = renderHook(() =>
            useGroupCollapseState(['England - Premier League'])
        );

        expect(result.current.isExpanded('England - Premier League')).toBe(false);

        act(() => {
            result.current.toggle('England - Premier League');
        });

        expect(result.current.isExpanded('England - Premier League')).toBe(true);
    });

    it('writes new state to sessionStorage on toggle', () => {
        const { result } = renderHook(() =>
            useGroupCollapseState(['England - Premier League'])
        );

        act(() => {
            result.current.toggle('England - Premier League');
        });

        expect(sessionStorage.getItem('fixture-group-state-England - Premier League')).toBe('collapsed');

        act(() => {
            result.current.toggle('England - Premier League');
        });

        expect(sessionStorage.getItem('fixture-group-state-England - Premier League')).toBe('expanded');
    });

    it('uses correct key format: fixture-group-state-${groupLabel}', () => {
        const { result } = renderHook(() =>
            useGroupCollapseState(['France - Ligue 1'])
        );

        act(() => {
            result.current.toggle('France - Ligue 1');
        });

        expect(sessionStorage.getItem('fixture-group-state-France - Ligue 1')).toBe('collapsed');
    });

    it('handles unknown labels by defaulting to expanded', () => {
        const { result } = renderHook(() =>
            useGroupCollapseState(['England - Premier League'])
        );

        // Query a label not in the initial list
        expect(result.current.isExpanded('Italy - Serie A')).toBe(true);
    });

    it('gracefully handles sessionStorage unavailability', () => {
        // Save a reference to the real sessionStorage
        const realSessionStorage = window.sessionStorage;

        // Mock sessionStorage to throw on all operations
        const brokenStorage = {
            getItem: () => { throw new Error('SecurityError'); },
            setItem: () => { throw new Error('SecurityError'); },
            removeItem: () => { throw new Error('SecurityError'); },
            clear: () => { throw new Error('SecurityError'); },
            length: 0,
            key: () => null,
        };

        Object.defineProperty(window, 'sessionStorage', {
            value: brokenStorage,
            writable: true,
            configurable: true,
        });

        try {
            const { result } = renderHook(() =>
                useGroupCollapseState(['England - Premier League'])
            );

            // Should default to expanded
            expect(result.current.isExpanded('England - Premier League')).toBe(true);

            // Toggle should work in memory without throwing
            act(() => {
                result.current.toggle('England - Premier League');
            });

            expect(result.current.isExpanded('England - Premier League')).toBe(false);
        } finally {
            // Restore real sessionStorage before afterEach runs
            Object.defineProperty(window, 'sessionStorage', {
                value: realSessionStorage,
                writable: true,
                configurable: true,
            });
        }
    });

    it('maintains independent state for multiple groups', () => {
        const { result } = renderHook(() =>
            useGroupCollapseState(['Group A', 'Group B', 'Group C'])
        );

        act(() => {
            result.current.toggle('Group B');
        });

        expect(result.current.isExpanded('Group A')).toBe(true);
        expect(result.current.isExpanded('Group B')).toBe(false);
        expect(result.current.isExpanded('Group C')).toBe(true);
    });
});
